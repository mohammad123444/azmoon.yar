package com.example.quizapp

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

data class SharedExam(val book: String, val exam: Exam)

/**
 * ارسال آزمون به‌صورت «لینک» و دریافت آن.
 * کل آزمون (سوال‌ها، پاسخ‌ها و تاریخ بارگذاری) فشرده و داخل لینک گذاشته می‌شود؛
 * پس بدون سرور و بدون اینترنت کار می‌کند. لینک: azmoon://exam?d=...
 */
object ExamText {
    private const val LINK_PREFIX = "azmoon://exam?d="
    private val linkRegex = Regex("azmoon://exam\\?d=([A-Za-z0-9_\\-]+)")

    // قالب متنی قدیمی (برای سازگاری با آزمون‌هایی که قبلاً ارسال شده‌اند)
    private const val BOOK_PREFIX = "کتاب:"
    private const val TITLE_PREFIX = "آزمون:"
    private const val Q_MARK = "[سوالات]"
    private const val A_MARK = "[پاسخنامه]"

    private fun questionJson(q: Question): JSONObject {
        val o = JSONObject().put("q", q.q).put("a", q.a).put("d", q.addedAt)
        if (q.analysis.isNotBlank()) o.put("x", q.analysis)
        ExamStore.putTestFields(o, q)
        return o
    }

    /** شماره‌گذاری فقط برای مواردِ پرشده (برای کادر اختیاریِ پاسخ تحلیلی) */
    fun numberedSparse(items: List<String>): String =
        items.mapIndexedNotNull { i, s -> if (s.isBlank()) null else "${i + 1}. $s" }.joinToString("\n")

    /** شماره‌گذاری: «1. متن» در هر خط */
    fun numbered(items: List<String>): String =
        items.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")

    // ---------- لینک ----------

    fun toShareLink(bookName: String, exam: Exam): String {
        val qArr = JSONArray()
        for (q in exam.questions) {
            qArr.put(questionJson(q))
        }
        val json = JSONObject()
            .put("v", 1)
            .put("b", bookName)
            .put("t", exam.title)
            .put("c", exam.chapter)
            .put("q", qArr)
            .apply { if (exam.timeLimitMin > 0) put("tl", exam.timeLimitMin) }
            .toString()

        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(json.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()

        val encoded = Base64.encodeToString(
            out.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return LINK_PREFIX + encoded
    }

    /** پیامی که برای دانش‌آموز فرستاده می‌شود: توضیح کوتاه + لینک */
    fun toShareText(bookName: String, exam: Exam): String =
        "📚 کتاب: $bookName\n" +
            "📝 آزمون: ${exam.title} (${exam.questions.size} سوال)\n" +
            (if (exam.timeLimitMin > 0) "⏱ زمان: ${exam.timeLimitMin} دقیقه\n" else "") + "\n" +
            "برای دریافت آزمون، این پیام را کپی کنید و در برنامهٔ آزمون‌یار، بخش «دریافت آزمون» وارد کنید " +
            "(یا اگر روی لینک زیر ضربه زد، مستقیم باز می‌شود):\n\n" +
            toShareLink(bookName, exam) + "\n"

    /** ساختن آزمون از JSON ارسال‌شده (لینک یا فایل)؛ اگر سوالی نداشته باشد null */
    private fun sharedFromObject(obj: JSONObject): SharedExam? {
        val qArr = obj.getJSONArray("q")
        val now = System.currentTimeMillis()
        val questions = mutableListOf<Question>()
        for (i in 0 until qArr.length()) {
            val qObj = qArr.getJSONObject(i)
            val d = qObj.optLong("d", 0L)
            val (opts, k) = ExamStore.readOptions(qObj)
            questions.add(Question(qObj.getString("q"), qObj.getString("a"), if (d > 0L) d else now, qObj.optString("x", ""), opts, k))
        }
        if (questions.isEmpty()) return null
        val book = obj.optString("b", "").trim().ifEmpty { "کتاب دریافتی" }
        val title = obj.optString("t", "").trim().ifEmpty { "آزمون دریافتی" }
        val limit = obj.optLong("tl", 0L).toInt().coerceIn(0, 600)
        return SharedExam(book, Exam(title, questions, chapter = obj.optString("c", "").trim(), timeLimitMin = limit))
    }

    fun fromLink(text: String): SharedExam? {
        val encoded = linkRegex.find(text)?.groupValues?.get(1) ?: return null
        return try {
            val compressed = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP)
            val inflater = Inflater()
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
            }
            inflater.end()

            val obj = JSONObject(out.toString("UTF-8"))
            sharedFromObject(obj)
        } catch (e: Exception) {
            null
        }
    }

    // ---------- فایل آزمون (روش اصلی ارسال؛ بدون محدودیت حجم) ----------

    /** لینک فقط برای آزمون‌های کوتاه کاربرد دارد؛ پیام‌رسان‌ها لینک خیلی بلند را خراب می‌کنند */
    const val MAX_SHARE_LINK_LENGTH = 1800

    /** محتوای فایل آزمون (JSON ساده، UTF-8) */
    fun toShareFileJson(bookName: String, exam: Exam): String {
        val qArr = JSONArray()
        for (q in exam.questions) {
            qArr.put(questionJson(q))
        }
        return JSONObject()
            .put("app", "azmoonsaz")
            .put("v", 1)
            .put("b", bookName)
            .put("t", exam.title)
            .put("c", exam.chapter)
            .put("q", qArr)
            .apply { if (exam.timeLimitMin > 0) put("tl", exam.timeLimitMin) }
            .toString()
    }

    fun fromFileJson(text: String): SharedExam? {
        val trimmed = text.trim().removePrefix("\uFEFF")
        if (!trimmed.startsWith("{")) return null
        return try {
            val obj = JSONObject(trimmed)
            sharedFromObject(obj)
        } catch (e: Exception) {
            null
        }
    }

    // ---------- قالب متنی قدیمی ----------

    private fun fromLegacyText(text: String): SharedExam? {
        val qIdx = text.indexOf(Q_MARK)
        val aIdx = text.indexOf(A_MARK)
        if (qIdx < 0 || aIdx < 0 || aIdx < qIdx) return null

        val headerLines = text.substring(0, qIdx).lines()
        val book = headerLines.firstOrNull { it.contains(BOOK_PREFIX) }
            ?.substringAfter(BOOK_PREFIX)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "کتاب دریافتی"
        val title = headerLines.firstOrNull { it.contains(TITLE_PREFIX) }
            ?.substringAfter(TITLE_PREFIX)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "آزمون دریافتی"

        val result = QuestionParser.pair(
            QuestionParser.parseItems(text.substring(qIdx + Q_MARK.length, aIdx)),
            QuestionParser.parseItems(text.substring(aIdx + A_MARK.length))
        )
        if (result.error != null || result.pairs.isEmpty()) return null

        val now = System.currentTimeMillis()
        return SharedExam(book, Exam(title, result.pairs.map { (q, a) -> Question(q, a, now) }))
    }

    /** اگر متن معتبر نباشد null برمی‌گرداند */
    fun fromShareText(text: String): SharedExam? =
        fromFileJson(text) ?: fromLink(text) ?: fromLegacyText(text)
}
