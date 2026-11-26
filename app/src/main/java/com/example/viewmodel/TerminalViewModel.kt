package com.example.viewmodel

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ArabicDictionary
import com.example.data.ArabicWordEntry
import com.example.data.PackageRepository
import com.example.data.ReferenceCatalog
import com.example.data.TerminalHistoryPreferences
import com.example.engine.ShellEngine
import com.example.engine.ShellEvent
import com.example.engine.VirtualFileSystem
import com.example.model.AppTab
import com.example.model.BackgroundJob
import com.example.model.ReferenceCategory
import com.example.model.ReferenceItem
import com.example.model.TerminalLine
import com.example.model.TerminalSession
import com.example.ui.theme.AllTerminalPalettes
import com.example.ui.theme.TerminalPalette
import com.example.ui.theme.TermuxClassicPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

data class TerminalUiState(
    val activeTab: AppTab = AppTab.TERMINAL,
    val sessions: List<TerminalSession> = emptyList(),
    val activeSessionId: Int = 1,
    val input: TextFieldValue = TextFieldValue(""),
    val historyBrowseIndex: Int? = null,
    val draftBeforeHistory: String = "",
    val autocompleteSuggestions: List<String> = emptyList(),
    val arabicSuggestions: List<ArabicWordEntry> = emptyList(),
    val ctrlArmed: Boolean = false,
    val altArmed: Boolean = false,
    val showExtraKeys: Boolean = true,
    val isArabicRtlMode: Boolean = false,
    val isReverseSearchActive: Boolean = false,
    val reverseSearchQuery: String = "",
    val reverseSearchMatch: String? = null,
    val fontSizeSp: Float = 13.5f,
    val palette: TerminalPalette = TermuxClassicPalette,
    val refCategory: ReferenceCategory = ReferenceCategory.ALL,
    val refSearchQuery: String = "",
    val dictSearchQuery: String = "",
    val dictEntries: List<ArabicWordEntry> = emptyList(),
    val shellScripts: List<String> = emptyList(),
    val toastMessage: String? = null,
    val scrollTrigger: Int = 0
) {
    val activeSession: TerminalSession?
        get() = sessions.firstOrNull { it.id == activeSessionId } ?: sessions.firstOrNull()

    val filteredReferenceItems: List<ReferenceItem>
        get() {
            val q = refSearchQuery.trim().lowercase()
            return ReferenceCatalog.allItems.filter { item ->
                val catMatch = refCategory == ReferenceCategory.ALL || item.category == refCategory
                val queryMatch = q.isEmpty() ||
                    item.symbol.lowercase().contains(q) ||
                    item.titleAr.lowercase().contains(q) ||
                    item.descriptionAr.lowercase().contains(q) ||
                    item.exampleCommand.lowercase().contains(q) ||
                    item.keywords.any { it.lowercase().contains(q) }
                catMatch && queryMatch
            }
        }
}

class TerminalViewModel(application: Application) : AndroidViewModel(application) {
    private val lineIdCounter = AtomicLong(1L)
    private val vfsFile = File(application.filesDir, "termux_vfs.json")
    private val prefsFile = File(application.filesDir, "termux_prefs.json")
    private val dictFile = File(application.filesDir, "arabic_dict.json")

    val vfs = VirtualFileSystem(vfsFile)
    val pkgRepo = PackageRepository()
    val arabicDict = ArabicDictionary(dictFile)
    private val historyPrefs = TerminalHistoryPreferences(application)
    private val shellEngine = ShellEngine(vfs, pkgRepo, arabicDict)

    private var activeCommandJob: Job? = null
    private var nextSessionId = 2
    private var hasUserInteracted = false
    private var prefsRecoveredFromCorruption = false

