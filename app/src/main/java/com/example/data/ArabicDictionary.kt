package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ArabicWordEntry(
    val word: String,
    val meaningAr: String,
    val categoryAr: String = "عام",
    val englishHint: String = "",
    val isCustom: Boolean = false
)

class ArabicDictionary(private val customFile: File? = null) {

    private val defaultEntries = listOf(
        // كلمات تقنية وبرمجية ونظام Linux/Termux
        ArabicWordEntry("طرفية", "واجهة سطر الأوامر للتفاعل مع النظام وتنفيذ الأوامر", "تقنية", "Terminal"),
        ArabicWordEntry("أمر", "تعليمة نصية ينفذها مفسر الأوامر bash", "تقنية", "Command"),
        ArabicWordEntry("ملف", "وحدة تخزين البيانات أو النصوص أو الشيفرة البرمجية", "تقنية", "File"),
        ArabicWordEntry("مجلد", "دليل يحتوي على ملفات ومجلدات فرعية", "تقنية", "Directory"),
        ArabicWordEntry("مسار", "عنوان يحدد موقع الملف أو المجلد داخل النظام", "تقنية", "Path"),
        ArabicWordEntry("حزمة", "برنامج أو مكتبة مجمعة قابلة للتثبيت عبر pkg أو apt", "تقنية", "Package"),
        ArabicWordEntry("مكتبة", "مجموعة دوال وأكواد جاهزة تُستخدم في البرمجة", "برمجة", "Library"),
        ArabicWordEntry("برمجة", "كتابة التعليمات والأكواد لبناء التطبيقات والسكربتات", "برمجة", "Programming"),
        ArabicWordEntry("سكربت", "ملف نصي يحتوي على سلسلة أوامر تُنفذ تلقائياً", "برمجة", "Script"),
        ArabicWordEntry("متغير", "اسم رمزي يخزن قيمة قابلة للتغيير مثل \$HOME", "برمجة", "Variable"),
        ArabicWordEntry("دالة", "كتلة برمجية تنفذ مهمة محددة ويمكن استدعاؤها", "برمجة", "Function"),
        ArabicWordEntry("خادم", "جهاز أو برنامج يقدم خدمات وبيانات عبر الشبكة", "شبكات", "Server"),
        ArabicWordEntry("شبكة", "ربط الأجهزة لتبادل البيانات والاتصال", "شبكات", "Network"),
        ArabicWordEntry("اتصال", "جلسة تواصل بين جهازين أو مع خادم بعيد مثل ssh", "شبكات", "Connection"),
        ArabicWordEntry("تشفير", "تحويل البيانات لصيغة آمنة لا تُقرأ إلا بمفتاح", "أمان", "Encryption"),
        ArabicWordEntry("أمان", "حماية النظام والبيانات والملفات من الاختراق", "أمان", "Security"),
        ArabicWordEntry("صلاحيات", "أذونات القراءة والكتابة والتنفيذ (chmod)", "نظام", "Permissions"),
        ArabicWordEntry("مستخدم", "الحساب الذي يعمل ضمنه النظام والجلسة الحالية", "نظام", "User"),
        ArabicWordEntry("جلسة", "نافذة عمل مستقلة داخل الطرفية لها تاريخ ومسار خاص", "نظام", "Session"),
        ArabicWordEntry("عملية", "برنامج قيد التشغيل في الذاكرة برقم PID", "نظام", "Process"),
        ArabicWordEntry("ذاكرة", "مساحة التخزين المؤقت RAM لتشغيل العمليات", "نظام", "Memory"),
        ArabicWordEntry("معالج", "وحدة المعالجة المركزية التي تنفذ التعليمات", "نظام", "CPU"),
        ArabicWordEntry("تخزين", "مساحة حفظ الملفات الدائمة في الجهاز", "نظام", "Storage"),
        ArabicWordEntry("نظام", "البيئة التشغيلية الكاملة مثل Android أو Linux", "نظام", "System"),
        ArabicWordEntry("تحديث", "جلب أحدث قائمة للحزم المتاحة عبر pkg update", "إدارة", "Update"),
        ArabicWordEntry("ترقية", "تثبيت الإصدارات الأحدث للحزم عبر pkg upgrade", "إدارة", "Upgrade"),
        ArabicWordEntry("تثبيت", "إضافة أداة أو حزمة جديدة للنظام عبر pkg install", "إدارة", "Install"),
        ArabicWordEntry("إزالة", "حذف حزمة أو ملف من النظام", "إدارة", "Uninstall"),
        ArabicWordEntry("بحث", "التنقيب عن ملفات أو حزم أو نصوص عبر grep و find", "عمليات", "Search"),
        ArabicWordEntry("تعديل", "تغيير محتوى ملف نصي عبر محرر مثل nano أو vim", "عمليات", "Edit"),
        ArabicWordEntry("حفظ", "تثبيت التغييرات في الملف على القرص", "عمليات", "Save"),
        ArabicWordEntry("نسخ", "إنشاء نسخة مطابقة من ملف أو نص (cp)", "عمليات", "Copy"),
        ArabicWordEntry("نقل", "تحريك ملف إلى مسار آخر أو إعادة تسميته (mv)", "عمليات", "Move"),
        ArabicWordEntry("حذف", "إزالة ملف أو مجلد نهائياً عبر rm", "عمليات", "Delete"),
        ArabicWordEntry("إنشاء", "توليد ملف جديد أو مجلد جديد عبر touch و mkdir", "عمليات", "Create"),
        ArabicWordEntry("عرض", "إظهار محتوى ملف أو مجلد على الشاشة مثل cat و ls", "عمليات", "Display"),
        ArabicWordEntry("طباعة", "إخراج النص إلى الشاشة عبر echo أو arprint", "عمليات", "Print"),
        ArabicWordEntry("توجيه", "تحويل مخرجات الأمر إلى ملف باستخدام > أو >>", "Shell", "Redirection"),
        ArabicWordEntry("أنبوب", "تمرير مخرجات أمر كمدخل لأمر آخر عبر |", "Shell", "Pipe"),
        ArabicWordEntry("اختصار", "مفتاح أو تركيبة مفاتيح لتنفيذ إجراء سريع مثل Ctrl+C", "تحكم", "Shortcut"),
        ArabicWordEntry("مؤشر", "علامة موضع الكتابة الحالية داخل الطرفية", "تحكم", "Cursor"),
        ArabicWordEntry("إكمال", "تخمين وتكميل اسم الأمر أو الملف عبر زر Tab", "تحكم", "Autocomplete"),
        ArabicWordEntry("تاريخ", "سجل الأوامر المنفذة سابقاً في الجلسة (history)", "تحكم", "History"),
        ArabicWordEntry("مخرجات", "النص الناتج عن تنفيذ الأمر (stdout)", "Shell", "Output"),
        ArabicWordEntry("مدخلات", "البيانات التي يتلقاها الأمر من لوحة المفاتيح أو ملف (stdin)", "Shell", "Input"),
        ArabicWordEntry("أخطاء", "رسائل التنبيه والفشل القياسية (stderr)", "Shell", "Errors"),
        ArabicWordEntry("مستودع", "مصدر الحزم أو مشروع برمجي مُدار عبر git", "تطوير", "Repository"),
        ArabicWordEntry("مشروع", "مجموعة ملفات وأكواد متكاملة لبناء برنامج", "تطوير", "Project"),
        ArabicWordEntry("كود", "نصوص برمجية مكتوبة بلغة مثل Python أو Kotlin", "تطوير", "Code"),
        ArabicWordEntry("بيانات", "معلومات مهيكلة مثل JSON أو نصوص يتم تحليلها", "تطوير", "Data"),
        ArabicWordEntry("نص", "سلسلة أحرف وكلمات قابلة للقراءة والمعالجة", "عام", "Text"),
        ArabicWordEntry("سطر", "صف واحد من النص ينتهي بمحرف سطر جديد", "عام", "Line"),
        ArabicWordEntry("كلمة", "وحدة لغوية مفصولة بمسافات", "عام", "Word"),
        ArabicWordEntry("حرف", "رمز أو حرف أبجدي واحد", "عام", "Character"),
        ArabicWordEntry("رمز", "علامة خاصة مثل $ أو | أو * لها دلالة تقنية", "عام", "Symbol"),
        ArabicWordEntry("قاموس", "مرجع يضم الكلمات العربية ومعانيها والمصطلحات", "عام", "Dictionary"),
        ArabicWordEntry("دليل", "مرجع تعليمي يشرح الأوامر والرموز مع أمثلة", "عام", "Guide"),
        ArabicWordEntry("رسالة", "إشعار نصي يظهر للمستخدم لتوضيح الحالة", "عام", "Message"),
        ArabicWordEntry("نتيجة", "المُحصلة النهائية بعد تنفيذ أمر أو عملية", "عام", "Result"),
        ArabicWordEntry("نجاح", "اكتمال تنفيذ الأمر برمز خروج 0", "عام", "Success"),
        ArabicWordEntry("فشل", "توقف الأمر أو حدوث خطأ برمز خروج غير صفري", "عام", "Failure"),
        // كلمات عربية شائعة للكتابة اليومية والتجربة السريعة
        ArabicWordEntry("مرحباً", "تحية عربية شائعة للترحيب وبداية الجلسة", "تحية", "Hello"),
        ArabicWordEntry("السلام", "كلمة عربية للتحية (السلام عليكم)", "تحية", "Peace"),
        ArabicWordEntry("أهلاً", "عبارة ترحيب بالمستخدم", "تحية", "Welcome"),
        ArabicWordEntry("شكراً", "تعبير عن الامتنان والتقدير", "عام", "Thanks"),
        ArabicWordEntry("العربية", "لغة الضاد المستخدمة في واجهة التطبيق والقاموس", "لغة", "Arabic"),
        ArabicWordEntry("المطور", "الشخص الذي يكتب البرامج ويدير الأدوات", "تطوير", "Developer"),
        ArabicWordEntry("التطبيق", "البرنامج التفاعلي المثبت على نظام أندرويد", "تقنية", "Application"),
        ArabicWordEntry("تجربة", "اختبار أمر أو مثال عملي في الطرفية", "عام", "Experiment"),
        ArabicWordEntry("توثيق", "شرح تفصيلي لطريقة عمل الأداة والأعلام", "تطوير", "Documentation"),
        ArabicWordEntry("تحليل", "فحص البيانات أو النصوص واستخلاص المعلومات", "تطوير", "Analysis"),
        ArabicWordEntry("تنسيق", "ترتيب وتلوين المخرجات بشكل واضح ومقروء", "تقنية", "Formatting"),
        ArabicWordEntry("سريع", "أداء عالٍ واستجابة فورية للأوامر", "عام", "Fast"),
        ArabicWordEntry("تفاعلي", "يستجيب مباشرة لمدخلات المستخدم وأزراره", "عام", "Interactive"),
        ArabicWordEntry("افتراضي", "الإعداد القياسي أو البيئة المحاكاة الجاهزة", "تقنية", "Default / Virtual"),
        ArabicWordEntry("مباشر", "تنفيذ أو معاينة فورية دون خطوات وسيطة", "عام", "Direct"),
        ArabicWordEntry("شامل", "يغطي جميع العناصر والتفاصيل المطلوبة", "عام", "Comprehensive")
    )

