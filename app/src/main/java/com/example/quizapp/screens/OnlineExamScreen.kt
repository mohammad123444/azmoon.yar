package com.example.quizapp.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Book
import com.example.quizapp.Exam
import com.example.quizapp.ExamStore
import com.example.quizapp.PdfInbox
import com.example.quizapp.Question
import com.example.quizapp.WebSearch
import com.example.quizapp.ui.theme.GreenPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * تبدیل یک فایل PDF (گرفته‌شده از اینترنت، گوشی یا برنامهٔ دیگر) به آزمون:
 * صفحه‌ها را ورق می‌زنید، می‌گویید سوال‌ها کدام صفحه‌هاست و پاسخ‌ها کدام، و مکان و نام آزمون را می‌نویسید.
 */
@Composable
fun OnlineExamScreen(
    books: List<Book>,
    onBooksChange: (List<Book>) -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pdf = remember { PdfInbox.file(context) }

    var loaded by remember { mutableStateOf(false) }
    var pageCount by remember { mutableStateOf(0) }
    var previewPage by remember { mutableStateOf(1) }
    var previewBmp by remember { mutableStateOf<Bitmap?>(null) }
    var busyText by remember { mutableStateOf<String?>(null) }

    var qFrom by remember { mutableStateOf("") }
    var qTo by remember { mutableStateOf("") }
    var aFrom by remember { mutableStateOf("") }
    var aTo by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var bookName by remember { mutableStateOf("") }
    var chapterName by remember { mutableStateOf("") }
    // پاسخِ پیدا‌شده برای فایلی که فقط سوال دارد: عکس صفحه‌های پاسخنامه، یا متن ساخته‌شده با هوش مصنوعی
    var webAnswerFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var aiQuestions by remember { mutableStateOf<List<Question>>(emptyList()) }

    // فایل موقت هنگام خروج از این صفحه پاک می‌شود
    DisposableEffect(Unit) { onDispose { pdf.delete() } }

    LaunchedEffect(Unit) {
        val count = withContext(Dispatchers.IO) {
            try { if (pdf.exists()) WebSearch.pageCount(pdf) else 0 } catch (e: Throwable) { 0 }
        }
        pageCount = count
        if (count > 0) {
            // حالت پیش‌فرض: همهٔ صفحه‌ها سوال است
            qFrom = "1"
            qTo = count.toString()
            title = PdfInbox.suggestedName
        }
        loaded = true
    }

    LaunchedEffect(pageCount, previewPage) {
        if (pageCount > 0) {
            previewBmp = withContext(Dispatchers.IO) { WebSearch.preview(pdf, previewPage, 900) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("→ بازگشت") }

        if (!loaded) {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        } else if (pageCount == 0) {
            Text("فایل PDF پیدا نشد یا باز نشد. دوباره امتحان کنید.", color = Color.Gray)
        } else {
            Text("📄 این فایل $pageCount صفحه دارد", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "صفحه‌ها را ورق بزنید. اگر پاسخ‌ها آخر فایل است، به اولین صفحهٔ پاسخ بروید و دکمهٔ سبز را بزنید.",
                color = Color.Gray,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { if (previewPage > 1) previewPage -= 1 }, enabled = previewPage > 1) { Text("▶ قبلی") }
                Text("صفحهٔ $previewPage از $pageCount", fontSize = 14.sp)
                TextButton(onClick = { if (previewPage < pageCount) previewPage += 1 }, enabled = previewPage < pageCount) { Text("بعدی ◀") }
            }
            val bmp = previewBmp
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
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
            Button(
                onClick = {
                    // پاسخ‌ها از این صفحه شروع می‌شوند؛ سوال‌ها از اول تا صفحهٔ قبل
                    if (previewPage > 1) {
                        qFrom = "1"
                        qTo = (previewPage - 1).toString()
                        aFrom = previewPage.toString()
                        aTo = pageCount.toString()
                    } else {
                        Toast.makeText(context, "صفحهٔ اول نمی‌تواند پاسخ باشد", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
            ) { Text("📍 پاسخ‌ها از همین صفحه شروع می‌شود", fontSize = 15.sp) }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    qFrom = "1"
                    qTo = pageCount.toString()
                    aFrom = ""
                    aTo = ""
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("همهٔ صفحه‌ها سوال است (پاسخ ندارد)") }
            Spacer(Modifier.height(14.dp))

            Text("صفحه‌های سوال:", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("از صفحه", qFrom, { qFrom = it }, Modifier.weight(1f))
                NumberField("تا صفحه", qTo, { qTo = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("صفحه‌های پاسخ:", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("از صفحه", aFrom, { aFrom = it }, Modifier.weight(1f))
                NumberField("تا صفحه", aTo, { aTo = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))

            if (aFrom.isBlank() && aTo.isBlank()) {
                AnswerFinder(
                    questionPdf = pdf,
                    qFrom = qFrom.toIntOrNull(),
                    qTo = qTo.toIntOrNull(),
                    examTitle = title,
                    webCount = webAnswerFiles.size,
                    aiCount = aiQuestions.count { it.a.isNotBlank() },
                    onWebAnswers = {
                        webAnswerFiles = it
                        aiQuestions = emptyList()
                    },
                    onAiAnswers = {
                        aiQuestions = it
                        webAnswerFiles = emptyList()
                    },
                    onClear = {
                        webAnswerFiles = emptyList()
                        aiQuestions = emptyList()
                    }
                )
                Spacer(Modifier.height(14.dp))
            }

            Text("کجا ذخیره شود؟", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = bookName,
                onValueChange = { bookName = it },
                label = { Text("درس (مثلاً ریاضی)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (books.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    books.forEach { b -> OutlinedButton(onClick = { bookName = b.name }) { Text(b.name, fontSize = 12.sp) } }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = chapterName,
                onValueChange = { chapterName = it },
                label = { Text("فصل (مثلاً فصل ۲)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            val chapters = books.firstOrNull { it.name == bookName.trim() }?.allChapters.orEmpty()
            if (chapters.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chapters.forEach { c -> OutlinedButton(onClick = { chapterName = c }) { Text(c, fontSize = 12.sp) } }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("نام آزمون (مثلاً آزمون ۱)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val qf = qFrom.toIntOrNull()
                    val qt = qTo.toIntOrNull()
                    val af = aFrom.toIntOrNull()
                    val at = aTo.toIntOrNull()
                    val hasAnswers = aFrom.isNotBlank() || aTo.isNotBlank()
                    val book = bookName.trim()
                    val chapter = chapterName.trim()
                    val name = title.trim()
                    when {
                        qf == null || qt == null || qf < 1 || qt > pageCount || qf > qt ->
                            Toast.makeText(context, "صفحه‌های سوال را درست بنویسید (۱ تا $pageCount)", Toast.LENGTH_LONG).show()
                        hasAnswers && (af == null || at == null || af < 1 || at > pageCount || af > at) ->
                            Toast.makeText(context, "صفحه‌های پاسخ را درست بنویسید (۱ تا $pageCount)", Toast.LENGTH_LONG).show()
                        hasAnswers && !(qt!! < af!! || at!! < qf!!) ->
                            Toast.makeText(context, "صفحه‌های سوال و پاسخ نباید روی هم بیفتند", Toast.LENGTH_LONG).show()
                        book.isEmpty() || chapter.isEmpty() || name.isEmpty() ->
                            Toast.makeText(context, "درس، فصل و نام آزمون را بنویسید", Toast.LENGTH_LONG).show()
                        books.firstOrNull { it.name == book }?.exams?.any { it.key == Exam.keyOf(chapter, name) } == true ->
                            Toast.makeText(context, "آزمونی با این نام در این فصل هست", Toast.LENGTH_LONG).show()
                        else -> scope.launch {
                            busyText = "در حال ذخیرهٔ صفحه‌ها..."
                            val exam = withContext(Dispatchers.IO) {
                                try {
                                    val q = WebSearch.extractPages(context, pdf, qf!!, qt!!)
                                    val a = if (hasAnswers) WebSearch.extractPages(context, pdf, af!!, at!!) else webAnswerFiles
                                    val written = if (hasAnswers || webAnswerFiles.isNotEmpty()) emptyList() else aiQuestions
                                    Exam(name, written, q, a, chapter)
                                } catch (e: Throwable) {
                                    null
                                }
                            }
                            busyText = null
                            if (exam == null) {
                                Toast.makeText(context, "ذخیرهٔ صفحه‌ها انجام نشد", Toast.LENGTH_LONG).show()
                            } else {
                                onBooksChange(ExamStore.saveExam(books, book, null, exam))
                                Toast.makeText(context, "آزمون «$name» ذخیره شد", Toast.LENGTH_LONG).show()
                                onSaved()
                            }
                        }
                    }
                },
                enabled = busyText == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
            ) { Text("💾 ذخیرهٔ آزمون", fontSize = 18.sp) }

            val busy = busyText
            if (busy != null) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(busy, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
