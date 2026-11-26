package com.example.data

import com.example.model.PackageInfo

class PackageRepository {
    private val packages = linkedMapOf(
        "bash" to PackageInfo(
            name = "bash",
            version = "5.2.37-1",
            sizeKb = 1420,
            descriptionAr = "مفسر الأوامر القياسي GNU Bourne Again SHell",
            categoryAr = "النظام والصدفة",
            providesBinaries = listOf("bash", "sh"),
            isInstalled = true
        ),
        "coreutils" to PackageInfo(
            name = "coreutils",
            version = "9.5-2",
            sizeKb = 3150,
            descriptionAr = "الأدوات الأساسية لإدارة الملفات والنصوص (ls, cp, mv, cat)",
            categoryAr = "الأدوات الأساسية",
            providesBinaries = listOf("ls", "cat", "cp", "mv", "rm", "mkdir", "touch", "wc", "head", "tail"),
            isInstalled = true
        ),
        "nano" to PackageInfo(
            name = "nano",
            version = "8.2-1",
            sizeKb = 680,
            descriptionAr = "محرر نصوص طرفي بسيط ومباشر مع شريط اختصارات سفلي",
            categoryAr = "محررات النصوص",
            providesBinaries = listOf("nano"),
            isInstalled = true
        ),
        "arprint" to PackageInfo(
            name = "arprint",
            version = "1.2.0-1",
            sizeKb = 240,
            descriptionAr = "أداة طباعة وتنسيق النصوص العربية مع قاموس الكلمات المدمج",
            categoryAr = "الأدوات العربية",
            providesBinaries = listOf("arprint", "dict"),
            isInstalled = true
        ),
        "curl" to PackageInfo(
            name = "curl",
            version = "8.10.1-1",
            sizeKb = 1120,
            descriptionAr = "أداة نقل البيانات عبر بروتوكولات HTTP/HTTPS/FTP من سطر الأوامر",
            categoryAr = "الشبكات",
            providesBinaries = listOf("curl"),
            isInstalled = true,
            hasUpgrade = true
        ),
        "git" to PackageInfo(
            name = "git",
            version = "2.47.0-1",
            sizeKb = 8450,
            descriptionAr = "نظام التحكم في الإصدارات الموزع لإدارة المستودعات البرمجية",
            categoryAr = "التطوير",
            providesBinaries = listOf("git"),
            isInstalled = false
        ),
        "python" to PackageInfo(
            name = "python",
            version = "3.12.7-1",
            sizeKb = 18600,
            descriptionAr = "مفسر لغة Python 3 ومدير حزم pip لتشغيل السكربتات",
            categoryAr = "لغات البرمجة",
            providesBinaries = listOf("python", "python3", "pip"),
            isInstalled = false
        ),
        "nodejs" to PackageInfo(
            name = "nodejs",
            version = "22.11.0-1",
            sizeKb = 24500,
            descriptionAr = "بيئة تشغيل JavaScript خارج المتصفح مع مدير حزم npm",
            categoryAr = "لغات البرمجة",
            providesBinaries = listOf("node", "npm", "npx"),
            isInstalled = false
        ),
        "openssh" to PackageInfo(
            name = "openssh",
            version = "9.9p1-1",
            sizeKb = 2890,
            descriptionAr = "أدوات الاتصال الآمن والمشفر بالخوادم البعيدة (ssh, scp, sftp)",
            categoryAr = "الشبكات والأمان",
            providesBinaries = listOf("ssh", "scp", "ssh-keygen"),
            isInstalled = false
        ),
        "clang" to PackageInfo(
            name = "clang",
            version = "19.1.2-1",
            sizeKb = 64200,
            descriptionAr = "مصرّف LLVM لترجمة برامج C و C++ داخل Termux",
            categoryAr = "التطوير",
            providesBinaries = listOf("clang", "clang++", "gcc"),
            isInstalled = false
        ),
        "nmap" to PackageInfo(
            name = "nmap",
            version = "7.95-1",
            sizeKb = 5240,
            descriptionAr = "أداة فحص الشبكات والمنافذ المفتوحة واكتشاف الأجهزة",
            categoryAr = "الشبكات والأمان",
            providesBinaries = listOf("nmap"),
            isInstalled = false
        ),
        "jq" to PackageInfo(
            name = "jq",
            version = "1.7.1-1",
            sizeKb = 490,
            descriptionAr = "معالج خفيف وسريع لتنسيق وتحليل بيانات JSON في الأنابيب",
            categoryAr = "معالجة البيانات",
            providesBinaries = listOf("jq"),
            isInstalled = false
        ),
        "vim" to PackageInfo(
            name = "vim",
            version = "9.1.0750-1",
            sizeKb = 4120,
            descriptionAr = "محرر النصوص المتقدم ذو الأوضاع المتعددة للمبرمجين",
            categoryAr = "محررات النصوص",
            providesBinaries = listOf("vim", "vi"),
            isInstalled = false
        ),
        "wget" to PackageInfo(
            name = "wget",
            version = "1.24.5-1",
            sizeKb = 910,
            descriptionAr = "أداة سطر أوامر لتنزيل الملفات من الويب",
            categoryAr = "الشبكات",
            providesBinaries = listOf("wget"),
            isInstalled = false
        ),
        "htop" to PackageInfo(
            name = "htop",
            version = "3.3.0-1",
            sizeKb = 410,
            descriptionAr = "مراقب تفاعلي للعمليات واستهلاك المعالج والذاكرة",
            categoryAr = "النظام",
            providesBinaries = listOf("htop"),
            isInstalled = false
        ),
        "neofetch" to PackageInfo(
            name = "neofetch",
            version = "7.1.0-3",
            sizeKb = 180,
            descriptionAr = "أداة لعرض معلومات النظام والهاردوير بشكل جمالي في الطرفية",
            categoryAr = "النظام",
            providesBinaries = listOf("neofetch"),
            isInstalled = false
        ),
        "zsh" to PackageInfo(
            name = "zsh",
            version = "5.9-4",
            sizeKb = 2780,
            descriptionAr = "صدفة أوامر متطورة تدعم الإكمال الذكي والإضافات",
            categoryAr = "النظام والصدفة",
            providesBinaries = listOf("zsh"),
            isInstalled = false
        )
    )

