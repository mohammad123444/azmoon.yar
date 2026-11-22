package com.example.quizapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random

data class AiConfig(
    val key: String,
    val model: String = AiQuestions.DEFAULT_MODEL,
    val baseUrl: String = AiQuestions.DEFAULT_BASE
)

/**
 * ساخت سوال تستی از متن کتاب با Gemini (Google).
 * کلید API داخل برنامه (هنگام بیلد، از BuildConfig) قرار می‌گیرد و هیچ‌جای رابط کاربری نشان داده نمی‌شود؛
 * متن کتاب برای ساخت سوال به سرور Google فرستاده می‌شود.
 */
object AiQuestions {
    const val DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta"
    const val DEFAULT_MODEL = "gemini-flash-latest"
    const val CHUNK_CHARS = 6000
    const val MAX_CHUNKS = 40
    private const val OLD_PREFS = "ai_config"

    /** کلید و تنظیمات سرویس قدیمی (DeepSeek) که کاربر قبلاً روی گوشی ذخیره کرده بود پاک می‌شود */
    fun clearLegacyConfig(context: Context) {
        try {
            val p = context.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE)
            if (p.getString("key", "").orEmpty().isNotEmpty()) {
                p.edit().remove("key").remove("base").remove("model").apply()
            }
        } catch (e: Exception) {
            // مهم نیست
        }
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

    fun systemPrompt(): String =
        "تو یک معلم باتجربه هستی. فقط بر اساس «متن» داده‌شده سوال چهارگزینه‌ای بساز، نه از دانسته‌های بیرون. " +
            "سوال‌ها پاسخ روشن و یکتا داشته باشند و به زبان خودِ متن باشند. در هر سوال دقیقاً ۴ گزینهٔ متفاوت بنویس که فقط یکی درست است. " +
            "خروجی فقط یک آرایهٔ JSON باشد و هیچ متن دیگری ننویس. قالب: " +
            "[{\"q\":\"متن سوال\",\"o\":[\"گزینه ۱\",\"گزینه ۲\",\"گزینه ۳\",\"گزینه ۴\"],\"k\":0}] که k شمارهٔ گزینهٔ درست از ۰ تا ۳ است."

    fun userPrompt(chunk: String, n: Int): String = "تعداد سوال: $n\n\nمتن:\n$chunk"

    /** بدنهٔ درخواست generateContent در Gemini */
    fun requestBody(chunk: String, n: Int): String =
        JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt()))))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", userPrompt(chunk, n))))
                )
            )
            .put(
                "generationConfig",
                JSONObject().put("temperature", 0.3).put("maxOutputTokens", 8192).put("responseMimeType", "application/json")
            )
            .toString()

    /** متن پاسخ را از ساختار پاسخ Gemini بیرون می‌کشد (بخش‌های «thought» نادیده گرفته می‌شود) */
    fun extractText(response: String): String {
        val root = JSONObject(response)
        val block = root.optJSONObject("promptFeedback")?.optString("blockReason", "").orEmpty()
        val cands = root.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) {
            if (block.isNotEmpty()) throw OnlineException("سرویس این متن را رد کرد؛ متن دیگری امتحان کنید.")
            throw OnlineException("پاسخ سرویس نامعتبر بود.")
        }
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: throw OnlineException("پاسخ سرویس خالی بود؛ دوباره امتحان کنید.")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought", false)) continue
            sb.append(part.optString("text", ""))
        }
        return sb.toString()
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

    private fun httpMessage(status: Int, errBody: String): String = when {
        status == 400 && errBody.contains("API key", ignoreCase = true) -> "کلید داخلی برنامه درست نیست."
        status == 401 || status == 403 -> "کلید داخلی برنامه کار نمی‌کند یا دسترسی ندارد."
        status == 429 -> "تعداد درخواست‌ها زیاد است؛ چند دقیقه بعد امتحان کنید."
        status == 404 -> "نام مدل درست نیست."
        status == 503 -> "سرویس شلوغ است؛ کمی بعد امتحان کنید."
        else -> "سرویس خطا داد ($status)."
    }

    /** برای یک بخش از متن، سوال می‌سازد (مسدودکننده). روی خطای شلوغی (۴۲۹/۵۰۳) چند بار با فاصله دوباره تلاش می‌کند. */
    fun generate(
        cfg: AiConfig,
        chunk: String,
        n: Int,
        random: Random = Random(),
        retries: Int = 2,
        retryDelayMs: Long = 15000
    ): List<Question> {
        if (cfg.key.isBlank()) throw OnlineException("کلید داخلی برنامه تنظیم نشده است.")
        val body = requestBody(chunk, n)
        val endpoint = cfg.baseUrl.trim().trimEnd('/') + "/models/" + cfg.model.trim() + ":generateContent"
        var attempt = 0
        while (true) {
            val conn = try {
                URL(endpoint).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                throw OnlineException("آدرس سرویس درست نیست.")
            }
            var text: String? = null
            var status = 0
            var errBody = ""
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 20000
                conn.readTimeout = 120000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-goog-api-key", cfg.key.trim())
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                status = conn.responseCode
                if (status in 200..299) {
                    text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    errBody = try {
                        conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    } catch (e: Exception) {
                        ""
                    }
                }
            } catch (e: Exception) {
                throw OnlineException("اتصال به اینترنت یا سرویس برقرار نشد.")
            } finally {
                conn.disconnect()
            }
            if (text != null) {
                val content = try {
                    extractText(text)
                } catch (e: OnlineException) {
                    throw e
                } catch (e: Exception) {
                    throw OnlineException("پاسخ سرویس نامعتبر بود.")
                }
                return parse(content, random)
            }
            if ((status == 429 || status == 503) && attempt < retries) {
                attempt++
                try {
                    Thread.sleep(retryDelayMs)
                } catch (e: InterruptedException) {
                    throw OnlineException("متوقف شد.")
                }
                continue
            }
            throw OnlineException(httpMessage(status, errBody))
        }
    }
}
