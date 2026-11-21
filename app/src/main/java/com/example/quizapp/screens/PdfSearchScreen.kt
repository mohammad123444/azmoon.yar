package com.example.quizapp.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.PdfInbox
import com.example.quizapp.PdfMeta
import com.example.quizapp.PersianDate
import com.example.quizapp.WebResult
import com.example.quizapp.WebSearch
import com.example.quizapp.ui.theme.GreenPrimary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * وضعیت صفحهٔ جستجو. این شیء بالاتر از صفحه (در AppRoot) نگه داشته می‌شود تا با رفتن به صفحهٔ دیگر
 * و برگشتن (مثلاً با دکمهٔ برگشت گوشی)، عبارت جستجو، نتیجه‌ها و جای اسکرول پاک نشوند.
 */
class PdfSearchState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<WebResult>?>(null)
    val metas = mutableStateMapOf<String, PdfMeta>()
    val scroll = ScrollState(0)
}

private fun faNum(n: Long): String {
    val s = n.toString()
    val sb = StringBuilder()
    for (ch in s) sb.append(if (ch in '0'..'9') '\u06F0' + (ch - '0') else ch)
    return sb.toString()
}

private fun sizeText(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes / (1024.0 * 1024.0)
    val raw = if (mb >= 1.0) String.format(Locale.US, "%.1f", mb) else String.format(Locale.US, "%d", maxOf(1L, bytes / 1024))
    val fa = StringBuilder()
    for (ch in raw) fa.append(if (ch in '0'..'9') '\u06F0' + (ch - '0') else if (ch == '.') '٫' else ch)
    return fa.toString() + (if (mb >= 1.0) " مگابایت" else " کیلوبایت")
}

/** خط مشخصات زیر نام فایل: تاریخ، تعداد برگه، حجم */
private fun metaLine(m: PdfMeta?): String {
    if (m == null || !m.loaded) return "⏳ در حال بررسی تاریخ و تعداد برگه‌ها..."
    val parts = ArrayList<String>()
    when {
        m.lastModified > 0L -> parts.add("📅 بارگذاری: " + PersianDate.format(m.lastModified))
        m.createdMillis > 0L -> parts.add("📅 ساخت فایل: " + PersianDate.format(m.createdMillis))
        else -> parts.add("📅 تاریخ: نامشخص")
    }
    if (m.pages > 0) parts.add("📑 " + faNum(m.pages.toLong()) + " برگ") else parts.add("📑 برگ: نامشخص")
    val sz = sizeText(m.sizeBytes)
    if (sz.isNotEmpty()) parts.add("💾 $sz")
    return parts.joinToString("   ")
}

/**
 * جستجوی آزمون در اینترنت: فهرست فایل‌های PDF با دو دکمه:
 * «نمایش» (دیدن فایل پیش از ذخیره) و «دانلود» (رفتن به مرحلهٔ مشخص کردن صفحه‌های سوال و پاسخ).
 */
