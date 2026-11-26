package com.example.engine

import com.example.data.ArabicDictionary
import com.example.data.PackageRepository
import com.example.data.ReferenceCatalog
import com.example.model.BackgroundJob
import com.example.model.NanoEditorState
import com.example.model.TerminalSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

sealed class ShellEvent {
    data class OutputLine(val text: String) : ShellEvent()
    data object ClearScreen : ShellEvent()
    data class UpdateSession(val transform: (TerminalSession) -> TerminalSession) : ShellEvent()
    data class OpenEditor(val editorState: NanoEditorState) : ShellEvent()
    data object ExitSession : ShellEvent()
}

data class ExecResult(
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = 0
)

private enum class ChainOp { NONE, AND, OR, SEMI, BG }

private data class ParsedCommand(
    val args: List<String>,
    val stdinFile: String? = null,
    val stdoutFile: String? = null,
    val stdoutAppend: Boolean = false,
    val stderrFile: String? = null,
    val combineStdErrToOut: Boolean = false
)

class ShellEngine(
    val vfs: VirtualFileSystem,
    val pkgRepo: PackageRepository,
    val arabicDict: ArabicDictionary = ArabicDictionary()
) {
    private val knownCommands = listOf(
        "pkg", "apt", "arprint", "dict", "bash", "sh", "source", "ls", "cd", "pwd",
        "cat", "echo", "printf", "touch", "mkdir", "rm", "cp", "mv", "chmod",
        "grep", "sed", "awk", "tr", "sort", "uniq", "wc", "head", "tail", "find",
        "tree", "nano", "vim", "clear", "history", "env", "export", "unset",
        "alias", "which", "whoami", "id", "uname", "date", "uptime", "jobs",
        "kill", "sleep", "termux-setup-storage", "help", "keys", "man", "neofetch",
        "htop", "git", "python", "python3", "pip", "node", "npm", "curl", "wget",
        "jq", "ssh", "nmap", "clang", "exit"
    )

    private val arabicCommandAliases = mapOf(
        "مساعدة" to "help",
        "اختصارات" to "keys",
        "تنظيف" to "clear",
        "مسح" to "clear",
        "مسار" to "pwd",
        "عرض" to "ls -la",
        "ملفات" to "ls",
        "شجرة" to "tree",
        "انتقال" to "cd",
        "مجلد" to "mkdir",
        "إنشاء" to "touch",
        "انشاء" to "touch",
        "قراءة" to "cat",
        "طباعة" to "arprint",
        "قاموس" to "dict",
        "تحرير" to "nano",
        "حذف" to "rm",
        "نسخ" to "cp",
        "نقل" to "mv",
        "بحث" to "grep",
        "تاريخ" to "history",
        "حزم" to "pkg",
        "تشغيل" to "bash"
    )

    fun completeInput(currentDir: String, input: String): Pair<String, List<String>> {
        if (input.isEmpty()) {
            return input to knownCommands.take(12)
        }
        val endsWithSpace = input.last().isWhitespace()
        val parts = input.trimStart().split(Regex("\\s+"))
        val completingCommand = parts.size <= 1 && !endsWithSpace

        return if (completingCommand) {
            val prefix = parts.firstOrNull().orEmpty()
            val pool = if (prefix.any { it in '\u0600'..'\u06FF' }) {
                arabicCommandAliases.keys.toList()
            } else {
                knownCommands
            }
            val matches = pool.filter { it.startsWith(prefix) }.sorted()
            when {
                matches.size == 1 -> "${matches.first()} " to emptyList()
                matches.size > 1 -> {
                    val lcp = longestCommonPrefix(matches)
                    val updated = if (lcp.length > prefix.length) lcp else input
                    updated to matches
                }
                else -> input to emptyList()
            }
        } else {
            val token = if (endsWithSpace) "" else parts.last()
            val cleanArabicPrefix = token.trimStart('"', '\'')
            if (cleanArabicPrefix.any { it in '\u0600'..'\u06FF' }) {
                val dictMatches = arabicDict.suggestForPrefix(cleanArabicPrefix, limit = 8).map { it.word }
                return when {
                    dictMatches.size == 1 -> {
                        val quotePrefix = if (token.startsWith("\"") || token.startsWith("'")) token.first().toString() else ""
                        val head = input.dropLast(token.length)
                        "$head$quotePrefix${dictMatches.first()} " to emptyList()
                    }
                    dictMatches.size > 1 -> input to dictMatches
                    else -> input to emptyList()
                }
            }
            val slashIdx = token.lastIndexOf('/')
            val dirPrefix = if (slashIdx >= 0) token.substring(0, slashIdx + 1) else ""
            val namePrefix = if (slashIdx >= 0) token.substring(slashIdx + 1) else token
            val searchDir = if (dirPrefix.isEmpty()) currentDir else vfs.resolvePath(currentDir, dirPrefix)
            val dirNode = vfs.getNode(searchDir)
            if (dirNode == null || !dirNode.isDirectory) {
                return input to emptyList()
            }
            val matches = dirNode.children.values
                .filter { it.name.startsWith(namePrefix) }
                .sortedBy { it.name }
                .map { node ->
                    val suffix = if (node.isDirectory) "/" else ""
                    "$dirPrefix${node.name}$suffix"
                }
            when {
                matches.size == 1 -> {
                    val replacement = matches.first()
                    val head = if (endsWithSpace) input else input.dropLast(token.length)
                    val trailingSpace = if (replacement.endsWith("/")) "" else " "
                    "$head$replacement$trailingSpace" to emptyList()
                }
                matches.size > 1 -> {
                    val lcp = longestCommonPrefix(matches)
                    val head = if (endsWithSpace) input else input.dropLast(token.length)
                    val updated = if (lcp.length > token.length) "$head$lcp" else input
                    updated to matches
                }
                else -> input to emptyList()
            }
        }
    }

    private fun longestCommonPrefix(strings: List<String>): String {
        if (strings.isEmpty()) return ""
        var prefix = strings[0]
        for (i in 1 until strings.size) {
            while (!strings[i].startsWith(prefix)) {
                prefix = prefix.dropLast(1)
                if (prefix.isEmpty()) return ""
            }
        }
        return prefix
    }

    fun execute(session: TerminalSession, rawCommand: String, depth: Int = 0): Flow<ShellEvent> = flow {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return@flow

        val cleanCommand = stripTrailingComment(trimmed)
        if (cleanCommand.isEmpty()) return@flow

        var workingSession = session
        val chains = splitChainOperators(cleanCommand)
        var lastCode = workingSession.lastExitCode

        for ((index, pair) in chains.withIndex()) {
            val (segment, op) = pair
            val prevOp = if (index == 0) ChainOp.NONE else chains[index - 1].second

            val shouldRun = when (prevOp) {
                ChainOp.NONE, ChainOp.SEMI, ChainOp.BG -> true
                ChainOp.AND -> lastCode == 0
                ChainOp.OR -> lastCode != 0
            }
            if (!shouldRun) continue

            if (op == ChainOp.BG) {
                val pid = Random.nextInt(2000, 9999)
                val jobId = (workingSession.jobs.maxOfOrNull { it.jobId } ?: 0) + 1
                val newJob = BackgroundJob(jobId = jobId, pid = pid, command = segment.trim())
                workingSession = workingSession.copy(jobs = workingSession.jobs + newJob, lastExitCode = 0)
                emit(ShellEvent.UpdateSession { s -> s.copy(jobs = s.jobs + newJob, lastExitCode = 0) })
                emit(ShellEvent.OutputLine("\u001B[36m[$jobId] $pid\u001B[0m  (تم التشغيل في الخلفية: ${segment.trim()})"))
                lastCode = 0
                continue
            }

            // Check if this segment is an interactive/streaming command
            val expandedPreview = expandAliases(segment.trim(), workingSession.aliases)
            val firstWord = expandedPreview.split(Regex("\\s+")).firstOrNull().orEmpty()

            if ((firstWord == "pkg" || firstWord == "apt") && !expandedPreview.contains("|") && !expandedPreview.contains(">")) {
                val args = tokenizeArgs(expandedPreview, workingSession)
                executePackageManagerStreaming(args).collect { ev ->
                    emit(ev)
                }
                lastCode = 0
                emit(ShellEvent.UpdateSession { it.copy(lastExitCode = 0) })
                continue
            }

            if (firstWord == "sleep" && !expandedPreview.contains("|") && !expandedPreview.contains("&")) {
                val args = tokenizeArgs(expandedPreview, workingSession)
                val secs = args.getOrNull(1)?.toDoubleOrNull()?.coerceIn(0.1, 15.0) ?: 1.0
                emit(ShellEvent.UpdateSession { it.copy(isRunningProcess = true, runningProcessLabel = "sleep ${args.getOrNull(1) ?: "1"}") })
                delay((secs * 1000).toLong())
                emit(ShellEvent.UpdateSession { it.copy(isRunningProcess = false, runningProcessLabel = null, lastExitCode = 0) })
                lastCode = 0
                continue
            }

            // Line-by-line .sh script runner via bash / sh / source / . / ./script.sh
            if (!expandedPreview.contains("|") && !expandedPreview.contains(">")) {
                val args = tokenizeArgs(expandedPreview, workingSession)
                val cmdToken = args.firstOrNull().orEmpty()
                val isShellInvocation = cmdToken == "bash" || cmdToken == "sh" || cmdToken == "source" || cmdToken == "."
                val isDirectExecutableSh = (cmdToken.startsWith("./") || cmdToken.startsWith("/") || cmdToken.startsWith("~/")) && !cmdToken.endsWith(".py")
                if (isShellInvocation || isDirectExecutableSh) {
                    if (isShellInvocation && args.size >= 2 && args[1] == "-c") {
                        val inlineCmd = args.drop(2).joinToString(" ")
                        execute(workingSession, inlineCmd, depth + 1).collect { ev ->
                            if (ev is ShellEvent.UpdateSession) workingSession = ev.transform(workingSession)
                            emit(ev)
                        }
                        lastCode = workingSession.lastExitCode
                        continue
                    }
                    if (isShellInvocation && args.size == 1) {
                        emit(ShellEvent.OutputLine("\u001B[36m$cmdToken\u001B[0m — لتشغيل سكربت اكتب: $cmdToken <file.sh> (أو $cmdToken -x <file.sh> للتتبع)"))
                        lastCode = 0
                        continue
                    }

                    val flags = if (isShellInvocation) args.drop(1).takeWhile { it.startsWith("-") } else emptyList()
                    val positional = if (isShellInvocation) args.drop(1 + flags.size) else args
                    val scriptTarget = positional.firstOrNull()
                    if (scriptTarget != null) {
                        val traceMode = flags.any { it.contains('x') }
                        val exitOnError = flags.any { it.contains('e') }
                        val isSource = cmdToken == "source" || cmdToken == "."
                        val scriptArgs = positional.drop(1)
                        var scriptExitCode = 0
                        executeScript(
                            session = workingSession,
                            scriptPathRaw = scriptTarget,
                            scriptArgs = scriptArgs,
                            requireExecPerm = isDirectExecutableSh,
                            traceMode = traceMode,
                            exitOnError = exitOnError,
                            isSource = isSource,
                            depth = depth
                        ).collect { ev ->
                            when (ev) {
                                is ShellEvent.UpdateSession -> {
                                    workingSession = ev.transform(workingSession)
                                    scriptExitCode = workingSession.lastExitCode
                                }
                                else -> Unit
                            }
                            emit(ev)
                        }
                        lastCode = scriptExitCode
                        continue
                    }
                }
            }

            // Pipeline execution
            val pipeStages = splitPipes(expandedPreview)
            var pipeInput = ""
            var stageResult = ExecResult()

            for ((stageIdx, stageRaw) in pipeStages.withIndex()) {
                val effectiveStage = if (stageIdx > 0) expandAliases(stageRaw, workingSession.aliases) else stageRaw
                val parsed = parseRedirections(tokenizeArgs(effectiveStage, workingSession))
                if (parsed.args.isEmpty()) continue

                // Handle inline variable assignment: FOO=bar
                if (parsed.args.size == 1 && parsed.args[0].matches(Regex("[A-Za-z_][A-Za-z0-9_]*=.*"))) {
                    val eqIdx = parsed.args[0].indexOf('=')
                    val k = parsed.args[0].substring(0, eqIdx)
                    val v = parsed.args[0].substring(eqIdx + 1)
                    workingSession = workingSession.copy(envVars = workingSession.envVars + (k to v))
                    emit(ShellEvent.UpdateSession { it.copy(envVars = it.envVars + (k to v), lastExitCode = 0) })
                    stageResult = ExecResult(exitCode = 0)
                    continue
                }

                // Stdin redirection
                var effectiveStdin = pipeInput
                if (parsed.stdinFile != null) {
                    val inPath = vfs.resolvePath(workingSession.currentDir, parsed.stdinFile)
                    val inNode = vfs.getNode(inPath)
                    if (inNode == null || inNode.isDirectory) {
                        stageResult = ExecResult(
                            stderr = "bash: ${parsed.stdinFile}: لا يوجد ملف بهذا الاسم",
                            exitCode = 1
                        )
                        break
                    }
                    effectiveStdin = inNode.content
                }

                val expandedArgs = expandGlobsForArgs(workingSession.currentDir, parsed.args)
                val outcome = runSingleBuiltin(
                    session = workingSession,
                    args = expandedArgs,
                    stdin = effectiveStdin,
                    emitSpecial = { ev ->
                        when (ev) {
                            is ShellEvent.UpdateSession -> workingSession = ev.transform(workingSession)
                            else -> Unit
                        }
                        emit(ev)
                    }
                )
                stageResult = outcome

                // Handle output redirection
                if (parsed.stderrFile != null && stageResult.stderr.isNotEmpty()) {
                    val errPath = vfs.resolvePath(workingSession.currentDir, parsed.stderrFile)
                    vfs.writeFile(errPath, stageResult.stderr, append = false)
                    stageResult = stageResult.copy(stderr = "")
                }

                if (parsed.stdoutFile != null) {
                    val outPath = vfs.resolvePath(workingSession.currentDir, parsed.stdoutFile)
                    val textToWrite = if (parsed.combineStdErrToOut && stageResult.stderr.isNotEmpty()) {
                        listOf(stageResult.stdout, stageResult.stderr).filter { it.isNotEmpty() }.joinToString("\n")
                    } else {
                        stageResult.stdout
                    }
                    val writeRes = vfs.writeFile(outPath, textToWrite, append = parsed.stdoutAppend)
                    if (writeRes.isFailure) {
                        stageResult = stageResult.copy(
                            stdout = "",
                            stderr = writeRes.exceptionOrNull()?.message ?: "خطأ في الكتابة إلى الملف",
                            exitCode = 1
                        )
                    } else {
                        stageResult = stageResult.copy(
                            stdout = "",
                            stderr = if (parsed.combineStdErrToOut) "" else stageResult.stderr
                        )
                    }
                } else if (stageIdx < pipeStages.lastIndex) {
                    pipeInput = stageResult.stdout
                }
            }

            if (stageResult.stdout.isNotEmpty()) {
                for (line in stageResult.stdout.split('\n')) {
                    emit(ShellEvent.OutputLine(line))
                }
            }
            if (stageResult.stderr.isNotEmpty()) {
                for (line in stageResult.stderr.split('\n')) {
                    emit(ShellEvent.OutputLine("\u001B[31m$line\u001B[0m"))
                }
            }

            lastCode = stageResult.exitCode
            val finalCode = lastCode
            workingSession = workingSession.copy(lastExitCode = finalCode)
            emit(ShellEvent.UpdateSession { it.copy(lastExitCode = finalCode) })
        }
    }

    private suspend fun runSingleBuiltin(
        session: TerminalSession,
        args: List<String>,
        stdin: String,
        emitSpecial: suspend (ShellEvent) -> Unit
    ): ExecResult {
        val cmd = args.firstOrNull().orEmpty()
        val rest = args.drop(1)

        return try {
            when (cmd) {
            "clear" -> {
                emitSpecial(ShellEvent.ClearScreen)
                ExecResult()
            }
            "exit" -> {
                emitSpecial(ShellEvent.ExitSession)
                ExecResult()
            }
            "pwd" -> ExecResult(stdout = session.currentDir)
            "whoami" -> ExecResult(stdout = session.envVars["USER"] ?: "u0_a241")
            "id" -> ExecResult(stdout = "uid=10241(${session.envVars["USER"] ?: "u0_a241"}) gid=10241(u0_a241) groups=10241(u0_a241),3003(inet)")
            "uname" -> {
                val all = rest.contains("-a")
                val out = if (all) {
                    "Linux localhost 6.1.75-android14-termux #1 SMP PREEMPT aarch64 Android"
                } else {
                    "Linux"
                }
                ExecResult(stdout = out)
            }
            "date" -> {
                val fmt = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH)
                ExecResult(stdout = fmt.format(Date()))
            }
            "uptime" -> ExecResult(stdout = " 12:44:10 up 2 days, 04:18,  1 user,  load average: 0.42, 0.38, 0.31")
            "cd" -> handleCd(session, rest, emitSpecial)
            "ls" -> handleLs(session.currentDir, rest)
            "tree" -> handleTree(session.currentDir, rest)
            "mkdir" -> handleMkdir(session.currentDir, rest)
            "touch" -> handleTouch(session.currentDir, rest)
            "cat" -> handleCat(session.currentDir, rest, stdin)
            "head" -> handleHeadTail(session.currentDir, rest, stdin, isHead = true)
            "tail" -> handleHeadTail(session.currentDir, rest, stdin, isHead = false)
            "wc" -> handleWc(session.currentDir, rest, stdin)
            "echo" -> handleEcho(rest)
            "arprint" -> handleArprint(rest, stdin)
            "dict" -> handleDict(rest)
            "printf" -> ExecResult(stdout = unescapeString(rest.joinToString(" ")))
            "rm" -> handleRm(session.currentDir, rest)
            "cp" -> handleCp(session.currentDir, rest)
            "mv" -> handleMv(session.currentDir, rest)
            "chmod" -> handleChmod(session.currentDir, rest)
            "grep" -> handleGrep(session.currentDir, rest, stdin)
            "sed" -> handleSed(session.currentDir, rest, stdin)
            "awk" -> handleAwk(session.currentDir, rest, stdin)
            "tr" -> handleTr(rest, stdin)
            "sort" -> handleSort(session.currentDir, rest, stdin)
            "uniq" -> handleUniq(stdin)
            "find" -> handleFind(session.currentDir, rest)
            "env" -> ExecResult(stdout = session.envVars.entries.joinToString("\n") { "${it.key}=${it.value}" })
            "export" -> handleExport(session, rest, emitSpecial)
            "unset" -> handleUnset(rest, emitSpecial)
            "alias" -> handleAlias(session, rest, emitSpecial)
            "history" -> {
                if (rest.contains("-c") || rest.contains("--clear")) {
                    emitSpecial(ShellEvent.UpdateSession { it.copy(history = emptyList()) })
                    ExecResult(stdout = "\u001B[32m✓\u001B[0m تم مسح سجل الأوامر المحفوظ (history).")
                } else {
                    val limit = rest.firstOrNull()?.toIntOrNull()?.coerceAtLeast(1)
                    val entries = if (limit != null) session.history.takeLast(limit) else session.history
                    if (entries.isEmpty()) {
                        ExecResult(stdout = "سجل الأوامر فارغ حالياً.")
                    } else {
                        val startOffset = session.history.size - entries.size
                        val out = entries.mapIndexed { idx, line ->
                            String.format(Locale.ENGLISH, "%4d  %s", startOffset + idx + 1, line)
                        }.joinToString("\n")
                        ExecResult(stdout = out)
                    }
                }
            }
            "which" -> handleWhich(rest)
            "jobs" -> {
                if (session.jobs.isEmpty()) {
                    ExecResult(stdout = "لا توجد عمليات تعمل في الخلفية حالياً.")
                } else {
                    val out = session.jobs.joinToString("\n") { "[${it.jobId}]+  ${it.statusArabic} (PID ${it.pid})   ${it.command}" }
                    ExecResult(stdout = out)
                }
            }
            "kill" -> handleKill(session, rest, emitSpecial)
            "nano", "vim", "vi" -> handleOpenEditor(session.currentDir, cmd, rest, emitSpecial)
            "termux-setup-storage" -> handleSetupStorage(emitSpecial)
            "help" -> ExecResult(stdout = buildHelpOutput(rest.firstOrNull()))
            "keys" -> ExecResult(stdout = buildKeysOutput(rest.firstOrNull()))
            "man" -> ExecResult(stdout = buildManOutput(rest.firstOrNull()))
            "neofetch" -> {
                if (!pkgRepo.isBinaryAvailable("neofetch")) {
                    ExecResult(stderr = "الأداة neofetch غير مثبتة. لتثبيتها نفّذ: pkg install neofetch", exitCode = 127)
                } else {
                    ExecResult(stdout = buildNeofetchOutput(session))
                }
            }
            "htop" -> {
                if (!pkgRepo.isBinaryAvailable("htop")) {
                    ExecResult(stderr = "الأداة htop غير مثبتة. لتثبيتها نفّذ: pkg install htop", exitCode = 127)
                } else {
                    ExecResult(stdout = buildHtopOutput(session))
                }
            }
            "git" -> handleGit(session.currentDir, rest)
            "python", "python3" -> handlePython(session.currentDir, rest)
            "pip" -> handlePip(rest)
            "node" -> handleNode(rest)
            "npm" -> handleNpm(rest)
            "jq" -> handleJq(rest, stdin)
            "curl" -> handleCurl(rest)
            "wget" -> handleWget(rest)
            "ssh", "nmap", "clang", "clang++", "zsh" -> {
                if (!pkgRepo.isBinaryAvailable(cmd)) {
                    val pkgName = when (cmd) {
                        "ssh" -> "openssh"
                        "clang++" -> "clang"
                        else -> cmd
                    }
                    ExecResult(stderr = "الأمر '$cmd' غير مثبت حالياً. لتثبيته نفّذ: pkg install $pkgName", exitCode = 127)
                } else {
                    ExecResult(stdout = "\u001B[36m[$cmd]\u001B[0m الأداة $cmd مثبتة وجاهزة (الإصدار المحاكي ${pkgRepo.get(if (cmd == "ssh") "openssh" else cmd.removeSuffix("++"))?.version ?: "1.0"}).")
                }
            }
            "pkg", "apt" -> {
                val lines = mutableListOf<String>()
                executePackageManagerStreaming(args).collect { ev ->
                    if (ev is ShellEvent.OutputLine) lines.add(ev.text)
                }
                ExecResult(stdout = lines.joinToString("\n"))
            }
            else -> {
                if (cmd.startsWith("./") || cmd.startsWith("/") || cmd.startsWith("~/")) {
                    handleRunScript(session.currentDir, cmd)
                } else {
                    ExecResult(
                        stderr = "bash: $cmd: الأمر غير موجود — اكتب help لعرض الأوامر أو pkg search $cmd",
                        exitCode = 127
                    )
                }
            }
        }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            ExecResult(
                stderr = "$cmd: تعذر إكمال التنفيذ بأمان (${t.message ?: "خطأ في المعاملات"})",
                exitCode = 1
            )
        }
    }

    private suspend fun handleCd(
        session: TerminalSession,
        args: List<String>,
        emitSpecial: suspend (ShellEvent) -> Unit
    ): ExecResult {
        val target = args.firstOrNull() ?: "~"
        val resolved = vfs.resolvePath(session.currentDir, target)
        val node = vfs.getNode(resolved)
            ?: return ExecResult(stderr = "bash: cd: $target: لا يوجد ملف أو مجلد بهذا الاسم", exitCode = 1)
        if (!node.isDirectory) {
            return ExecResult(stderr = "bash: cd: $target: ليس مجلداً", exitCode = 1)
        }
        emitSpecial(ShellEvent.UpdateSession { it.copy(currentDir = resolved, envVars = it.envVars + ("PWD" to resolved)) })
        return ExecResult(exitCode = 0)
    }

    private fun handleLs(currentDir: String, args: List<String>): ExecResult {
        val flags = args.filter { it.startsWith("-") }.joinToString("")
        val showAll = flags.contains('a')
        val longFmt = flags.contains('l')
        val human = flags.contains('h')
        val paths = args.filter { !it.startsWith("-") }.ifEmpty { listOf(".") }

        val out = StringBuilder()
        for ((idx, rawPath) in paths.withIndex()) {
            val resolved = vfs.resolvePath(currentDir, rawPath)
            val node = vfs.getNode(resolved)
                ?: return ExecResult(stderr = "ls: لا يمكن الوصول إلى '$rawPath': غير موجود", exitCode = 2)

            if (paths.size > 1) {
                if (idx > 0) out.append("\n")
                out.append("$rawPath:\n")
            }

            if (!node.isDirectory) {
                if (longFmt) {
                    out.append("${node.permissions}  1 u0_a241 u0_a241 ${formatSize(node.sizeBytes, human)} ${node.name}\n")
                } else {
                    out.append("${node.name}\n")
                }
            } else {
                val entries = node.children.values.sortedWith(compareBy<com.example.model.VfsNode> { !it.isDirectory }.thenBy { it.name })
                if (longFmt) {
                    out.append("\u001B[2mtotal ${entries.size * 4}\u001B[0m\n")
                    if (showAll) {
                        out.append("\u001B[34m${"drwxr-xr-x"}  2 u0_a241 u0_a241 ${formatSize(4096, human)} .\u001B[0m\n")
                        out.append("\u001B[34m${"drwxr-xr-x"}  4 u0_a241 u0_a241 ${formatSize(4096, human)} ..\u001B[0m\n")
                    }
                    for (child in entries) {
                        if (!showAll && child.name.startsWith(".")) continue
                        val coloredName = when {
                            child.isDirectory -> "\u001B[1;34m${child.name}/\u001B[0m"
                            child.permissions.contains('x') -> "\u001B[1;32m${child.name}*\u001B[0m"
                            else -> child.name
                        }
                        out.append("${child.permissions}  1 u0_a241 u0_a241 ${formatSize(child.sizeBytes, human)} $coloredName\n")
                    }
                } else {
                    val items = buildList {
                        if (showAll) {
                            add("\u001B[34m.\u001B[0m")
                            add("\u001B[34m..\u001B[0m")
                        }
                        for (child in entries) {
                            if (!showAll && child.name.startsWith(".")) continue
                            when {
                                child.isDirectory -> add("\u001B[1;34m${child.name}/\u001B[0m")
                                child.permissions.contains('x') -> add("\u001B[1;32m${child.name}*\u001B[0m")
                                else -> add(child.name)
                            }
                        }
                    }
                    if (items.isNotEmpty()) out.append(items.joinToString("   ")).append("\n")
                }
            }
        }
        return ExecResult(stdout = out.toString().trimEnd('\n'))
    }

    private fun formatSize(bytes: Int, human: Boolean): String {
        if (!human) return String.format(Locale.ENGLISH, "%6d", bytes)
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.ENGLISH, "%5.1fM", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format(Locale.ENGLISH, "%5.1fK", bytes / 1024f)
            else -> String.format(Locale.ENGLISH, "%5dB", bytes)
        }
    }

    private fun handleTree(currentDir: String, args: List<String>): ExecResult {
        val target = args.firstOrNull() ?: "."
        val abs = vfs.resolvePath(currentDir, target)
        val node = vfs.getNode(abs) ?: return ExecResult(stderr = "tree: '$target' غير موجود", exitCode = 1)
        val sb = StringBuilder("\u001B[1;34m$target\u001B[0m\n")
        var dirs = 0
        var files = 0
        fun walk(n: com.example.model.VfsNode, prefix: String) {
            val list = n.children.values.sortedBy { it.name }
            list.forEachIndexed { idx, child ->
                val isLast = idx == list.lastIndex
                val branch = if (isLast) "└── " else "├── "
                val label = if (child.isDirectory) {
                    dirs++
                    "\u001B[1;34m${child.name}\u001B[0m"
                } else {
                    files++
                    if (child.permissions.contains('x')) "\u001B[32m${child.name}\u001B[0m" else child.name
                }
                sb.append("$prefix$branch$label\n")
                if (child.isDirectory) {
                    walk(child, prefix + if (isLast) "    " else "│   ")
                }
            }
        }
        if (node.isDirectory) walk(node, "")
        sb.append("\n$dirs مجلدات، $files ملفات")
        return ExecResult(stdout = sb.toString())
    }

    private fun handleMkdir(currentDir: String, args: List<String>): ExecResult {
        val recursive = args.any { it == "-p" || it == "--parents" }
        val targets = args.filter { !it.startsWith("-") }
        if (targets.isEmpty()) return ExecResult(stderr = "mkdir: المعامل مفقود (مثال: mkdir -p demo/sub)", exitCode = 1)
        for (t in targets) {
            val res = vfs.mkdir(currentDir, t, recursive)
            if (res.isFailure) return ExecResult(stderr = res.exceptionOrNull()?.message.orEmpty(), exitCode = 1)
        }
        return ExecResult()
    }

    private fun handleTouch(currentDir: String, args: List<String>): ExecResult {
        val targets = args.filter { !it.startsWith("-") }
        if (targets.isEmpty()) return ExecResult(stderr = "touch: يرجى تحديد اسم الملف (مثال: touch test.txt)", exitCode = 1)
        for (t in targets) {
            val res = vfs.touch(currentDir, t)
            if (res.isFailure) return ExecResult(stderr = res.exceptionOrNull()?.message.orEmpty(), exitCode = 1)
        }
        return ExecResult()
    }

    private fun handleCat(currentDir: String, args: List<String>, stdin: String): ExecResult {
        val showNumbers = args.contains("-n")
        val files = args.filter { !it.startsWith("-") }
        val text = if (files.isEmpty()) {
            stdin
        } else {
            val parts = mutableListOf<String>()
            for (f in files) {
                val abs = vfs.resolvePath(currentDir, f)
                val node = vfs.getNode(abs)
                    ?: return ExecResult(stderr = "cat: $f: لا يوجد ملف بهذا الاسم", exitCode = 1)
                if (node.isDirectory) {
                    return ExecResult(stderr = "cat: $f: هذا المسار مجلد", exitCode = 1)
                }
                parts.add(node.content)
            }
            parts.joinToString("\n")
        }
        val formatted = if (showNumbers && text.isNotEmpty()) {
            text.split('\n').mapIndexed { i, l -> String.format(Locale.ENGLISH, "%4d  %s", i + 1, l) }.joinToString("\n")
        } else text
        return ExecResult(stdout = formatted)
    }

    private fun handleHeadTail(currentDir: String, args: List<String>, stdin: String, isHead: Boolean): ExecResult {
        var count = 10
        val files = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            when {
                args[i] == "-n" && i + 1 < args.size -> {
                    count = args[i + 1].toIntOrNull()?.coerceAtLeast(0) ?: 10
                    i += 2
                }
                args[i].startsWith("-") && args[i].drop(1).all { it.isDigit() } -> {
                    count = args[i].drop(1).toIntOrNull() ?: 10
                    i++
                }
                else -> {
                    files.add(args[i])
                    i++
                }
            }
        }
        val content = if (files.isEmpty()) stdin else {
            val abs = vfs.resolvePath(currentDir, files.first())
            val node = vfs.getNode(abs) ?: return ExecResult(stderr = "الملف '${files.first()}' غير موجود", exitCode = 1)
            node.content
        }
        val lines = if (content.isEmpty()) emptyList() else content.split('\n')
        val sliced = if (isHead) lines.take(count) else lines.takeLast(count)
        return ExecResult(stdout = sliced.joinToString("\n"))
    }

    private fun handleWc(currentDir: String, args: List<String>, stdin: String): ExecResult {
        val flags = args.filter { it.startsWith("-") }.joinToString("")
        val files = args.filter { !it.startsWith("-") }
        val text = if (files.isEmpty()) stdin else {
            val abs = vfs.resolvePath(currentDir, files.first())
            val node = vfs.getNode(abs) ?: return ExecResult(stderr = "wc: '${files.first()}': الملف غير موجود", exitCode = 1)
            node.content
        }
        val lines = if (text.isEmpty()) 0 else text.split('\n').size
        val words = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
        val bytes = text.toByteArray().size
        val label = files.firstOrNull()?.let { " $it" }.orEmpty()
        val out = when {
            flags.contains("l") -> "$lines$label"
            flags.contains("w") -> "$words$label"
            flags.contains("c") -> "$bytes$label"
            else -> String.format(Locale.ENGLISH, "%4d %4d %4d%s", lines, words, bytes, label)
        }
        return ExecResult(stdout = out)
    }

    private fun handleEcho(args: List<String>): ExecResult {
        var interpretEscapes = false
        var noNewline = false
        val rest = mutableListOf<String>()
        var parsingFlags = true
        for (a in args) {
            if (parsingFlags && a.startsWith("-") && a.length > 1 && a.drop(1).all { it == 'e' || it == 'n' }) {
                if (a.contains('e')) interpretEscapes = true
                if (a.contains('n')) noNewline = true
            } else {
                parsingFlags = false
                rest.add(a)
            }
        }
        val joined = rest.joinToString(" ")
        val text = if (interpretEscapes) unescapeString(joined) else joined
        return ExecResult(stdout = if (noNewline) text else text)
    }

    private fun unescapeString(raw: String): String {
        return raw
            .replace("\\033", "\u001B")
            .replace("\\x1b", "\u001B")
            .replace("\\x1B", "\u001B")
            .replace("\\e", "\u001B")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "")
    }

    private fun handleRm(currentDir: String, args: List<String>): ExecResult {
        val flags = args.filter { it.startsWith("-") }.joinToString("")
        val recursive = flags.contains('r') || flags.contains('R')
        val force = flags.contains('f')
        val targets = args.filter { !it.startsWith("-") }
        if (targets.isEmpty()) return ExecResult(stderr = "rm: المعامل مفقود", exitCode = 1)
        for (t in targets) {
            val res = vfs.remove(currentDir, t, recursive, force)
            if (res.isFailure) return ExecResult(stderr = res.exceptionOrNull()?.message.orEmpty(), exitCode = 1)
        }
        return ExecResult()
    }

    private fun handleCp(currentDir: String, args: List<String>): ExecResult {
        val recursive = args.any { it == "-r" || it == "-R" || it == "-rf" }
        val paths = args.filter { !it.startsWith("-") }
        if (paths.size < 2) return ExecResult(stderr = "cp: الاستخدام الصحيح: cp <المصدر> <الوجهة>", exitCode = 1)
        val res = vfs.copy(currentDir, paths[0], paths[1], recursive)
        return if (res.isFailure) ExecResult(stderr = res.exceptionOrNull()?.message.orEmpty(), exitCode = 1) else ExecResult()
    }

    private fun handleMv(currentDir: String, args: List<String>): ExecResult {
        val paths = args.filter { !it.startsWith("-") }
        if (paths.size < 2) return ExecResult(stderr = "mv: الاستخدام الصحيح: mv <المصدر> <الوجهة>", exitCode = 1)
        val res = vfs.move(currentDir, paths[0], paths[1])
        return if (res.isFailure) ExecResult(stderr = res.exceptionOrNull()?.message.orEmpty(), exitCode = 1) else ExecResult()
    }

    private fun handleChmod(currentDir: String, args: List<String>): ExecResult {
        if (args.size < 2) return ExecResult(stderr = "chmod: الاستخدام: chmod +x <file>", exitCode = 1)
        val res = vfs.chmod(currentDir, args[0], args[1])
        return if (res.isFailure) ExecResult(stderr = res.exceptionOrNull()?.message.orEmpty(), exitCode = 1) else ExecResult()
    }

    private fun handleGrep(currentDir: String, args: List<String>, stdin: String): ExecResult {
        val flags = args.filter { it.startsWith("-") }.joinToString("")
        val ignoreCase = flags.contains('i')
        val lineNumbers = flags.contains('n')
        val invert = flags.contains('v')
        val positional = args.filter { !it.startsWith("-") }
        if (positional.isEmpty()) return ExecResult(stderr = "grep: الاستخدام: grep [خيارات] <نمط> [ملف]", exitCode = 2)

        val patternStr = positional[0]
        val regex = runCatching {
            if (ignoreCase) Regex(patternStr, RegexOption.IGNORE_CASE) else Regex(patternStr)
        }.getOrElse {
            Regex(Regex.escape(patternStr), if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        }

        val inputLines = if (positional.size >= 2) {
            val abs = vfs.resolvePath(currentDir, positional[1])
            val node = vfs.getNode(abs) ?: return ExecResult(stderr = "grep: ${positional[1]}: الملف غير موجود", exitCode = 2)
            if (node.isDirectory) return ExecResult(stderr = "grep: ${positional[1]}: هذا المسار مجلد", exitCode = 2)
            node.content.split('\n')
        } else {
            if (stdin.isEmpty()) emptyList() else stdin.split('\n')
        }

        val matched = mutableListOf<String>()
        inputLines.forEachIndexed { index, line ->
            val found = regex.containsMatchIn(line)
            if (if (invert) !found else found) {
                val highlighted = if (!invert) {
                    regex.replace(line) { mr -> "\u001B[1;31m${mr.value}\u001B[0m" }
                } else line
                val prefix = if (lineNumbers) "\u001B[32m${index + 1}\u001B[0m:" else ""
                matched.add("$prefix$highlighted")
            }
        }
        return ExecResult(stdout = matched.joinToString("\n"), exitCode = if (matched.isEmpty()) 1 else 0)
    }

    private fun handleSed(currentDir: String, args: List<String>, stdin: String): ExecResult {
        val expr = args.firstOrNull { !it.startsWith("-") }
            ?: return ExecResult(stderr = "sed: يرجى تمرير تعبير مثل s/old/new/g", exitCode = 1)
        val fileArg = args.filter { !it.startsWith("-") }.getOrNull(1)
        val content = if (fileArg != null) {
            val abs = vfs.resolvePath(currentDir, fileArg)
            val node = vfs.getNode(abs) ?: return ExecResult(stderr = "sed: $fileArg: الملف غير موجود", exitCode = 1)
            if (node.isDirectory) return ExecResult(stderr = "sed: $fileArg: هذا المسار مجلد", exitCode = 1)
            node.content
        } else stdin

        if (expr.startsWith("s/")) {
            val parts = expr.split('/')
            if (parts.size >= 3) {
                val target = parts[1]
                val repl = parts[2]
                val global = parts.getOrNull(3)?.contains('g') == true
                val out = content.split('\n').joinToString("\n") { line ->
                    if (global) line.replace(target, repl) else line.replaceFirst(target, repl)
                }
                return ExecResult(stdout = out)
            }
        }
        return ExecResult(stdout = content)
    }

    private fun handleAwk(currentDir: String, args: List<String>, stdin: String): ExecResult {
        val prog = args.firstOrNull { !it.startsWith("-") } ?: "{print $1}"
        val fileArg = args.filter { !it.startsWith("-") }.getOrNull(1)
        val content = if (fileArg != null) {
            val abs = vfs.resolvePath(currentDir, fileArg)
            val node = vfs.getNode(abs) ?: return ExecResult(stderr = "awk: $fileArg: الملف غير موجود", exitCode = 1)
            if (node.isDirectory) return ExecResult(stderr = "awk: $fileArg: هذا المسار مجلد", exitCode = 1)
            node.content
        } else stdin

        val colMatch = Regex("\\$(\\d+)").find(prog)
        val col = colMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val out = content.split('\n').filter { it.isNotEmpty() }.joinToString("\n") { line ->
            if (col == 0) line else {
                val cols = line.trim().split(Regex("\\s+"))
                cols.getOrElse(col - 1) { "" }
            }
        }
        return ExecResult(stdout = out)
    }

    private fun handleTr(args: List<String>, stdin: String): ExecResult {
        if (args.size < 2) return ExecResult(stdout = stdin)
        val from = unescapeString(args[0])
        val to = unescapeString(args[1])
        var result = stdin
        from.forEachIndexed { idx, c ->
            val rep = to.getOrElse(idx) { to.lastOrNull() ?: c }
            result = result.replace(c, rep)
        }
        return ExecResult(stdout = result)
    }

    private fun handleSort(currentDir: String, args: List<String>, stdin: String): ExecResult {
        val reverse = args.contains("-r")
        val fileArg = args.firstOrNull { !it.startsWith("-") }
        val text = if (fileArg != null) {
            val abs = vfs.resolvePath(currentDir, fileArg)
            val node = vfs.getNode(abs) ?: return ExecResult(stderr = "sort: $fileArg: الملف غير موجود", exitCode = 1)
            if (node.isDirectory) return ExecResult(stderr = "sort: $fileArg: هذا المسار مجلد", exitCode = 1)
            node.content
        } else stdin
        val lines = if (text.isEmpty()) emptyList() else text.split('\n')
        val sorted = if (reverse) lines.sortedDescending() else lines.sorted()
        return ExecResult(stdout = sorted.joinToString("\n"))
    }

    private fun handleUniq(stdin: String): ExecResult {
        if (stdin.isEmpty()) return ExecResult()
        val out = mutableListOf<String>()
        for (line in stdin.split('\n')) {
            if (out.isEmpty() || out.last() != line) out.add(line)
        }
        return ExecResult(stdout = out.joinToString("\n"))
    }

    private fun handleFind(currentDir: String, args: List<String>): ExecResult {
        val startPathRaw = args.firstOrNull { !it.startsWith("-") } ?: "."
        val nameIdx = args.indexOf("-name")
        val namePattern = if (nameIdx >= 0) args.getOrNull(nameIdx + 1) else null
        val startAbs = vfs.resolvePath(currentDir, startPathRaw)
        val startNode = vfs.getNode(startAbs) ?: return ExecResult(stderr = "find: '$startPathRaw': غير موجود", exitCode = 1)

        val results = mutableListOf<String>()
        val regex = namePattern?.let {
            Regex("^" + it.replace(".", "\\.").replace("*", ".*").replace("?", ".") + "$")
        }

        fun dfs(node: com.example.model.VfsNode, relPath: String) {
            if (regex == null || regex.matches(node.name)) {
                results.add(relPath)
            }
            if (node.isDirectory) {
                for (child in node.children.values.sortedBy { it.name }) {
                    val childRel = if (relPath == ".") "./${child.name}" else "$relPath/${child.name}"
                    dfs(child, childRel)
                }
            }
        }
        dfs(startNode, startPathRaw.trimEnd('/').ifEmpty { "/" })
        return ExecResult(stdout = results.joinToString("\n"))
    }

    private suspend fun handleExport(
        session: TerminalSession,
        args: List<String>,
        emitSpecial: suspend (ShellEvent) -> Unit
    ): ExecResult {
        if (args.isEmpty()) {
            val out = session.envVars.entries.joinToString("\n") { "declare -x ${it.key}=\"${it.value}\"" }
            return ExecResult(stdout = out)
        }
        val updates = mutableMapOf<String, String>()
        for (arg in args) {
            val eq = arg.indexOf('=')
            if (eq > 0) {
                updates[arg.substring(0, eq)] = arg.substring(eq + 1)
            }
        }
        if (updates.isNotEmpty()) {
            emitSpecial(ShellEvent.UpdateSession { it.copy(envVars = it.envVars + updates) })
        }
        return ExecResult()
    }

    private suspend fun handleUnset(args: List<String>, emitSpecial: suspend (ShellEvent) -> Unit): ExecResult {
        if (args.isNotEmpty()) {
            emitSpecial(ShellEvent.UpdateSession { it.copy(envVars = it.envVars - args.toSet()) })
        }
        return ExecResult()
    }

    private suspend fun handleAlias(
        session: TerminalSession,
        args: List<String>,
        emitSpecial: suspend (ShellEvent) -> Unit
    ): ExecResult {
        if (args.isEmpty()) {
            val out = session.aliases.entries.joinToString("\n") { "alias ${it.key}='${it.value}'" }
            return ExecResult(stdout = out)
        }
        val joined = args.joinToString(" ")
        val eq = joined.indexOf('=')
        if (eq > 0) {
            val k = joined.substring(0, eq).trim()
            val v = joined.substring(eq + 1).trim().trim('\'', '"')
            emitSpecial(ShellEvent.UpdateSession { it.copy(aliases = it.aliases + (k to v)) })
        }
        return ExecResult()
    }

    private fun handleWhich(args: List<String>): ExecResult {
        val target = args.firstOrNull() ?: return ExecResult(exitCode = 1)
        return if (target in knownCommands && (pkgRepo.isBinaryAvailable(target) || target !in listOf("git", "python", "python3", "pip", "node", "npm", "jq", "vim", "neofetch", "htop", "nmap", "clang", "ssh", "wget", "zsh"))) {
            ExecResult(stdout = "${VirtualFileSystem.USR_BIN_PATH}/$target")
        } else {
            ExecResult(stderr = "which: لا يوجد '$target' في (\$PATH)", exitCode = 1)
        }
    }

    private suspend fun handleKill(
        session: TerminalSession,
        args: List<String>,
        emitSpecial: suspend (ShellEvent) -> Unit
    ): ExecResult {
        val target = args.lastOrNull()?.removePrefix("%")?.toIntOrNull()
            ?: return ExecResult(stderr = "kill: يرجى تحديد رقم العملية PID أو %job", exitCode = 1)
        val found = session.jobs.find { it.pid == target || it.jobId == target }
            ?: return ExecResult(stderr = "kill: ($target) - العملية غير موجودة", exitCode = 1)
        emitSpecial(ShellEvent.UpdateSession { s -> s.copy(jobs = s.jobs.filterNot { it.pid == found.pid }) })
        return ExecResult(stdout = "تم إنهاء العملية ${found.command} (PID ${found.pid}).")
    }

    private suspend fun handleOpenEditor(
        currentDir: String,
        cmd: String,
        args: List<String>,
        emitSpecial: suspend (ShellEvent) -> Unit
    ): ExecResult {
        if (cmd == "vim" && !pkgRepo.isBinaryAvailable("vim")) {
            return ExecResult(stderr = "المحرر vim غير مثبت. استخدم nano أو ثبّته عبر: pkg install vim", exitCode = 127)
        }
        val fileName = args.firstOrNull() ?: "untitled.txt"
        val abs = vfs.resolvePath(currentDir, fileName)
        val existing = vfs.getNode(abs)
        if (existing != null && existing.isDirectory) {
            return ExecResult(stderr = "$cmd: '$fileName' هو مجلد وليس ملفاً", exitCode = 1)
        }
        val state = NanoEditorState(
            filePath = abs,
            displayFileName = fileName,
            content = existing?.content.orEmpty(),
            statusMessage = if (existing == null) "ملف جديد: $fileName" else "تم قراءة ${existing.content.lines().size} سطر"
        )
        emitSpecial(ShellEvent.OpenEditor(state))
        return ExecResult()
    }

    private suspend fun handleSetupStorage(emitSpecial: suspend (ShellEvent) -> Unit): ExecResult {
        val homeStorage = "${VirtualFileSystem.HOME_PATH}/storage"
        vfs.mkdir("/", homeStorage, recursive = true)
        listOf("shared", "downloads", "dcim", "documents", "music").forEach { sub ->
            vfs.mkdir("/", "$homeStorage/$sub", recursive = true)
        }
        vfs.writeFile("$homeStorage/README_storage.txt", "تم ربط مجلد ~/storage الافتراضي بنجاح.")
        return ExecResult(
            stdout = "\u001B[32m✓\u001B[0m تم إنشاء وربط مجلد التخزين \u001B[36m~/storage\u001B[0m (shared, downloads, dcim, documents, music) بنجاح."
        )
    }

    private fun executeScript(
        session: TerminalSession,
        scriptPathRaw: String,
        scriptArgs: List<String>,
        requireExecPerm: Boolean,
        traceMode: Boolean,
        exitOnError: Boolean,
        isSource: Boolean,
        depth: Int
    ): Flow<ShellEvent> = flow {
        if (depth >= 5) {
            emit(ShellEvent.OutputLine("\u001B[31mbash: تجاوز الحد الأقصى لتداخل استدعاء السكربتات ($scriptPathRaw)\u001B[0m"))
            emit(ShellEvent.UpdateSession { it.copy(lastExitCode = 1) })
            return@flow
        }

        val abs = vfs.resolvePath(session.currentDir, scriptPathRaw)
        val node = vfs.getNode(abs)
        if (node == null) {
            emit(ShellEvent.OutputLine("\u001B[31mbash: $scriptPathRaw: لا يوجد ملف بهذا الاسم\u001B[0m"))
            emit(ShellEvent.UpdateSession { it.copy(lastExitCode = 127) })
            return@flow
        }
        if (node.isDirectory) {
            emit(ShellEvent.OutputLine("\u001B[31mbash: $scriptPathRaw: هذا المسار مجلد وليس ملف سكربت\u001B[0m"))
            emit(ShellEvent.UpdateSession { it.copy(lastExitCode = 126) })
            return@flow
        }
        if (requireExecPerm && !node.permissions.contains('x')) {
            emit(ShellEvent.OutputLine("\u001B[31mbash: $scriptPathRaw: تم رفض الإذن — نفّذ: chmod +x $scriptPathRaw أو شغّله بـ bash $scriptPathRaw\u001B[0m"))
            emit(ShellEvent.UpdateSession { it.copy(lastExitCode = 126) })
            return@flow
        }

        val scriptEnv = session.envVars.toMutableMap()
        scriptEnv["0"] = scriptPathRaw
        scriptEnv["#"] = scriptArgs.size.toString()
        scriptEnv["@"] = scriptArgs.joinToString(" ")
        scriptArgs.forEachIndexed { idx, arg ->
            scriptEnv[(idx + 1).toString()] = arg
        }

        var scriptSession = session.copy(envVars = scriptEnv, lastExitCode = 0)
        emit(ShellEvent.UpdateSession { it.copy(isRunningProcess = true, runningProcessLabel = "bash $scriptPathRaw") })

        val lines = node.content.lines()
        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line == "exit" || line.startsWith("exit ")) {
                val exitCode = line.removePrefix("exit").trim().toIntOrNull() ?: 0
                scriptSession = scriptSession.copy(lastExitCode = exitCode)
                break
            }

            if (traceMode) {
                emit(ShellEvent.OutputLine("\u001B[2m+ [سطر ${index + 1}] $line\u001B[0m"))
            }

            execute(scriptSession, line, depth + 1).collect { ev ->
                when (ev) {
                    is ShellEvent.UpdateSession -> {
                        scriptSession = ev.transform(scriptSession)
                        if (isSource) emit(ev)
                    }
                    is ShellEvent.ExitSession -> Unit
                    else -> emit(ev)
                }
            }

            if (exitOnError && scriptSession.lastExitCode != 0) {
                emit(ShellEvent.OutputLine("\u001B[31m[توقف السكربت عند السطر ${index + 1} برمز خروج ${scriptSession.lastExitCode}]\u001B[0m"))
                break
            }
            delay(30)
        }

        val finalExitCode = scriptSession.lastExitCode
        emit(ShellEvent.UpdateSession {
            it.copy(
                isRunningProcess = false,
                runningProcessLabel = null,
                lastExitCode = finalExitCode
            )
        })
    }

    private fun handleRunScript(currentDir: String, scriptToken: String): ExecResult {
        val abs = vfs.resolvePath(currentDir, scriptToken)
        val node = vfs.getNode(abs)
            ?: return ExecResult(stderr = "bash: $scriptToken: لا يوجد ملف بهذا الاسم", exitCode = 127)
        if (node.isDirectory) return ExecResult(stderr = "bash: $scriptToken: إنه مجلد", exitCode = 126)
        if (!node.permissions.contains('x')) {
            return ExecResult(stderr = "bash: $scriptToken: تم رفض الإذن (استخدم chmod +x $scriptToken)", exitCode = 126)
        }
        return if (scriptToken.endsWith(".py")) {
            handlePython(currentDir, listOf(scriptToken))
        } else {
            ExecResult(stdout = "استخدم bash $scriptToken لتنفيذ السكربت.")
        }
    }

    private fun executePackageManagerStreaming(args: List<String>): Flow<ShellEvent> = flow {
        val tool = args.firstOrNull() ?: "pkg"
        val cleanArgs = args.drop(1).filter { it != "-y" && it != "--yes" }
        val sub = cleanArgs.firstOrNull().orEmpty()
        val target = cleanArgs.getOrNull(1)

        when (sub) {
            "", "--help", "-h", "help" -> {
                emit(ShellEvent.OutputLine("\u001B[1;32mاستخدام $tool:\u001B[0m"))
                emit(ShellEvent.OutputLine("  $tool update             تحديث قائمة الحزم من المستودعات"))
                emit(ShellEvent.OutputLine("  $tool upgrade            ترقية الحزم المثبتة"))
                emit(ShellEvent.OutputLine("  $tool install <package>  تثبيت حزمة جديدة (مثل git, python, nodejs)"))
                emit(ShellEvent.OutputLine("  $tool search <query>     البحث في مستودع الحزم"))
                emit(ShellEvent.OutputLine("  $tool list-installed     عرض الحزم المثبتة حالياً"))
                emit(ShellEvent.OutputLine("  $tool show <package>     عرض تفاصيل حزمة محددة"))
                emit(ShellEvent.OutputLine("  $tool uninstall <pkg>    إزالة حزمة مثبتة"))
            }
            "update" -> {
                emit(ShellEvent.UpdateSession { it.copy(isRunningProcess = true, runningProcessLabel = "$tool update") })
                emit(ShellEvent.OutputLine("\u001B[36mجاري فحص المستودعات...\u001B[0m"))
                delay(280)
                emit(ShellEvent.OutputLine("Get:1 https://packages.termux.dev/apt/termux-main stable InRelease [14.0 kB]"))
                delay(260)
                emit(ShellEvent.OutputLine("Get:2 https://packages.termux.dev/apt/termux-main stable/main aarch64 Packages [512 kB]"))
                delay(280)
                pkgRepo.lastUpdatedTimestamp = System.currentTimeMillis()
                val upgradable = pkgRepo.listUpgradable().size
                emit(ShellEvent.OutputLine("\u001B[32mتم تحديث قوائم الحزم بنجاح.\u001B[0m ($upgradable حزم قابلة للترقية — نفّذ $tool upgrade)"))
                emit(ShellEvent.UpdateSession { it.copy(isRunningProcess = false, runningProcessLabel = null) })
            }
            "upgrade" -> {
                emit(ShellEvent.UpdateSession { it.copy(isRunningProcess = true, runningProcessLabel = "$tool upgrade") })
                val upgradable = pkgRepo.listUpgradable()
                if (upgradable.isEmpty()) {
                    emit(ShellEvent.OutputLine("\u001B[32mجميع الحزم المثبتة محدثة إلى آخر إصدار.\u001B[0m"))
                } else {
                    emit(ShellEvent.OutputLine("سيتم ترقية: ${upgradable.joinToString { it.name }}"))
                    delay(350)
                    emit(ShellEvent.OutputLine("\u001B[33m[##########] 100%\u001B[0m جاري فك الضغط والإعداد..."))
                    delay(350)
                    pkgRepo.markUpgradedAll()
                    emit(ShellEvent.OutputLine("\u001B[32m✓ اكتملت الترقية بنجاح!\u001B[0m"))
                }
                emit(ShellEvent.UpdateSession { it.copy(isRunningProcess = false, runningProcessLabel = null) })
            }
            "install" -> {
                if (target == null) {
                    emit(ShellEvent.OutputLine("\u001B[31mخطأ: يرجى تحديد اسم الحزمة (مثال: $tool install python)\u001B[0m"))
                    return@flow
                }
                val targets = cleanArgs.drop(1)
                emit(ShellEvent.UpdateSession { it.copy(isRunningProcess = true, runningProcessLabel = "$tool install ${targets.joinToString(" ")}") })
                for (pkgName in targets) {
                    val info = pkgRepo.get(pkgName)
                    if (info == null) {
                        emit(ShellEvent.OutputLine("\u001B[31mE: تعذر العثور على الحزمة '$pkgName' — جرّب $tool search $pkgName\u001B[0m"))
                    } else if (info.isInstalled) {
                        emit(ShellEvent.OutputLine("\u001B[33mالحزمة ${info.name} (${info.version}) مثبتة بالفعل بأحدث إصدار.\u001B[0m"))
                    } else {
                        emit(ShellEvent.OutputLine("قراءة قوائم الحزم... تم"))
                        emit(ShellEvent.OutputLine("سيتم تثبيت الحزمة الجديدة: \u001B[1;36m${info.name}\u001B[0m (${info.version}، الحجم ${info.sizeKb} kB)"))
                        delay(280)
                        emit(ShellEvent.OutputLine("Get:1 https://packages.termux.dev/apt/termux-main ${info.name} [${info.sizeKb} kB]"))
                        delay(320)
                        emit(ShellEvent.OutputLine("\u001B[32m[#####.....] 52%\u001B[0m جاري التنزيل..."))
                        delay(300)
                        emit(ShellEvent.OutputLine("\u001B[32m[##########] 100%\u001B[0m فك الضغط وإعداد ${info.name}..."))
                        delay(240)
                        pkgRepo.install(info.name)
                        emit(ShellEvent.OutputLine("\u001B[1;32m✓ تم تثبيت ${info.name} بنجاح!\u001B[0m (${info.descriptionAr})"))
                    }
                }
                emit(ShellEvent.UpdateSession { it.copy(isRunningProcess = false, runningProcessLabel = null) })
            }
            "uninstall", "remove", "purge" -> {
                if (target == null) {
                    emit(ShellEvent.OutputLine("\u001B[31mخطأ: يرجى تحديد اسم الحزمة المراد إزالتها\u001B[0m"))
                } else if (pkgRepo.uninstall(target)) {
                    emit(ShellEvent.OutputLine("\u001B[33mتمت إزالة الحزمة $target بنجاح.\u001B[0m"))
                } else {
                    emit(ShellEvent.OutputLine("\u001B[31mتعذر إزالة '$target' (غير مثبتة أو حزمة نظام أساسية).\u001B[0m"))
                }
            }
            "search" -> {
                val q = target.orEmpty()
                val matches = pkgRepo.search(q)
                emit(ShellEvent.OutputLine("\u001B[36mنتائج البحث في المستودع${if (q.isNotEmpty()) " عن '$q'" else ""}:\u001B[0m"))
                if (matches.isEmpty()) {
                    emit(ShellEvent.OutputLine("  لم يتم العثور على حزم مطابقة."))
                } else {
                    for (p in matches) {
                        val badge = if (p.isInstalled) "\u001B[32m[مثبتة]\u001B[0m" else "\u001B[2m[متاحة]\u001B[0m"
                        emit(ShellEvent.OutputLine("  \u001B[1;32m${p.name}\u001B[0m/${p.version} $badge — ${p.descriptionAr}"))
                    }
                }
            }
            "list-installed", "list" -> {
                val installed = pkgRepo.listInstalled()
                emit(ShellEvent.OutputLine("\u001B[36mالحزم المثبتة حالياً (${installed.size}):\u001B[0m"))
                for (p in installed) {
                    emit(ShellEvent.OutputLine("  \u001B[32m${p.name}\u001B[0m  ${p.version}  (${p.categoryAr})"))
                }
            }
            "show" -> {
                val p = target?.let { pkgRepo.get(it) }
                if (p == null) {
                    emit(ShellEvent.OutputLine("\u001B[31mالحزمة '${target.orEmpty()}' غير موجودة.\u001B[0m"))
                } else {
                    emit(ShellEvent.OutputLine("Package: \u001B[1;32m${p.name}\u001B[0m"))
                    emit(ShellEvent.OutputLine("Version: ${p.version}"))
                    emit(ShellEvent.OutputLine("Installed: ${if (p.isInstalled) "نعم" else "لا"}"))
                    emit(ShellEvent.OutputLine("Size: ${p.sizeKb} kB"))
                    emit(ShellEvent.OutputLine("Binaries: ${p.providesBinaries.joinToString(", ")}"))
                    emit(ShellEvent.OutputLine("Description: ${p.descriptionAr}"))
                }
            }
            else -> {
                emit(ShellEvent.OutputLine("\u001B[31mأمر غير معروف: '$sub' — اكتب $tool --help\u001B[0m"))
            }
        }
    }

    private fun handleGit(currentDir: String, args: List<String>): ExecResult {
        if (!pkgRepo.isBinaryAvailable("git")) {
            return ExecResult(stderr = "الأمر 'git' غير مثبت. نفّذ أولاً: pkg install git", exitCode = 127)
        }
        val sub = args.firstOrNull().orEmpty()
        return when (sub) {
            "--version", "-v" -> ExecResult(stdout = "git version ${pkgRepo.get("git")?.version ?: "2.47.0"}")
            "init" -> {
                vfs.mkdir(currentDir, ".git", recursive = true)
                ExecResult(stdout = "تم تهيئة مستودع Git فارغ في $currentDir/.git/")
            }
            "status" -> {
                val hasGit = vfs.getNode(vfs.resolvePath(currentDir, ".git")) != null
                if (!hasGit) {
                    ExecResult(stderr = "fatal: هذا المجلد ليس مستودع git (شغّل git init أولاً)", exitCode = 128)
                } else {
                    ExecResult(stdout = "On branch main\nNo commits yet\nnothing to commit (use git add / git commit)")
                }
            }
            "log" -> ExecResult(stdout = "\u001B[33mcommit a8f3c1d\u001B[0m (HEAD -> main)\nAuthor: u0_a241 <termux@localhost>\n\n    Initial Termux workspace")
            else -> ExecResult(stdout = "git ${pkgRepo.get("git")?.version}: متاح (الأوامر المدعومة: init, status, log, --version)")
        }
    }

    private fun handlePython(currentDir: String, args: List<String>): ExecResult {
        if (!pkgRepo.isBinaryAvailable("python")) {
            return ExecResult(stderr = "الأمر 'python' غير مثبت. نفّذ أولاً: pkg install python", exitCode = 127)
        }
        val first = args.firstOrNull()
        return when {
            first == "--version" || first == "-V" -> ExecResult(stdout = "Python 3.12.7")
            first == "-c" && args.size >= 2 -> {
                val code = args.drop(1).joinToString(" ")
                val printRegex = Regex("""print\((.+)\)""")
                val outputs = printRegex.findAll(code).map { m ->
                    val inner = m.groupValues[1].trim()
                    if (inner.length >= 2 && ((inner.startsWith("\"") && inner.endsWith("\"")) || (inner.startsWith("'") && inner.endsWith("'")))) {
                        inner.substring(1, inner.length - 1)
                    } else {
                        evalSimpleMath(inner)?.toString() ?: inner
                    }
                }.toList()
                ExecResult(stdout = outputs.ifEmpty { listOf("[تم تنفيذ التعبير بنجاح]") }.joinToString("\n"))
            }
            first != null && !first.startsWith("-") -> {
                val abs = vfs.resolvePath(currentDir, first)
                val node = vfs.getNode(abs)
                    ?: return ExecResult(stderr = "python: can't open file '$first': غير موجود", exitCode = 2)
                if (node.isDirectory) {
                    return ExecResult(stderr = "python: '$first': هذا المسار مجلد وليس ملف سكربت", exitCode = 2)
                }
                if (first.endsWith("hello.py")) {
                    ExecResult(stdout = "مرحباً من Termux عربي!\nنتيجة 7 * 6 = 42")
                } else {
                    val out = node.content.lines()
                        .mapNotNull { Regex("""print\((.+)\)""").find(it)?.groupValues?.get(1) }
                        .joinToString("\n") { it.trim('"', '\'') }
                    ExecResult(stdout = out.ifEmpty { "تم تشغيل $first بواسطة Python 3.12.7" })
                }
            }
            else -> ExecResult(stdout = "Python 3.12.7 (Termux) — جرب: python -c \"print(6 * 7)\" أو python scripts/hello.py")
        }
    }

    private fun handlePip(args: List<String>): ExecResult {
        if (!pkgRepo.isBinaryAvailable("pip")) {
            return ExecResult(stderr = "الأمر 'pip' غير مثبت. ثبّت حزمة python عبر: pkg install python", exitCode = 127)
        }
        return when (args.firstOrNull()) {
            "list" -> ExecResult(stdout = "Package    Version\n---------- -------\npip        24.2\nsetuptools 75.1.0")
            else -> ExecResult(stdout = "pip 24.2 من python 3.12 (استخدم pip list)")
        }
    }

    private fun handleNode(args: List<String>): ExecResult {
        if (!pkgRepo.isBinaryAvailable("node")) {
            return ExecResult(stderr = "الأمر 'node' غير مثبت. نفّذ: pkg install nodejs", exitCode = 127)
        }
        return when (args.firstOrNull()) {
            "-v", "--version" -> ExecResult(stdout = "v22.11.0")
            "-e" -> {
                val code = args.drop(1).joinToString(" ")
                val m = Regex("""console\.log\((.+)\)""").find(code)
                val out = m?.groupValues?.get(1)?.trim()?.trim('"', '\'') ?: "[تم تنفيذ JavaScript]"
                ExecResult(stdout = out)
            }
            else -> ExecResult(stdout = "Node.js v22.11.0 — جرب: node -e \"console.log('Hello')\"")
        }
    }

    private fun handleNpm(args: List<String>): ExecResult {
        if (!pkgRepo.isBinaryAvailable("npm")) {
            return ExecResult(stderr = "الأمر 'npm' غير مثبت. نفّذ: pkg install nodejs", exitCode = 127)
        }
        return ExecResult(stdout = "10.9.0")
    }

    private fun handleJq(args: List<String>, stdin: String): ExecResult {
        if (!pkgRepo.isBinaryAvailable("jq")) {
            return ExecResult(stderr = "الأداة 'jq' غير مثبتة. نفّذ: pkg install jq", exitCode = 127)
        }
        val input = stdin.ifBlank { "{}" }
        return ExecResult(stdout = "\u001B[36m$input\u001B[0m")
    }

    private fun handleCurl(args: List<String>): ExecResult {
        val url = args.firstOrNull { !it.startsWith("-") }
            ?: return ExecResult(stderr = "curl: يرجى تمرير رابط (مثال: curl -I https://example.com)", exitCode = 2)
        return ExecResult(
            stdout = "\u001B[36mHTTP/2 200 OK\u001B[0m\nserver: termux-sandbox\ncontent-type: text/html; charset=UTF-8\nurl: $url\n\u001B[2m(استجابة محاكاة في بيئة Termux المحلية)\u001B[0m"
        )
    }

    private fun handleWget(args: List<String>): ExecResult {
        if (!pkgRepo.isBinaryAvailable("wget")) {
            return ExecResult(stderr = "الأداة 'wget' غير مثبتة. نفّذ: pkg install wget", exitCode = 127)
        }
        val url = args.firstOrNull { !it.startsWith("-") }
            ?: return ExecResult(stderr = "wget: الرابط مفقود", exitCode = 1)
        return ExecResult(stdout = "--2026-11-25--  $url\nتم الاتصال بنجاح (200 OK) — وضع المحاكاة المحلي.")
    }

    private fun handleArprint(args: List<String>, stdin: String): ExecResult {
        if (args.contains("--help") || args.contains("-h")) {
            return ExecResult(
                stdout = """
                    |\u001B[1;32mأداة arprint — طباعة النصوص العربية والقاموس\u001B[0m
                    |الاستخدام:
                    |  arprint "مرحباً بكم في Termux"     طباعة نص عربي
                    |  arprint -b "نص مؤطر"               طباعة النص داخل إطار أنيق
                    |  arprint -c green "نص ملون"         تلوين النص (green, cyan, yellow, red, blue, magenta)
                    |  arprint -d <كلمة>                  البحث عن كلمة في القاموس العربي
                    |  arprint --add <كلمة> <المعنى>      إضافة كلمة جديدة للقاموس
                    |  arprint --list                     عرض كلمات القاموس
                    |  arprint -r "نص"                    عرض تفاصيل الأحرف والطول
                    |  cat file.txt | arprint -b          قراءة النص من الأنبوب وتنسيقه
                """.trimMargin()
            )
        }

        if (args.firstOrNull() == "--list") {
            val list = arabicDict.getAll().take(30).joinToString("\n") {
                "  \u001B[1;32m${it.word}\u001B[0m \u001B[2m[${it.categoryAr}]\u001B[0m — ${it.meaningAr}"
            }
            return ExecResult(stdout = "\u001B[36mكلمات القاموس العربي (${arabicDict.getAll().size} كلمة):\u001B[0m\n$list")
        }

        if (args.firstOrNull() == "--add") {
            val word = args.getOrNull(1)
            val meaning = args.drop(2).joinToString(" ")
            if (word.isNullOrBlank()) {
                return ExecResult(stderr = "arprint: الاستخدام: arprint --add <كلمة> <المعنى>", exitCode = 1)
            }
            val added = arabicDict.addWord(word, meaning)
            return ExecResult(stdout = "\u001B[32m✓\u001B[0m تمت إضافة '\u001B[1;36m${added.word}\u001B[0m' إلى القاموس: ${added.meaningAr}")
        }

        val dictIdx = args.indexOfFirst { it == "-d" || it == "--dict" }
        if (dictIdx >= 0) {
            val query = args.drop(dictIdx + 1).joinToString(" ")
            return handleDict(if (query.isBlank()) emptyList() else listOf(query))
        }

        var useBox = false
        var showInfo = false
        var colorCode = ""
        val textParts = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            when (val a = args[i]) {
                "-b", "--box" -> { useBox = true; i++ }
                "-r", "--reverse" -> { showInfo = true; i++ }
                "-c", "--color" -> {
                    val cName = args.getOrNull(i + 1)?.lowercase().orEmpty()
                    colorCode = when (cName) {
                        "green" -> "\u001B[1;32m"
                        "cyan" -> "\u001B[1;36m"
                        "yellow" -> "\u001B[1;33m"
                        "red" -> "\u001B[1;31m"
                        "blue" -> "\u001B[1;34m"
                        "magenta" -> "\u001B[1;35m"
                        else -> "\u001B[1;32m"
                    }
                    i += 2
                }
                else -> { textParts.add(a); i++ }
            }
        }

        val rawText = if (textParts.isNotEmpty()) textParts.joinToString(" ") else stdin
        if (rawText.isBlank()) {
            return ExecResult(stdout = "arprint: أدخل نصاً عربياً للطباعة أو اكتب arprint --help للمساعدة.")
        }

        val formatted = buildString {
            if (useBox) {
                val lines = rawText.split('\n')
                val width = (lines.maxOfOrNull { it.length } ?: 10).coerceIn(10, 48)
                val border = "═".repeat(width + 2)
                appendLine("\u001B[36m╔$border╗\u001B[0m")
                for (line in lines) {
                    val pad = " ".repeat((width - line.length).coerceAtLeast(0))
                    val cStart = if (colorCode.isNotEmpty()) colorCode else "\u001B[1;32m"
                    appendLine("\u001B[36m║\u001B[0m $cStart$line\u001B[0m$pad \u001B[36m║\u001B[0m")
                }
                append("\u001B[36m╚$border╝\u001B[0m")
            } else if (colorCode.isNotEmpty()) {
                append("$colorCode$rawText\u001B[0m")
            } else {
                append(rawText)
            }

            if (showInfo) {
                appendLine()
                append("\u001B[2m[الطول: ${rawText.length} حرف | الكلمات: ${rawText.trim().split(Regex("\\s+")).size}]\u001B[0m")
            }
        }
        return ExecResult(stdout = formatted)
    }

    private fun handleDict(args: List<String>): ExecResult {
        val query = args.joinToString(" ").trim()
        val matches = arabicDict.lookup(query)
        if (matches.isEmpty()) {
            return ExecResult(
                stdout = "لم يتم العثور على '$query' في القاموس. لإضافتها: arprint --add \"$query\" \"المعنى\""
            )
        }
        val sb = StringBuilder()
        sb.appendLine("\u001B[1;36mنتائج القاموس العربي (${matches.size}):\u001B[0m")
        for (entry in matches.take(15)) {
            val eng = if (entry.englishHint.isNotEmpty()) " (${entry.englishHint})" else ""
            sb.appendLine("  \u001B[1;32m${entry.word}\u001B[0m$eng \u001B[33m[${entry.categoryAr}]\u001B[0m: ${entry.meaningAr}")
        }
        return ExecResult(stdout = sb.toString().trimEnd())
    }

    private fun buildHelpOutput(topic: String?): String {
        if (topic != null) return buildManOutput(topic)
        return buildString {
            appendLine("\u001B[1;32m╔══════════════════════════════════════════════════╗\u001B[0m")
            appendLine("\u001B[1;32m║       دليل Termux عربي السريع (Help)             ║\u001B[0m")
            appendLine("\u001B[1;32m╚══════════════════════════════════════════════════╝\u001B[0m")
            appendLine("\u001B[1;36m• الكتابة العربية والقاموس:\u001B[0m")
            appendLine("  \u001B[32marprint \"مرحباً\"\u001B[0m (طباعة عربية)، \u001B[32marprint -b \"نص\"\u001B[0m (بإطار)")
            appendLine("  \u001B[32marprint -d <كلمة>\u001B[0m أو \u001B[32mdict <كلمة>\u001B[0m (بحث في القاموس)")
            appendLine("\u001B[1;36m• تشغيل السكربتات (.sh):\u001B[0m")
            appendLine("  \u001B[32mbash scripts/backup.sh\u001B[0m | \u001B[32m./scripts/sysinfo.sh\u001B[0m | \u001B[32mbash -x <file.sh>\u001B[0m")
            appendLine("\u001B[1;36m• إدارة الملفات والمسارات:\u001B[0m")
            appendLine("  \u001B[32mpwd\u001B[0m, \u001B[32mcd\u001B[0m, \u001B[32mls -la\u001B[0m, \u001B[32mtree\u001B[0m, \u001B[32mfind\u001B[0m")
            appendLine("  \u001B[32mtouch\u001B[0m, \u001B[32mmkdir -p\u001B[0m, \u001B[32mcat\u001B[0m, \u001B[32mcp\u001B[0m, \u001B[32mmv\u001B[0m, \u001B[32mrm -rf\u001B[0m, \u001B[32mchmod +x\u001B[0m")
            appendLine("\u001B[1;36m• النصوص والمحرر:\u001B[0m")
            appendLine("  \u001B[32mnano <file>\u001B[0m (محرر تفاعلي)، \u001B[32mgrep\u001B[0m، \u001B[32msed\u001B[0m، \u001B[32mawk\u001B[0m، \u001B[32mwc\u001B[0m، \u001B[32mhead\u001B[0m، \u001B[32mtail\u001B[0m")
            appendLine("\u001B[1;36m• إدارة الحزم (pkg / apt):\u001B[0m")
            appendLine("  \u001B[32mpkg update\u001B[0m | \u001B[32mpkg upgrade\u001B[0m | \u001B[32mpkg search <name>\u001B[0m")
            appendLine("  \u001B[32mpkg install git python nodejs neofetch htop jq\u001B[0m")
            appendLine("  \u001B[32mpkg list-installed\u001B[0m | \u001B[32mtermux-setup-storage\u001B[0m")
            appendLine("\u001B[1;36m• الفهارس والشرح:\u001B[0m")
            appendLine("  اكتب \u001B[33mkeys\u001B[0m لعرض فهرس الاختصارات والرموز داخل الطرفية،")
            appendLine("  أو افتح تبويب \u001B[1;33m\"القاموس\"\u001B[0m أو \u001B[1;33m\"فهرس الرموز\"\u001B[0m في الأعلى.")
        }.trimEnd()
    }

    private fun buildKeysOutput(filter: String?): String {
        val items = if (filter.isNullOrBlank()) {
            ReferenceCatalog.allItems
        } else {
            val q = filter.lowercase()
            ReferenceCatalog.allItems.filter {
                it.symbol.lowercase().contains(q) ||
                    it.titleAr.lowercase().contains(q) ||
                    it.keywords.any { k -> k.lowercase().contains(q) }
            }
        }
        return buildString {
            appendLine("\u001B[1;33m=== فهرس الأحرف والرموز والمدخلات (keys) ===\u001B[0m")
            for (item in items) {
                appendLine("  \u001B[1;32m${item.symbol.padEnd(22)}\u001B[0m ${item.titleAr}")
                appendLine("    \u001B[2mمثال:\u001B[0m \u001B[36m${item.exampleCommand}\u001B[0m")
            }
            appendLine("\u001B[2mتلميح: اكتب keys <كلمة> للتصفية، أو افتح تبويب الفهرس للشرح الكامل.\u001B[0m")
        }.trimEnd()
    }

    private fun buildManOutput(cmd: String?): String {
        if (cmd.isNullOrBlank()) return "ما هي صفحة الدليل المطلوبة؟ مثال: man grep أو help"
        val item = ReferenceCatalog.allItems.firstOrNull {
            it.symbol.contains(cmd, ignoreCase = true) || it.keywords.any { k -> k.equals(cmd, ignoreCase = true) }
        }
        return if (item != null) {
            "\u001B[1;36m${item.symbol}\u001B[0m — ${item.titleAr}\n${item.descriptionAr}\nمثال: \u001B[32m${item.exampleCommand}\u001B[0m"
        } else {
            "دليل $cmd: استخدم الأعلام القياسية مع $cmd، أو جرب keys للبحث عن الرموز."
        }
    }

    private fun buildNeofetchOutput(session: TerminalSession): String {
        val installedCount = pkgRepo.listInstalled().size
        return """
            |\u001B[1;32m         -o          o-         \u001B[1;36mu0_a241@localhost\u001B[0m
            |\u001B[1;32m          +hydNNNNdyh+         \u001B[0m-----------------
            |\u001B[1;32m        +mMMMMMMMMMMMMm+       \u001B[1;32mالنظام:\u001B[0m Android (Termux عربي)
            |\u001B[1;32m      `dMMm:NMMMMMMN:mMMd`     \u001B[1;32mالنواة:\u001B[0m 6.1.75-android14 aarch64
            |\u001B[1;32m      hMMMMMMMMMMMMMMMMMMh     \u001B[1;32mالصدفة:\u001B[0m bash 5.2.37
            |\u001B[1;32m  ..  yyyyyyyyyyyyyyyyyyyy  .. \u001B[1;32mالطرفية:\u001B[0m xterm-256color
            |\u001B[1;32m.mMMm`MMMMMMMMMMMMMMMMMMMM`mMMm\u001B[1;32m الحزم:\u001B[0m $installedCount (pkg / dpkg)
            |\u001B[1;32m:MMMM-MMMMMMMMMMMMMMMMMMMM-MMMM\u001B[1;32m المسار:\u001B[0m ${vfs.formatPromptPath(session.currentDir)}
            |\u001B[1;32m:MMMM-MMMMMMMMMMMMMMMMMMMM-MMMM\u001B[1;32m اللغة:\u001B[0m ${session.envVars["LANG"]}
            |\u001B[1;32m `yMMm`MMMMMMMMMMMMMMMMMM`mMMy`\u001B[0m
            |\u001B[1;32m       -ymMMMMMMMMMMMMmy-      \u001B[41m   \u001B[42m   \u001B[43m   \u001B[44m   \u001B[45m   \u001B[46m   \u001B[0m
        """.trimMargin()
    }

    private fun buildHtopOutput(session: TerminalSession): String {
        val sb = StringBuilder()
        sb.appendLine("\u001B[1;36m  CPU[\u001B[32m||||||||||      \u001B[36m24.5%]   Tasks: ${3 + session.jobs.size}, 1 running\u001B[0m")
        sb.appendLine("\u001B[1;36m  Mem[\u001B[33m||||||||||||    \u001B[36m2.1G/8.0G] Load average: 0.42 0.38 0.31\u001B[0m")
        sb.appendLine("\u001B[42m  PID USER      PRI  NI  VIRT   RES S CPU% MEM%   Command             \u001B[0m")
        sb.appendLine(" 1001 u0_a241    20   0 18.4M  6.2M S  0.3  0.1   /data/data/com.termux/files/usr/bin/bash")
        sb.appendLine(" 1422 u0_a241    20   0 24.1M  9.8M R  3.1  0.2   htop")
        for (job in session.jobs) {
            sb.appendLine(String.format(Locale.ENGLISH, " %4d u0_a241    20   0 12.0M  4.1M S  0.1  0.1   %s", job.pid, job.command))
        }
        return sb.toString().trimEnd()
    }

    // ======================== Shell Parsing Helpers ========================

    private fun stripTrailingComment(input: String): String {
        var inSingle = false
        var inDouble = false
        var escape = false
        for (i in input.indices) {
            val c = input[i]
            if (escape) {
                escape = false
                continue
            }
            when (c) {
                '\\' -> if (!inSingle) escape = true
                '\'' -> if (!inDouble) inSingle = !inSingle
                '"' -> if (!inSingle) inDouble = !inDouble
                '#' -> if (!inSingle && !inDouble && (i == 0 || input[i - 1].isWhitespace())) {
                    return input.substring(0, i).trim()
                }
            }
        }
        return input
    }

    private fun splitChainOperators(input: String): List<Pair<String, ChainOp>> {
        val result = mutableListOf<Pair<String, ChainOp>>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var escape = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (escape) {
                current.append(c)
                escape = false
                i++
                continue
            }
            if (c == '\\' && !inSingle) {
                escape = true
                current.append(c)
                i++
                continue
            }
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle
                current.append(c)
                i++
                continue
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble
                current.append(c)
                i++
                continue
            }
            if (!inSingle && !inDouble) {
                if (c == '&' && i + 1 < input.length && input[i + 1] == '&') {
                    result.add(current.toString().trim() to ChainOp.AND)
                    current.clear()
                    i += 2
                    continue
                }
                if (c == '|' && i + 1 < input.length && input[i + 1] == '|') {
                    result.add(current.toString().trim() to ChainOp.OR)
                    current.clear()
                    i += 2
                    continue
                }
                if (c == ';') {
                    result.add(current.toString().trim() to ChainOp.SEMI)
                    current.clear()
                    i++
                    continue
                }
                if (c == '&' && (i == 0 || (input[i - 1] != '>' && input[i - 1] != '2')) && !(i + 1 < input.length && input[i + 1] == '>')) {
                    result.add(current.toString().trim() to ChainOp.BG)
                    current.clear()
                    i++
                    continue
                }
            }
            current.append(c)
            i++
        }
        if (current.isNotBlank()) {
            result.add(current.toString().trim() to ChainOp.NONE)
        }
        return result.filter { it.first.isNotEmpty() }
    }

    private fun splitPipes(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var escape = false
        for (c in input) {
            if (escape) {
                current.append(c)
                escape = false
                continue
            }
            when (c) {
                '\\' -> if (!inSingle) { escape = true; current.append(c) } else current.append(c)
                '\'' -> { if (!inDouble) inSingle = !inSingle; current.append(c) }
                '"' -> { if (!inSingle) inDouble = !inDouble; current.append(c) }
                '|' -> if (!inSingle && !inDouble) {
                    result.add(current.toString().trim())
                    current.clear()
                } else current.append(c)
                else -> current.append(c)
            }
        }
        if (current.isNotBlank()) result.add(current.toString().trim())
        return result
    }

    private fun expandAliases(segment: String, aliases: Map<String, String>): String {
        val parts = segment.split(Regex("\\s+"), limit = 2)
        val first = parts.firstOrNull().orEmpty()
        val mapped = aliases[first] ?: arabicCommandAliases[first] ?: return segment
        return if (parts.size > 1) "$mapped ${parts[1]}" else mapped
    }

    private fun tokenizeArgs(raw: String, session: TerminalSession): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var escape = false
        var hasToken = false
        var i = 0

        while (i < raw.length) {
            val c = raw[i]
            if (escape) {
                current.append(c)
                escape = false
                hasToken = true
                i++
                continue
            }
            if (c == '\\' && !inSingle) {
                escape = true
                hasToken = true
                i++
                continue
            }
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle
                hasToken = true
                i++
                continue
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble
                hasToken = true
                i++
                continue
            }

            // Arithmetic $(( ... )) and Command Substitution $( ... )
            if (!inSingle && c == '$' && i + 1 < raw.length && raw[i + 1] == '(') {
                if (i + 2 < raw.length && raw[i + 2] == '(') {
                    val close = raw.indexOf("))", i + 3)
                    if (close > i) {
                        val expr = raw.substring(i + 3, close)
                        val valNum = evalSimpleMath(expandVariables(expr, session)) ?: 0
                        current.append(valNum)
                        hasToken = true
                        i = close + 2
                        continue
                    }
                } else {
                    val close = raw.indexOf(')', i + 2)
                    if (close > i) {
                        val subCmd = raw.substring(i + 2, close).trim()
                        val subOut = evalSubshellSimple(subCmd, session)
                        current.append(subOut)
                        hasToken = true
                        i = close + 1
                        continue
                    }
                }
            }

            // Backtick substitution `...`
            if (!inSingle && c == '`') {
                val close = raw.indexOf('`', i + 1)
                if (close > i) {
                    val subCmd = raw.substring(i + 1, close).trim()
                    val subOut = evalSubshellSimple(subCmd, session)
                    current.append(subOut)
                    hasToken = true
                    i = close + 1
                    continue
                }
            }

            // Variable $VAR, ${VAR}, $?, $#, $@, $0..$9
            if (!inSingle && c == '$') {
                if (i + 1 < raw.length && raw[i + 1] == '?') {
                    current.append(session.lastExitCode)
                    hasToken = true
                    i += 2
                    continue
                } else if (i + 1 < raw.length && (raw[i + 1] in '0'..'9' || raw[i + 1] == '#' || raw[i + 1] == '@')) {
                    val key = raw[i + 1].toString()
                    current.append(session.envVars[key] ?: "")
                    hasToken = true
                    i += 2
                    continue
                } else if (i + 1 < raw.length && raw[i + 1] == '{') {
                    val close = raw.indexOf('}', i + 2)
                    if (close > i) {
                        val varName = raw.substring(i + 2, close)
                        current.append(session.envVars[varName] ?: "")
                        hasToken = true
                        i = close + 1
                        continue
                    }
                } else {
                    val m = Regex("^([A-Za-z_][A-Za-z0-9_]*)").find(raw.substring(i + 1))
                    if (m != null) {
                        val varName = m.groupValues[1]
                        current.append(session.envVars[varName] ?: "")
                        hasToken = true
                        i += 1 + varName.length
                        continue
                    }
                }
            }

            if (!inSingle && !inDouble && c.isWhitespace()) {
                if (hasToken) {
                    tokens.addAll(expandBraces(current.toString()))
                    current.clear()
                    hasToken = false
                }
                i++
                continue
            }

            // Separate redirection operators when unquoted
            if (!inSingle && !inDouble) {
                if (raw.startsWith("2>&1", i) || raw.startsWith("&>", i) || raw.startsWith("2>", i) || raw.startsWith(">>", i)) {
                    if (hasToken) {
                        tokens.addAll(expandBraces(current.toString()))
                        current.clear()
                        hasToken = false
                    }
                    val opLen = when {
                        raw.startsWith("2>&1", i) -> 4
                        else -> 2
                    }
                    tokens.add(raw.substring(i, i + opLen))
                    i += opLen
                    continue
                }
                if (c == '>' || c == '<') {
                    if (hasToken) {
                        tokens.addAll(expandBraces(current.toString()))
                        current.clear()
                        hasToken = false
                    }
                    tokens.add(c.toString())
                    i++
                    continue
                }
            }

            current.append(c)
            hasToken = true
            i++
        }

        if (hasToken) {
            tokens.addAll(expandBraces(current.toString()))
        }
        return tokens
    }

    private fun expandBraces(token: String): List<String> {
        val m = Regex("""(.*)\{([^,{}]+,[^}]+)\}(.*)""").matchEntire(token) ?: return listOf(token)
        val prefix = m.groupValues[1]
        val items = m.groupValues[2].split(',')
        val suffix = m.groupValues[3]
        return items.map { "$prefix$it$suffix" }
    }

    private fun expandVariables(raw: String, session: TerminalSession): String {
        return Regex("""\$([A-Za-z_][A-Za-z0-9_]*|[0-9@#])""").replace(raw) { mr ->
            session.envVars[mr.groupValues[1]] ?: "0"
        }
    }

    private fun evalSubshellSimple(subCmd: String, session: TerminalSession): String {
        return when (subCmd) {
            "pwd" -> session.currentDir
            "whoami" -> session.envVars["USER"] ?: "u0_a241"
            "date" -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date())
            "uname" -> "Linux"
            else -> subCmd
        }
    }

    private fun parseRedirections(tokens: List<String>): ParsedCommand {
        val args = mutableListOf<String>()
        var stdinFile: String? = null
        var stdoutFile: String? = null
        var stdoutAppend = false
        var stderrFile: String? = null
        var combine = false

        var i = 0
        while (i < tokens.size) {
            when (tokens[i]) {
                ">" -> {
                    stdoutFile = tokens.getOrNull(i + 1)
                    stdoutAppend = false
                    i += 2
                }
                ">>" -> {
                    stdoutFile = tokens.getOrNull(i + 1)
                    stdoutAppend = true
                    i += 2
                }
                "<" -> {
                    stdinFile = tokens.getOrNull(i + 1)
                    i += 2
                }
                "2>" -> {
                    stderrFile = tokens.getOrNull(i + 1)
                    i += 2
                }
                "&>" -> {
                    stdoutFile = tokens.getOrNull(i + 1)
                    stdoutAppend = false
                    combine = true
                    i += 2
                }
                "2>&1" -> {
                    combine = true
                    i += 1
                }
                else -> {
                    args.add(tokens[i])
                    i++
                }
            }
        }
        return ParsedCommand(args, stdinFile, stdoutFile, stdoutAppend, stderrFile, combine)
    }

    private fun expandGlobsForArgs(currentDir: String, args: List<String>): List<String> {
        if (args.isEmpty()) return args
        val cmd = args.first()
        if (cmd == "grep" || cmd == "sed" || cmd == "awk" || cmd == "echo") return args
        val out = mutableListOf(cmd)
        for (a in args.drop(1)) {
            if (a.startsWith("-")) out.add(a) else out.addAll(vfs.expandGlob(currentDir, a))
        }
        return out
    }

    private fun evalSimpleMath(expr: String): Long? {
        return runCatching {
            val clean = expr.replace(" ", "")
            val tokens = mutableListOf<String>()
            var num = StringBuilder()
            for (c in clean) {
                if (c.isDigit()) {
                    num.append(c)
                } else if (c in listOf('+', '-', '*', '/', '%')) {
                    if (num.isNotEmpty()) {
                        tokens.add(num.toString())
                        num = StringBuilder()
                    }
                    tokens.add(c.toString())
                }
            }
            if (num.isNotEmpty()) tokens.add(num.toString())
            if (tokens.isEmpty()) return null

            // Handle *, /, %
            val pass1 = mutableListOf<String>()
            var i = 0
            while (i < tokens.size) {
                val t = tokens[i]
                if ((t == "*" || t == "/" || t == "%") && pass1.isNotEmpty() && i + 1 < tokens.size) {
                    val left = pass1.removeAt(pass1.lastIndex).toLong()
                    val right = tokens[i + 1].toLong()
                    val res = when (t) {
                        "*" -> left * right
                        "/" -> if (right == 0L) return null else left / right
                        else -> if (right == 0L) return null else left % right
                    }
                    pass1.add(res.toString())
                    i += 2
                } else {
                    pass1.add(t)
                    i++
                }
            }
            // Handle +, -
            var acc = pass1[0].toLong()
            var j = 1
            while (j + 1 < pass1.size) {
                val op = pass1[j]
                val rhs = pass1[j + 1].toLong()
                acc = if (op == "+") acc + rhs else acc - rhs
                j += 2
            }
            acc
        }.getOrNull()
    }
}
