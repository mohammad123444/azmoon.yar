package com.example.quizapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Html
import android.util.Base64
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Calendar
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** یک نتیجهٔ جستجو */
data class WebResult(val title: String, val url: String, val host: String, val isPdf: Boolean)

/**
 * مشخصات یک فایل PDF پیش از دانلود نهایی.
 * lastModified: تاریخی که سرور برای فایل اعلام کرده (نزدیک‌ترین چیز به «تاریخ بارگذاری»)؛ ۰ یعنی سرور نگفته.
 * createdMillis: تاریخ ساخت فایل از داخل خود PDF (اگر تاریخ سرور نبود به‌عنوان جایگزین نشان داده می‌شود).
 * sizeBytes: ‎-۱ یعنی نامعلوم. pages: ۰ یعنی شمرده نشد.
 */
data class PdfMeta(
    val lastModified: Long = 0L,
    val createdMillis: Long = 0L,
    val sizeBytes: Long = -1L,
    val pages: Int = 0,
    val loaded: Boolean = false,
    /** false یعنی فایل واقعاً وجود ندارد یا PDF نیست (از فهرست حذف می‌شود) */
    val exists: Boolean = false
)

/**
 * جستجو در اینترنت، دریافت فایل PDF و جدا کردن صفحه‌های دلخواه از آن.
 * همهٔ توابع این شیء باید خارج از ترد اصلی (Dispatchers.IO) صدا زده شوند.
 */
object WebSearch {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    private const val MAX_BYTES = 80L * 1024 * 1024

