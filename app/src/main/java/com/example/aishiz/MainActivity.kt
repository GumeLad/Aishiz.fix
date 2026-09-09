package com.example.aishiz

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aishiz.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AishizPrefs
    private lateinit var chatStore: ChatStore
    private lateinit var appSettings: AppSettings

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var modelAdapter: ModelAdapter
    private lateinit var chatSessionAdapter: ChatSessionAdapter

    private var currentChatId: String? = null

    private var generationJob: Job? = null
    private var tokenFlushJob: Job? = null
    private val tokenBuffer = StringBuilder()
    private var streamedAssistantText = ""

    private var activeNativeRequestId = 0L
    private var generationSerial = 0L
    private var isGenerating = false

    private val nativeAvailable: Boolean by lazy {
        try {
            NativeLlamaBridge.hashCode()
            true
        } catch (_: Throwable) {
            false
        }
    }

    private val pickModel =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult

            val name = getDisplayName(uri) ?: "Model"
            if (!name.endsWith(".gguf", ignoreCase = true)) {
                Toast.makeText(this, R.string.gguf_only, Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some document providers grant access without persistable permissions.
            }

            val model = prefs.addModel(name, uri.toString())
            prefs.setSelectedModelId(model.id)

            refreshModelsUi()
            loadParamsIntoUi()
            updateActiveModelLabel()

            binding.leftDrawerTabs.getTabAt(0)?.select()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AishizPrefs(this)
        chatStore = ChatStore(this)
        appSettings = AppSettings(this)

        setSupportActionBar(binding.toolbar)

        setupChatUi()
        setupModelsUi()
        setupChatSessionsUi()
        setupLeftDrawerTabs()
        setupActions()

        refreshModelsUi()
        loadParamsIntoUi()
        updateActiveModelLabel()

        handleLaunchIntent()
        setGenerating(false)
    }

    private fun setupChatUi() {
        chatAdapter = ChatAdapter(messages)
        binding.messages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.messages.adapter = chatAdapter
        binding.messages.itemAnimator = null
        binding.messages.setItemViewCacheSize(12)
    }

    private fun setupModelsUi() {
        modelAdapter = ModelAdapter(
            models = mutableListOf(),
            selectedId = prefs.getSelectedModelId(),
            onSelect = { model ->
                prefs.setSelectedModelId(model.id)
                refreshModelsUi()
                loadParamsIntoUi()
                updateActiveModelLabel()
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        )

        binding.modelsList.layoutManager = LinearLayoutManager(this)
        binding.modelsList.adapter = modelAdapter
        binding.modelsList.itemAnimator = null
    }

    private fun setupChatSessionsUi() {
        chatSessionAdapter = ChatSessionAdapter(
            onSelect = { session ->
                loadChat(session.id)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            },
            onDelete = { session ->
                confirmDeleteChat(session)
            }
        )

        binding.chatsList.layoutManager = LinearLayoutManager(this)
        binding.chatsList.adapter = chatSessionAdapter
        binding.chatsList.itemAnimator = null

        binding.newChatButton.setOnClickListener {
            createNewChat()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private fun setupLeftDrawerTabs() {
        binding.leftDrawerTabs.removeAllTabs()
        binding.leftDrawerTabs.addTab(
            binding.leftDrawerTabs.newTab().setText(R.string.models)
        )
        binding.leftDrawerTabs.addTab(
            binding.leftDrawerTabs.newTab().setText(R.string.chats)
        )

        showLeftPanel(TAB_MODELS)

        binding.leftDrawerTabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    showLeftPanel(if (tab.position == 1) TAB_CHATS else TAB_MODELS)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            }
        )
    }

    private fun showLeftPanel(tab: String) {
        val showChats = tab == TAB_CHATS
        binding.modelsPanel.visibility = if (showChats) View.GONE else View.VISIBLE
        binding.chatsPanel.visibility = if (showChats) View.VISIBLE else View.GONE

        if (showChats) refreshChatSessionsUi()
    }

    private fun setupActions() {
        binding.addModelButton.setOnClickListener {
            pickModel.launch(arrayOf("application/octet-stream", "*/*"))
        }

        binding.removeModelButton.setOnClickListener {
            val selected = modelAdapter.getSelected() ?: return@setOnClickListener
            prefs.removeModel(selected.id)

            lifecycleScope.launch(Dispatchers.IO) {
                ModelStorage.deleteLocalModel(this@MainActivity, selected.id)
            }

            refreshModelsUi()
            loadParamsIntoUi()
            updateActiveModelLabel()
        }

        binding.sendButton.setOnClickListener { onSend() }
        binding.stopButton.setOnClickListener { stopGeneration(savePartial = true) }

        binding.resetParamsButton.setOnClickListener {
            val selected = modelAdapter.getSelected() ?: return@setOnClickListener
            prefs.saveParams(selected.id, InferenceParams())
            loadParamsIntoUi()
        }

        binding.saveParamsButton.setOnClickListener {
            val selected = modelAdapter.getSelected() ?: return@setOnClickListener
            prefs.saveParams(selected.id, readParamsFromUi())
            Toast.makeText(this, R.string.params_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleLaunchIntent() {
        if (intent.getBooleanExtra(EXTRA_NEW_CHAT, false)) {
            createNewChat()
        } else {
            val requested = intent.getStringExtra(EXTRA_CHAT_ID)
            if (requested != null && chatStore.listSessions().any { it.id == requested }) {
                loadChat(requested)
            } else {
                ensureChatSelected()
            }
        }

        when (intent.getStringExtra(EXTRA_OPEN_LEFT_TAB)) {
            TAB_CHATS -> {
                binding.leftDrawerTabs.getTabAt(1)?.select()
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
            TAB_MODELS -> {
                binding.leftDrawerTabs.getTabAt(0)?.select()
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // Prevent a configuration change from replaying "New chat".
        intent.removeExtra(EXTRA_NEW_CHAT)
        intent.removeExtra(EXTRA_CHAT_ID)
        intent.removeExtra(EXTRA_OPEN_LEFT_TAB)
    }

    private fun ensureChatSelected() {
        val sessions = chatStore.listSessions()
        val selected = chatStore.getSelectedSessionId()

        val sessionId = when {
            selected != null && sessions.any { it.id == selected } -> selected
            sessions.isNotEmpty() -> sessions.first().id
            else -> chatStore.createSession().id
        }

        loadChat(sessionId)
    }

    private fun createNewChat() {
        stopGeneration(savePartial = true)
        val session = chatStore.createSession()
        loadChat(session.id)
    }

    private fun loadChat(sessionId: String) {
        stopGeneration(savePartial = true)

        currentChatId = sessionId
        chatStore.setSelectedSessionId(sessionId)

        chatAdapter.replaceAll(chatStore.loadMessages(sessionId))
        refreshChatSessionsUi()
        scrollToBottom()
    }

    private fun refreshChatSessionsUi() {
        chatSessionAdapter.submitItems(
            chatStore.listSessions(),
            currentChatId
        )
    }

    private fun confirmDeleteChat(session: ChatSession) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_chat)
            .setMessage(R.string.delete_chat_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val deletingCurrent = session.id == currentChatId
                chatStore.deleteSession(session.id)

                if (deletingCurrent) {
                    val next = chatStore.listSessions().firstOrNull()
                    if (next != null) {
                        loadChat(next.id)
                    } else {
                        createNewChat()
                    }
                } else {
                    refreshChatSessionsUi()
                }
            }
            .show()
    }

    private fun clearCurrentChat() {
        val id = currentChatId ?: return
        if (messages.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.clear_chat)
            .setMessage(R.string.delete_chat_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear) { _, _ ->
                stopGeneration(savePartial = false)
                chatStore.clearMessages(id)
                chatAdapter.replaceAll(emptyList())
                refreshChatSessionsUi()
            }
            .show()
    }

    private fun onSend() {
        if (isGenerating) return

        val prompt = binding.promptInput.text?.toString()?.trim().orEmpty()
        if (prompt.isEmpty()) return

        val modelId = prefs.getSelectedModelId()
        val model = prefs.getModels().firstOrNull { it.id == modelId }

        if (model == null) {
            Toast.makeText(this, R.string.select_gguf_model, Toast.LENGTH_SHORT).show()
            binding.leftDrawerTabs.getTabAt(0)?.select()
            binding.drawerLayout.openDrawer(GravityCompat.START)
            return
        }

        val chatId = currentChatId ?: chatStore.createSession().id.also {
            currentChatId = it
        }

        val userMessage = ChatMessage(
            id = System.currentTimeMillis(),
            role = Role.USER,
            text = prompt
        )
        chatAdapter.add(userMessage)
        chatStore.titleFromFirstUserMessage(chatId, prompt)
        chatStore.saveMessages(chatId, messages)

        binding.promptInput.text?.clear()

        val inferencePrompt = buildConversationPrompt()

        chatAdapter.add(
            ChatMessage(
                id = System.currentTimeMillis() + 1L,
                role = Role.ASSISTANT,
                text = ""
            )
        )

        scrollToBottom()
        refreshChatSessionsUi()

        val params = prefs.getParams(model.id)
        startNativeGeneration(model, params, inferencePrompt)
    }

    private fun startNativeGeneration(
        model: ModelInfo,
        params: InferenceParams,
        inferencePrompt: String
    ) {
        if (!nativeAvailable) {
            chatAdapter.updateLastAssistantText(getString(R.string.backend_unavailable))
            currentChatId?.let { chatStore.saveMessages(it, messages) }
            return
        }

        generationSerial += 1L
        val serial = generationSerial

        synchronized(tokenBuffer) {
            tokenBuffer.setLength(0)
        }
        streamedAssistantText = ""
        setGenerating(true)
        startTokenFlush(serial)

        generationJob = lifecycleScope.launch {
            try {
                binding.activeModelLabel.text = getString(R.string.preparing_model)

                val localModel = withContext(Dispatchers.IO) {
                    ModelStorage.ensureLocalModelFile(this@MainActivity, model)
                }

                if (serial != generationSerial || !isGenerating) return@launch

                updateActiveModelLabel()

                val requestId = NativeLlamaBridge.startGeneration(
                    modelPath = localModel.absolutePath,
                    prompt = inferencePrompt,
                    temperature = params.temperature,
                    topP = params.topP,
                    topK = params.topK,
                    minP = params.minP,
                    repeatPenalty = params.repeatPenalty,
                    maxTokens = params.maxTokens,
                    contextLength = params.contextLength,
                    batchSize = params.batchSize,
                    threads = params.threads,
                    seed = params.seed,
                    callback = object : NativeLlamaBridge.TokenCallback {
                        override fun onToken(tokenChunk: String) {
                            if (serial != generationSerial || !isGenerating) return
                            synchronized(tokenBuffer) {
                                tokenBuffer.append(tokenChunk)
                            }
                        }

                        override fun onComplete() {
                            runOnUiThread {
                                finishGeneration(serial, null)
                            }
                        }

                        override fun onError(message: String) {
                            runOnUiThread {
                                finishGeneration(serial, message)
                            }
                        }
                    }
                )

                if (requestId == 0L) {
                    finishGeneration(serial, getString(R.string.backend_unavailable))
                    return@launch
                }

                // A native callback can complete extremely quickly. If that happened
                // before startGeneration returned, don't retain a stale request id.
                if (serial != generationSerial || !isGenerating) {
                    runCatching { NativeLlamaBridge.stopGeneration(requestId) }
                    return@launch
                }

                activeNativeRequestId = requestId
            } catch (t: Throwable) {
                if (serial == generationSerial) {
                    finishGeneration(
                        serial,
                        t.message ?: getString(R.string.backend_unavailable)
                    )
                }
            }
        }
    }

    private fun startTokenFlush(serial: Long) {
        tokenFlushJob?.cancel()
        tokenFlushJob = lifecycleScope.launch {
            while (isActive && serial == generationSerial && isGenerating) {
                delay(appSettings.streamIntervalMs)
                flushTokenBuffer(serial)
            }
        }
    }

    private fun flushTokenBuffer(serial: Long) {
        if (serial != generationSerial) return

        val chunk = synchronized(tokenBuffer) {
            if (tokenBuffer.isEmpty()) return
            tokenBuffer.toString().also { tokenBuffer.setLength(0) }
        }

        val shouldScroll = appSettings.autoScroll && isNearBottom()
        streamedAssistantText += chunk
        chatAdapter.updateLastAssistantText(streamedAssistantText)

        if (shouldScroll) {
            scrollToBottom()
        }
    }

    private fun finishGeneration(serial: Long, error: String?) {
        if (serial != generationSerial || !isGenerating) return

        flushTokenBuffer(serial)
        tokenFlushJob?.cancel()
        tokenFlushJob = null
        generationJob = null
        activeNativeRequestId = 0L

        if (error != null) {
            val visible = if (streamedAssistantText.isBlank()) {
                "Error: $error"
            } else {
                "$streamedAssistantText\n\nError: $error"
            }
            streamedAssistantText = visible
            chatAdapter.updateLastAssistantText(visible)
        } else if (streamedAssistantText.isBlank()) {
            chatAdapter.removeLastAssistantIfEmpty()
        }

        currentChatId?.let { chatStore.saveMessages(it, messages) }
        refreshChatSessionsUi()

        setGenerating(false)
        generationSerial += 1L
    }

    private fun stopGeneration(savePartial: Boolean) {
        if (!isGenerating) {
            generationJob?.cancel()
            generationJob = null
            return
        }

        val serial = generationSerial

        if (activeNativeRequestId != 0L) {
            runCatching {
                NativeLlamaBridge.stopGeneration(activeNativeRequestId)
            }
        }

        generationJob?.cancel()
        generationJob = null

        flushTokenBuffer(serial)
        tokenFlushJob?.cancel()
        tokenFlushJob = null

        activeNativeRequestId = 0L

        if (savePartial) {
            if (streamedAssistantText.isBlank()) {
                chatAdapter.removeLastAssistantIfEmpty()
            }
            currentChatId?.let { chatStore.saveMessages(it, messages) }
        } else {
            chatAdapter.removeLastAssistantIfEmpty()
        }

        setGenerating(false)
        generationSerial += 1L
    }

    private fun setGenerating(active: Boolean) {
        isGenerating = active
        binding.generationProgress.visibility = if (active) View.VISIBLE else View.GONE
        binding.sendButton.isEnabled = !active
        binding.stopButton.isEnabled = active

        if (appSettings.keepScreenAwake && active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        if (!active) {
            updateActiveModelLabel()
        }
    }

    private fun buildConversationPrompt(): String {
        val usable = messages.filter { it.text.isNotBlank() }
        val builder = StringBuilder()

        usable.forEach { message ->
            when (message.role) {
                Role.USER -> {
                    builder.append("User:\n")
                    builder.append(message.text)
                    builder.append("\n\n")
                }
                Role.ASSISTANT -> {
                    builder.append("Assistant:\n")
                    builder.append(message.text)
                    builder.append("\n\n")
                }
            }
        }

        builder.append("Assistant:\n")
        return builder.toString()
    }

    private fun refreshModelsUi() {
        modelAdapter.setModels(
            prefs.getModels(),
            prefs.getSelectedModelId()
        )
    }

    private fun readParamsFromUi(): InferenceParams {
        return InferenceParams(
            temperature = binding.paramTemp.value.coerceIn(0.0f, 2.0f),
            topP = binding.paramTopP.value.coerceIn(0.0f, 1.0f),
            topK = binding.paramTopK.value.toInt().coerceIn(0, 200),
            minP = binding.paramMinP.value.coerceIn(0.0f, 1.0f),
            repeatPenalty = binding.paramRepeatPenalty.value.coerceIn(0.8f, 2.0f),
            maxTokens = (binding.paramMaxTokens.text?.toString()?.toIntOrNull() ?: 384)
                .coerceIn(1, 8192),
            contextLength = (binding.paramContext.text?.toString()?.toIntOrNull() ?: 2048)
                .coerceIn(512, 32768),
            batchSize = (binding.paramBatch.text?.toString()?.toIntOrNull() ?: 256)
                .coerceIn(32, 2048),
            threads = (binding.paramThreads.text?.toString()?.toIntOrNull() ?: 0)
                .coerceIn(0, 12),
            seed = binding.paramSeed.text?.toString()?.toIntOrNull() ?: -1
        )
    }

    private fun loadParamsIntoUi() {
        val modelId = prefs.getSelectedModelId()
        val p = if (modelId != null) prefs.getParams(modelId) else InferenceParams()

        binding.paramTemp.value = p.temperature
        binding.paramTopP.value = p.topP
        binding.paramTopK.value = p.topK.toFloat()
        binding.paramMinP.value = p.minP
        binding.paramRepeatPenalty.value = p.repeatPenalty
        binding.paramMaxTokens.setText(p.maxTokens.toString())
        binding.paramContext.setText(p.contextLength.toString())
        binding.paramBatch.setText(p.batchSize.toString())
        binding.paramThreads.setText(p.threads.toString())
        binding.paramSeed.setText(p.seed.toString())
    }

    private fun updateActiveModelLabel() {
        val modelId = prefs.getSelectedModelId()
        val model = prefs.getModels().firstOrNull { it.id == modelId }
        binding.activeModelLabel.text =
            model?.name ?: getString(R.string.no_model_selected)
    }

    private fun isNearBottom(): Boolean {
        if (chatAdapter.itemCount <= 1) return true
        val manager = binding.messages.layoutManager as? LinearLayoutManager ?: return true
        val lastVisible = manager.findLastVisibleItemPosition()
        return lastVisible >= chatAdapter.itemCount - 3
    }

    private fun scrollToBottom() {
        binding.messages.post {
            if (chatAdapter.itemCount > 0) {
                binding.messages.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_home -> {
                stopGeneration(savePartial = true)
                startActivity(Intent(this, HomeActivity::class.java))
                true
            }

            R.id.action_models -> {
                binding.leftDrawerTabs.getTabAt(0)?.select()
                binding.drawerLayout.openDrawer(GravityCompat.START)
                true
            }

            R.id.action_params -> {
                binding.drawerLayout.openDrawer(GravityCompat.END)
                true
            }

            R.id.action_clear_chat -> {
                clearCurrentChat()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        stopGeneration(savePartial = true)
        super.onDestroy()
    }
}
