package com.example.quizapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

    fun systemPrompt(pdf: Boolean = false): String =
        "تو یک معلم باتجربه هستی. فقط بر اساس " + (if (pdf) "محتوای کتابِ پیوست (فایل PDF)" else "«متن» داده‌شده") +
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

    /** بدنهٔ درخواست برای ساخت سوال از صفحه‌های from..to یک PDF که قبلاً آپلود شده */
    fun pdfRequestBody(fileUri: String, n: Int, from: Int, to: Int, mime: String = "application/pdf"): String =
        JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt(true)))))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("fileData", JSONObject().put("mimeType", mime).put("fileUri", fileUri)))
                            .put(
                                JSONObject().put(
                                    "text",
                                    "تعداد سوال: $n\nفقط از صفحه‌های $from تا $to همین کتاب (شمارهٔ صفحه همان است که در نمایشگر PDF دیده می‌شود) سوال بساز."
                                )
                            )
                    )
                )
            )
            .put(
                "generationConfig",
                JSONObject().put("temperature", 0.3).put("maxOutputTokens", 8192).put("responseMimeType", "application/json")
            )
            .toString()

    /** صفحه‌های from..to را به چند مرحله می‌شکند و تعداد سوال را بینشان پخش می‌کند: (از صفحه، تا صفحه، تعداد سوال) */
    fun planPdf(from: Int, to: Int, total: Int, perBatch: Int = 15): List<Triple<Int, Int, Int>> {
        if (total <= 0 || from < 1 || to < from) return emptyList()
        val pages = to - from + 1
        val batches = minOf((total + perBatch - 1) / perBatch, pages, total)
        val base = total / batches
        var extra = total % batches
        return (0 until batches).map { i ->
            val a = from + i * pages / batches
            val b = from + (i + 1) * pages / batches - 1
            val n = base + if (extra > 0) { extra--; 1 } else 0
            Triple(a, b, n)
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

    private class HttpResult(val status: Int, val body: String, val uploadUrl: String)

    private fun http(url: String, method: String, headers: Map<String, String>, bytes: ByteArray?, file: File?): HttpResult {
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
            if (bytes != null || file != null) {
                conn.doOutput = true
                if (file != null) {
                    conn.setFixedLengthStreamingMode(file.length())
                    file.inputStream().use { ins -> conn.outputStream.use { out -> ins.copyTo(out) } }
                } else {
                    conn.outputStream.use { it.write(bytes ?: ByteArray(0)) }
                }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = try {
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } catch (e: Exception) {
                ""
            }
            return HttpResult(status, body, conn.getHeaderField("X-Goog-Upload-URL").orEmpty())
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
                    Thread.sleep(delayMs)
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
        val url = base(cfg) + "/models/" + cfg.model.trim() + ":generateContent"
        val r = withRetry(retries, retryDelayMs) { http(url, "POST", jsonHeaders(cfg), body.toByteArray(Charsets.UTF_8), null) }
        if (r.status !in 200..299) throw OnlineException(httpMessage(r.status, r.body))
        return questionsFromResponse(r.body, random)
    }

    /** برای یک بخش از متن، سوال می‌سازد (مسدودکننده). */
    fun generate(
        cfg: AiConfig,
        chunk: String,
        n: Int,
        random: Random = Random(),
        retries: Int = 3,
        retryDelayMs: Long = 20000
    ): List<Question> = generateWith(cfg, requestBody(chunk, n), random, retries, retryDelayMs)

    /** برای صفحه‌های from..to از PDFِ آپلودشده سوال می‌سازد (مسدودکننده). */
    fun generateFromPdf(
        cfg: AiConfig,
        file: GeminiFile,
        n: Int,
        from: Int,
        to: Int,
        random: Random = Random(),
        retries: Int = 3,
        retryDelayMs: Long = 20000
    ): List<Question> = generateWith(cfg, pdfRequestBody(file.uri, n, from, to, file.mime), random, retries, retryDelayMs)

    /** فایل (مثلاً PDF) را در Files API گوگل آپلود می‌کند (۴۸ ساعت می‌ماند) و تا آماده شدن صبر می‌کند. */
    fun uploadFile(cfg: AiConfig, file: File, mime: String = "application/pdf", displayName: String = "book"): GeminiFile {
        requireKey(cfg)
        val b = base(cfg)
        val origin = b.substringBefore("/v1beta")
        val startHeaders = mapOf(
            "x-goog-api-key" to cfg.key.trim(),
            "X-Goog-Upload-Protocol" to "resumable",
            "X-Goog-Upload-Command" to "start",
            "X-Goog-Upload-Header-Content-Length" to file.length().toString(),
            "X-Goog-Upload-Header-Content-Type" to mime,
            "Content-Type" to "application/json"
        )
        val meta = JSONObject().put("file", JSONObject().put("display_name", displayName)).toString().toByteArray(Charsets.UTF_8)
        val start = http("$origin/upload/v1beta/files", "POST", startHeaders, meta, null)
        if (start.status !in 200..299) throw OnlineException(httpMessage(start.status, start.body))
        if (start.uploadUrl.isBlank()) throw OnlineException("پاسخ سرویس نامعتبر بود.")
        val up = withRetry(2, 10000) {
            http(
                start.uploadUrl, "POST",
                mapOf(
                    "x-goog-api-key" to cfg.key.trim(),
                    "X-Goog-Upload-Offset" to "0",
                    "X-Goog-Upload-Command" to "upload, finalize"
                ),
                null, file
            )
        }
        if (up.status !in 200..299) throw OnlineException(httpMessage(up.status, up.body))
        var info = try {
            JSONObject(up.body).getJSONObject("file")
        } catch (e: Exception) {
            throw OnlineException("پاسخ سرویس نامعتبر بود.")
        }
        var tries = 0
        while (info.optString("state", "ACTIVE") == "PROCESSING" && tries < 40) {
            tries++
            try {
                Thread.sleep(3000)
            } catch (e: InterruptedException) {
                throw OnlineException("متوقف شد.")
            }
            val st = http(origin + "/v1beta/" + info.getString("name"), "GET", mapOf("x-goog-api-key" to cfg.key.trim()), null, null)
            if (st.status !in 200..299) throw OnlineException(httpMessage(st.status, st.body))
            info = try {
                JSONObject(st.body)
            } catch (e: Exception) {
                throw OnlineException("پاسخ سرویس نامعتبر بود.")
            }
        }
        val state = info.optString("state", "ACTIVE")
        if (state == "FAILED") throw OnlineException("سرویس نتوانست این PDF را پردازش کند.")
        if (state == "PROCESSING") throw OnlineException("پردازش PDF طول کشید؛ دوباره امتحان کنید.")
        val uri = info.optString("uri", "")
        if (uri.isBlank()) throw OnlineException("پاسخ سرویس نامعتبر بود.")
        return GeminiFile(info.optString("name", ""), uri, mime)
    }
}

class GeminiFile(val name: String, val uri: String, val mime: String = "application/pdf")