@Composable
fun PdfSearchScreen(state: PdfSearchState, onPdfReady: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pdf = remember { PdfInbox.file(context) }

    var busyText by remember { mutableStateOf<String?>(null) }
    // پیشرفت دانلود: ‎-۱ یعنی اندازه نامعلوم
    var progress by remember { mutableStateOf(-1f) }
    var progressNote by remember { mutableStateOf("") }
    val job = remember { arrayOfNulls<Job>(1) }
    // جای اسکرول فهرست در state.scroll می‌ماند؛ نمایش فایل اسکرول جدا دارد تا جای فهرست به هم نخورد
    val previewScroll = rememberScrollState()

    // حالت «نمایش»: فایل دریافت شده و فقط دیده می‌شود
    var previewing by remember { mutableStateOf(false) }
    var pageCount by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(previewing, page) {
        if (previewing && pageCount > 0) {
            bmp = withContext(Dispatchers.IO) { WebSearch.preview(pdf, page, 1100) }
        }
    }

    // تاریخ، تعداد برگه و حجم فایل‌ها را پشت صحنه (۳ تا هم‌زمان) درمی‌آورد
    val resultList = state.results
    LaunchedEffect(resultList) {
        val items = resultList ?: return@LaunchedEffect
        val gate = Semaphore(3)
        coroutineScope {
            for (r in items) {
                if (state.metas[r.url]?.loaded == true) continue
                launch {
                    gate.withPermit {
                        val m = withContext(Dispatchers.IO) {
                            val me = coroutineContext[Job]
                            try {
                                WebSearch.fetchMeta(r.url, WebSearch.cacheFile(context, r.url), check = { me?.ensureActive() })
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                PdfMeta(loaded = true)
                            }
                        }
                        state.metas[r.url] = m
                    }
                }
            }
        }
    }

    fun friendly(msg: String?): String = when {
        msg == null -> "فایل گرفته نشد"
        msg.startsWith("HTTP") -> "این فایل اجازهٔ دانلود نداد؛ فایل دیگری را امتحان کنید"
        else -> msg
    }

    fun cancelBusy() {
        job[0]?.cancel()
        job[0] = null
        busyText = null
        pdf.delete()
    }

    // فایل را می‌گیرد؛ اگر show باشد فقط نمایش می‌دهد، وگرنه به مرحلهٔ بعد می‌رود
    fun fetch(r: WebResult, show: Boolean) {
        if (busyText != null) return
        job[0] = scope.launch {
            busyText = if (show) "در حال باز کردن فایل..." else "در حال دانلود..."
            progress = -1f
            progressNote = ""
            val error = withContext(Dispatchers.IO) {
                val me = coroutineContext[Job]
                try {
                    val cached = WebSearch.cacheFile(context, r.url)
                    if (cached.exists() && cached.length() > 0L) {
                        // همین فایل پیش‌تر برای شمردن برگه‌ها گرفته شده؛ دوباره دانلود نمی‌شود
                        pdf.parentFile?.mkdirs()
                        cached.copyTo(pdf, overwrite = true)
                    } else {
                        WebSearch.downloadPdf(
                            r.url, pdf,
                            onProgress = { done, total ->
                                if (total > 0L) {
                                    val f = done.toFloat() / total.toFloat()
                                    if (f - progress >= 0.01f) {
                                        progress = f
                                        progressNote = faNum((f * 100).toLong()) + "٪"
                                    }
                                } else {
                                    progressNote = sizeText(done)
                                }
                            },
                            check = { me?.ensureActive() }
                        )
                    }
                    pageCount = WebSearch.pageCount(pdf)
                    null
                } catch (e: CancellationException) {
                    pdf.delete()
                    throw e
                } catch (e: Exception) {
                    pdf.delete()
                    friendly(e.message)
                }
            }
            busyText = null
            job[0] = null
            if (error != null) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            } else {
                PdfInbox.suggestedName = PdfInbox.nameFrom(r.url.substringBefore('?'))
                if (show) {
                    page = 1
                    bmp = null
                    previewing = true
                } else {
                    onPdfReady()
                }
            }
        }
    }

    fun doSearch() {
        if (state.query.isBlank() || busyText != null) return
        val q = state.query
        job[0] = scope.launch {
            busyText = "در حال جستجو..."
            progress = -1f
            progressNote = ""
            state.results = null
            state.metas.clear()
            withContext(Dispatchers.IO) { WebSearch.clearCache(context) }
            val found = withContext(Dispatchers.IO) {
                try { WebSearch.searchPdfs(q) } catch (e: Exception) { null }
            }
            busyText = null
            job[0] = null
            if (found == null) {
                Toast.makeText(context, "جستجو انجام نشد؛ اینترنت را بررسی کنید", Toast.LENGTH_LONG).show()
            } else {
                state.results = found
                state.scroll.scrollTo(0)
            }
        }
    }

    // دکمهٔ برگشت گوشی: در حال کار ← لغو؛ در حال نمایش فایل ← فقط برگشت به فهرست (جستجو پاک نمی‌شود)
    BackHandler(enabled = busyText != null) { cancelBusy() }
    BackHandler(enabled = previewing && busyText == null) {
        pdf.delete()
        previewing = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(if (previewing) previewScroll else state.scroll)
                .padding(16.dp)
        ) {
            if (previewing) {
                Text("👁 نمایش فایل", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { if (page > 1) page -= 1 }, enabled = page > 1) { Text("▶ قبلی") }
                    Text("صفحهٔ $page از $pageCount", fontSize = 14.sp)
                    TextButton(onClick = { if (page < pageCount) page += 1 }, enabled = page < pageCount) { Text("بعدی ◀") }
                }
                val b = bmp
                if (b != null) {
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onPdfReady,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                ) { Text("⬇️ دانلود و انتخاب صفحه‌ها", fontSize = 17.sp) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            shareLooseFile(context, pdf, PdfInbox.suggestedName.ifBlank { "azmoon" }, "ارسال PDF")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("📤 ارسال این PDF به برنامه‌های دیگر") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        pdf.delete()
                        previewing = false
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("↩️ برگشت به فهرست") }
            } else {
                TextButton(onClick = onBack) { Text("→ بازگشت") }
                Text("🔎 جستجوی آزمون در اینترنت", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { state.query = it },
                    label = { Text("چه آزمونی؟ (مثلاً ریاضی هشتم فصل ۲ با پاسخ)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { doSearch() },
                    enabled = state.query.isNotBlank() && busyText == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                ) { Text("🔍 جستجو", fontSize = 18.sp) }
                Spacer(Modifier.height(14.dp))

                // فایل‌هایی که بررسی شدند و وجود ندارند (یا PDF نیستند) از فهرست حذف می‌شوند
                val found = state.results?.filter { r -> state.metas[r.url]?.let { !it.loaded || it.exists } != false }
                if (found != null) {
                    val stillChecking = state.results?.any { r -> state.metas[r.url]?.loaded != true } == true
                    if (found.isEmpty() && !stillChecking) {
                        Text(
                            "فایل PDF قابل دانلود پیدا نشد. عبارت ساده‌تری بنویسید (مثلاً «ریاضی هشتم نمونه سوال»).",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                    found.forEach { r ->
                        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("📄 " + r.title, fontSize = 15.sp, maxLines = 3)
                                Spacer(Modifier.height(6.dp))
                                Text(metaLine(state.metas[r.url]), color = Color.Gray, fontSize = 12.sp, lineHeight = 18.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { fetch(r, show = true) },
                                        enabled = busyText == null,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    ) { Text("👁 نمایش") }
                                    Button(
                                        onClick = { fetch(r, show = false) },
                                        enabled = busyText == null,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                                    ) { Text("⬇️ دانلود") }
                                }
                            }
                        }
                    }
                }
            }
        }

        // پنجرهٔ «صبر کنید»: وسط صفحه دیده می‌شود و تا تمام شدن کار، لمس صفحه را می‌گیرد
        val busy = busyText
        if (busy != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center
            ) {
                Card(modifier = Modifier.padding(32.dp)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (progress in 0f..1f) CircularProgressIndicator(progress = progress) else CircularProgressIndicator()
                        Spacer(Modifier.height(14.dp))
                        Text(busy, fontSize = 16.sp)
                        if (progressNote.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(progressNote, color = Color.Gray, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { cancelBusy() }) { Text("لغو") }
                    }
                }
            }
        }
    }
}