    private val lock = Any()
    private val entries = linkedMapOf<String, ArabicWordEntry>()
    var recoveredFromCorruption: Boolean = false
        private set

    init {
        synchronized(lock) {
            defaultEntries.forEach { entries[it.word] = it }
            loadCustomEntries()
        }
    }

    fun getAll(): List<ArabicWordEntry> = synchronized(lock) { entries.values.toList() }

    fun lookup(query: String): List<ArabicWordEntry> = synchronized(lock) {
        val q = normalizeArabic(query.trim())
        if (q.isEmpty()) return entries.values.toList()
        return entries.values.filter { entry ->
            normalizeArabic(entry.word).contains(q) ||
                normalizeArabic(entry.meaningAr).contains(q) ||
                entry.englishHint.lowercase().contains(q) ||
                normalizeArabic(entry.categoryAr).contains(q)
        }
    }

    fun suggestForPrefix(prefix: String, limit: Int = 8): List<ArabicWordEntry> = synchronized(lock) {
        val clean = normalizeArabic(prefix.trim())
        if (clean.isEmpty()) return emptyList()
        val starts = entries.values.filter { normalizeArabic(it.word).startsWith(clean) }
        val contains = entries.values.filter {
            !normalizeArabic(it.word).startsWith(clean) && normalizeArabic(it.word).contains(clean)
        }
        return (starts + contains).take(limit)
    }

