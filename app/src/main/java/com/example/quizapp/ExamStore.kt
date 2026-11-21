package com.example.quizapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** addedAt: زمان بارگذاری سوال (میلی‌ثانیه). برای داده‌های قدیمی هنگام اولین بارگذاری مقدار می‌گیرد */
data class Question(
    val q: String,
    val a: String,
    val addedAt: Long = 0L,
    /** پاسخ تحلیلی (اختیاری): توضیح کامل دربارهٔ همین سوال؛ می‌تواند خالی باشد */
    val analysis: String = "",
    /** سوال تستی: گزینه‌ها (۲ تا ۶ مورد) و شمارهٔ گزینهٔ درست (از صفر). برای سوال تشریحی خالی است */
    val options: List<String> = emptyList(),
    val correct: Int = -1
) {
    val isTest: Boolean get() = options.size >= 2 && correct in options.indices

    /** متن سوال همراه با گزینه‌ها برای نمایش */
    val displayText: String
        get() = if (isTest) q + "\n\n" + options.mapIndexed { i, o -> "${i + 1}) $o" }.joinToString("\n") else q

    /** پاسخ برای نمایش: در سوال تستی «شماره) متن گزینهٔ درست» */
    val answerText: String
        get() = if (isTest) "${correct + 1}) ${options[correct]}" else a
}
/**
 * آزمون. دو نوع دارد:
 *  - نوشتاری: سوال‌ها و پاسخ‌ها متن هستند (questionFiles خالی است)
 *  - فایل‌دار: سوال‌ها PDF یا عکس هستند (questionFiles پر است). پاسخ‌ها یا فایل‌اند (answerFiles)
 *    یا متنِ دستی (questions: «q» خالی و «a» پاسخِ سوالِ شمارهٔ index+1)
 */
data class Exam(
    val title: String,
    val questions: List<Question>,
    val questionFiles: List<String> = emptyList(),
    val answerFiles: List<String> = emptyList(),
    /** فصلی که آزمون داخل آن است؛ برای آزمون‌های قدیمی خالی است */
    val chapter: String = "",
    /** زمان آزمون به دقیقه؛ صفر یعنی بدون محدودیت */
    val timeLimitMin: Int = 0
) {
    val isFileExam: Boolean get() = questionFiles.isNotEmpty()

    /** آزمون تستی: همهٔ سوال‌ها گزینه‌دار هستند */
    val isTest: Boolean get() = !isFileExam && questions.isNotEmpty() && questions.all { it.isTest }

    /** شناسهٔ یکتای آزمون داخل کتاب: آزمونِ هم‌نام در فصل‌های مختلف مجاز است */
    val key: String get() = keyOf(chapter, title)

    companion object {
        fun keyOf(chapter: String, title: String): String = chapter + "\u001F" + title
    }
}

/** کتاب ← فصل‌ها ← آزمون‌ها. chapters فصل‌هایی است که ساخته شده‌اند (حتی اگر هنوز آزمونی ندارند) */
data class Book(val name: String, val exams: List<Exam>, val chapters: List<String> = emptyList()) {
    /** همهٔ فصل‌ها به ترتیب: فصل‌های ساخته‌شده + فصل‌هایی که آزمون دارند */
    val allChapters: List<String>
        get() = (chapters + exams.map { it.chapter }.filter { it.isNotBlank() }).distinct()

    /** آزمون‌های قدیمی که فصل ندارند */
    val unsortedExams: List<Exam> get() = exams.filter { it.chapter.isBlank() }

    fun examsIn(chapter: String): List<Exam> = exams.filter { it.chapter == chapter }
}

/** ذخیره و بازیابی کتاب‌ها و آزمون‌ها روی خود گوشی (آفلاین) + پشتیبان‌گیری */
object ExamStore {
    private const val PREFS = "exam_store"
    private const val KEY = "books_v2"

    private class ParseState { var missingDate = false }

    /** گزینه‌ها و گزینهٔ درست (فقط برای سوال تستی نوشته می‌شود؛ قالب قدیمی بدون تغییر می‌ماند) */
    fun putTestFields(o: JSONObject, q: Question) {
        if (q.isTest) {
            o.put("o", JSONArray(q.options))
            o.put("k", q.correct)
        }
    }