    private fun connect(startUrl: String, extra: Map<String, String> = emptyMap()): HttpURLConnection {
        var current = startUrl
        for (i in 0 until 6) {
            // اندروید اتصال بدون رمزنگاری (http) را مسدود می‌کند؛ نسخهٔ https امتحان می‌شود
            if (current.startsWith("http://")) current = "https://" + current.removePrefix("http://")
            val c = URL(current).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept-Language", "fa,en;q=0.8")
            for ((k, v) in extra) c.setRequestProperty(k, v)
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) throw IOException("redirect")
                current = URL(URL(current), loc).toString()
                continue
            }
            return c
        }
        throw IOException("too many redirects")
    }

    private fun cleanHtml(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().replace('\n', ' ').trim()

    private fun looksLikePdf(url: String): Boolean =
        url.substringBefore('?').substringBefore('#').endsWith(".pdf", ignoreCase = true)

    private fun fetchHtml(url: String, limitChars: Int = 3_000_000): String? {
        val c = connect(url)
        return try {
            if (c.responseCode != 200) null
            else c.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                val sb = StringBuilder()
                val buf = CharArray(16 * 1024)
                while (sb.length < limitChars) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
        } finally {
            c.disconnect()
        }
    }

    private fun hostOf(url: String): String? = try { URL(url).host.removePrefix("www.") } catch (e: Exception) { null }

    /** نتیجهٔ DuckDuckGo: آدرس واقعی داخل پارامتر uddg است */
    private fun parseDuckDuckGo(html: String): List<WebResult> {
        val regex = Regex("""<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val out = mutableListOf<WebResult>()
        for (m in regex.findAll(html)) {
            var href = Html.fromHtml(m.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
            val uddg = Regex("""[?&]uddg=([^&]+)""").find(href)
            if (uddg != null) href = URLDecoder.decode(uddg.groupValues[1], "UTF-8")
            if (href.startsWith("//")) href = "https:$href"
            if (!href.startsWith("http")) continue
            val host = hostOf(href) ?: continue
            out.add(WebResult(cleanHtml(m.groupValues[2]).ifBlank { host }, href, host, looksLikePdf(href)))
        }
        return out
    }

    /** نتیجهٔ Bing: گاهی آدرس واقعی با پیشوند a1 و base64 داخل پارامتر u است */
    private fun parseBing(html: String): List<WebResult> {
        val regex = Regex("""<li class="b_algo"[^>]*>.*?<h2[^>]*><a[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val out = mutableListOf<WebResult>()
        for (m in regex.findAll(html)) {
            var href = Html.fromHtml(m.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
            val u = Regex("""[?&]u=([^&]+)""").find(href)
            if (href.contains("bing.com/ck/") && u != null) {
                val v = URLDecoder.decode(u.groupValues[1], "UTF-8")
                if (v.startsWith("a1")) {
                    try {
                        href = String(Base64.decode(v.substring(2), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
            if (!href.startsWith("http")) continue
            val host = hostOf(href) ?: continue
            out.add(WebResult(cleanHtml(m.groupValues[2]).ifBlank { host }, href, host, looksLikePdf(href)))
        }
        return out
    }

    // ------------------------------------------------------------------
    // جستجوی هوشمند: عبارت کاربر به چند شکل نوشته می‌شود و نتیجه‌ها بر اساس نزدیکی به عبارت مرتب می‌شوند
    // ------------------------------------------------------------------

    private const val MAX_RESULTS = 20

    /** گروه‌های هم‌معنی؛ اگر کاربر یکی را بنویسد، فایل‌هایی که یکی دیگر از گروه را دارند هم نزدیک حساب می‌شوند */
    private val EXAM_WORDS = listOf("سوال", "سوالات", "آزمون", "امتحان", "امتحانی", "امتحانات", "نمونه سوال", "exam", "quiz")
    private val ANSWER_WORDS = listOf("پاسخ", "پاسخنامه", "پاسخ نامه", "جواب", "جواب نامه", "کلید", "answer", "pasokh", "javab")
    private val ORDINAL_WORDS = listOf(
        listOf("اول", "یکم", "1"), listOf("دوم", "2"), listOf("سوم", "3"), listOf("چهارم", "4"),
        listOf("پنجم", "5"), listOf("ششم", "6"), listOf("هفتم", "7"), listOf("هشتم", "8"),
        listOf("نهم", "9"), listOf("دهم", "10"), listOf("یازدهم", "11"), listOf("دوازدهم", "12")
    )
    private val OTHER_GROUPS = listOf(
        listOf("ریاضی", "ریاضیات", "math"),
        listOf("علوم", "علوم تجربی", "science"),
        listOf("ادبیات", "فارسی", "نگارش"),
        listOf("ترم", "نیمسال", "نوبت"),
        listOf("پایانی", "نهایی", "پایان ترم"),
        listOf("پایه", "کلاس"),
        listOf("فصل", "درس")
    )
    /** گروه‌هایی که در جستجوی «یا» (OR) هم به موتور جستجو داده می‌شوند */
    private val OR_GROUPS: List<List<String>> = listOf(EXAM_WORDS, ANSWER_WORDS) + ORDINAL_WORDS
    private val ALL_GROUPS: List<List<String>> = OR_GROUPS + OTHER_GROUPS
    private val STOP_WORDS = setOf("با", "و", "برای", "در", "از", "به", "را", "که", "یک", "این", "های", "ی")

    private fun hasAnswer(r: WebResult): Boolean {
        val text = r.title + " " + (try { URLDecoder.decode(r.url, "UTF-8") } catch (e: Exception) { r.url })
        val spaced = fold(text)
        val compact = spaced.replace(" ", "")
        return ANSWER_WORDS.any { matches(spaced, compact, fold(it).replace(" ", "")) }
    }

    /** یکسان‌سازی نوشته برای مقایسه: ی/ي، ک/ك، آ/ا، اعراب، نیم‌فاصله، رقم فارسی/عربی/لاتین */
    private fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c == 'ي' || c == 'ى' -> sb.append('ی')
                c == 'ك' -> sb.append('ک')
                c == 'آ' || c == 'أ' || c == 'إ' || c == 'ٱ' -> sb.append('ا')
                c == 'ة' -> sb.append('ه')
                c == 'ؤ' -> sb.append('و')
                c in '\u06F0'..'\u06F9' -> sb.append('0' + (c - '\u06F0'))
                c in '\u0660'..'\u0669' -> sb.append('0' + (c - '\u0660'))
                c == '\u0640' || c in '\u064B'..'\u065F' || c == '\u0670' -> {}
                Character.isLetterOrDigit(c) -> sb.append(c.lowercaseChar())
                else -> sb.append(' ')
            }
        }
        return sb.toString().replace(Regex(" +"), " ").trim()
    }

    /** شکل سبک‌شدهٔ عبارت کاربر برای فرستادن به موتور جستجو (فقط ي/ك عربی و اعراب اصلاح می‌شود) */
    private fun cleanQuery(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c == 'ي' || c == 'ى' -> sb.append('ی')
                c == 'ك' -> sb.append('ک')
                c == '\u0640' || c in '\u064B'..'\u065F' || c == '\u0670' -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun toAsciiDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                    in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    private fun toFaDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in '0'..'9') '\u06F0' + (c - '0') else c)
        return sb.toString()
    }

    /** ریشهٔ ساده: «سوالات» ← «سوال»، «کتاب‌ها» ← «کتاب» */
    private fun stem(w: String): String {
        for (suf in listOf("های", "ها", "ات", "ان")) {
            if (w.endsWith(suf) && w.length - suf.length >= 3) return w.dropLast(suf.length)
        }
        return w
    }

    private fun compactOf(w: String): String = fold(w).replace(" ", "")

    private fun queryTokens(query: String): List<String> =
        fold(query).split(' ').filter { it.isNotEmpty() && it !in STOP_WORDS }

    /** همهٔ شکل‌هایی که یک واژه می‌تواند در نام فایل داشته باشد */
    private fun altsOf(token: String): Set<String> {
        val alts = LinkedHashSet<String>()
        alts.add(token)
        val st = stem(token)
        alts.add(st)
        for (g in ALL_GROUPS) {
            if (g.any { val k = compactOf(it); k == token || k == st }) {
                for (w in g) alts.add(compactOf(w))
            }
        }
        return alts
    }

    private fun matches(spaced: String, compact: String, alt: String): Boolean {
        if (alt.isEmpty()) return false
        return if (alt.all { it in '0'..'9' }) {
            Regex("(?<![0-9])$alt(?![0-9])").containsMatchIn(spaced)
        } else {
            compact.contains(alt)
        }
    }

    /** چه بخشی از واژه‌های عبارت کاربر (با هم‌معنی‌ها) در متن دیده می‌شود: عددی بین ۰ و ۱ */
    private fun relevance(tokens: List<String>, text: String): Double {
        if (tokens.isEmpty()) return 0.0
        val spaced = fold(text)
        val compact = spaced.replace(" ", "")
        var hit = 0
        for (t in tokens) if (altsOf(t).any { matches(spaced, compact, it) }) hit++
        return hit.toDouble() / tokens.size
    }

    /** نتیجه‌هایی که به عبارت کاربر نزدیک‌ترند اول می‌آیند؛ اگر پاسخ هم داشته باشند کمی بالاتر می‌روند */
    private fun rank(list: List<WebResult>, query: String): List<WebResult> {
        val tokens = queryTokens(query)
        return list
            .map { r ->
                val text = r.title + " " + (try { URLDecoder.decode(r.url, "UTF-8") } catch (e: Exception) { r.url })
                Pair(r, relevance(tokens, text) + (if (hasAnswer(r)) 0.3 else 0.0))
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /** یک واژه را به شکل «(الف OR ب OR ج)» درمی‌آورد؛ اگر هم‌معنی نداشت خودش برمی‌گردد */
    private fun orForm(raw: String): String {
        val f = compactOf(raw)
        val st = stem(f)
        val alts = LinkedHashSet<String>()
        alts.add(raw)
        if (f.isNotEmpty() && f.all { it in '0'..'9' }) {
            alts.add(f)
            alts.add(toFaDigits(f))
        }
        for (g in OR_GROUPS) {
            if (g.any { val k = compactOf(it); k == f || k == st }) {
                for (w in g) {
                    alts.add(w)
                    if (w.all { it in '0'..'9' }) alts.add(toFaDigits(w))
                }
            }
        }
        val list = alts.toList().take(5)
        if (list.size < 2) return raw
        return "(" + list.joinToString(" OR ") { if (it.contains(' ')) "\"$it\"" else it } + ")"
    }

    /** چند شکل مختلف از همان عبارت برای موتورهای جستجو (حداکثر ۵ تا) */
    private fun buildVariants(query: String): List<String> {
        val base = cleanQuery(query)
        val out = LinkedHashSet<String>()
        if (base.isEmpty()) return emptyList()
        out.add(base)
        val tokens = base.split(' ').filter { it.isNotEmpty() }
        // شکل «یا»: سوال یا سوالات یا آزمون، پاسخ یا جواب، هشتم یا ۸ ...
        val orQuery = tokens.joinToString(" ") { orForm(it) }
        out.add(orQuery)
        // رقم لاتین به‌جای فارسی (و برعکس)
        out.add(toAsciiDigits(base))
        // نیم‌فاصله به‌جای فاصله
        if (base.contains('\u200c')) out.add(base.replace('\u200c', ' '))
        // اگر نوشته‌اند فقط «ریاضی هشتم»، «نمونه سوال» هم اضافه شود
        val tk = queryTokens(base)
        val hasExamWord = tk.any { t -> altsOf(t).any { a -> EXAM_WORDS.any { compactOf(it) == a } } }
        if (!hasExamWord) out.add("$base نمونه سوال")
        // آخرین واژه کنار گذاشته شود (گاهی واژهٔ آخر باعث می‌شود چیزی پیدا نشود)
        if (tokens.size >= 3) out.add(tokens.dropLast(1).joinToString(" "))
        return out.toList().take(5)
    }

    private fun fetchEngine(url: String, answered: AtomicBoolean, parse: (String) -> List<WebResult>): List<WebResult>? {
        val html = (try { fetchHtml(url) } catch (e: Exception) { null }) ?: return null
        answered.set(true)
        return try { parse(html) } catch (e: Exception) { null }
    }

    /**
     * جستجو در کل اینترنت؛ فقط فایل‌های PDF (قابل دانلود) برمی‌گردند، حداکثر ۲۰ تا.
     * عبارت به چند شکل (املای دیگر، رقم لاتین، هم‌معنی‌ها) و در دو موتور جستجو هم‌زمان امتحان می‌شود
     * و نتیجه‌ها بر اساس نزدیکی به عبارت کاربر مرتب می‌شوند.
     * null یعنی هیچ موتوری در دسترس نبود (مثلاً اینترنت قطع است).
     */
    fun searchPdfs(query: String): List<WebResult>? {
        val variants = buildVariants(query)
        if (variants.isEmpty()) return emptyList()
        val answered = AtomicBoolean(false)
        val tasks = ArrayList<Callable<List<WebResult>?>>()
        variants.forEachIndexed { i, v ->
            val enc = URLEncoder.encode("$v filetype:pdf", "UTF-8")
            // DuckDuckGo با درخواست‌های زیاد پشت‌سرهم مسدود می‌کند؛ فقط برای دو شکل اول
            if (i < 2) tasks.add(Callable<List<WebResult>?> { fetchEngine("https://html.duckduckgo.com/html/?q=$enc", answered) { parseDuckDuckGo(it) } })
            tasks.add(Callable<List<WebResult>?> { fetchEngine("https://www.bing.com/search?q=$enc&setlang=fa&count=30", answered) { parseBing(it) } })
        }
        val found = LinkedHashMap<String, WebResult>()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = pool.invokeAll(tasks, 40, TimeUnit.SECONDS)
            for (f in futures) {
                if (f.isCancelled) continue
                val list = (try { f.get() } catch (e: Exception) { null }) ?: continue
                for (r in list) if (r.isPdf) found.putIfAbsent(r.url.substringBefore('#'), r)
            }
        } finally {
            pool.shutdownNow()
        }
        if (!answered.get()) return null
        return rank(found.values.toList(), query).take(MAX_RESULTS)
    }

    /** دریافت PDF در فایل dest؛ اگر فایل PDF نباشد خطا می‌دهد */
    fun downloadPdf(
        url: String,
        dest: File,
        cookie: String? = null,
        userAgent: String? = null,
        referer: String? = null,
        onProgress: ((done: Long, total: Long) -> Unit)? = null,
        check: () -> Unit = {}
    ) {
        val extra = HashMap<String, String>()
        if (!cookie.isNullOrBlank()) extra["Cookie"] = cookie
        if (!userAgent.isNullOrBlank()) extra["User-Agent"] = userAgent
        if (!referer.isNullOrBlank()) extra["Referer"] = referer
        val c = connect(url, extra)
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            val len = c.contentLengthLong
            if (len > MAX_BYTES) throw IOException("حجم فایل زیاد است")
            dest.parentFile?.mkdirs()
            var total = 0L
            c.inputStream.use { ins ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var first = true
                    while (true) {
                        check()
                        val n = ins.read(buf)
                        if (n < 0) break
                        if (first) {
                            first = false
                            val isPdf = n >= 4 && buf[0] == 0x25.toByte() && buf[1] == 0x50.toByte() &&
                                buf[2] == 0x44.toByte() && buf[3] == 0x46.toByte()
                            if (!isPdf) throw IOException("این لینک فایل PDF نیست")
                        }
                        total += n
                        if (total > MAX_BYTES) throw IOException("حجم فایل زیاد است")
                        out.write(buf, 0, n)
                        onProgress?.invoke(total, len)
                    }
                }
            }
            if (total <= 0L) throw IOException("فایل خالی بود")
        } catch (e: Exception) {
            dest.delete()
            throw e
        } finally {
            c.disconnect()
        }
    }

    // ------------------------------------------------------------------
    // مشخصات فایل‌ها در فهرست نتیجه: تاریخ، تعداد برگه، حجم
    // ------------------------------------------------------------------

    /** فایل‌هایی که از این حجم بزرگ‌ترند برای شمردن برگه‌ها دانلود نمی‌شوند (فقط حجمشان نشان داده می‌شود) */
    private const val META_MAX_BYTES = 10L * 1024 * 1024

    /** مسیر نسخهٔ موقتی که برای شمردن برگه‌ها گرفته می‌شود؛ بعداً «نمایش» و «دانلود» از همین استفاده می‌کنند */
    fun cacheFile(context: Context, url: String): File =
        File(context.cacheDir, "online/meta/" + Integer.toHexString(url.substringBefore('#').hashCode()) + ".pdf")

    fun clearCache(context: Context) {
        File(context.cacheDir, "online/meta").listFiles()?.forEach { it.delete() }
    }

    private fun isPdfHead(buf: ByteArray, n: Int): Boolean =
        n >= 4 && buf[0] == 0x25.toByte() && buf[1] == 0x50.toByte() &&
            buf[2] == 0x44.toByte() && buf[3] == 0x46.toByte()

    /**
     * تاریخ (از سرور)، حجم و تعداد برگه‌های یک PDF را درمی‌آورد. برای شمردن برگه‌ها فایل (تا ۱۰ مگابایت)
     * در cache ذخیره می‌شود. check بین خواندن‌ها صدا زده می‌شود تا بشود کار را لغو کرد.
     */
    fun fetchMeta(url: String, cache: File, check: () -> Unit = {}): PdfMeta {
        val c = connect(url)
        val part = File(cache.parentFile, cache.name + ".part")
        try {
            if (c.responseCode != 200) return PdfMeta(loaded = true)
            val lastMod = c.lastModified
            val len = c.contentLengthLong
            if (len > META_MAX_BYTES) return PdfMeta(lastModified = lastMod, sizeBytes = len, loaded = true, exists = true)
            cache.parentFile?.mkdirs()
            var total = 0L
            var tooBig = false
            var notPdf = false
            c.inputStream.use { ins ->
                part.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var first = true
                    while (true) {
                        check()
                        val n = ins.read(buf)
                        if (n < 0) break
                        if (first) {
                            first = false
                            if (!isPdfHead(buf, n)) { notPdf = true; break }
                        }
                        total += n
                        if (total > META_MAX_BYTES) { tooBig = true; break }
                        out.write(buf, 0, n)
                    }
                }
            }
            if (notPdf || total <= 0L) {
                part.delete()
                return PdfMeta(lastModified = lastMod, sizeBytes = len, loaded = true)
            }
            if (tooBig) {
                part.delete()
                return PdfMeta(lastModified = lastMod, sizeBytes = META_MAX_BYTES + 1, loaded = true, exists = true)
            }
            cache.delete()
            if (!part.renameTo(cache)) {
                part.delete()
                return PdfMeta(lastModified = lastMod, sizeBytes = total, loaded = true, exists = true)
            }
            val pages = try { pageCount(cache) } catch (e: Throwable) { 0 }
            return PdfMeta(
                lastModified = lastMod,
                createdMillis = pdfCreationMillis(cache),
                sizeBytes = total,
                pages = pages,
                loaded = true,
                exists = true
            )
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            c.disconnect()
        }
    }

    /** تاریخ ساختِ فایل که داخل خود PDF نوشته شده (اگر پیدا نشد ۰) */
    private fun pdfCreationMillis(f: File): Long = try {
        val len = f.length()
        val chunk = minOf(len, 64L * 1024).toInt()
        val text = java.io.RandomAccessFile(f, "r").use { r ->
            val head = ByteArray(chunk)
            r.readFully(head)
            val tail = ByteArray(chunk)
            r.seek(len - chunk)
            r.readFully(tail)
            String(head, Charsets.ISO_8859_1) + "\n" + String(tail, Charsets.ISO_8859_1)
        }
        val m = Regex("""/CreationDate\s*\(\s*D:(\d{4})(\d{2})(\d{2})""").find(text)
            ?: Regex("""/ModDate\s*\(\s*D:(\d{4})(\d{2})(\d{2})""").find(text)
        if (m == null) {
            0L
        } else {
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val d = m.groupValues[3].toInt()
            if (y !in 1995..2100 || mo !in 1..12 || d !in 1..31) 0L
            else Calendar.getInstance().apply { clear(); set(y, mo - 1, d) }.timeInMillis
        }
    } catch (e: Exception) {
        0L
    }

    fun pageCount(pdf: File): Int {
        val pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            val r = PdfRenderer(pfd)
            try { r.pageCount } finally { r.close() }
        } finally {
            pfd.close()
        }
    }

    private fun renderPage(r: PdfRenderer, index: Int, targetWidth: Int): Bitmap {
        val page = r.openPage(index)
        try {
            val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
            val w = targetWidth
            val h = (page.height * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } finally {
            page.close()
        }
    }

    /** پیش‌نمایش یک صفحه (شماره از ۱) */
    fun preview(pdf: File, pageNumber: Int, width: Int): Bitmap? = try {
        val pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val r = PdfRenderer(pfd)
            try { renderPage(r, pageNumber - 1, width) } finally { r.close() }
        } finally {
            pfd.close()
        }
    } catch (e: Throwable) {
        null
    }

    /**
     * صفحه‌های from..to (شماره از ۱) را به‌صورت عکس داخل حافظهٔ برنامه ذخیره می‌کند
     * و نام فایل‌ها را به ترتیب برمی‌گرداند. در صورت خطا فایل‌های نیمه‌کاره پاک می‌شوند.
     */
    fun extractPages(context: Context, pdf: File, from: Int, to: Int): List<String> {
        val names = mutableListOf<String>()
        try {
            val pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val r = PdfRenderer(pfd)
                try {
                    for (p in from..to) {
                        val bmp = renderPage(r, p - 1, 1500)
                        val name = ExamFiles.newName("jpg")
                        try {
                            ExamFiles.file(context, name).outputStream().use { out ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                        } finally {
                            bmp.recycle()
                        }
                        names.add(name)
                    }
                } finally {
                    r.close()
                }
            } finally {
                pfd.close()
            }
        } catch (e: Throwable) {
            names.forEach { ExamFiles.file(context, it).delete() }
            throw e
        }
        return names
    }
}
