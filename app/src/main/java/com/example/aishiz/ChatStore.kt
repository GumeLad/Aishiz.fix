package com.example.aishiz

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ChatStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun listSessions(): List<ChatSession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ChatSession(
                            id = o.getString("id"),
                            title = o.optString("title", "New chat"),
                            createdAt = o.optLong("createdAt", 0L),
                            updatedAt = o.optLong("updatedAt", 0L)
                        )
                    )
                }
            }.sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun createSession(title: String = "New chat"): ChatSession {
        val now = System.currentTimeMillis()
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now
        )
        val sessions = listSessions().toMutableList()
        sessions.add(0, session)
        saveSessions(sessions)
        saveMessagesInternal(session.id, emptyList(), touch = false)
        setSelectedSessionId(session.id)
        return session
    }

    @Synchronized
    fun deleteSession(id: String) {
        saveSessions(listSessions().filterNot { it.id == id })
        prefs.edit().remove(messageKey(id)).apply()

        if (getSelectedSessionId() == id) {
            setSelectedSessionId(listSessions().firstOrNull()?.id)
        }
    }

    fun getSelectedSessionId(): String? = prefs.getString(KEY_SELECTED_SESSION, null)

    fun setSelectedSessionId(id: String?) {
        prefs.edit().putString(KEY_SELECTED_SESSION, id).apply()
    }

    @Synchronized
    fun loadMessages(sessionId: String): List<ChatMessage> {
        val raw = prefs.getString(messageKey(sessionId), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val role = runCatching { Role.valueOf(o.getString("role")) }
                        .getOrDefault(Role.ASSISTANT)
                    add(
                        ChatMessage(
                            id = o.optLong("id", System.currentTimeMillis() + i),
                            role = role,
                            text = o.optString("text", "")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveMessages(sessionId: String, messages: List<ChatMessage>) {
        saveMessagesInternal(sessionId, messages, touch = true)
    }

    @Synchronized
    fun clearMessages(sessionId: String) {
        saveMessagesInternal(sessionId, emptyList(), touch = true)
    }

    @Synchronized
    fun titleFromFirstUserMessage(sessionId: String, text: String) {
        val sessions = listSessions()
        val current = sessions.firstOrNull { it.id == sessionId } ?: return
        if (current.title != "New chat") return

        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        val title = if (cleaned.length <= 48) cleaned else cleaned.take(45) + "…"
        if (title.isBlank()) return

        saveSessions(
            sessions.map {
                if (it.id == sessionId) {
                    it.copy(title = title, updatedAt = System.currentTimeMillis())
                } else {
                    it
                }
            }
        )
    }

    private fun saveMessagesInternal(
        sessionId: String,
        messages: List<ChatMessage>,
        touch: Boolean
    ) {
        val arr = JSONArray()
        messages.forEach { message ->
            arr.put(JSONObject().apply {
                put("id", message.id)
                put("role", message.role.name)
                put("text", message.text)
            })
        }
        prefs.edit().putString(messageKey(sessionId), arr.toString()).apply()

        if (touch) {
            val now = System.currentTimeMillis()
            saveSessions(
                listSessions().map {
                    if (it.id == sessionId) it.copy(updatedAt = now) else it
                }
            )
        }
    }

    private fun saveSessions(sessions: List<ChatSession>) {
        val arr = JSONArray()
        sessions.sortedByDescending { it.updatedAt }.forEach { session ->
            arr.put(JSONObject().apply {
                put("id", session.id)
                put("title", session.title)
                put("createdAt", session.createdAt)
                put("updatedAt", session.updatedAt)
            })
        }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    private fun messageKey(id: String) = "messages_$id"

    companion object {
        private const val PREFS_NAME = "aishiz_chats"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_SELECTED_SESSION = "selected_session"
    }
}
