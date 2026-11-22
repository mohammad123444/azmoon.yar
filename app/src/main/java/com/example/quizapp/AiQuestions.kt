package com.example.quizapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random

data class AiConfig(val key: String, val baseUrl: String, val model: String)

/**
 * ساخت سوال تستی از متن کتاب با هر سرویس سازگار با OpenAI (پیش‌فرض: DeepSeek).
 * کلید API را خود کاربر می‌دهد و فقط روی همین گوشی ذخیره می‌شود؛ متن کتاب برای ساخت سوال به سرور آن سرویس فرستاده می‌شود.
 */
object AiQuestions {
    const val DEFAULT_BASE = "https://api.deepseek.com"
    const val DEFAULT_MODEL = "deepseek-chat"
    const val CHUNK_CHARS = 6000
    const val MAX_CHUNKS = 40
    private const val PREFS = "ai_config"

    fun loadConfig(context: Context): AiConfig {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AiConfig(
            p.getString("key", "").orEmpty(),
            p.getString("base", DEFAULT_BASE).orEmpty().ifBlank { DEFAULT_BASE },
            p.getString("model", DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
        )
    }

    fun saveConfig(context: Context, c: AiConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("key", c.key.trim()).putString("base", c.baseUrl.trim()).putString("model", c.model.trim()).apply()
    }

    /** متن را به بخش‌های حدود CHUNK_CHARS نویسه‌ای (در مرز پاراگراف/جمله) می‌شکند */
    fun splitText(text: String, maxChars: Int = CHUNK_CHARS): List<String> {
        val clean = text.replace("\r", "").replace(Regex("[ \t]+"), " ").replace(Regex("\n{3,}"), "\n\n").trim()
        if (clean.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isNotBlank()) out.add(cur.toString().trim())
            cur.setLength(0)
        }
        for (para in clean.split("\n")) {
            var p = para.trim()
            if (p.isEmpty()) continue
            while (p.length > maxChars) {
                var cut = p.lastIndexOfAny(charArrayOf('.', '؟', '?', '!', '،', ' '), maxChars)
                if (cut < maxChars / 2) cut = maxChars
                if (cur.isNotEmpty()) flush()
                out.add(p.substring(0, cut).trim())
                p = p.substring(cut).trim()
            }
            if (p.isEmpty()) continue
            if (cur.length + p.length + 1 > maxChars) flush()
            if (cur.isNotEmpty()) cur.append('\n')
            cur.append(p)
        }
        flush()
        return out
    }

    /** چند بخش و چند سوال از هر بخش: بخش‌ها (در صورت زیاد بودن) به‌طور یکنواخت نمونه‌برداری می‌شوند */
    fun plan(chunks: Int, total: Int): List<Pair<Int, Int>> {
        if (chunks <= 0 || total <= 0) return emptyList()
        val use = minOf(chunks, MAX_CHUNKS, total)
        val picked = (0 until use).map { if (use == 1) 0 else it * (chunks - 1) / (use - 1) }.distinct()
        val base = total / picked.size
        var extra = total % picked.size
        return picked.map { idx ->
            val n = base + if (extra > 0) { extra--; 1 } else 0
            idx to n
        }
    }

    fun messages(chunk: String, n: Int): JSONArray {
        val system = "تو یک معلم باتجربه هستی. فقط بر اساس «متن» داده‌شده سوال چهارگزینه‌ای بساز، نه از دانسته‌های بیرون. " +
            "سوال‌ها پاسخ روشن و یکتا داشته باشند و به زبان خودِ متن باشند. در هر سوال دقیقاً ۴ گزینهٔ متفاوت بنویس که فقط یکی درست است. " +
            "خروجی فقط یک آرایهٔ JSON باشد و هیچ متن دیگری ننویس. قالب: " +
            "[{\"q\":\"متن سوال\",\"o\":[\"گزینه ۱\",\"گزینه ۲\",\"گزینه ۳\",\"گزینه ۴\"],\"k\":0}] که k شمارهٔ گزینهٔ درست از ۰ تا ۳ است."
        return JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", "تعداد سوال: $n\n\nمتن:\n$chunk"))
    }

    /** پاسخ مدل را به سوال تبدیل می‌کند؛ موردهای ناقص یا نامعتبر کنار گذاشته می‌شوند. جای گزینهٔ درست تصادفی می‌شود. */
    fun parse(reply: String, random: Random = Random(), now: Long = System.currentTimeMillis()): List<Question> {
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val arr = try {
            JSONArray(reply.substring(start, end + 1))
        } catch (e: Exception) {
            return emptyList()
        }
        val out = ArrayList<Question>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val q = o.optString("q", "").trim()
            val opts = o.optJSONArray("o") ?: continue
            if (q.isEmpty() || opts.length() != 4) continue
            val list = (0 until 4).map { opts.optString(it, "").trim() }
            if (list.any { it.isEmpty() } || list.toSet().size != 4) continue
            val k = o.optInt("k", -1)
            if (k !in 0..3) continue
            val order = (0 until 4).toMutableList().also { it.shuffle(random) }
            val shuffled = order.map { list[it] }
            val newCorrect = order.indexOf(k)
            out.add(Question(q, shuffled[newCorrect], now, "", shuffled, newCorrect))
        }
        return out
    }

    private fun httpMessage(status: Int): String = when (status) {
        401 -> "کلید API درست نیست یا منقضی شده."
        402 -> "اعتبار حساب این سرویس تمام شده است."
        429 -> "تعداد درخواست‌ها زیاد است؛ چند دقیقه بعد امتحان کنید."
        404 -> "آدرس یا نام مدل درست نیست."
        else -> "سرویس خطا داد ($status)."
    }

    /** برای یک بخش از متن، سوال می‌سازد (مسدودکننده) */
    fun generate(cfg: AiConfig, chunk: String, n: Int, random: Random = Random()): List<Question> {
        if (cfg.key.isBlank()) throw OnlineException("کلید API را بنویسید.")
        val body = JSONObject()
            .put("model", cfg.model)
            .put("messages", messages(chunk, n))
            .put("temperature", 0.3)
            .put("max_tokens", 3500)
            .put("stream", false)
            .toString()
        val conn = try {
            URL(cfg.baseUrl.trim().trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw OnlineException("آدرس سرویس درست نیست.")
        }
        val text: String
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 120000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer " + cfg.key.trim())
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw OnlineException(httpMessage(code))
            text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: OnlineException) {
            throw e
        } catch (e: Exception) {
            throw OnlineException("اتصال به اینترنت یا سرویس برقرار نشد.")
        } finally {
            conn.disconnect()
        }
        val content = try {
            JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } catch (e: Exception) {
            throw OnlineException("پاسخ سرویس نامعتبر بود.")
        }
        return parse(content, random)
    }
}
