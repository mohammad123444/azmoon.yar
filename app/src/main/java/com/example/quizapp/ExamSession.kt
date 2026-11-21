package com.example.quizapp

import java.security.MessageDigest
import java.util.Random

/**
 * وضعیت ذخیره‌شدهٔ یک آزمون نیمه‌تمام (تمرین یا امتحان).
 * order: شمارهٔ سوال‌ها در Exam.questions به ترتیبی که پرسیده می‌شوند.
 * answers / marks: کلیدها «جایگاه» سوال در order هستند (نه شمارهٔ اصلی)، پس هر سوال فقط یک پاسخ دارد.
 */
data class SavedSession(
    val bookName: String,
    val chapter: String,
    val title: String,
    val fingerprint: String,
    val mode: Int,
    val order: List<Int>,
    val index: Int,
    val finished: Boolean,
    val answers: Map<Int, String>,
    val marks: Map<Int, Boolean>,
    val updatedAt: Long,
    /** لحظهٔ شروع و پایان (میلی‌ثانیه)؛ برای آزمون زمان‌دار، ساعت با خروج و ورود دوباره از اول نمی‌شود */
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val studentName: String = "",
    /** آزمون زمان‌دار: از لحظهٔ شروع ذخیره می‌شود تا با خروج و ورود، ساعت از اول نشود */
    val timed: Boolean = false
) {
    /** هر آزمون فقط یک وضعیت ذخیره‌شده دارد */
    val key: String get() = SessionLogic.keyOf(bookName, chapter, title)
}

object SessionLogic {
    const val MODE_PRACTICE = 0
    const val MODE_EXAM = 1

    fun keyOf(bookName: String, chapter: String, title: String): String =
        bookName + "\u001F" + chapter + "\u001F" + title

    /** اثر انگشتِ سوال‌ها و پاسخ‌ها: اگر معلم آزمون را ویرایش کند، وضعیت قدیمی نامعتبر می‌شود */
    fun fingerprint(exam: Exam): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (q in exam.questions) {
            md.update(q.q.toByteArray(Charsets.UTF_8))
            md.update(0x1F.toByte())
            md.update(q.a.toByteArray(Charsets.UTF_8))
            md.update(0x1E.toByte())
            // گزینه‌ها فقط برای سوال تستی در اثر انگشت می‌آیند؛ پس برای آزمون‌های قدیمی مقدار همان قبلی است
            if (q.isTest) {
                for (o in q.options) {
                    md.update(o.toByteArray(Charsets.UTF_8))
                    md.update(0x1D.toByte())
                }
                md.update(q.correct.toByte())
            }
        }
        val bytes = md.digest()
        val sb = StringBuilder()
        for (i in 0 until 8) sb.append(String.format("%02x", bytes[i].toInt() and 0xFF))
        return sb.toString() + ":" + exam.questions.size
    }

    /** انتخاب و ترتیب سوال‌ها؛ count بیشتر از تعداد سوال‌ها همهٔ سوال‌ها را برمی‌گرداند */
    fun pickOrder(total: Int, count: Int, shuffle: Boolean, random: Random = Random()): List<Int> {
        val all = (0 until total).toMutableList()
        if (shuffle) all.shuffle(random)
        return all.take(count.coerceAtLeast(0))
    }

    /** آیا چیزی برای ادامه دادن وجود دارد؟ (جلسهٔ دست‌نخورده ذخیره نمی‌شود) */
    fun isMeaningful(s: SavedSession): Boolean =
        s.finished || (s.timed && s.startedAt > 0L) || s.index > 0 || s.marks.isNotEmpty() || s.answers.values.any { it.isNotBlank() }

    /** آزمونِ فعلیِ این وضعیت را برمی‌گرداند؛ اگر آزمون حذف یا ویرایش شده یا داده خراب است null */
    fun resolve(s: SavedSession, books: List<Book>): Exam? {
        val exam = books.firstOrNull { it.name == s.bookName }
            ?.exams?.firstOrNull { it.chapter == s.chapter && it.title == s.title }
            ?: return null
        if (exam.isFileExam) return null
        if (fingerprint(exam) != s.fingerprint) return null
        val n = s.order.size
        if (n == 0) return null
        if (s.order.any { it < 0 || it >= exam.questions.size }) return null
        if (s.order.toSet().size != n) return null
        if (s.index < 0 || s.index >= n) return null
        if (s.answers.keys.any { it < 0 || it >= n }) return null
        if (s.marks.keys.any { it < 0 || it >= n }) return null
        if (s.mode != MODE_PRACTICE && s.mode != MODE_EXAM) return null
        return exam
    }

    /** جدیدترین وضعیت معتبر برای نمایش «ادامهٔ آزمون» */
    fun latestValid(sessions: List<SavedSession>, books: List<Book>): Pair<SavedSession, Exam>? {
        for (s in sessions.sortedByDescending { it.updatedAt }) {
            val exam = resolve(s, books)
            if (exam != null) return s to exam
        }
        return null
    }

    /** برای توضیح کوتاه کارت «ادامه» */
    fun progressText(s: SavedSession): String {
        val total = s.order.size
        val label = if (s.mode == MODE_EXAM) "امتحان" else "تمرین"
        return if (s.finished) "$label — نتیجه آماده است"
        else "$label — سوال ${s.index + 1} از $total"
    }

    // ----- مقایسهٔ پاسخ در حالت امتحان -----

    private val punctuation = Regex("[.,،؛;:!؟?\"'«»()\\[\\]\\-–—_/\\\\]")

    private fun loose(s: String): String =
        ExamSearch.normalize(s).replace(punctuation, " ").replace(Regex(" +"), " ").trim()

    /** فقط وقتی پاسخ با پاسخ صحیح یکسان است «درست» برمی‌گرداند؛ در غیر این صورت null (بررسی دستی) */
    fun autoMark(given: String, correct: String): Boolean? {
        val g = loose(given)
        if (g.isEmpty()) return null
        return if (g == loose(correct)) true else null
    }

    class Tally(val correct: Int, val wrong: Int, val pending: Int)

    fun tally(total: Int, marks: Map<Int, Boolean>): Tally {
        var c = 0
        var w = 0
        for (i in 0 until total) {
            when (marks[i]) {
                true -> c++
                false -> w++
                null -> Unit
            }
        }
        return Tally(c, w, total - c - w)
    }

    /**
     * هنگام پایان امتحان: سوال بی‌پاسخ «اشتباه» و پاسخِ کاملاً یکسان با پاسخ صحیح «درست» ثبت می‌شود؛
     * بقیه برای بررسی دستی خالی می‌مانند. نشانه‌گذاری‌هایی که دانش‌آموز قبلاً زده دست نمی‌خورد.
     */
    fun finalizeMarks(
        questions: List<Question>,
        answers: Map<Int, String>,
        marks: Map<Int, Boolean>
    ): Map<Int, Boolean> {
        val out = marks.toMutableMap()
        for (i in questions.indices) {
            val q = questions[i]
            if (q.isTest) {
                // سوال تستی همیشه خودکار تصحیح می‌شود: گزینهٔ انتخابی (شمارهٔ گزینه از صفر) باید درست باشد
                out[i] = answers[i]?.trim()?.toIntOrNull() == q.correct
                continue
            }
            if (out.containsKey(i)) continue
            val given = answers[i].orEmpty()
            if (given.isBlank()) {
                out[i] = false
            } else if (autoMark(given, questions[i].a) == true) {
                out[i] = true
            }
        }
        return out
    }
}