    fun readOptions(o: JSONObject): Pair<List<String>, Int> {
        val arr = o.optJSONArray("o") ?: return Pair(emptyList(), -1)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.getString(i))
        val k = try { o.getInt("k") } catch (e: Exception) { -1 }
        return if (list.size >= 2 && k in list.indices) Pair(list, k) else Pair(emptyList(), -1)
    }

    private fun readStrings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.getString(i))
        return list
    }

    private fun booksToArray(books: List<Book>): JSONArray {
        val booksArr = JSONArray()
        for (book in books) {
            val examsArr = JSONArray()
            for (exam in book.exams) {
                val qArr = JSONArray()
                for (question in exam.questions) {
                    val qo = JSONObject()
                        .put("q", question.q)
                        .put("a", question.a)
                        .put("t", question.addedAt)
                        .put("x", question.analysis)
                    putTestFields(qo, question)
                    qArr.put(qo)
                }
                val eo = JSONObject()
                    .put("title", exam.title)
                    .put("questions", qArr)
                    .put("qf", JSONArray(exam.questionFiles))
                    .put("af", JSONArray(exam.answerFiles))
                    .put("ch", exam.chapter)
                if (exam.timeLimitMin > 0) eo.put("tl", exam.timeLimitMin)
                examsArr.put(eo)
            }
            booksArr.put(
                JSONObject()
                    .put("name", book.name)
                    .put("exams", examsArr)
                    .put("chapters", JSONArray(book.chapters))
            )
        }
        return booksArr
    }

    private fun booksFromArray(booksArr: JSONArray, now: Long, state: ParseState): List<Book> {
        val books = mutableListOf<Book>()
        for (i in 0 until booksArr.length()) {
            val bObj = booksArr.getJSONObject(i)
            val examsArr = bObj.getJSONArray("exams")
            val exams = mutableListOf<Exam>()
            for (j in 0 until examsArr.length()) {
                val eObj = examsArr.getJSONObject(j)
                val qArr = eObj.getJSONArray("questions")
                val questions = mutableListOf<Question>()
                for (k in 0 until qArr.length()) {
                    val qObj = qArr.getJSONObject(k)
                    val t = qObj.optLong("t", 0L)
                    if (t <= 0L) state.missingDate = true
                    val (opts, k) = readOptions(qObj)
                    questions.add(
                        Question(qObj.getString("q"), qObj.getString("a"), if (t > 0L) t else now, qObj.optString("x", ""), opts, k)
                    )
                }
                exams.add(
                    Exam(
                        eObj.getString("title"),
                        questions,
                        readStrings(eObj.optJSONArray("qf")),
                        readStrings(eObj.optJSONArray("af")),
                        eObj.optString("ch", ""),
                        eObj.optLong("tl", 0L).toInt()
                    )
                )
            }
            books.add(Book(bObj.getString("name"), exams, readStrings(bObj.optJSONArray("chapters"))))
        }
        return books
    }

    fun load(context: Context): List<Book> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val state = ParseState()
            val books = booksFromArray(JSONArray(raw), System.currentTimeMillis(), state)
            // سوال‌های قدیمیِ بدون تاریخ، همان لحظه تاریخ می‌گیرند و ذخیره می‌شوند
            if (state.missingDate) save(context, books)
            books
        } catch (e: Exception) {
            // دادهٔ خراب هرگز پاک نمی‌شود: یک نسخهٔ جدا نگه داشته می‌شود تا ذخیرهٔ بعدی آن را از بین نبرد
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY + "_corrupt", raw).commit()
            emptyList()
        }
    }

    fun save(context: Context, books: List<Book>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, booksToArray(books).toString()).apply()
    }

    // ---- پشتیبان‌گیری ----

    fun toBackupJson(books: List<Book>): String =
        JSONObject()
            .put("app", "azmoonsaz")
            .put("version", 4)
            .put("exportedAt", System.currentTimeMillis())
            .put("books", booksToArray(books))
            .toString(2)

    /** اگر فایل معتبر نباشد null برمی‌گرداند */
    fun fromBackupJson(raw: String): List<Book>? = try {
        val t = raw.trim()
        val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).getJSONArray("books")
        booksFromArray(arr, System.currentTimeMillis(), ParseState())
    } catch (e: Exception) {
        null
    }

    /** ادغام پشتیبان با داده‌های فعلی (آزمونِ هم‌نام از پشتیبان جایگزین می‌شود) */
    fun merge(current: List<Book>, incoming: List<Book>): List<Book> {
        var result = current
        for (b in incoming) {
            result = addBook(result, b.name)
            for (c in b.chapters) result = addChapter(result, b.name, c)
            for (e in b.exams) result = saveExam(result, b.name, null, e)
        }
        return result
    }

    // ---- عملیات خالص روی فهرست (بدون تغییر فهرست اصلی) ----

    fun addBook(books: List<Book>, name: String): List<Book> =
        if (books.any { it.name == name }) books else books + Book(name, emptyList())

    fun renameBook(books: List<Book>, oldName: String, newName: String): List<Book> =
        books.map { if (it.name == oldName) it.copy(name = newName) else it }

    fun deleteBook(books: List<Book>, name: String): List<Book> =
        books.filter { it.name != name }

    // ---- فصل‌ها ----

    fun addChapter(books: List<Book>, bookName: String, chapter: String): List<Book> =
        books.map { book ->
            if (book.name != bookName || chapter in book.allChapters) book
            else book.copy(chapters = book.chapters + chapter)
        }

    fun renameChapter(books: List<Book>, bookName: String, oldName: String, newName: String): List<Book> =
        books.map { book ->
            if (book.name != bookName) book
            else book.copy(
                chapters = book.allChapters.map { if (it == oldName) newName else it }.distinct(),
                exams = book.exams.map { if (it.chapter == oldName) it.copy(chapter = newName) else it }
            )
        }

    /** فصل با همهٔ آزمون‌هایش حذف می‌شود */
    fun deleteChapter(books: List<Book>, bookName: String, chapter: String): List<Book> =
        books.map { book ->
            if (book.name != bookName) book
            else book.copy(
                chapters = book.chapters.filter { it != chapter },
                exams = book.exams.filter { it.chapter != chapter }
            )
        }

    /**
     * ذخیرهٔ آزمون در یک کتاب.
     * originalKey: شناسهٔ (Exam.key) قبلی آزمون هنگام ویرایش (برای آزمون جدید null).
     * اگر کتاب وجود نداشته باشد ساخته می‌شود.
     */
    fun saveExam(books: List<Book>, bookName: String, originalKey: String?, exam: Exam): List<Book> {
        val withBook = if (books.any { it.name == bookName }) books else books + Book(bookName, emptyList())
        return withBook.map { book ->
            if (book.name != bookName) {
                book
            } else {
                val list = book.exams.toMutableList()
                val idx = list.indexOfFirst { it.key == (originalKey ?: exam.key) }
                if (idx >= 0) list[idx] = exam else list.add(exam)
                book.copy(exams = list.filterIndexed { _, e -> e.key != exam.key || e === exam })
            }
        }
    }

    /** آیا در این کتاب آزمونی با همین فصل و نام هست؟ */
    fun hasExam(books: List<Book>, bookName: String, exam: Exam): Boolean =
        books.firstOrNull { it.name == bookName }?.exams?.any { it.key == exam.key } == true

    /** اگر آزمونِ هم‌نام هست، به نام آزمون «(۲)»، «(۳)»... اضافه می‌شود تا هر دو نگه داشته شوند */
    fun withUniqueTitle(books: List<Book>, bookName: String, exam: Exam): Exam {
        if (!hasExam(books, bookName, exam)) return exam
        for (n in 2..999) {
            val candidate = exam.copy(title = "${exam.title} ($n)")
            if (!hasExam(books, bookName, candidate)) return candidate
        }
        return exam.copy(title = exam.title + " (" + System.currentTimeMillis() + ")")
    }

    fun deleteExam(books: List<Book>, bookName: String, key: String): List<Book> =
        books.map { book ->
            if (book.name == bookName) book.copy(exams = book.exams.filter { it.key != key }) else book
        }

    // ---- جابه‌جایی و انتقال ----

    /** آزمون را در همان فصل یک جا بالا (delta = -1) یا پایین (delta = +1) می‌برد */
    fun moveExam(books: List<Book>, bookName: String, key: String, delta: Int): List<Book> =
        books.map { book ->
            if (book.name != bookName) return@map book
            val exam = book.exams.firstOrNull { it.key == key } ?: return@map book
            val idxs = book.exams.indices.filter { book.exams[it].chapter == exam.chapter }
            val pos = idxs.indexOfFirst { book.exams[it].key == key }
            val target = pos + delta
            if (pos < 0 || target !in idxs.indices) return@map book
            val list = book.exams.toMutableList()
            val t = list[idxs[pos]]
            list[idxs[pos]] = list[idxs[target]]
            list[idxs[target]] = t
            book.copy(exams = list)
        }

    /** فصل را یک جا بالا یا پایین می‌برد */
    fun moveChapter(books: List<Book>, bookName: String, chapter: String, delta: Int): List<Book> =
        books.map { book ->
            if (book.name != bookName) return@map book
            val list = book.allChapters.toMutableList()
            val pos = list.indexOf(chapter)
            val target = pos + delta
            if (pos < 0 || target !in list.indices) return@map book
            val t = list[pos]
            list[pos] = list[target]
            list[target] = t
            book.copy(chapters = list)
        }

    /**
     * انتقال آزمون به فصلی دیگر (در همان کتاب یا کتابی دیگر).
     * اگر در مقصد آزمونی با همین نام باشد null برمی‌گرداند.
     */
    fun moveExamTo(books: List<Book>, fromBook: String, key: String, toBook: String, toChapter: String): List<Book>? {
        val exam = books.firstOrNull { it.name == fromBook }?.exams?.firstOrNull { it.key == key } ?: return books
        if (fromBook == toBook && exam.chapter == toChapter) return books
        val moved = exam.copy(chapter = toChapter)
        val dest = books.firstOrNull { it.name == toBook }
        if (dest != null && dest.exams.any { it.key == moved.key }) return null
        val removed = deleteExam(books, fromBook, key)
        val withChapter = if (toChapter.isNotBlank()) addChapter(removed, toBook, toChapter) else removed
        return saveExam(withChapter, toBook, null, moved)
    }
}
