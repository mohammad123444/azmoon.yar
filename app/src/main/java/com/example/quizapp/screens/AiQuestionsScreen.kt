package com.example.quizapp.screens

import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.AiConfig
import com.example.quizapp.AiQuestions
import com.example.quizapp.BuildConfig
import com.example.quizapp.Book
import com.example.quizapp.Exam
import com.example.quizapp.ExamSearch
import com.example.quizapp.ExamStore
import com.example.quizapp.GeminiFile
import com.example.quizapp.OnlineException
import com.example.quizapp.Question
import com.example.quizapp.ui.theme.GreenDark
import com.example.quizapp.ui.theme.GreenLight
import com.example.quizapp.ui.theme.GreenPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val AiRed = Color(0xFFC62828)

/**
 * ساخت سوال تستی از متن کتاب با هوش مصنوعی (Gemini). کلید داخل برنامه است و جایی نشان داده نمی‌شود.
 * متن را بچسبانید یا فایل .txt بدهید؛ سوال‌ها ساخته می‌شوند، شما مرور می‌کنید و به‌عنوان آزمون ذخیره می‌شود.
 */
@Composable
fun AiQuestionsScreen(
    books: List<Book>,
    bookName: String,
    chapter: String,
    onBooksChange: (List<Book>) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = BuildConfig.GEMINI_API_KEY
    remember { AiQuestions.clearLegacyConfig(context) }
    var source by remember { mutableStateOf("") }
    var countText by remember { mutableStateOf("10") }
    var title by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val generated = remember { mutableStateListOf<Question>() }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pdfPages by remember { mutableIntStateOf(0) }
    var fromText by remember { mutableStateOf("1") }
    var toText by remember { mutableStateOf("") }
    var uploaded by remember { mutableStateOf<GeminiFile?>(null) }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res: Pair<Int, String?> = withContext(Dispatchers.IO) {
                    try {
                        val dest = File(context.cacheDir, "ai_book.pdf")
                        val ins = context.contentResolver.openInputStream(uri)
                            ?: return@withContext 0 to "فایل خوانده نشد."
                        val size = ins.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                        if (size > 150_000_000L) {
                            dest.delete()
                            return@withContext 0 to "فایل خیلی بزرگ است (حداکثر ۱۵۰ مگابایت)."
                        }
                        val pages = ParcelFileDescriptor.open(dest, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                            PdfRenderer(pfd).use { r -> r.pageCount }
                        }
                        if (pages <= 0) 0 to "این PDF صفحه‌ای ندارد." else pages to null
                    } catch (e: Exception) {
                        0 to "این PDF خوانده نشد (خراب یا رمزدار است)."
                    }
                }
                if (res.second != null) {
                    message = res.second
                } else {
                    pdfFile = File(context.cacheDir, "ai_book.pdf")
                    pdfPages = res.first
                    fromText = "1"
                    toText = res.first.toString()
                    uploaded = null
                    source = ""
                    message = null
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { ins ->
                            val bytes = ins.readBytes()
                            if (bytes.size > 8_000_000) null else String(bytes, Charsets.UTF_8).removePrefix("﻿")
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (text.isNullOrBlank()) {
                    message = "فایل خوانده نشد یا خیلی بزرگ است (حداکثر ۸ مگابایت). فایل باید متنی (.txt) باشد."
                } else if (text.count { it == '\u0000' } > 0) {
                    message = "این فایل متنی نیست. برای PDF، متن را کپی کنید و در کادر بچسبانید."
                } else {
                    source = text
                    message = null
                }
            }
        }
    }

    val chunks = remember(source) { AiQuestions.splitText(source) }
    val count = countText.toIntOrNull()
    val pdfFrom = fromText.toIntOrNull()
    val pdfTo = toText.toIntOrNull()
    val pdfOk = pdfFile != null && pdfFrom != null && pdfTo != null && pdfFrom >= 1 && pdfTo >= pdfFrom && pdfTo <= pdfPages
    val sourceOk = if (pdfFile != null) pdfOk else chunks.isNotEmpty()
    val canGenerate = !busy && key.isNotBlank() && sourceOk && count != null && count in 1..100

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("→ بازگشت") }
        Text("کتاب: $bookName" + if (chapter.isBlank()) "" else " — فصل: $chapter", color = Color.Gray)
        Text("🤖 ساخت سوال با هوش مصنوعی", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "۱) متن کتاب   ۲) تعداد سوال   ۳) مرور و ذخیره",
            color = Color.Gray,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))

        if (key.isBlank()) {
            Text(
                "❌ کلید Gemini هنگام ساخت برنامه تنظیم نشده است. (راهنما: GEMINI-SETUP.md)",
                color = AiRed,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "متنی که می‌فرستید برای ساخت سوال به سرور Google (Gemini) می‌رود.",
            color = Color.Gray,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        // ---- متن کتاب ----
        Text("منبع سوال‌ها", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "کتاب PDF را انتخاب کنید (برای PDF اسکن‌شده هم کار می‌کند)، یا فایل متنی (.txt) بدهید، یا متن را بچسبانید.",
            color = Color.Gray,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("📕 انتخاب کتاب PDF") }
        Spacer(Modifier.height(6.dp))
        if (pdfFile != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("✅ PDF انتخاب شد — $pdfPages صفحه", fontWeight = FontWeight.Bold)
                    Text(
                        "از کدام صفحه تا کدام صفحه سوال ساخته شود؟ (شمارهٔ صفحه مثل نمایشگر PDF). بازهٔ کوچک‌تر دقیق‌تر و سریع‌تر است.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fromText,
                            onValueChange = { fromText = it.filter { ch -> ch.isDigit() }.take(5) },
                            label = { Text("از صفحه") },
                            singleLine = true,
                            enabled = !busy,
                            isError = !pdfOk,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = toText,
                            onValueChange = { toText = it.filter { ch -> ch.isDigit() }.take(5) },
                            label = { Text("تا صفحه") },
                            singleLine = true,
                            enabled = !busy,
                            isError = !pdfOk,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TextButton(
                        onClick = {
                            pdfFile?.delete()
                            pdfFile = null
                            pdfPages = 0
                            uploaded = null
                        },
                        enabled = !busy
                    ) { Text("🗑 حذف PDF", color = AiRed) }
                }
            }
        } else {
        OutlinedButton(
            onClick = { picker.launch(arrayOf("text/plain", "text/*")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("📄 انتخاب فایل متنی") }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            label = { Text("یا متن را اینجا بچسبانید") },
            minLines = 4,
            maxLines = 8,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )
        }
        if (pdfFile == null && chunks.isNotEmpty()) {
            val used = minOf(chunks.size, AiQuestions.MAX_CHUNKS, count ?: 1)
            Text(
                "${source.length} نویسه • ${chunks.size} بخش" +
                    if (chunks.size > used) " • از بین آن‌ها $used بخش به‌طور یکنواخت استفاده می‌شود" else "",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = countText,
                onValueChange = { countText = it.filter { ch -> ch.isDigit() }.take(3) },
                label = { Text("تعداد سوال (۱ تا ۱۰۰)") },
                singleLine = true,
                isError = count == null || count !in 1..100,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = timeText,
                onValueChange = { timeText = it.filter { ch -> ch.isDigit() }.take(3) },
                label = { Text("زمان (دقیقه)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))

        val msg = message
        if (msg != null) {
            Text("❌ $msg", color = AiRed, fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(8.dp))
        }

        if (busy) {
            Text("⏳ $progress", fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    job?.cancel()
                    busy = false
                    progress = ""
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("توقف") }
        } else {
            Button(
                onClick = {
                    val cfg = AiConfig(key)
                    val plan = AiQuestions.plan(chunks.size, count ?: 0)
                    val pdf = pdfFile
                    val pdfPlan = if (pdf != null) AiQuestions.planPdf(pdfFrom ?: 1, pdfTo ?: 1, count ?: 0) else emptyList()
                    generated.clear()
                    message = null
                    round++
                    busy = true
                    job = scope.launch {
                        var failed = 0
                        var lastError: String? = null
                        if (pdf != null) {
                            try {
                                val upf: GeminiFile = uploaded ?: run {
                                    progress = "بارگذاری کتاب برای هوش مصنوعی... (برای کتاب بزرگ چند دقیقه طول می‌کشد)"
                                    withContext(Dispatchers.IO) { AiQuestions.uploadFile(cfg, pdf, "application/pdf") }
                                }.also { uploaded = it }
                                for ((i, b) in pdfPlan.withIndex()) {
                                    if (!isActive) break
                                    progress = "ساخت سوال... مرحله ${i + 1} از ${pdfPlan.size} (${generated.size} سوال تا الان)"
                                    try {
                                        val qs = withContext(Dispatchers.IO) {
                                            AiQuestions.generateFromPdf(cfg, upf, b.third, b.first, b.second)
                                        }
                                        val seen = generated.map { it.q.trim() }.toHashSet()
                                        generated.addAll(qs.filter { seen.add(it.q.trim()) })
                                    } catch (e: OnlineException) {
                                        failed++
                                        lastError = e.userMessage
                                        if (e.userMessage.contains("کلید") || e.userMessage.contains("اعتبار") || e.userMessage.contains("نام مدل")) break
                                    } catch (e: Exception) {
                                        failed++
                                        lastError = "خطای نامشخص"
                                    }
                                }
                            } catch (e: OnlineException) {
                                uploaded = null
                                failed++
                                lastError = e.userMessage
                            } catch (e: Exception) {
                                uploaded = null
                                failed++
                                lastError = "خطای نامشخص"
                            }
                        } else for ((i, step) in plan.withIndex()) {
                            if (!isActive) break
                            progress = "ساخت سوال... بخش ${i + 1} از ${plan.size} (${generated.size} سوال تا الان)"
                            try {
                                val qs = withContext(Dispatchers.IO) { AiQuestions.generate(cfg, chunks[step.first], step.second) }
                                generated.addAll(qs)
                            } catch (e: OnlineException) {
                                failed++
                                lastError = e.userMessage
                                // خطای کلید/اعتبار/نام مدل را ادامه دادن فایده ندارد
                                if (e.userMessage.contains("کلید") || e.userMessage.contains("اعتبار") || e.userMessage.contains("نام مدل")) break
                            } catch (e: Exception) {
                                failed++
                                lastError = "خطای نامشخص"
                            }
                        }
                        if (generated.isEmpty()) {
                            message = lastError ?: "سوالی ساخته نشد. متن را بررسی کنید و دوباره امتحان کنید."
                        } else if (failed > 0) {
                            message = "$failed بخش ساخته نشد (${lastError ?: ""}). سوال‌های ساخته‌شده پایین است."
                        }
                        busy = false
                        progress = ""
                    }
                },
                enabled = canGenerate,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
            ) { Text("✨ ساخت سوال", fontSize = 18.sp) }
            if (!canGenerate && !busy) {
                Text(
                    if (key.isBlank()) "کلید Gemini در این نسخهٔ برنامه تنظیم نشده است."
                    else if (pdfFile != null && !pdfOk) "بازهٔ صفحه‌ها را درست بنویسید (از ۱ تا $pdfPages)."
                    else if (chunks.isEmpty() && pdfFile == null) "PDF کتاب را انتخاب کنید یا متن را وارد کنید."
                    else "تعداد سوال را بین ۱ تا ۱۰۰ بنویسید.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---- مرور و ذخیره ----
        if (generated.isNotEmpty() && !busy) {
            Text("✅ ${generated.size} سوال ساخته شد — مرور کنید", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GreenDark)
            Text(
                "هوش مصنوعی ممکن است اشتباه کند؛ حتماً هر سوال و گزینهٔ درست (✔) را بخوانید و سوال نادرست را حذف کنید.",
                color = AiRed,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(8.dp))
            generated.toList().forEachIndexed { i, q ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = GreenLight)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${i + 1}. ${q.q}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 22.sp)
                        q.options.forEachIndexed { oi, o ->
                            Text(
                                (if (oi == q.correct) "✔ " else "   ") + "${oi + 1}) $o",
                                color = if (oi == q.correct) GreenDark else Color.DarkGray,
                                fontWeight = if (oi == q.correct) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                        TextButton(onClick = { generated.remove(q) }) { Text("🗑 حذف این سوال", color = AiRed) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("نام آزمون") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val newTitle = title.trim()
                    val minutes = ExamSearch.normalize(timeText).ifEmpty { "0" }.toIntOrNull() ?: -1
                    val book = books.firstOrNull { it.name == bookName }
                    if (newTitle.isEmpty()) {
                        Toast.makeText(context, "نام آزمون را بنویسید", Toast.LENGTH_SHORT).show()
                    } else if (minutes !in 0..600) {
                        Toast.makeText(context, "زمان باید بین ۰ تا ۶۰۰ دقیقه باشد", Toast.LENGTH_SHORT).show()
                    } else if (book?.exams?.any { it.key == Exam.keyOf(chapter, newTitle) } == true) {
                        Toast.makeText(context, "آزمونی با این نام در این فصل هست", Toast.LENGTH_SHORT).show()
                    } else {
                        val exam = Exam(newTitle, generated.toList(), chapter = chapter, timeLimitMin = minutes)
                        onBooksChange(ExamStore.saveExam(books, bookName, null, exam))
                        Toast.makeText(context, "آزمون ذخیره شد. برای ارسال، «ارسال و نتایج» را بزنید.", Toast.LENGTH_LONG).show()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
            ) { Text("💾 ذخیره به‌عنوان آزمون", fontSize = 18.sp) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
