package com.example

import com.example.data.ArabicDictionary
import com.example.data.PackageRepository
import com.example.data.ReferenceCatalog
import com.example.data.TerminalHistoryCodec
import com.example.engine.ShellEngine
import com.example.engine.ShellEvent
import com.example.engine.VirtualFileSystem
import com.example.model.ReferenceCategory
import com.example.model.TerminalLine
import com.example.model.TerminalSession
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShellEngineTest {

    private fun newEngine(): Pair<ShellEngine, TerminalSession> {
        val vfs = VirtualFileSystem(null)
        val pkg = PackageRepository()
        val engine = ShellEngine(vfs, pkg)
        val session = TerminalSession(
            id = 1,
            title = "جلسة #1",
            currentDir = VirtualFileSystem.HOME_PATH,
            lines = emptyList()
        )
        return engine to session
    }

    @Test
    fun redirectionAndPipeWorkCorrectly() = runTest {
        val (engine, session) = newEngine()
        engine.execute(session, "echo \"hello termux\" > out.txt").toList()
        engine.execute(session, "echo \"second line\" >> out.txt").toList()
        val events = engine.execute(session, "cat out.txt | grep termux").toList()
        val output = events.filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(output.contains("hello"))
        assertTrue(output.contains("termux"))
    }

    @Test
    fun pkgInstallUnlocksGitAndPython() = runTest {
        val (engine, session) = newEngine()
        val beforeGit = engine.execute(session, "git --version").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(beforeGit.contains("pkg install git"))

        engine.execute(session, "pkg install -y git python").toList()
        val afterGit = engine.execute(session, "git --version").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(afterGit.contains("git version"))

        val pyOut = engine.execute(session, "python -c \"print(6 * 7)\"").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertEquals("42", pyOut.trim())
    }

    @Test
    fun autocompleteCompletesCommandsAndFiles() {
        val (engine, session) = newEngine()
        val (completedCmd, _) = engine.completeInput(session.currentDir, "neofe")
        assertEquals("neofetch ", completedCmd)

        val (completedFile, _) = engine.completeInput(session.currentDir, "cat RE")
        assertEquals("cat README.txt ", completedFile)
    }

    @Test
    fun referenceCatalogCoversAllRequiredCategories() {
        val categories = ReferenceCatalog.allItems.map { it.category }.toSet()
        assertTrue(ReferenceCategory.CONTROL_KEYS in categories)
        assertTrue(ReferenceCategory.SHELL_OPERATORS in categories)
        assertTrue(ReferenceCategory.PUNCTUATION_MATH in categories)
        assertTrue(ReferenceCategory.KEYBOARD_ASCII in categories)
    }

    @Test
    fun arprintAndArabicDictionaryWorkCorrectly() = runTest {
        val (engine, session) = newEngine()
        val printOut = engine.execute(session, "arprint -b \"مرحباً بكم\"").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(printOut.contains("مرحباً بكم"))
        assertTrue(printOut.contains("╔"))

        val dictOut = engine.execute(session, "arprint -d طرفية").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(dictOut.contains("Terminal"))

        engine.execute(session, "arprint --add سحابة منصة الحوسبة").toList()
        val addedLookup = engine.execute(session, "dict سحابة").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(addedLookup.contains("منصة الحوسبة"))

        val (_, arMatches) = engine.completeInput(session.currentDir, "arprint طر")
        assertTrue(arMatches.isEmpty() || "طرفية" in arMatches)
    }

    @Test
    fun shellScriptLineByLineRunnerExecutesVirtualShFiles() = runTest {
        val (engine, session) = newEngine()
        // Create a custom .sh script in VFS with multiple commands
        engine.execute(session, "echo \"# demo script\" > demo.sh").toList()
        engine.execute(session, "echo \"mkdir -p my_out\" >> demo.sh").toList()
        engine.execute(session, "echo \"arprint \\\"مرحبا \\\$1\\\" > my_out/res.txt\" >> demo.sh").toList()
        engine.execute(session, "echo \"cat my_out/res.txt\" >> demo.sh").toList()

        // Running via ./demo.sh without +x must fail with permission error
        val deniedOut = engine.execute(session, "./demo.sh أحمد").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(deniedOut.contains("chmod +x"))

        // Running via bash demo.sh executes line-by-line with positional arg $1
        val bashOut = engine.execute(session, "bash -x demo.sh أحمد").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(bashOut.contains("+ [سطر"))
        assertTrue(bashOut.contains("مرحبا أحمد"))

        // Grant +x and run directly via ./demo.sh
        engine.execute(session, "chmod +x demo.sh").toList()
        val directOut = engine.execute(session, "./demo.sh سارة").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(directOut.contains("مرحبا سارة"))
    }

    @Test
    fun historyPreferencesCodecAndClearCommandWorkCorrectly() = runTest {
        val sampleHistory = listOf("ls -la", "arprint \"مرحباً\"", "pwd")
        val encodedHist = TerminalHistoryCodec.encodeHistory(sampleHistory)
        val decodedHist = TerminalHistoryCodec.decodeHistory(encodedHist)
        assertEquals(sampleHistory, decodedHist)

        val sampleLines = listOf(
            TerminalLine(1L, "ls -la", isCommandEcho = true, promptPrefix = "~ $"),
            TerminalLine(2L, "README.txt")
        )
        val encodedLines = TerminalHistoryCodec.encodeLines(sampleLines)
        val decodedLines = TerminalHistoryCodec.decodeLines(encodedLines)
        assertEquals(2, decodedLines.size)
        assertTrue(decodedLines[0].isCommandEcho)
        assertEquals("~ $", decodedLines[0].promptPrefix)
        assertEquals("README.txt", decodedLines[1].rawText)

        val (engine, baseSession) = newEngine()
        var working = baseSession.copy(history = sampleHistory)
        val events = engine.execute(working, "history -c").toList()
        events.filterIsInstance<ShellEvent.UpdateSession>().forEach { working = it.transform(working) }
        assertTrue(working.history.isEmpty())
    }

    @Test
    fun strictErrorHandlingPreventsCrashesOnBadInputsAndSelfRecursion() = runTest {
        val (engine, session) = newEngine()
        engine.execute(session, "pkg install -y python").toList()

        // 1. Malformed regex in grep must not throw PatternSyntaxException
        val grepBad = engine.execute(session, "grep \"[unclosed\" README.txt").toList()
        assertTrue(grepBad.any { it is ShellEvent.UpdateSession })

        // 2. Directory passed to text tools / python must fail gracefully
        val grepDir = engine.execute(session, "grep test scripts").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(grepDir.contains("مجلد"))

        // 3. Single-quote edge case in python -c print(") must not throw StringIndexOutOfBoundsException
        val pyQuote = engine.execute(session, "python -c \"print(\\\")\"").toList()
        assertTrue(pyQuote.isNotEmpty())

        // 4. Division by zero in arithmetic expansion must not throw ArithmeticException
        val divZero = engine.execute(session, "echo ${'$'}((10 / 0))").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertEquals("0", divZero.trim())

        // 5. Copying or moving a directory into itself must fail safely
        val selfCopy = engine.execute(session, "cp -r scripts scripts/sub").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(selfCopy.contains("داخل نفسه"))

        // 6. Self-recursive .sh script must stop safely at max depth without stack overflow
        engine.execute(session, "echo \"bash loop.sh\" > loop.sh").toList()
        val loopOut = engine.execute(session, "bash loop.sh").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(loopOut.contains("تجاوز الحد الأقصى"))
    }

    @Test
    fun defaultResetAndCorruptedStorageRecoveryWorkCleanly() {
        // Test full reset across VFS, PackageRepository, and ArabicDictionary
        val vfs = VirtualFileSystem(null)
        val pkgRepo = PackageRepository()
        val dict = ArabicDictionary(null)

        vfs.touch(VirtualFileSystem.HOME_PATH, "custom_temp.txt")
        vfs.remove(VirtualFileSystem.HOME_PATH, "README.txt", recursive = false, force = false)
        pkgRepo.install("git")
        dict.addWord("كلمة_مؤقتة", "اختبار")

        vfs.resetToDefault()
        pkgRepo.resetToDefault()
        dict.resetToDefault()

        assertNull(vfs.getNode("${VirtualFileSystem.HOME_PATH}/custom_temp.txt"))
        assertNotNull(vfs.getNode("${VirtualFileSystem.HOME_PATH}/README.txt"))
        assertNotNull(vfs.getNode("${VirtualFileSystem.HOME_PATH}/scripts/sysinfo.sh"))
        assertFalse(pkgRepo.isInstalled("git"))
        assertTrue(pkgRepo.isInstalled("bash"))
        assertNull(dict.exactMatch("كلمة_مؤقتة"))
        assertNotNull(dict.exactMatch("طرفية"))

        // Test corrupted VFS file auto-recovery
        val tempFile = File.createTempFile("corrupt_vfs", ".json")
        try {
            tempFile.writeText("{not valid json!!!")
            val recoveredVfs = VirtualFileSystem(tempFile)
            assertTrue(recoveredVfs.recoveredFromCorruption)
            assertNotNull(recoveredVfs.getNode("${VirtualFileSystem.HOME_PATH}/README.txt"))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun arabicCommandsAliasesAndAutocompleteWorkCorrectly() = runTest {
        val (engine, session) = newEngine()

        // 1. Arabic pwd ("مسار")
        val pwdOut = engine.execute(session, "مسار").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertEquals(VirtualFileSystem.HOME_PATH, pwdOut.trim())

        // 2. Arabic mkdir ("مجلد") & ls ("عرض") with pipe grep ("بحث")
        engine.execute(session, "مجلد مجلدي").toList()
        val pipedOut = engine.execute(session, "عرض | بحث مجلدي").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(pipedOut.contains("مجلدي"))

        // 3. Arabic arprint ("طباعة") & dictionary lookup ("قاموس")
        val printOut = engine.execute(session, "طباعة \"أهلاً بالطرفية\"").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(printOut.contains("أهلاً بالطرفية"))

        val dictOut = engine.execute(session, "قاموس طرفية").toList()
            .filterIsInstance<ShellEvent.OutputLine>().joinToString("\n") { it.text }
        assertTrue(dictOut.contains("Terminal"))

        // 4. TAB completion of Arabic command prefix
        val (completedAr, _) = engine.completeInput(session.currentDir, "طب")
        assertEquals("طباعة ", completedAr)
    }
}
