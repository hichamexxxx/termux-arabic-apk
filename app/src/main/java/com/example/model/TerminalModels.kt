package com.example.model

enum class AppTab(val arabicTitle: String) {
    TERMINAL("الطرفية"),
    DICTIONARY("القاموس"),
    REFERENCE("فهرس الرموز والمدخلات")
}

data class TerminalLine(
    val id: Long,
    val rawText: String,
    val isCommandEcho: Boolean = false,
    val promptPrefix: String = ""
)

data class NanoEditorState(
    val filePath: String,
    val displayFileName: String,
    val content: String,
    val statusMessage: String = "تم فتح الملف — استخدم أزرار الأسفل أو الاختصارات",
    val isModified: Boolean = false,
    val searchQuery: String = "",
    val showSearchBar: Boolean = false
)

data class BackgroundJob(
    val jobId: Int,
    val pid: Int,
    val command: String,
    val statusArabic: String = "قيد التشغيل"
)

data class TerminalSession(
    val id: Int,
    val title: String,
    val currentDir: String,
    val lines: List<TerminalLine>,
    val history: List<String> = emptyList(),
    val envVars: Map<String, String> = defaultEnvVars(),
    val aliases: Map<String, String> = mapOf(
        "ll" to "ls -la",
        "la" to "ls -a",
        "cls" to "clear"
    ),
    val lastExitCode: Int = 0,
    val isRunningProcess: Boolean = false,
    val runningProcessLabel: String? = null,
    val jobs: List<BackgroundJob> = emptyList(),
    val nanoState: NanoEditorState? = null
)

fun defaultEnvVars(): Map<String, String> = mapOf(
    "HOME" to "/data/data/com.termux/files/home",
    "PREFIX" to "/data/data/com.termux/files/usr",
    "PATH" to "/data/data/com.termux/files/usr/bin:/system/bin",
    "SHELL" to "/data/data/com.termux/files/usr/bin/bash",
    "TERM" to "xterm-256color",
    "USER" to "u0_a241",
    "LANG" to "ar_SA.UTF-8",
    "EDITOR" to "nano"
)

data class VfsNode(
    val name: String,
    val isDirectory: Boolean,
    val content: String = "",
    val permissions: String = if (isDirectory) "drwxr-xr-x" else "-rw-r--r--",
    val modifiedAt: Long = System.currentTimeMillis(),
    val children: MutableMap<String, VfsNode> = mutableMapOf()
) {
    val sizeBytes: Int
        get() = if (isDirectory) children.size * 4096.coerceAtLeast(4096) else content.toByteArray().size
}

data class PackageInfo(
    val name: String,
    val version: String,
    val sizeKb: Int,
    val descriptionAr: String,
    val categoryAr: String,
    val providesBinaries: List<String>,
    val isInstalled: Boolean = false,
    val hasUpgrade: Boolean = false
)

enum class ReferenceCategory(val arabicLabel: String) {
    ALL("الكل"),
    CONTROL_KEYS("مفاتيح التحكم"),
    SHELL_OPERATORS("رموز Shell"),
    PUNCTUATION_MATH("الترقيم والمنطق"),
    KEYBOARD_ASCII("المدخلات و ASCII")
}

enum class UsageContextBadge(val arabicLabel: String) {
    SHELL_SYSTEM("رمز نظام Shell"),
    INSIDE_TOOLS("داخل الأدوات واللغات"),
    BOTH("Shell والأدوات معاً"),
    KEYBOARD_CONTROL("إدخال طرفية وتحكم")
}

data class ReferenceItem(
    val id: String,
    val symbol: String,
    val titleAr: String,
    val category: ReferenceCategory,
    val contextBadge: UsageContextBadge,
    val descriptionAr: String,
    val shellVsToolNoteAr: String? = null,
    val exampleCommand: String,
    val exampleResultAr: String,
    val insertText: String,
    val runDirectlyInTerminal: Boolean = false,
    val keywords: List<String> = emptyList()
)
