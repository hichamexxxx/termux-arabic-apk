package com.example.engine

import com.example.model.VfsNode
import org.json.JSONObject
import java.io.File

class VirtualFileSystem(private val storageFile: File? = null) {
    companion object {
        const val HOME_PATH = "/data/data/com.termux/files/home"
        const val USR_BIN_PATH = "/data/data/com.termux/files/usr/bin"
    }

    private val lock = Any()

    var root: VfsNode = createDefaultTree()
        private set

    var recoveredFromCorruption: Boolean = false
        private set

    init {
        loadFromDisk()
    }

    private fun createDefaultTree(): VfsNode {
        val rootNode = VfsNode("/", isDirectory = true)
        ensureDir(rootNode, "/data/data/com.termux/files/home/scripts")
        ensureDir(rootNode, "/data/data/com.termux/files/usr/bin")
        ensureDir(rootNode, "/data/data/com.termux/files/usr/etc")
        ensureDir(rootNode, "/tmp")

        writeInitialFile(
            rootNode,
            "$HOME_PATH/README.txt",
            """
            |مرحباً بك في Termux عربي!
            |هذه بيئة طرفية تفاعلية محاكية بنظام ملفات افتراضي كامل.
            |
            |أوامر سريعة للبداية:
            |  help                عرض دليل الأوامر السريع
            |  keys                عرض فهرس الاختصارات والرموز داخل الطرفية
            |  ls -la              عرض كافة الملفات والمجلدات
            |  pkg search python   البحث عن حزمة
            |  pkg install git     تثبيت أداة git
            |  nano notes.md       تعديل ملف باستخدام محرر nano
            """.trimMargin()
        )

        writeInitialFile(
            rootNode,
            "$HOME_PATH/notes.md",
            """
            |# ملاحظات Termux اليومية
            |1. استخدم Tab للإكمال التلقائي للأوامر والملفات.
            |2. استخدم | لتمرير مخرج أمر إلى grep أو wc.
            |3. استخدم > للكتابة و >> للإلحاق دون مسح الملف.
            |4. اكتب termux-setup-storage لربط مجلد ~/storage.
            |5. افتح تبويب "فهرس الرموز والمدخلات" لأي اختصار.
            """.trimMargin()
        )

        writeInitialFile(
            rootNode,
            "$HOME_PATH/scripts/backup.sh",
            """
            |#!/data/data/com.termux/files/usr/bin/bash
            |# سكربت نسخ احتياطي تجريبي
            |# يقوم بفحص المجلد الرئيسي وطباعة حالة الملفات
            |echo "بدء فحص الملفات في: $HOME_PATH"
            |ls -la ~
            |echo "اكتمل الفحص بنجاح."
            """.trimMargin(),
            permissions = "-rwxr-xr-x"
        )

        writeInitialFile(
            rootNode,
            "$HOME_PATH/scripts/sysinfo.sh",
            """
            |#!/data/data/com.termux/files/usr/bin/bash
            |# سكربت عرض تقرير النظام
            |arprint -b "تقرير النظام السريع - Termux عربي"
            |uname -a
            |date
            |echo "المسار الحالي: ${'$'}PWD"
            |ls -lh ~
            """.trimMargin(),
            permissions = "-rwxr-xr-x"
        )

        writeInitialFile(
            rootNode,
            "$HOME_PATH/scripts/hello.py",
            """
            |#!/data/data/com.termux/files/usr/bin/python3
            |# مثال Python سريع
            |name = "Termux عربي"
            |print(f"مرحباً من {name}!")
            |print("نتيجة 7 * 6 =", 7 * 6)
            """.trimMargin(),
            permissions = "-rwxr-xr-x"
        )

        writeInitialFile(
            rootNode,
            "$HOME_PATH/data.json",
            """
            |{
            |  "app": "Termux",
            |  "shell": "bash",
            |  "lang": "ar",
            |  "tools": ["pkg", "git", "python", "nano"]
            |}
            """.trimMargin()
        )

        return rootNode
    }

    private fun writeInitialFile(
        rootNode: VfsNode,
        absPath: String,
        content: String,
        permissions: String = "-rw-r--r--"
    ) {
        val parts = absPath.trim('/').split('/').filter { it.isNotEmpty() }
        var cur = rootNode
        for (i in 0 until parts.size - 1) {
            val dir = parts[i]
            cur = cur.children.getOrPut(dir) { VfsNode(dir, isDirectory = true) }
        }
        val fileName = parts.last()
        cur.children[fileName] = VfsNode(
            name = fileName,
            isDirectory = false,
            content = content,
            permissions = permissions
        )
    }

