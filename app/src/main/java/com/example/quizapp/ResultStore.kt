package com.example.quizapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** نتیجهٔ دریافت‌شده از دانش‌آموز + نمره‌ای که معلم (در صورت نیاز) خودش وارد کرده است */
data class StoredResult(
    val result: StudentResult,
    val receivedAt: Long,
    /** نمرهٔ دستیِ معلم از ۲۰؛ اگر خالی باشد نمرهٔ خودکار نشان داده می‌شود */
    val scoreOverride: Double? = null
)

/** ذخیرهٔ آفلاین نتیجه‌ها روی گوشی معلم. هر نتیجه فقط یک رکورد دارد (بر اساس شناسه). */
object ResultStore {
    private const val PREFS = "exam_results"
    private const val KEY = "results_v1"

    fun loadAll(context: Context): List<StoredResult> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<StoredResult>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val r = ResultCodec.fromJson(o) ?: continue
                val ov = if (o.has("ov")) o.optString("ov", "").toDoubleOrNull() else null
                list.add(StoredResult(r, o.optLong("rt_at", 0L), ov))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeAll(context: Context, list: List<StoredResult>) {
        val arr = JSONArray()
        for (s in list) {
            val o = ResultCodec.toJson(s.result).put("rt_at", s.receivedAt)
            if (s.scoreOverride != null) o.put("ov", s.scoreOverride.toString())
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    /** نتیجه را ذخیره می‌کند. اگر همین نتیجه قبلاً بوده، تکراری نمی‌سازد و نمرهٔ دستیِ معلم را نگه می‌دارد. true = تازه بود */
    fun add(context: Context, r: StudentResult): Boolean {
        val all = loadAll(context)
        val existing = all.firstOrNull { it.result.id == r.id }
        val kept = all.filter { it.result.id != r.id }
        writeAll(context, kept + StoredResult(r, existing?.receivedAt ?: System.currentTimeMillis(), existing?.scoreOverride))
        return existing == null
    }

    fun setScore(context: Context, id: String, score: Double?) {
        writeAll(context, loadAll(context).map { if (it.result.id == id) it.copy(scoreOverride = score) else it })
    }

    fun remove(context: Context, id: String) {
        val all = loadAll(context)
        val left = all.filter { it.result.id != id }
        if (left.size != all.size) writeAll(context, left)
    }

    /** نتیجه‌های یک آزمون: هم‌نام و هم‌محتوا (حتی اگر به فصل دیگر منتقل شده)، یا هم‌نام در همان کتاب و فصل (اگر بعد از ارسال ویرایش شده) */
    fun forExam(all: List<StoredResult>, bookName: String, exam: Exam): List<StoredResult> {
        val fp = SessionLogic.fingerprint(exam)
        return all.filter {
            (it.result.fingerprint == fp && it.result.examTitle == exam.title) ||
                (it.result.bookName == bookName && it.result.chapter == exam.chapter && it.result.examTitle == exam.title)
        }
    }
}