    fun exactMatch(word: String): ArabicWordEntry? = synchronized(lock) {
        val clean = normalizeArabic(word.trim())
        return entries.values.firstOrNull { normalizeArabic(it.word) == clean }
    }

    fun addWord(word: String, meaningAr: String, categoryAr: String = "مخصص"): ArabicWordEntry = synchronized(lock) {
        val cleanWord = word.trim()
        val entry = ArabicWordEntry(
            word = cleanWord,
            meaningAr = meaningAr.trim().ifEmpty { "كلمة مضافة من المستخدم" },
            categoryAr = categoryAr,
            isCustom = true
        )
        entries[cleanWord] = entry
        saveCustomEntries()
        return entry
    }

    fun removeCustomWord(word: String): Boolean = synchronized(lock) {
        val existing = entries[word] ?: return false
        if (!existing.isCustom) return false
        entries.remove(word)
        saveCustomEntries()
        return true
    }

    fun resetToDefault() {
        synchronized(lock) {
            entries.clear()
            defaultEntries.forEach { entries[it.word] = it }
            recoveredFromCorruption = false
            runCatching {
                customFile?.let { if (it.exists()) it.delete() }
            }
        }
    }

    private fun normalizeArabic(text: String): String {
        return text
            .replace(Regex("[\u064B-\u065F\u0670]"), "") // remove tashkeel
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .lowercase()
    }

    private fun saveCustomEntries() {
        val file = customFile ?: return
        runCatching {
            val arr = JSONArray()
            entries.values.filter { it.isCustom }.forEach { e ->
                val obj = JSONObject()
                obj.put("word", e.word)
                obj.put("meaning", e.meaningAr)
                obj.put("category", e.categoryAr)
                arr.put(obj)
            }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(arr.toString())
                tmp.delete()
            }
        }
    }

    private fun loadCustomEntries() {
        val file = customFile ?: return
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val w = obj.optString("word")
                val m = obj.optString("meaning")
                val c = obj.optString("category", "مخصص")
                if (w.isNotBlank()) {
                    entries[w] = ArabicWordEntry(word = w, meaningAr = m, categoryAr = c, isCustom = true)
                }
            }
        }.onFailure {
            recoveredFromCorruption = true
            runCatching { file.delete() }
        }
    }
}
