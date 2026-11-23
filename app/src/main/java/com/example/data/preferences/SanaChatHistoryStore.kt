package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CopilotMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sanaHistoryDataStore by preferencesDataStore(name = "sana_chat_history_prefs")

@Serializable
data class SanaSavedMessage(
    val id: String,
    val isAssistant: Boolean,
    val content: String,
    val timestamp: String,
    val isError: Boolean = false,
    val respondingModelLabel: String? = null
)

@Serializable
data class SanaChatSession(
    val id: String,
    val title: String,
    val updatedAtMs: Long,
    val messages: List<SanaSavedMessage>
)

fun CopilotMessage.toSavedMessage(): SanaSavedMessage = SanaSavedMessage(
    id = id,
    isAssistant = isAssistant,
    content = content,
    timestamp = timestamp,
    isError = isError,
    respondingModelLabel = respondingModelLabel
)

fun SanaSavedMessage.toCopilotMessage(): CopilotMessage = CopilotMessage(
    id = id,
    isAssistant = isAssistant,
    content = content,
    timestamp = timestamp,
    isError = isError,
    respondingModelLabel = respondingModelLabel
)

class SanaChatHistoryStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val sessionsKey = stringPreferencesKey("sana_chat_sessions_v1")
    private val activeSessionIdKey = stringPreferencesKey("sana_active_session_id_v1")

    val sessionsFlow: Flow<List<SanaChatSession>> = context.sanaHistoryDataStore.data.map { prefs ->
        decodeSessions(prefs[sessionsKey])
    }

    suspend fun loadSessions(): List<SanaChatSession> {
        val prefs = context.sanaHistoryDataStore.data.first()
        return decodeSessions(prefs[sessionsKey])
    }

    suspend fun loadActiveSessionId(): String? {
        val prefs = context.sanaHistoryDataStore.data.first()
        return prefs[activeSessionIdKey]
    }

    suspend fun setActiveSessionId(sessionId: String?) {
        context.sanaHistoryDataStore.edit { prefs ->
            if (sessionId.isNullOrBlank()) {
                prefs.remove(activeSessionIdKey)
            } else {
                prefs[activeSessionIdKey] = sessionId
            }
        }
    }

    suspend fun upsertSession(session: SanaChatSession) {
        context.sanaHistoryDataStore.edit { prefs ->
            val current = decodeSessions(prefs[sessionsKey]).toMutableList()
            current.removeAll { it.id == session.id }
            current.add(0, session)
            val trimmed = current
                .sortedByDescending { it.updatedAtMs }
                .take(MAX_SAVED_SESSIONS)
            prefs[sessionsKey] = json.encodeToString(trimmed)
            prefs[activeSessionIdKey] = session.id
        }
    }

    suspend fun deleteSession(sessionId: String) {
        context.sanaHistoryDataStore.edit { prefs ->
            val updated = decodeSessions(prefs[sessionsKey]).filterNot { it.id == sessionId }
            prefs[sessionsKey] = json.encodeToString(updated)
            if (prefs[activeSessionIdKey] == sessionId) {
                prefs.remove(activeSessionIdKey)
            }
        }
    }

    private fun decodeSessions(raw: String?): List<SanaChatSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<SanaChatSession>>(raw)
                .sortedByDescending { it.updatedAtMs }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX_SAVED_SESSIONS = 25
    }
}
