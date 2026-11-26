package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.TerminalLine
import kotlinx.coroutines.flow.first

private val Context.terminalHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "termux_history_preferences"
)

data class TerminalLineSnapshot(
    val rawText: String,
    val isCommandEcho: Boolean = false,
    val promptPrefix: String = ""
)

data class SavedTerminalSnapshot(
    val history: List<String> = emptyList(),
    val lines: List<TerminalLineSnapshot> = emptyList(),
    val lastDir: String? = null,
    val hasClearedLog: Boolean = false
)

object TerminalHistoryCodec {
    private const val MAX_HISTORY = 100
    private const val MAX_LINES = 250

    private fun escapeField(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun unescapeField(encoded: String): String = buildString(encoded.length) {
        var i = 0
        while (i < encoded.length) {
            val ch = encoded[i]
            if (ch == '\\' && i + 1 < encoded.length) {
                when (encoded[i + 1]) {
                    '\\' -> append('\\')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(encoded[i + 1])
                }
                i += 2
            } else {
                append(ch)
                i++
            }
        }
    }

    fun encodeHistory(history: List<String>): String =
        history.takeLast(MAX_HISTORY)
            .filter { it.isNotBlank() }
            .joinToString("\n") { escapeField(it) }

    fun decodeHistory(encoded: String?): List<String> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split('\n')
            .map { unescapeField(it) }
            .filter { it.isNotBlank() }
    }

    fun encodeLines(lines: List<TerminalLine>): String =
        lines.takeLast(MAX_LINES).joinToString("\n") { line ->
            val echoFlag = if (line.isCommandEcho) "1" else "0"
            "$echoFlag\t${escapeField(line.promptPrefix)}\t${escapeField(line.rawText)}"
        }

    fun decodeLines(encoded: String?): List<TerminalLineSnapshot> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split('\n').mapNotNull { record ->
            if (record.isEmpty()) return@mapNotNull null
            val parts = record.split('\t', limit = 3)
            if (parts.size < 3) return@mapNotNull null
            TerminalLineSnapshot(
                isCommandEcho = parts[0] == "1",
                promptPrefix = unescapeField(parts[1]),
                rawText = unescapeField(parts[2])
            )
        }
    }
}

class TerminalHistoryPreferences(context: Context) {
    private val dataStore = context.applicationContext.terminalHistoryDataStore

    companion object {
        private val KEY_COMMAND_HISTORY = stringPreferencesKey("command_history_json")
        private val KEY_TERMINAL_LINES = stringPreferencesKey("terminal_lines_json")
        private val KEY_LAST_DIR = stringPreferencesKey("last_current_dir")
        private val KEY_HAS_CLEARED_LOG = booleanPreferencesKey("has_cleared_log")
    }

    suspend fun loadSnapshot(): SavedTerminalSnapshot {
        return runCatching {
            val prefs = dataStore.data.first()
            SavedTerminalSnapshot(
                history = TerminalHistoryCodec.decodeHistory(prefs[KEY_COMMAND_HISTORY]),
                lines = TerminalHistoryCodec.decodeLines(prefs[KEY_TERMINAL_LINES]),
                lastDir = prefs[KEY_LAST_DIR],
                hasClearedLog = prefs[KEY_HAS_CLEARED_LOG] ?: false
            )
        }.getOrDefault(SavedTerminalSnapshot())
    }

    suspend fun saveSessionSnapshot(
        history: List<String>,
        lines: List<TerminalLine>,
        currentDir: String,
        hasClearedLog: Boolean
    ) {
        runCatching {
            dataStore.edit { prefs ->
                prefs[KEY_COMMAND_HISTORY] = TerminalHistoryCodec.encodeHistory(history)
                prefs[KEY_TERMINAL_LINES] = TerminalHistoryCodec.encodeLines(lines)
                prefs[KEY_LAST_DIR] = currentDir
                prefs[KEY_HAS_CLEARED_LOG] = hasClearedLog
            }
        }
    }

    suspend fun clearLogOnly(currentDir: String) {
        runCatching {
            dataStore.edit { prefs ->
                prefs[KEY_TERMINAL_LINES] = ""
                prefs[KEY_LAST_DIR] = currentDir
                prefs[KEY_HAS_CLEARED_LOG] = true
            }
        }
    }

    suspend fun clearCommandHistory() {
        runCatching {
            dataStore.edit { prefs ->
                prefs[KEY_COMMAND_HISTORY] = ""
            }
        }
    }

    suspend fun resetToDefault() {
        runCatching {
            dataStore.edit { prefs ->
                prefs.clear()
            }
        }
    }
}
