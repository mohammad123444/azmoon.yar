package com.example.quizapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * ذخیرهٔ آزمون‌های نیمه‌تمام روی گوشی (آفلاین).
 * هر آزمون حداکثر یک رکورد دارد؛ ذخیرهٔ دوباره، رکورد قبلی را عوض می‌کند (تکراری ساخته نمی‌شود).
 * رکورد خراب نادیده گرفته می‌شود و هرگز باعث بسته شدن برنامه نمی‌شود.
 */
object SessionStore {
    private const val PREFS = "exam_sessions"
    private const val KEY = "sessions_v1"
    private const val MAX_SESSIONS = 10

    fun encode(s: SavedSession): JSONObject {
        val answers = JSONObject()
        for ((k, v) in s.answers) if (v.isNotBlank()) answers.put(k.toString(), v)
        val marks = JSONObject()
        for ((k, v) in s.marks) marks.put(k.toString(), v)
        return JSONObject()
            .put("book", s.bookName)
            .put("ch", s.chapter)
            .put("title", s.title)
            .put("fp", s.fingerprint)
            .put("mode", s.mode)
            .put("order", JSONArray(s.order))
            .put("i", s.index)
            .put("fin", s.finished)
            .put("ans", answers)
            .put("marks", marks)
            .put("t", s.updatedAt)
            .put("st", s.startedAt)
            .put("fa", s.finishedAt)
            .put("sn", s.studentName)
            .put("tm", s.timed)
    }

    /** اگر داده ناقص یا خراب باشد null برمی‌گرداند */
    fun decode(o: JSONObject): SavedSession? = try {
        val orderArr = o.getJSONArray("order")
        val order = ArrayList<Int>()
        for (i in 0 until orderArr.length()) order.add(orderArr.getInt(i))
        val answers = HashMap<Int, String>()
        val ansObj = o.optJSONObject("ans")
        if (ansObj != null) {
            val it = ansObj.keys()
            while (it.hasNext()) {
                val k = it.next()
                answers[k.toInt()] = ansObj.getString(k)
            }
        }
        val marks = HashMap<Int, Boolean>()
        val markObj = o.optJSONObject("marks")
        if (markObj != null) {
            val it = markObj.keys()
            while (it.hasNext()) {
                val k = it.next()
                marks[k.toInt()] = markObj.getBoolean(k)
            }
        }
        SavedSession(
            bookName = o.getString("book"),
            chapter = o.optString("ch", ""),
            title = o.getString("title"),
            fingerprint = o.getString("fp"),
            mode = o.getInt("mode"),
            order = order,
            index = o.getInt("i"),
            finished = o.optBoolean("fin", false),
            answers = answers,
            marks = marks,
            updatedAt = o.optLong("t", 0L),
            startedAt = o.optLong("st", 0L),
            finishedAt = o.optLong("fa", 0L),
            studentName = o.optString("sn", ""),
            timed = o.optBoolean("tm", false)
        )
    } catch (e: Exception) {
        null
    }

    fun loadAll(context: Context): List<SavedSession> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<SavedSession>()
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i)?.let { decode(it) }
                if (s != null) list.add(s)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeAll(context: Context, list: List<SavedSession>) {
        val arr = JSONArray()
        for (s in list) arr.put(encode(s))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    /** وضعیت را (به‌جای رکورد قبلیِ همان آزمون) ذخیره می‌کند */
    fun put(context: Context, s: SavedSession) {
        // وضعیت دست‌نخورده ذخیره نمی‌شود و رکورد قبلیِ همان آزمون را هم پاک نمی‌کند
        if (!SessionLogic.isMeaningful(s)) return
        val others = loadAll(context).filter { it.key != s.key }
        val merged = (others + s).sortedByDescending { it.updatedAt }.take(MAX_SESSIONS)
        writeAll(context, merged)
    }

    fun remove(context: Context, key: String) {
        val all = loadAll(context)
        val left = all.filter { it.key != key }
        if (left.size != all.size) writeAll(context, left)
    }

    /** رکوردهای آزمون‌های حذف‌شده یا ویرایش‌شده پاک می‌شوند */
    fun prune(context: Context, books: List<Book>) {
        val all = loadAll(context)
        val left = all.filter { SessionLogic.resolve(it, books) != null }
        if (left.size != all.size) writeAll(context, left)
    }
}