    private fun ensureDir(rootNode: VfsNode, absPath: String) {
        val parts = absPath.trim('/').split('/').filter { it.isNotEmpty() }
        var cur = rootNode
        for (part in parts) {
            cur = cur.children.getOrPut(part) { VfsNode(part, isDirectory = true) }
        }
    }

    fun resetToDefault() = synchronized(lock) {
        root = createDefaultTree()
        recoveredFromCorruption = false
        saveToDisk()
    }

    fun formatPromptPath(absPath: String): String {
        return when {
            absPath == HOME_PATH -> "~"
            absPath.startsWith("$HOME_PATH/") -> "~" + absPath.removePrefix(HOME_PATH)
            else -> absPath
        }
    }

    fun resolvePath(currentDir: String, inputPath: String): String {
        val trimmed = inputPath.trim()
        val raw = when {
            trimmed.isEmpty() -> currentDir
            trimmed == "~" -> HOME_PATH
            trimmed.startsWith("~/") -> "$HOME_PATH/${trimmed.removePrefix("~/")}"
            trimmed.startsWith("/") -> trimmed
            else -> if (currentDir == "/") "/$trimmed" else "$currentDir/$trimmed"
        }

        val stack = ArrayDeque<String>()
        for (segment in raw.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(segment)
            }
        }
        return if (stack.isEmpty()) "/" else "/" + stack.joinToString("/")
    }

    fun getNode(absPath: String): VfsNode? = synchronized(lock) {
        if (absPath == "/") return root
        val parts = absPath.trim('/').split('/').filter { it.isNotEmpty() }
        var cur: VfsNode = root
        for (part in parts) {
            if (!cur.isDirectory) return null
            cur = cur.children[part] ?: return null
        }
        return cur
    }

    fun parentAndName(absPath: String): Pair<String, String>? {
        if (absPath == "/") return null
        val idx = absPath.lastIndexOf('/')
        val parent = if (idx <= 0) "/" else absPath.substring(0, idx)
        val name = absPath.substring(idx + 1)
        return parent to name
    }

    fun mkdir(currentDir: String, rawPath: String, recursive: Boolean): Result<String> = synchronized(lock) {
        val abs = resolvePath(currentDir, rawPath)
        if (abs == "/") return Result.failure(IllegalStateException("mkdir: لا يمكن إنشاء '/': المجلد موجود"))
        if (recursive) {
            val parts = abs.trim('/').split('/').filter { it.isNotEmpty() }
            var cur = root
            for (part in parts) {
                val existing = cur.children[part]
                if (existing != null) {
                    if (!existing.isDirectory) {
                        return Result.failure(IllegalStateException("mkdir: لا يمكن إنشاء '$rawPath': يوجد ملف بنفس الاسم"))
                    }
                    cur = existing
                } else {
                    val newDir = VfsNode(part, isDirectory = true)
                    cur.children[part] = newDir
                    cur = newDir
                }
            }
            saveToDisk()
            return Result.success(abs)
        } else {
            val (parentPath, name) = parentAndName(abs)
                ?: return Result.failure(IllegalStateException("mkdir: مسار غير صالح '$rawPath'"))
            val parent = getNode(parentPath)
                ?: return Result.failure(IllegalStateException("mkdir: المسار '$rawPath': لا يوجد ملف أو مجلد بهذا الاسم"))
            if (!parent.isDirectory) {
                return Result.failure(IllegalStateException("mkdir: '$parentPath' ليس مجلداً"))
            }
            if (parent.children.containsKey(name)) {
                return Result.failure(IllegalStateException("mkdir: لا يمكن إنشاء '$rawPath': المجلد موجود مسبقاً"))
            }
            parent.children[name] = VfsNode(name, isDirectory = true)
            saveToDisk()
            return Result.success(abs)
        }
    }

    fun touch(currentDir: String, rawPath: String): Result<String> = synchronized(lock) {
        val abs = resolvePath(currentDir, rawPath)
        val (parentPath, name) = parentAndName(abs)
            ?: return Result.failure(IllegalStateException("touch: مسار غير صالح '/'"))
        val parent = getNode(parentPath)
            ?: return Result.failure(IllegalStateException("touch: المسار '$rawPath': المجلد الأب غير موجود"))
        if (!parent.isDirectory) {
            return Result.failure(IllegalStateException("touch: '$parentPath' ليس مجلداً"))
        }
        val existing = parent.children[name]
        if (existing != null) {
            parent.children[name] = existing.copy(modifiedAt = System.currentTimeMillis())
        } else {
            parent.children[name] = VfsNode(name, isDirectory = false, content = "")
        }
        saveToDisk()
        return Result.success(abs)
    }

    fun writeFile(absPath: String, content: String, append: Boolean = false): Result<Unit> = synchronized(lock) {
        val (parentPath, name) = parentAndName(absPath)
            ?: return Result.failure(IllegalStateException("خطأ: مسار غير صالح"))
        val parent = getNode(parentPath)
            ?: return Result.failure(IllegalStateException("المسار '$parentPath': المجلد غير موجود"))
        if (!parent.isDirectory) {
            return Result.failure(IllegalStateException("'$parentPath' ليس مجلداً"))
        }
        val existing = parent.children[name]
        if (existing != null && existing.isDirectory) {
            return Result.failure(IllegalStateException("'$absPath': هذا المسار مجلد وليس ملفاً"))
        }
        val newContent = if (append && existing != null) {
            if (existing.content.isEmpty()) content else "${existing.content}\n$content"
        } else {
            content
        }
        parent.children[name] = VfsNode(
            name = name,
            isDirectory = false,
            content = newContent,
            permissions = existing?.permissions ?: "-rw-r--r--",
            modifiedAt = System.currentTimeMillis()
        )
        saveToDisk()
        return Result.success(Unit)
    }

    fun remove(currentDir: String, rawPath: String, recursive: Boolean, force: Boolean): Result<Unit> = synchronized(lock) {
        val abs = resolvePath(currentDir, rawPath)
        if (abs == "/" || abs == HOME_PATH) {
            return Result.failure(IllegalStateException("rm: عملية مرفوضة لحماية النظام على '$rawPath'"))
        }
        val (parentPath, name) = parentAndName(abs)
            ?: return Result.failure(IllegalStateException("rm: مسار غير صالح '$rawPath'"))
        val parent = getNode(parentPath)
        val target = parent?.children?.get(name)
        if (target == null) {
            return if (force) Result.success(Unit)
            else Result.failure(IllegalStateException("rm: لا يمكن حذف '$rawPath': الملف غير موجود"))
        }
        if (target.isDirectory && !recursive) {
            return Result.failure(IllegalStateException("rm: لا يمكن حذف '$rawPath': إنه مجلد (استخدم -r)"))
        }
        parent.children.remove(name)
        saveToDisk()
        return Result.success(Unit)
    }

    fun copy(currentDir: String, srcRaw: String, dstRaw: String, recursive: Boolean): Result<Unit> = synchronized(lock) {
        val srcAbs = resolvePath(currentDir, srcRaw)
        val srcNode = getNode(srcAbs)
            ?: return Result.failure(IllegalStateException("cp: تعذر العثور على '$srcRaw': الملف غير موجود"))
        if (srcNode.isDirectory && !recursive) {
            return Result.failure(IllegalStateException("cp: تم تخطي المجلد '$srcRaw' (استخدم -r)"))
        }
        val dstAbs = resolvePath(currentDir, dstRaw)
        if (srcNode.isDirectory && (dstAbs == srcAbs || dstAbs.startsWith("$srcAbs/"))) {
            return Result.failure(IllegalStateException("cp: لا يمكن نسخ المجلد '$srcRaw' إلى داخل نفسه '$dstRaw'"))
        }
        val dstExisting = getNode(dstAbs)
        if (dstExisting != null && dstExisting.isDirectory) {
            dstExisting.children[srcNode.name] = deepCopyNode(srcNode, srcNode.name)
            saveToDisk()
            return Result.success(Unit)
        }
        val (dstParentPath, dstName) = parentAndName(dstAbs)
            ?: return Result.failure(IllegalStateException("cp: وجهة غير صالحة '$dstRaw'"))
        val dstParent = getNode(dstParentPath)
            ?: return Result.failure(IllegalStateException("cp: المجلد الوجهة '$dstParentPath' غير موجود"))
        if (!dstParent.isDirectory) {
            return Result.failure(IllegalStateException("cp: '$dstParentPath' ليس مجلداً"))
        }
        dstParent.children[dstName] = deepCopyNode(srcNode, dstName)
        saveToDisk()
        return Result.success(Unit)
    }

    fun move(currentDir: String, srcRaw: String, dstRaw: String): Result<Unit> = synchronized(lock) {
        val srcAbs = resolvePath(currentDir, srcRaw)
        if (srcAbs == "/" || srcAbs == HOME_PATH) {
            return Result.failure(IllegalStateException("mv: لا يمكن نقل '$srcRaw'"))
        }
        val (srcParentPath, srcName) = parentAndName(srcAbs)
            ?: return Result.failure(IllegalStateException("mv: مسار غير صالح '$srcRaw'"))
        val srcParent = getNode(srcParentPath)
        val srcNode = srcParent?.children?.get(srcName)
            ?: return Result.failure(IllegalStateException("mv: تعذر العثور على '$srcRaw': الملف غير موجود"))

        val dstAbs = resolvePath(currentDir, dstRaw)
        if (dstAbs == srcAbs) {
            return Result.failure(IllegalStateException("mv: '$srcRaw' و'$dstRaw' يشيران إلى نفس المسار"))
        }
        if (srcNode.isDirectory && dstAbs.startsWith("$srcAbs/")) {
            return Result.failure(IllegalStateException("mv: لا يمكن نقل المجلد '$srcRaw' إلى داخل نفسه '$dstRaw'"))
        }
        val dstExisting = getNode(dstAbs)
        if (dstExisting != null && dstExisting.isDirectory) {
            dstExisting.children[srcName] = srcNode
            srcParent.children.remove(srcName)
            saveToDisk()
            return Result.success(Unit)
        }
        val (dstParentPath, dstName) = parentAndName(dstAbs)
            ?: return Result.failure(IllegalStateException("mv: وجهة غير صالحة '$dstRaw'"))
        val dstParent = getNode(dstParentPath)
            ?: return Result.failure(IllegalStateException("mv: المجلد الوجهة '$dstParentPath' غير موجود"))
        if (!dstParent.isDirectory) {
            return Result.failure(IllegalStateException("mv: '$dstParentPath' ليس مجلداً"))
        }
        dstParent.children[dstName] = srcNode.copy(name = dstName, modifiedAt = System.currentTimeMillis())
        srcParent.children.remove(srcName)
        saveToDisk()
        return Result.success(Unit)
    }

    fun chmod(currentDir: String, mode: String, rawPath: String): Result<Unit> = synchronized(lock) {
        val abs = resolvePath(currentDir, rawPath)
        val (parentPath, name) = parentAndName(abs)
            ?: return Result.failure(IllegalStateException("chmod: مسار غير صالح"))
        val parent = getNode(parentPath)
        val target = parent?.children?.get(name)
            ?: return Result.failure(IllegalStateException("chmod: لا يمكن الوصول إلى '$rawPath': غير موجود"))

        val prefix = if (target.isDirectory) "d" else "-"
        val newPerms = when {
            mode == "+x" -> {
                val p = target.permissions.toCharArray()
                if (p.size == 10) {
                    p[3] = 'x'; p[6] = 'x'; p[9] = 'x'
                    String(p)
                } else "${prefix}rwxr-xr-x"
            }
            mode == "-x" -> "${prefix}rw-r--r--"
            mode.matches(Regex("[0-7]{3}")) -> {
                val rwx = mode.map { digit ->
                    val v = digit.digitToInt()
                    buildString {
                        append(if ((v and 4) != 0) 'r' else '-')
                        append(if ((v and 2) != 0) 'w' else '-')
                        append(if ((v and 1) != 0) 'x' else '-')
                    }
                }.joinToString("")
                "$prefix$rwx"
            }
            else -> return Result.failure(IllegalStateException("chmod: صيغة غير مدعومة '$mode' (جرب +x أو 755)"))
        }
        parent.children[name] = target.copy(permissions = newPerms, modifiedAt = System.currentTimeMillis())
        saveToDisk()
        return Result.success(Unit)
    }

    fun listShellScripts(): List<String> = synchronized(lock) {
        val homeNode = getNode(HOME_PATH) ?: return emptyList()
        val found = mutableListOf<String>()
        fun walk(node: VfsNode, absPath: String) {
            if (!node.isDirectory && node.name.endsWith(".sh")) {
                found.add(formatPromptPath(absPath))
            } else if (node.isDirectory) {
                for (child in node.children.values.sortedBy { it.name }) {
                    walk(child, "$absPath/${child.name}")
                }
            }
        }
        walk(homeNode, HOME_PATH)
        return found
    }

    fun expandGlob(currentDir: String, token: String): List<String> = synchronized(lock) {
        if (!token.contains('*') && !token.contains('?') && !token.contains('[')) {
            return listOf(token)
        }
        val slashIdx = token.lastIndexOf('/')
        val dirPart = if (slashIdx >= 0) token.substring(0, slashIdx + 1) else ""
        val patternPart = if (slashIdx >= 0) token.substring(slashIdx + 1) else token
        val targetDirAbs = if (dirPart.isEmpty()) currentDir else resolvePath(currentDir, dirPart)
        val dirNode = getNode(targetDirAbs) ?: return listOf(token)
        if (!dirNode.isDirectory) return listOf(token)

        val regex = globToRegex(patternPart)
        val matches = dirNode.children.keys
            .filter { name ->
                if (!patternPart.startsWith(".") && name.startsWith(".")) false
                else regex.matches(name)
            }
            .sorted()
            .map { "$dirPart$it" }

        return matches.ifEmpty { listOf(token) }
    }

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                '[' -> {
                    val close = glob.indexOf(']', i + 1)
                    if (close > i) {
                        sb.append(glob.substring(i, close + 1))
                        i = close
                    } else {
                        sb.append("\\[")
                    }
                }
                '.', '(', ')', '+', '|', '^', '$', '\\', '{', '}' -> sb.append("\\").append(c)
                else -> sb.append(c)
            }
            i++
        }
        sb.append("$")
        return Regex(sb.toString())
    }

    private fun deepCopyNode(node: VfsNode, newName: String): VfsNode {
        val copiedChildren = mutableMapOf<String, VfsNode>()
        for ((k, v) in node.children) {
            copiedChildren[k] = deepCopyNode(v, v.name)
        }
        return node.copy(name = newName, children = copiedChildren, modifiedAt = System.currentTimeMillis())
    }

    fun saveToDisk(): Unit = synchronized(lock) {
        val file = storageFile ?: return@synchronized
        runCatching {
            val payload = nodeToJson(root).toString()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(payload)
            if (!tmp.renameTo(file)) {
                file.writeText(payload)
                tmp.delete()
            }
        }
    }

    private fun loadFromDisk(): Unit = synchronized(lock) {
        val file = storageFile ?: return@synchronized
        if (!file.exists()) return@synchronized
        runCatching {
            val json = JSONObject(file.readText())
            val parsed = jsonToNode(json)
            if (!parsed.isDirectory || parsed.children[HOME_PATH.trim('/').split('/').first()] == null) {
                throw IllegalStateException("Corrupted VFS root")
            }
            root = parsed
        }.onFailure {
            recoveredFromCorruption = true
            root = createDefaultTree()
            saveToDisk()
        }
    }

    private fun nodeToJson(node: VfsNode): JSONObject {
        val obj = JSONObject()
        obj.put("name", node.name)
        obj.put("isDir", node.isDirectory)
        obj.put("perms", node.permissions)
        obj.put("mod", node.modifiedAt)
        if (!node.isDirectory) {
            obj.put("content", node.content)
        } else {
            val childrenObj = JSONObject()
            for ((k, v) in node.children) {
                childrenObj.put(k, nodeToJson(v))
            }
            obj.put("children", childrenObj)
        }
        return obj
    }

    private fun jsonToNode(obj: JSONObject): VfsNode {
        val isDir = obj.optBoolean("isDir", false)
        val childrenMap = mutableMapOf<String, VfsNode>()
        if (isDir) {
            val childrenObj = obj.optJSONObject("children")
            if (childrenObj != null) {
                val keys = childrenObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    childrenMap[key] = jsonToNode(childrenObj.getJSONObject(key))
                }
            }
        }
        return VfsNode(
            name = obj.optString("name", ""),
            isDirectory = isDir,
            content = obj.optString("content", ""),
            permissions = obj.optString("perms", if (isDir) "drwxr-xr-x" else "-rw-r--r--"),
            modifiedAt = obj.optLong("mod", System.currentTimeMillis()),
            children = childrenMap
        )
    }
}