    private val commandExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        val errLine = TerminalLine(
            lineIdCounter.getAndIncrement(),
            "\u001B[31mخطأ غير متوقع في الجلسة: ${throwable.message ?: "تم استعادة الاستقرار"}\u001B[0m"
        )
        updateActiveSession { s ->
            s.copy(
                isRunningProcess = false,
                runningProcessLabel = null,
                lastExitCode = 1,
                lines = (s.lines + errLine).takeLast(600)
            )
        }
        _uiState.update { it.copy(scrollTrigger = it.scrollTrigger + 1) }
    }

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    init {
        loadPrefs()
        val initialSession = createSessionObject(id = 1)
        val recoveredNotice = if (vfs.recoveredFromCorruption || arabicDict.recoveredFromCorruption || prefsRecoveredFromCorruption) {
            "تم استعادة البيانات الافتراضية تلقائياً بعد اكتشاف تلف في التخزين"
        } else null
        _uiState.update {
            it.copy(
                sessions = listOf(initialSession),
                activeSessionId = initialSession.id,
                dictEntries = arabicDict.getAll(),
                shellScripts = vfs.listShellScripts(),
                toastMessage = recoveredNotice
            )
        }
        restoreHistoryFromPreferences()
    }

    private fun restoreHistoryFromPreferences() {
        viewModelScope.launch {
            runCatching { historyPrefs.loadSnapshot() }.getOrNull()?.let { snap ->
                val validDir = snap.lastDir?.takeIf { vfs.getNode(it)?.isDirectory == true }
                val restoredLines = snap.lines.map {
                    TerminalLine(
                        id = lineIdCounter.getAndIncrement(),
                        rawText = it.rawText,
                        isCommandEcho = it.isCommandEcho,
                        promptPrefix = it.promptPrefix
                    )
                }
                _uiState.update { state ->
                    val updatedSessions = state.sessions.map { s ->
                        if (s.id != 1) return@map s
                        val mergedHistory = if (!hasUserInteracted) {
                            snap.history
                        } else {
                            snap.history + s.history
                        }
                        val mergedLines = when {
                            hasUserInteracted && restoredLines.isNotEmpty() && !snap.hasClearedLog ->
                                (restoredLines + s.lines).takeLast(600)
                            hasUserInteracted -> s.lines
                            snap.hasClearedLog -> emptyList()
                            restoredLines.isNotEmpty() -> restoredLines
                            else -> s.lines
                        }
                        s.copy(
                            history = mergedHistory,
                            lines = mergedLines,
                            currentDir = if (!hasUserInteracted && validDir != null) validDir else s.currentDir
                        )
                    }
                    state.copy(
                        sessions = updatedSessions,
                        scrollTrigger = state.scrollTrigger + 1
                    )
                }
            }
        }
    }

    private fun createSessionObject(id: Int): TerminalSession {
        val motdLines = listOf(
            "\u001B[1;32mمرحباً بك في Termux عربي!\u001B[0m",
            "",
            "\u001B[36m• الوثائق والأدلة:\u001B[0m اكتب \u001B[33mhelp\u001B[0m أو \u001B[33mkeys\u001B[0m أو افتح تبويب الفهرس بالأعلى",
            "\u001B[36m• العربية والقاموس:\u001B[0m \u001B[33marprint \"مرحباً\"\u001B[0m | \u001B[33marprint -b \"نص\"\u001B[0m | \u001B[33mdict طرفية\u001B[0m",
            "\u001B[36m• إدارة الحزم:\u001B[0m    pkg search <query>  |  pkg install <package>",
            "\u001B[36m• نظام الملفات:\u001B[0m   ls -la  |  cat README.txt  |  nano notes.md",
            "\u001B[36m• التخزين:\u001B[0m        termux-setup-storage",
            ""
        ).map { TerminalLine(lineIdCounter.getAndIncrement(), it) }

        return TerminalSession(
            id = id,
            title = "جلسة #$id (bash)",
            currentDir = VirtualFileSystem.HOME_PATH,
            lines = motdLines
        )
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun createNewSession() {
        val newId = nextSessionId++
        val session = createSessionObject(newId)
        _uiState.update {
            it.copy(
                sessions = it.sessions + session,
                activeSessionId = newId,
                input = TextFieldValue(""),
                autocompleteSuggestions = emptyList(),
                isReverseSearchActive = false,
                activeTab = AppTab.TERMINAL
            )
        }
    }

    fun switchSession(sessionId: Int) {
        _uiState.update {
            it.copy(
                activeSessionId = sessionId,
                input = TextFieldValue(""),
                historyBrowseIndex = null,
                autocompleteSuggestions = emptyList(),
                isReverseSearchActive = false
            )
        }
    }

    fun closeSession(sessionId: Int) {
        val current = _uiState.value.sessions
        if (current.size <= 1) {
            // Reset single session instead of leaving zero sessions
            val fresh = createSessionObject(1)
            nextSessionId = 2
            _uiState.update {
                it.copy(
                    sessions = listOf(fresh),
                    activeSessionId = 1,
                    input = TextFieldValue("")
                )
            }
            return
        }
        val remaining = current.filterNot { it.id == sessionId }
        val newActive = if (_uiState.value.activeSessionId == sessionId) remaining.last().id else _uiState.value.activeSessionId
        _uiState.update {
            it.copy(
                sessions = remaining,
                activeSessionId = newActive,
                input = TextFieldValue("")
            )
        }
    }

    fun onInputChange(newValue: TextFieldValue) {
        val state = _uiState.value
        if (state.ctrlArmed) {
            val oldText = state.input.text
            val newText = newValue.text
            if (newText.length == oldText.length + 1) {
                val insertedChar = findInsertedChar(oldText, newText)
                if (insertedChar != null && handleCtrlCharacter(insertedChar)) {
                    return
                }
            }
            _uiState.update { it.copy(ctrlArmed = false) }
        }
        if (state.altArmed) {
            _uiState.update { it.copy(altArmed = false) }
        }
        val arabicSuggs = extractArabicPrefixAtCursor(newValue)?.let { prefix ->
            arabicDict.suggestForPrefix(prefix, limit = 8)
        }.orEmpty()
        _uiState.update {
            it.copy(
                input = newValue,
                autocompleteSuggestions = emptyList(),
                arabicSuggestions = arabicSuggs,
                historyBrowseIndex = null
            )
        }
    }

    private fun extractArabicPrefixAtCursor(value: TextFieldValue): String? {
        val pos = value.selection.start.coerceIn(0, value.text.length)
        if (pos == 0) return null
        var start = pos
        while (start > 0 && !value.text[start - 1].isWhitespace() && value.text[start - 1] !in listOf('"', '\'', '(', ')')) {
            start--
        }
        val token = value.text.substring(start, pos)
        return if (token.isNotEmpty() && token.any { it in '\u0600'..'\u06FF' }) token else null
    }

    fun applyArabicSuggestion(entry: ArabicWordEntry) {
        val cur = _uiState.value.input
        val pos = cur.selection.start.coerceIn(0, cur.text.length)
        var start = pos
        while (start > 0 && !cur.text[start - 1].isWhitespace() && cur.text[start - 1] !in listOf('"', '\'', '(', ')')) {
            start--
        }
        val replacement = "${entry.word} "
        val newText = cur.text.substring(0, start) + replacement + cur.text.substring(pos)
        val newCursor = start + replacement.length
        _uiState.update {
            it.copy(
                input = TextFieldValue(newText, TextRange(newCursor)),
                arabicSuggestions = emptyList()
            )
        }
    }

    private fun findInsertedChar(oldText: String, newText: String): Char? {
        for (i in oldText.indices) {
            if (oldText[i] != newText[i]) return newText[i]
        }
        return newText.lastOrNull()
    }

    fun submitCommand(overrideCommand: String? = null) {
        val state = _uiState.value
        if (state.isReverseSearchActive && overrideCommand == null) {
            val match = state.reverseSearchMatch
            _uiState.update {
                it.copy(
                    isReverseSearchActive = false,
                    reverseSearchQuery = "",
                    reverseSearchMatch = null,
                    input = TextFieldValue(match ?: "", TextRange((match ?: "").length))
                )
            }
            if (!match.isNullOrBlank()) submitCommand(match)
            return
        }

        val session = state.activeSession ?: return
        val cmdText = (overrideCommand ?: state.input.text)
        val promptPrefix = "${vfs.formatPromptPath(session.currentDir)} $"

        if (session.isRunningProcess && overrideCommand == null) {
            return
        }

        hasUserInteracted = true

        val echoLine = TerminalLine(
            id = lineIdCounter.getAndIncrement(),
            rawText = cmdText,
            isCommandEcho = true,
            promptPrefix = promptPrefix
        )

        val updatedHistory = if (cmdText.isNotBlank() && session.history.lastOrNull() != cmdText.trim()) {
            session.history + cmdText.trim()
        } else {
            session.history
        }

        updateActiveSession { s ->
            s.copy(
                lines = (s.lines + echoLine).takeLast(600),
                history = updatedHistory
            )
        }

        _uiState.update {
            it.copy(
                input = TextFieldValue(""),
                historyBrowseIndex = null,
                draftBeforeHistory = "",
                autocompleteSuggestions = emptyList(),
                arabicSuggestions = emptyList(),
                ctrlArmed = false,
                altArmed = false,
                scrollTrigger = it.scrollTrigger + 1
            )
        }

        if (cmdText.isBlank()) return

        activeCommandJob?.cancel()
        activeCommandJob = viewModelScope.launch(commandExceptionHandler) {
            val latestSession = _uiState.value.activeSession ?: return@launch
            var clearedDuringCommand = false
            try {
                shellEngine.execute(latestSession, cmdText).collect { event ->
                    when (event) {
                        is ShellEvent.OutputLine -> {
                            clearedDuringCommand = false
                            val line = TerminalLine(lineIdCounter.getAndIncrement(), event.text)
                            updateActiveSession { s ->
                                s.copy(lines = (s.lines + line).takeLast(600))
                            }
                            _uiState.update { it.copy(scrollTrigger = it.scrollTrigger + 1) }
                        }
                        is ShellEvent.ClearScreen -> {
                            clearedDuringCommand = true
                            updateActiveSession { s -> s.copy(lines = emptyList()) }
                        }
                        is ShellEvent.UpdateSession -> {
                            updateActiveSession(event.transform)
                        }
                        is ShellEvent.OpenEditor -> {
                            updateActiveSession { s -> s.copy(nanoState = event.editorState) }
                        }
                        is ShellEvent.ExitSession -> {
                            closeSession(latestSession.id)
                        }
                    }
                }
            } catch (ce: CancellationException) {
                updateActiveSession { s -> s.copy(isRunningProcess = false, runningProcessLabel = null) }
                throw ce
            } catch (t: Throwable) {
                val errLine = TerminalLine(
                    lineIdCounter.getAndIncrement(),
                    "\u001B[31mخطأ في التنفيذ: ${t.message ?: "تعذر إكمال الأمر"}\u001B[0m"
                )
                updateActiveSession { s ->
                    s.copy(
                        isRunningProcess = false,
                        runningProcessLabel = null,
                        lastExitCode = 1,
                        lines = (s.lines + errLine).takeLast(600)
                    )
                }
            } finally {
                updateActiveSession { s ->
                    if (s.isRunningProcess) s.copy(isRunningProcess = false, runningProcessLabel = null) else s
                }
                _uiState.update { it.copy(shellScripts = vfs.listShellScripts(), scrollTrigger = it.scrollTrigger + 1) }
                savePrefs()
                _uiState.value.activeSession?.let { finished ->
                    runCatching {
                        historyPrefs.saveSessionSnapshot(
                            history = finished.history,
                            lines = finished.lines,
                            currentDir = finished.currentDir,
                            hasClearedLog = clearedDuringCommand && finished.lines.isEmpty()
                        )
                    }
                }
            }
        }
    }

    fun clearCurrentLog() {
        hasUserInteracted = true
        val currentDir = _uiState.value.activeSession?.currentDir ?: VirtualFileSystem.HOME_PATH
        updateActiveSession { s -> s.copy(lines = emptyList()) }
        _uiState.update {
            it.copy(
                autocompleteSuggestions = emptyList(),
                arabicSuggestions = emptyList(),
                toastMessage = "تم تنظيف السجل الحالي (clear)"
            )
        }
        viewModelScope.launch {
            runCatching { historyPrefs.clearLogOnly(currentDir) }
        }
    }

    fun clearSavedHistory() {
        hasUserInteracted = true
        updateActiveSession { s -> s.copy(history = emptyList()) }
        _uiState.update {
            it.copy(
                historyBrowseIndex = null,
                draftBeforeHistory = "",
                toastMessage = "تم مسح سجل الأوامر المحفوظ"
            )
        }
        viewModelScope.launch {
            runCatching { historyPrefs.clearCommandHistory() }
        }
    }

    fun toggleCtrl() {
        _uiState.update { it.copy(ctrlArmed = !it.ctrlArmed, altArmed = false) }
    }

    fun toggleAlt() {
        _uiState.update { it.copy(altArmed = !it.altArmed, ctrlArmed = false) }
    }

    fun onEscPressed() {
        val session = _uiState.value.activeSession
        if (session?.nanoState != null) {
            updateActiveSession { s ->
                s.copy(nanoState = s.nanoState?.copy(showSearchBar = false, statusMessage = "ESC — تم إلغاء الوضع الفرعي"))
            }
            return
        }
        if (_uiState.value.isReverseSearchActive) {
            _uiState.update {
                it.copy(isReverseSearchActive = false, reverseSearchQuery = "", reverseSearchMatch = null)
            }
            return
        }
        _uiState.update {
            it.copy(
                ctrlArmed = false,
                altArmed = false,
                autocompleteSuggestions = emptyList(),
                toastMessage = "ESC — تم إرسال رمز الهروب (0x1B)"
            )
        }
    }

    fun onTabPressed() {
        val state = _uiState.value
        val session = state.activeSession ?: return
        val (completed, suggestions) = shellEngine.completeInput(session.currentDir, state.input.text)
        _uiState.update {
            it.copy(
                input = TextFieldValue(completed, TextRange(completed.length)),
                autocompleteSuggestions = suggestions
            )
        }
    }

    fun applyAutocompleteSuggestion(suggestion: String) {
        val text = _uiState.value.input.text
        val endsWithSpace = text.lastOrNull()?.isWhitespace() == true
        val tokens = text.trimStart().split(Regex("\\s+"))
        val updated = if (tokens.size <= 1 && !endsWithSpace) {
            "$suggestion "
        } else {
            val lastToken = if (endsWithSpace) "" else tokens.last()
            val head = text.dropLast(lastToken.length)
            val suffix = if (suggestion.endsWith("/")) "" else " "
            "$head$suggestion$suffix"
        }
        _uiState.update {
            it.copy(
                input = TextFieldValue(updated, TextRange(updated.length)),
                autocompleteSuggestions = emptyList()
            )
        }
    }

    fun insertExtraText(textToInsert: String) {
        val state = _uiState.value
        val current = state.input
        val selStart = current.selection.min.coerceIn(0, current.text.length)
        val selEnd = current.selection.max.coerceIn(0, current.text.length)
        val newText = buildString {
            append(current.text.substring(0, selStart))
            append(textToInsert)
            append(current.text.substring(selEnd))
        }
        val newCursor = (selStart + textToInsert.length).coerceIn(0, newText.length)
        _uiState.update {
            it.copy(
                input = TextFieldValue(newText, TextRange(newCursor)),
                ctrlArmed = false,
                altArmed = false,
                autocompleteSuggestions = emptyList()
            )
        }
    }

    fun navigateHistoryUp() {
        val state = _uiState.value
        val history = state.activeSession?.history.orEmpty()
        if (history.isEmpty()) return
        val currentIdx = state.historyBrowseIndex
        val newIdx = if (currentIdx == null) history.lastIndex else (currentIdx - 1).coerceAtLeast(0)
        val draft = if (currentIdx == null) state.input.text else state.draftBeforeHistory
        val cmd = history[newIdx]
        _uiState.update {
            it.copy(
                historyBrowseIndex = newIdx,
                draftBeforeHistory = draft,
                input = TextFieldValue(cmd, TextRange(cmd.length)),
                autocompleteSuggestions = emptyList()
            )
        }
    }

    fun navigateHistoryDown() {
        val state = _uiState.value
        val history = state.activeSession?.history.orEmpty()
        val currentIdx = state.historyBrowseIndex ?: return
        if (currentIdx >= history.lastIndex) {
            val draft = state.draftBeforeHistory
            _uiState.update {
                it.copy(
                    historyBrowseIndex = null,
                    input = TextFieldValue(draft, TextRange(draft.length))
                )
            }
        } else {
            val newIdx = currentIdx + 1
            val cmd = history[newIdx]
            _uiState.update {
                it.copy(
                    historyBrowseIndex = newIdx,
                    input = TextFieldValue(cmd, TextRange(cmd.length))
                )
            }
        }
    }

    fun moveCursorLeft() {
        val cur = _uiState.value.input
        val pos = (cur.selection.start - 1).coerceAtLeast(0)
        _uiState.update { it.copy(input = cur.copy(selection = TextRange(pos))) }
    }

    fun moveCursorRight() {
        val cur = _uiState.value.input
        val pos = (cur.selection.start + 1).coerceAtMost(cur.text.length)
        _uiState.update { it.copy(input = cur.copy(selection = TextRange(pos))) }
    }

    fun moveCursorHome() {
        val cur = _uiState.value.input
        _uiState.update { it.copy(input = cur.copy(selection = TextRange(0))) }
    }

    fun moveCursorEnd() {
        val cur = _uiState.value.input
        _uiState.update { it.copy(input = cur.copy(selection = TextRange(cur.text.length))) }
    }

    fun handleCtrlCharacter(char: Char): Boolean {
        val c = char.lowercaseChar()
        val state = _uiState.value
        val session = state.activeSession ?: return false
        _uiState.update { it.copy(ctrlArmed = false) }

        return when (c) {
            'c' -> {
                if (session.isRunningProcess) {
                    activeCommandJob?.cancel()
                    updateActiveSession { s ->
                        val stopLine = TerminalLine(lineIdCounter.getAndIncrement(), "\u001B[31m^C (تم إيقاف العملية عبر SIGINT)\u001B[0m")
                        s.copy(
                            isRunningProcess = false,
                            runningProcessLabel = null,
                            lastExitCode = 130,
                            lines = (s.lines + stopLine).takeLast(600)
                        )
                    }
                } else {
                    val promptPrefix = "${vfs.formatPromptPath(session.currentDir)} $"
                    val cancelLine = TerminalLine(
                        lineIdCounter.getAndIncrement(),
                        "${state.input.text}^C",
                        isCommandEcho = true,
                        promptPrefix = promptPrefix
                    )
                    updateActiveSession { s ->
                        s.copy(lines = (s.lines + cancelLine).takeLast(600), lastExitCode = 130)
                    }
                    _uiState.update { it.copy(input = TextFieldValue(""), scrollTrigger = it.scrollTrigger + 1) }
                }
                true
            }
            'd' -> {
                if (state.input.text.isEmpty() && !session.isRunningProcess) {
                    val exitLine = TerminalLine(lineIdCounter.getAndIncrement(), "\u001B[33m^D (EOF — إغلاق الجلسة)\u001B[0m")
                    updateActiveSession { s -> s.copy(lines = (s.lines + exitLine).takeLast(600)) }
                    closeSession(session.id)
                } else {
                    // Delete character at cursor safely
                    val txt = state.input.text
                    val pos = state.input.selection.start.coerceIn(0, txt.length)
                    if (pos < txt.length) {
                        val updated = txt.removeRange(pos, pos + 1)
                        _uiState.update { it.copy(input = TextFieldValue(updated, TextRange(pos.coerceIn(0, updated.length)))) }
                    }
                }
                true
            }
            'z' -> {
                if (session.isRunningProcess) {
                    val label = session.runningProcessLabel ?: "process"
                    activeCommandJob?.cancel()
                    val jobId = (session.jobs.maxOfOrNull { it.jobId } ?: 0) + 1
                    val pid = Random.nextInt(3000, 9999)
                    val job = BackgroundJob(jobId = jobId, pid = pid, command = label, statusArabic = "معلّقة (Stopped)")
                    val msgLine = TerminalLine(lineIdCounter.getAndIncrement(), "\u001B[33m^Z  [$jobId]+  تم تعليق العملية: $label (SIGTSTP)\u001B[0m")
                    updateActiveSession { s ->
                        s.copy(
                            isRunningProcess = false,
                            runningProcessLabel = null,
                            jobs = s.jobs + job,
                            lines = (s.lines + msgLine).takeLast(600)
                        )
                    }
                } else {
                    _uiState.update { it.copy(toastMessage = "Ctrl+Z (SIGTSTP): لا توجد عملية نشطة لتعليقها") }
                }
                true
            }
            'l' -> {
                clearCurrentLog()
                true
            }
            'a' -> {
                moveCursorHome()
                true
            }
            'e' -> {
                moveCursorEnd()
                true
            }
            'u' -> {
                val cur = state.input
                val pos = cur.selection.start.coerceIn(0, cur.text.length)
                val remaining = cur.text.substring(pos)
                _uiState.update { it.copy(input = TextFieldValue(remaining, TextRange(0))) }
                true
            }
            'k' -> {
                val cur = state.input
                val pos = cur.selection.start.coerceIn(0, cur.text.length)
                val remaining = cur.text.substring(0, pos)
                _uiState.update { it.copy(input = TextFieldValue(remaining, TextRange(pos))) }
                true
            }
            'w' -> {
                val cur = state.input
                val pos = cur.selection.start.coerceIn(0, cur.text.length)
                var idx = pos
                while (idx > 0 && cur.text[idx - 1].isWhitespace()) idx--
                while (idx > 0 && !cur.text[idx - 1].isWhitespace()) idx--
                val updated = cur.text.removeRange(idx, pos)
                _uiState.update { it.copy(input = TextFieldValue(updated, TextRange(idx))) }
                true
            }
            'r' -> {
                _uiState.update {
                    it.copy(
                        isReverseSearchActive = true,
                        reverseSearchQuery = "",
                        reverseSearchMatch = session.history.lastOrNull()
                    )
                }
                true
            }
            else -> false
        }
    }

    fun onReverseSearchQueryChange(query: String) {
        val history = _uiState.value.activeSession?.history.orEmpty()
        val match = if (query.isEmpty()) history.lastOrNull() else history.lastOrNull { it.contains(query, ignoreCase = true) }
        _uiState.update {
            it.copy(reverseSearchQuery = query, reverseSearchMatch = match)
        }
    }

    fun acceptReverseSearch() {
        val match = _uiState.value.reverseSearchMatch.orEmpty()
        _uiState.update {
            it.copy(
                isReverseSearchActive = false,
                reverseSearchQuery = "",
                reverseSearchMatch = null,
                input = TextFieldValue(match, TextRange(match.length))
            )
        }
    }

    fun cancelReverseSearch() {
        _uiState.update {
            it.copy(isReverseSearchActive = false, reverseSearchQuery = "", reverseSearchMatch = null)
        }
    }

    // ======================== Nano Editor Actions ========================

    fun updateNanoContent(newContent: String) {
        updateActiveSession { s ->
            s.copy(
                nanoState = s.nanoState?.copy(
                    content = newContent,
                    isModified = true,
                    statusMessage = "تم التعديل (غير محفوظ)"
                )
            )
        }
    }

    fun saveNanoFile() {
        val nano = _uiState.value.activeSession?.nanoState ?: return
        vfs.writeFile(nano.filePath, nano.content, append = false)
        val linesCount = if (nano.content.isEmpty()) 0 else nano.content.lines().size
        updateActiveSession { s ->
            s.copy(
                nanoState = nano.copy(
                    isModified = false,
                    statusMessage = "[ تم حفظ $linesCount سطر في ${nano.displayFileName} ]"
                )
            )
        }
        _uiState.update { it.copy(shellScripts = vfs.listShellScripts()) }
    }

    fun saveAndRunNanoScript() {
        val nano = _uiState.value.activeSession?.nanoState ?: return
        vfs.writeFile(nano.filePath, nano.content, append = false)
        val scriptPath = vfs.formatPromptPath(nano.filePath)
        updateActiveSession { s -> s.copy(nanoState = null) }
        _uiState.update { it.copy(shellScripts = vfs.listShellScripts(), activeTab = AppTab.TERMINAL) }
        submitCommand("bash $scriptPath")
    }

    fun runShellScript(scriptPath: String) {
        _uiState.update { it.copy(activeTab = AppTab.TERMINAL) }
        submitCommand("bash $scriptPath")
    }

    fun openScriptInNano(scriptPath: String) {
        val abs = vfs.resolvePath(VirtualFileSystem.HOME_PATH, scriptPath)
        val content = vfs.getNode(abs)?.content.orEmpty()
        val display = scriptPath.substringAfterLast('/')
        _uiState.update { it.copy(activeTab = AppTab.TERMINAL) }
        updateActiveSession { s ->
            s.copy(
                nanoState = com.example.model.NanoEditorState(
                    filePath = abs,
                    displayFileName = display,
                    content = content,
                    statusMessage = "تم فتح السكربت: $scriptPath"
                )
            )
        }
    }

    fun createNewShellScript() {
        val existing = vfs.listShellScripts()
        var idx = 1
        var candidate = "~/scripts/custom_$idx.sh"
        while (candidate in existing) {
            idx++
            candidate = "~/scripts/custom_$idx.sh"
        }
        val abs = vfs.resolvePath(VirtualFileSystem.HOME_PATH, candidate)
        val starter = """
            |#!/data/data/com.termux/files/usr/bin/bash
            |# سكربت مخصص جديد
            |arprint -b "تشغيل السكربت المخصص!"
            |pwd
            |ls -la
        """.trimMargin()
        vfs.writeFile(abs, starter, append = false)
        vfs.chmod("/", abs, "+x")
        _uiState.update { it.copy(shellScripts = vfs.listShellScripts(), activeTab = AppTab.TERMINAL) }
        updateActiveSession { s ->
            s.copy(
                nanoState = com.example.model.NanoEditorState(
                    filePath = abs,
                    displayFileName = candidate.substringAfterLast('/'),
                    content = starter,
                    statusMessage = "سكربت جديد: عدّل ثم اضغط ▶ تشغيل"
                )
            )
        }
    }

    fun cutLastLineNano() {
        val nano = _uiState.value.activeSession?.nanoState ?: return
        val lines = nano.content.lines().toMutableList()
        if (lines.isNotEmpty()) {
            lines.removeAt(lines.lastIndex)
            updateActiveSession { s ->
                s.copy(
                    nanoState = nano.copy(
                        content = lines.joinToString("\n"),
                        isModified = true,
                        statusMessage = "تم قص السطر الأخير (^K)"
                    )
                )
            }
        }
    }

    fun toggleNanoSearch() {
        updateActiveSession { s ->
            val nano = s.nanoState ?: return@updateActiveSession s
            s.copy(nanoState = nano.copy(showSearchBar = !nano.showSearchBar))
        }
    }

    fun updateNanoSearchQuery(q: String) {
        updateActiveSession { s ->
            val nano = s.nanoState ?: return@updateActiveSession s
            val found = q.isNotEmpty() && nano.content.contains(q, ignoreCase = true)
            s.copy(
                nanoState = nano.copy(
                    searchQuery = q,
                    statusMessage = if (q.isEmpty()) "أدخل كلمة للبحث" else if (found) "تم العثور على '$q'" else "لم يتم العثور على '$q'"
                )
            )
        }
    }

    fun closeNanoEditor() {
        val nano = _uiState.value.activeSession?.nanoState
        updateActiveSession { s -> s.copy(nanoState = null) }
        if (nano != null) {
            val line = TerminalLine(lineIdCounter.getAndIncrement(), "\u001B[2m[تم إغلاق nano: ${nano.displayFileName}]\u001B[0m")
            updateActiveSession { s -> s.copy(lines = (s.lines + line).takeLast(600)) }
        }
    }

    // ======================== Reference Index & Settings ========================

    fun selectReferenceCategory(category: ReferenceCategory) {
        _uiState.update { it.copy(refCategory = category) }
    }

    fun updateReferenceSearch(query: String) {
        _uiState.update { it.copy(refSearchQuery = query) }
    }

    fun tryReferenceItemInTerminal(item: ReferenceItem) {
        _uiState.update { it.copy(activeTab = AppTab.TERMINAL) }
        if (item.runDirectlyInTerminal) {
            submitCommand(item.insertText)
        } else {
            insertExtraText(item.insertText)
        }
    }

    // ======================== Arabic Dictionary Actions ========================

    fun updateDictSearch(query: String) {
        _uiState.update {
            it.copy(
                dictSearchQuery = query,
                dictEntries = arabicDict.lookup(query)
            )
        }
    }

    fun addCustomDictWord(word: String, meaning: String) {
        if (word.isBlank()) return
        arabicDict.addWord(word, meaning)
        val q = _uiState.value.dictSearchQuery
        _uiState.update {
            it.copy(
                dictEntries = arabicDict.lookup(q),
                toastMessage = "تمت إضافة \"$word\" إلى القاموس"
            )
        }
    }

    fun removeCustomDictWord(word: String) {
        if (arabicDict.removeCustomWord(word)) {
            val q = _uiState.value.dictSearchQuery
            _uiState.update {
                it.copy(
                    dictEntries = arabicDict.lookup(q),
                    toastMessage = "تم حذف \"$word\" من القاموس"
                )
            }
        }
    }

    fun insertDictWordToTerminal(word: String) {
        _uiState.update { it.copy(activeTab = AppTab.TERMINAL) }
        insertExtraText("$word ")
    }

    fun runArprintForWord(word: String) {
        _uiState.update { it.copy(activeTab = AppTab.TERMINAL) }
        submitCommand("arprint -b \"$word\"")
    }

    fun refreshDictionaryState() {
        val q = _uiState.value.dictSearchQuery
        _uiState.update { it.copy(dictEntries = arabicDict.lookup(q)) }
    }

    fun setFontSize(sizeSp: Float) {
        _uiState.update { it.copy(fontSizeSp = sizeSp.coerceIn(10f, 20f)) }
        savePrefs()
    }

    fun setPalette(palette: TerminalPalette) {
        _uiState.update { it.copy(palette = palette) }
        savePrefs()
    }

    fun toggleExtraKeys() {
        _uiState.update { it.copy(showExtraKeys = !it.showExtraKeys) }
    }

    fun toggleArabicRtlMode() {
        val next = !_uiState.value.isArabicRtlMode
        _uiState.update {
            it.copy(
                isArabicRtlMode = next,
                toastMessage = if (next) {
                    "تم تفعيل وضع الكتابة من اليمين إلى اليسار والأوامر العربية"
                } else {
                    "تم التبديل إلى الوضع القياسي (LTR)"
                }
            )
        }
        savePrefs()
    }

    fun applyArabicCommandTemplate(template: String, runImmediately: Boolean) {
        if (runImmediately) {
            submitCommand(template)
        } else {
            val cursorPos = if (template.endsWith("\"\"")) {
                template.length - 1
            } else {
                template.length
            }
            _uiState.update {
                it.copy(
                    input = TextFieldValue(template, TextRange(cursorPos.coerceIn(0, template.length))),
                    autocompleteSuggestions = emptyList()
                )
            }
        }
    }

    fun resetEnvironment() {
        hasUserInteracted = true
        activeCommandJob?.cancel()
        vfs.resetToDefault()
        pkgRepo.resetToDefault()
        arabicDict.resetToDefault()
        runCatching {
            if (prefsFile.exists()) prefsFile.delete()
        }
        val fresh = createSessionObject(1)
        nextSessionId = 2
        _uiState.update {
            it.copy(
                activeTab = AppTab.TERMINAL,
                sessions = listOf(fresh),
                activeSessionId = 1,
                input = TextFieldValue(""),
                historyBrowseIndex = null,
                draftBeforeHistory = "",
                autocompleteSuggestions = emptyList(),
                arabicSuggestions = emptyList(),
                ctrlArmed = false,
                altArmed = false,
                isArabicRtlMode = false,
                isReverseSearchActive = false,
                reverseSearchQuery = "",
                reverseSearchMatch = null,
                fontSizeSp = 13.5f,
                palette = TermuxClassicPalette,
                dictSearchQuery = "",
                dictEntries = arabicDict.getAll(),
                shellScripts = vfs.listShellScripts(),
                toastMessage = "تمت إعادة ضبط جميع بيانات التطبيق الافتراضية بنجاح",
                scrollTrigger = it.scrollTrigger + 1
            )
        }
        viewModelScope.launch {
            historyPrefs.resetToDefault()
        }
    }

    fun consumeToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun updateActiveSession(transform: (TerminalSession) -> TerminalSession) {
        _uiState.update { state ->
            val updated = state.sessions.map { s ->
                if (s.id == state.activeSessionId) transform(s) else s
            }
            state.copy(sessions = updated)
        }
    }

    private fun savePrefs() {
        runCatching {
            val state = _uiState.value
            val obj = JSONObject()
            obj.put("fontSize", state.fontSizeSp.toDouble())
            obj.put("paletteId", state.palette.id)
            obj.put("arabicRtlMode", state.isArabicRtlMode)
            val installedArr = JSONArray()
            pkgRepo.listInstalled().forEach { installedArr.put(it.name) }
            obj.put("installedPkgs", installedArr)
            val payload = obj.toString()
            val tmp = File(prefsFile.parentFile, "${prefsFile.name}.tmp")
            tmp.writeText(payload)
            if (!tmp.renameTo(prefsFile)) {
                prefsFile.writeText(payload)
                tmp.delete()
            }
        }
    }

    private fun loadPrefs() {
        if (!prefsFile.exists()) return
        runCatching {
            val obj = JSONObject(prefsFile.readText())
            val font = obj.optDouble("fontSize", 13.5).toFloat().coerceIn(10f, 20f)
            val paletteId = obj.optString("paletteId", "classic")
            val palette = AllTerminalPalettes.firstOrNull { it.id == paletteId } ?: TermuxClassicPalette
            val arabicRtl = obj.optBoolean("arabicRtlMode", false)
            val pkgsArr = obj.optJSONArray("installedPkgs")
            if (pkgsArr != null) {
                val names = mutableSetOf<String>()
                for (i in 0 until pkgsArr.length()) {
                    names.add(pkgsArr.getString(i))
                }
                pkgRepo.restoreInstalledNames(names)
            }
            _uiState.update { it.copy(fontSizeSp = font, palette = palette, isArabicRtlMode = arabicRtl) }
        }.onFailure {
            prefsRecoveredFromCorruption = true
            runCatching { prefsFile.delete() }
        }
    }
}
