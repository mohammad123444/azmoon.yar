package com.example.quizapp

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * نتیجهٔ یک دانش‌آموز در آزمون تستی. همین مقدار به‌صورت «کد نتیجه» برای معلم فرستاده می‌شود.
 * order: شمارهٔ سوال‌ها (در آزمونِ معلم) به ترتیبی که پرسیده شدند؛ chosen: گزینهٔ انتخابی هر سوال (از صفر، یا ۱- یعنی بی‌پاسخ).
 * معلم نمره را از روی همین پاسخ‌ها و آزمونِ خودش دوباره حساب می‌کند.
 */
data class StudentResult(
    val fingerprint: String,
    val bookName: String,
    val chapter: String,
    val examTitle: String,
    val studentName: String,
    val order: List<Int>,
    val chosen: List<Int>,
    val durationSec: Int,
    val finishedAt: Long,
    val reportedCorrect: Int,
    val reportedTotal: Int,
    val timeLimitMin: Int = 0
) {
    /** هر نتیجه یک شناسه دارد تا دو بار وارد کردنِ یک کد، نتیجهٔ تکراری نسازد */
    val id: String get() = fingerprint + "|" + studentName.trim() + "|" + finishedAt
}

/** نتیجهٔ حساب‌شده: درست، کل و نمره از ۲۰ */
class Evaluation(val correct: Int, val total: Int, val fromExam: Boolean) {
    val percent: Int get() = if (total == 0) 0 else Math.round(correct * 100.0 / total).toInt()
    val score20: Double get() = if (total == 0) 0.0 else Math.round(correct * 20.0 / total * 100.0) / 100.0
}

object ResultLogic {
    /** اگر آزمونِ معلم موجود و هم‌اثرانگشت باشد، از روی پاسخ‌ها حساب می‌شود؛ وگرنه عددِ اعلام‌شدهٔ دانش‌آموز */
    fun evaluate(r: StudentResult, exam: Exam?): Evaluation {
        if (exam != null && SessionLogic.fingerprint(exam) == r.fingerprint &&
            r.order.size == r.chosen.size && r.order.isNotEmpty() &&
            r.order.all { it in exam.questions.indices } && r.order.toSet().size == r.order.size
        ) {
            var c = 0
            for (i in r.order.indices) {
                val q = exam.questions[r.order[i]]
                if (q.isTest && r.chosen[i] == q.correct) c++
            }
            return Evaluation(c, r.order.size, true)
        }
        val total = r.reportedTotal.coerceAtLeast(0)
        return Evaluation(r.reportedCorrect.coerceIn(0, total), total, false)
    }

    /** مدت زمان «۱۲:۳۰» */
    fun durationText(sec: Int): String {
        val s = sec.coerceAtLeast(0)
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
    }

    fun scoreText(v: Double): String =
        if (v == Math.floor(v)) v.toInt().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    /** نمرهٔ واردشده توسط معلم؛ ارقام فارسی و «٫» پذیرفته می‌شود. فقط ۰ تا ۱۰۰ */
    fun parseScore(text: String): Double? {
        val t = ExamSearch.normalize(text).replace('٫', '.').replace(',', '.')
        val v = t.toDoubleOrNull() ?: return null
        return if (v.isNaN() || v < 0.0 || v > 100.0) null else v
    }
}

object ResultCodec {
    private const val PREFIX = "azmoon://result?d="
    private val regex = Regex("azmoon://result\\?d=([A-Za-z0-9_\\-]+)")

    fun toJson(r: StudentResult): JSONObject = JSONObject()
        .put("v", 1)
        .put("fp", r.fingerprint)
        .put("b", r.bookName)
        .put("c", r.chapter)
        .put("t", r.examTitle)
        .put("n", r.studentName)
        .put("o", JSONArray(r.order))
        .put("a", JSONArray(r.chosen))
        .put("du", r.durationSec)
        .put("fa", r.finishedAt)
        .put("rc", r.reportedCorrect)
        .put("rt", r.reportedTotal)
        .put("tl", r.timeLimitMin)

    /** null اگر داده ناقص یا ناسازگار باشد */
    fun fromJson(o: JSONObject): StudentResult? = try {
        val oa = o.getJSONArray("o")
        val aa = o.getJSONArray("a")
        val order = ArrayList<Int>()
        val chosen = ArrayList<Int>()
        for (i in 0 until oa.length()) order.add(oa.getInt(i))
        for (i in 0 until aa.length()) chosen.add(aa.getInt(i))
        if (order.size != chosen.size || order.size > 2000) null
        else StudentResult(
            fingerprint = o.getString("fp"),
            bookName = o.optString("b", ""),
            chapter = o.optString("c", ""),
            examTitle = o.optString("t", ""),
            studentName = o.getString("n").trim().take(60),
            order = order,
            chosen = chosen,
            durationSec = o.optLong("du", 0L).toInt().coerceAtLeast(0),
            finishedAt = o.optLong("fa", 0L),
            reportedCorrect = o.optLong("rc", 0L).toInt(),
            reportedTotal = o.optLong("rt", 0L).toInt(),
            timeLimitMin = o.optLong("tl", 0L).toInt()
        )
    } catch (e: Exception) {
        null
    }

    fun toCode(r: StudentResult): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(toJson(r).toString().toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return PREFIX + Base64.encodeToString(out.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** پیامی که دانش‌آموز برای معلم می‌فرستد */
    fun toShareText(r: StudentResult): String =
        "📨 نتیجهٔ آزمون «${r.examTitle}»\n" +
            "👤 ${r.studentName}\n" +
            "✅ ${r.reportedCorrect} از ${r.reportedTotal} • ⏱ ${ResultLogic.durationText(r.durationSec)}\n\n" +
            "معلم: این پیام را کپی کنید و در آزمون‌یار، «معلم ← فصل ← 📊 نتایج» وارد کنید.\n\n" +
            toCode(r) + "\n"

    fun fromText(text: String): StudentResult? {
        val encoded = regex.find(text)?.groupValues?.get(1) ?: return null
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
                if (out.size() > 1_000_000) break
            }
            inflater.end()
            fromJson(JSONObject(out.toString("UTF-8")))
        } catch (e: Exception) {
            null
        }
    }
}
