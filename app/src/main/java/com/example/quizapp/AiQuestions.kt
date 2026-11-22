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
    const val DEFAULT_MODEL = "gemini-2.5-flash"

    /** اگر مدل اول شلوغ/محدود بود، به‌ترتیب به مدل بعدی می‌رود (۲.۵ فلش پایدار و رایگان‌تر از «latest» است) */
    val MODELS = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-flash-latest")

    @Volatile
    private var preferredModel: String? = null
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

    fun systemPrompt(pdf: Boolean = false): String =
        "تو یک معلم باتجربه هستی. فقط بر اساس " + (if (pdf) "تصویر صفحه‌های کتابِ پیوست" else "«متن» داده‌شده") +
            " سوال چهارگزینه‌ای بساز، نه از دانسته‌های بیرون. " +
            (if (pdf) "از فهرست مطالب، پیش‌گفتار و صفحه‌های بدون محتوای آموزشی سوال نساز. " else "") +
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

    /** بدنهٔ درخواست با تصویر چند صفحه: هر عضو (شمارهٔ صفحه، تصویر JPEG به‌صورت base64) */
    fun imagesRequestBody(pages: List<Pair<Int, String>>, n: Int): String {
        val parts = JSONArray()
        for ((num, b64) in pages) {
            parts.put(JSONObject().put("text", "صفحهٔ $num:"))
            parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", b64)))
        }
        parts.put(
            JSONObject().put(
                "text",
                "تعداد سوال: $n\nفقط از محتوای همین صفحه‌ها (${pages.joinToString("، ") { it.first.toString() }}) سوال بساز. " +
                    "اگر صفحه‌ای محتوای آموزشی ندارد از آن سوال نساز."
            )
        )
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt(true)))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put(
                "generationConfig",
                JSONObject().put("temperature", 0.3).put("maxOutputTokens", 16384).put("responseMimeType", "application/json")
            )
            .toString()
    }

    /**
     * صفحه‌های from..to را به چند مرحله می‌شکند (هر مرحله حدود qPerBatch سوال) و در هر مرحله حداکثر maxPages صفحه
     * (به‌طور یکنواخت از بخش همان مرحله) برمی‌دارد. مرحله‌های کوچک = سریع‌تر و کمتر شلوغ.
     */
    fun planPdf(from: Int, to: Int, total: Int, qPerBatch: Int = 6, maxPages: Int = 10): List<PdfBatch> {
        if (total <= 0 || from < 1 || to < from) return emptyList()
        val pages = to - from + 1
        val batches = minOf((total + qPerBatch - 1) / qPerBatch, pages, total)
        val base = total / batches
        var extra = total % batches
        return (0 until batches).map { i ->
            val a = from + i * pages / batches
            val b = from + (i + 1) * pages / batches - 1
            val size = b - a + 1
            val list = if (size <= maxPages) (a..b).toList() else (0 until maxPages).map { k -> a + k * size / maxPages }.distinct()
            val n = base + if (extra > 0) { extra--; 1 } else 0
            PdfBatch(list, n)
        }
    }

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
        if (start < 0) return emptyList()
        val arr = try {
            JSONArray(reply.substring(start, end + 1))
        } catch (e: Exception) {
            // پاسخِ نیمه‌کاره (قطع‌شده): فقط موردهای کامل نگه داشته می‌شود
            try {
                val cut = reply.lastIndexOf('}')
                if (cut <= start) return emptyList()
                JSONArray(reply.substring(start, cut + 1) + "]")
            } catch (e2: Exception) {
                return emptyList()
            }
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

    private fun shortError(body: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message", "").orEmpty().take(150)
    } catch (e: Exception) {
        ""
    }

    private fun httpMessage(status: Int, errBody: String): String = when {
        status == 400 && errBody.contains("API key", ignoreCase = true) -> "کلید داخلی برنامه درست نیست."
        status == 401 || status == 403 -> "کلید داخلی برنامه کار نمی‌کند یا دسترسی ندارد."
        status == 429 -> "تعداد درخواست‌ها زیاد است؛ چند دقیقه بعد امتحان کنید."
        status == 404 -> "نام مدل یا فایل پیدا نشد."
        status == 413 -> "فایل خیلی بزرگ است."
        status == 503 -> "سرویس شلوغ است؛ کمی بعد امتحان کنید."
        else -> "سرویس خطا داد ($status)" + shortError(errBody).let { if (it.isBlank()) "." else ": $it" }
    }

    private class HttpResult(val status: Int, val body: String)

    private fun http(url: String, method: String, headers: Map<String, String>, bytes: ByteArray?): HttpResult {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw OnlineException("آدرس سرویس درست نیست.")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 20000
            conn.readTimeout = 180000
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (bytes != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = try {
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } catch (e: Exception) {
                ""
            }
            return HttpResult(status, body)
        } catch (e: OnlineException) {
            throw e
        } catch (e: Exception) {
            throw OnlineException("اتصال به اینترنت یا سرویس برقرار نشد.")
        } finally {
            conn.disconnect()
        }
    }

    /** روی خطای شلوغی (۴۲۹/۵۰۳) چند بار با فاصله دوباره تلاش می‌کند */
    private fun withRetry(retries: Int, delayMs: Long, call: () -> HttpResult): HttpResult {
        var attempt = 0
        while (true) {
            val r = call()
            if ((r.status == 429 || r.status == 503) && attempt < retries) {
                attempt++
                try {
                    Thread.sleep(minOf(delayMs shl (attempt - 1), 60000L))
                } catch (e: InterruptedException) {
                    throw OnlineException("متوقف شد.")
                }
                continue
            }
            return r
        }
    }

    private fun requireKey(cfg: AiConfig) {
        if (cfg.key.isBlank()) throw OnlineException("کلید داخلی برنامه تنظیم نشده است.")
    }

    private fun base(cfg: AiConfig) = cfg.baseUrl.trim().trimEnd('/')

    private fun jsonHeaders(cfg: AiConfig) =
        mapOf("Content-Type" to "application/json; charset=utf-8", "x-goog-api-key" to cfg.key.trim())

    private fun questionsFromResponse(text: String, random: Random): List<Question> {
        val content = try {
            extractText(text)
        } catch (e: OnlineException) {
            throw e
        } catch (e: Exception) {
            throw OnlineException("پاسخ سرویس نامعتبر بود.")
        }
        return parse(content, random)
    }

    private fun generateWith(cfg: AiConfig, body: String, random: Random, retries: Int, retryDelayMs: Long): List<Question> {
        requireKey(cfg)
        // مدل انتخابی (یا آخرین مدلی که جواب داد) اول؛ بعد بقیه
        val first = preferredModel ?: cfg.model.trim()
        val order = (listOf(first, cfg.model.trim()) + MODELS).filter { it.isNotBlank() }.distinct()
        var last: OnlineException? = null
        for (model in order) {
            val url = base(cfg) + "/models/" + model + ":generateContent"
            val r = withRetry(retries, retryDelayMs) { http(url, "POST", jsonHeaders(cfg), body.toByteArray(Charsets.UTF_8)) }
            if (r.status in 200..299) {
                val qs = questionsFromResponse(r.body, random)
                preferredModel = model
                if (qs.isEmpty()) throw OnlineException("مدل از این بخش سوال قابل‌استفاده‌ای نساخت.")
                return qs
            }
            val msg = OnlineException(httpMessage(r.status, r.body))
            // شلوغی، سهمیه یا مدل ناموجود: مدل بعدی را امتحان کن؛ بقیهٔ خطاها (کلید و...) را همین‌جا گزارش بده
            if (r.status == 429 || r.status == 503 || r.status == 500 || r.status == 404) {
                last = msg
                if (preferredModel == model) preferredModel = null
                continue
            }
            throw msg
        }
        throw last ?: OnlineException("سرویس در دسترس نیست.")
    }

    /** برای یک بخش از متن، سوال می‌سازد (مسدودکننده). */
    fun generate(
        cfg: AiConfig,
        chunk: String,
        n: Int,
        random: Random = Random(),
        retries: Int = 2,
        retryDelayMs: Long = 8000
    ): List<Question> = generateWith(cfg, requestBody(chunk, n), random, retries, retryDelayMs)

    /** برای چند صفحهٔ کتاب (به‌صورت تصویر JPEG) سوال می‌سازد (مسدودکننده). */
    fun generateFromPages(
        cfg: AiConfig,
        pages: List<Pair<Int, String>>,
        n: Int,
        random: Random = Random(),
        retries: Int = 2,
        retryDelayMs: Long = 8000
    ): List<Question> = generateWith(cfg, imagesRequestBody(pages, n), random, retries, retryDelayMs)
}

class PdfBatch(val pages: List<Int>, val n: Int)