    var lastUpdatedTimestamp: Long = 0L

    fun getAll(): List<PackageInfo> = packages.values.toList()

    fun get(name: String): PackageInfo? = packages[name.lowercase()]

    fun isInstalled(name: String): Boolean = packages[name.lowercase()]?.isInstalled == true

    fun isBinaryAvailable(binary: String): Boolean {
        return packages.values.any { it.isInstalled && binary in it.providesBinaries }
    }

    fun install(name: String): Boolean {
        val key = name.lowercase()
        val existing = packages[key] ?: return false
        packages[key] = existing.copy(isInstalled = true, hasUpgrade = false)
        return true
    }

    fun uninstall(name: String): Boolean {
        val key = name.lowercase()
        val existing = packages[key] ?: return false
        if (key == "bash" || key == "coreutils") return false
        packages[key] = existing.copy(isInstalled = false)
        return true
    }

    fun search(query: String): List<PackageInfo> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return getAll()
        return packages.values.filter {
            it.name.contains(q) ||
                it.descriptionAr.lowercase().contains(q) ||
                it.categoryAr.lowercase().contains(q)
        }
    }

    fun listInstalled(): List<PackageInfo> = packages.values.filter { it.isInstalled }

    fun listUpgradable(): List<PackageInfo> = packages.values.filter { it.isInstalled && it.hasUpgrade }

    fun markUpgradedAll() {
        packages.keys.forEach { key ->
            val pkg = packages[key]
            if (pkg != null && pkg.isInstalled && pkg.hasUpgrade) {
                packages[key] = pkg.copy(hasUpgrade = false)
            }
        }
    }

    fun restoreInstalledNames(names: Set<String>) {
        names.forEach { name ->
            packages[name]?.let { packages[name] = it.copy(isInstalled = true) }
        }
    }

    fun resetToDefault() {
        val defaultInstalled = setOf("bash", "coreutils", "nano", "arprint", "curl")
        packages.keys.toList().forEach { key ->
            val pkg = packages[key] ?: return@forEach
            packages[key] = pkg.copy(
                isInstalled = key in defaultInstalled,
                hasUpgrade = (key == "curl")
            )
        }
        lastUpdatedTimestamp = 0L
    }
}
