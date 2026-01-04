package com.example.aishiz

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aishiz.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AishizPrefs

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    private lateinit var modelAdapter: ModelAdapter

    private var generationJob: Job? = null

    private var activeNativeRequestId: Long = 0L
    private val nativeAvailable: Boolean by lazy {
        try {
            // Triggers System.loadLibrary()
            NativeLlamaBridge.toString()
            true
        } catch (_: Throwable) {
            false
        }
    }

    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult

        // Persist permission so the URI keeps working after reboot
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Not all providers support persistable permissions.
        }

        val name = getDisplayName(uri) ?: "Model"
        val model = prefs.addModel(name, uri.toString())
        prefs.setSelectedModelId(model.id)
        refreshModelsUi()
        loadParamsIntoUi()
        updateActiveModelLabel()
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AishizPrefs(this)

        setSupportActionBar(binding.toolbar)

        chatAdapter = ChatAdapter(messages)
        binding.messages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.messages.adapter = chatAdapter

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

        binding.addModelButton.setOnClickListener {
            pickModel.launch(arrayOf("*/*"))
        }

        binding.removeModelButton.setOnClickListener {
            val selected = modelAdapter.getSelected()
            if (selected != null) {
                prefs.removeModel(selected.id)
                refreshModelsUi()
                loadParamsIntoUi()
                updateActiveModelLabel()
            }
        }

        binding.sendButton.setOnClickListener { onSend() }
        binding.stopButton.setOnClickListener { stopGeneration() }

        // Params buttons
        binding.resetParamsButton.setOnClickListener {
            val selected = modelAdapter.getSelected()
            if (selected != null) {
                val default = InferenceParams()
                prefs.saveParams(selected.id, default)
                loadParamsIntoUi()
            }
        }

        binding.saveParamsButton.setOnClickListener {
            val selected = modelAdapter.getSelected()
            if (selected != null) {
                val p = InferenceParams(
                    temperature = binding.paramTemp.value,
                    topP = binding.paramTopP.value,
                    topK = binding.paramTopK.value.toInt(),
                    repeatPenalty = binding.paramRepeatPenalty.value,
                    maxTokens = binding.paramMaxTokens.text.toString().toIntOrNull() ?: 256,
                    contextLength = binding.paramContext.text.toString().toIntOrNull() ?: 2048,
                    seed = binding.paramSeed.text.toString().toIntOrNull() ?: -1
                )
                prefs.saveParams(selected.id, p)
            }
        }

        refreshModelsUi()
        loadParamsIntoUi()
        updateActiveModelLabel()
    }

    private fun onSend() {
        val prompt = binding.promptInput.text.toString().trim()
        if (prompt.isEmpty()) return

        stopGeneration()

        // Add user message
        chatAdapter.add(ChatMessage(System.currentTimeMillis(), Role.USER, prompt))
        binding.promptInput.text?.clear()
        scrollToBottom()

        val modelId = prefs.getSelectedModelId()
        val model = prefs.getModels().firstOrNull { it.id == modelId }
        val params = if (modelId != null) prefs.getParams(modelId) else InferenceParams()

        // Add placeholder assistant message (we update it incrementally)
        chatAdapter.add(ChatMessage(System.currentTimeMillis() + 1, Role.ASSISTANT, ""))
        scrollToBottom()

        // Prefer native (JNI) streaming if available; fallback to Kotlin stub.
        val canUseNative = nativeAvailable && model != null
        if (canUseNative) {
            try {
                val localModelFile = ModelStorage.ensureLocalModelFile(this, model!!)
                val sb = StringBuilder()

                activeNativeRequestId = NativeLlamaBridge.startGeneration(
                    modelPath = localModelFile.absolutePath,
                    prompt = prompt,
                    temperature = params.temperature,
                    topP = params.topP,
                    topK = params.topK,
                    repeatPenalty = params.repeatPenalty,
                    maxTokens = params.maxTokens,
                    seed = params.seed,
                    callback = object : NativeLlamaBridge.TokenCallback {
                        override fun onToken(tokenChunk: String) {
                            runOnUiThread {
                                sb.append(tokenChunk)
                                chatAdapter.updateLastAssistantText(sb.toString())
                                scrollToBottom()
                            }
                        }

                        override fun onComplete() {
                            runOnUiThread {
                                activeNativeRequestId = 0L
                            }
                        }

                        override fun onError(message: String) {
                            runOnUiThread {
                                activeNativeRequestId = 0L
                                chatAdapter.updateLastAssistantText("Error: $message")
                                scrollToBottom()
                            }
                        }
                    }
                )
                return
            } catch (_: Throwable) {
                // Fall back to Kotlin stub below
            }
        }

        val header = buildString {
            append("Model: ")
            append(model?.name ?: "none")
            append("\nParams: ")
            append("temp=" + String.format("%.2f", params.temperature))
            append(", topP=" + String.format("%.2f", params.topP))
            append(", topK=" + params.topK)
            append(", rep=" + String.format("%.2f", params.repeatPenalty))
            append(", maxTok=" + params.maxTokens)
            append(", ctx=" + params.contextLength)
            append(", seed=" + params.seed)
            append("\n\n")
        }

        val targetText = header + "Echo: " + prompt + "\n\n(Llama backend integration pending.)"

        generationJob = lifecycleScope.launch {
            val sb = StringBuilder()
            for (ch in targetText) {
                sb.append(ch)
                chatAdapter.updateLastAssistantText(sb.toString())
                scrollToBottom()
                delay(10)
            }
        }
    }

    private fun refreshModelsUi() {
        val models = prefs.getModels()
        val selectedId = prefs.getSelectedModelId()
        modelAdapter.setModels(models, selectedId)
    }

    private fun loadParamsIntoUi() {
        val modelId = prefs.getSelectedModelId()
        val p = if (modelId != null) prefs.getParams(modelId) else InferenceParams()

        binding.paramTemp.value = p.temperature
        binding.paramTopP.value = p.topP
        binding.paramTopK.value = p.topK.toFloat()
        binding.paramRepeatPenalty.value = p.repeatPenalty
        binding.paramMaxTokens.setText(p.maxTokens.toString())
        binding.paramContext.setText(p.contextLength.toString())
        binding.paramSeed.setText(p.seed.toString())
    }

    private fun updateActiveModelLabel() {
        val modelId = prefs.getSelectedModelId()
        val model = prefs.getModels().firstOrNull { it.id == modelId }
        binding.activeModelLabel.text = model?.name ?: getString(R.string.no_model_selected)
    }

    private fun stopGeneration() {
        // Stop Kotlin stub (if running)
        generationJob?.cancel()
        generationJob = null

        // Stop native generation (if running)
        if (activeNativeRequestId != 0L) {
            try {
                NativeLlamaBridge.stopGeneration(activeNativeRequestId)
            } catch (_: Throwable) {
            }
            activeNativeRequestId = 0L
        }
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
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
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
            R.id.action_models -> {
                binding.drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            R.id.action_params -> {
                binding.drawerLayout.openDrawer(GravityCompat.END)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
