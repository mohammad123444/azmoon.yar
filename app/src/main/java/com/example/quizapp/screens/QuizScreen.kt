package com.example.quizapp.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Exam
import com.example.quizapp.LocalAppSettings
import com.example.quizapp.PersianDate
import com.example.quizapp.Question
import com.example.quizapp.SavedSession
import com.example.quizapp.SessionLogic
import com.example.quizapp.ui.theme.BlueSecondary
import com.example.quizapp.ui.theme.GreenDark
import com.example.quizapp.ui.theme.GreenLight
import com.example.quizapp.ui.theme.GreenPrimary

private val RedWrong = Color(0xFFC62828)
private val RedLight = Color(0xFFFFEBEE)
private val AnalysisBg = Color(0xFFE3F2FD)
private val AnalysisText = Color(0xFF0D47A1)

/**
 * هر سوال در یک صفحه. پایین: «پاسخ» ← «صحیح گفتم / اشتباه گفتم» ← «سوال بعدی».
 * تا پاسخ دیده و یکی از دو گزینه انتخاب نشود، سوال بعدی باز نمی‌شود.
 * سوال‌ها در هر شروع و شروع مجدد به‌صورت تصادفی انتخاب می‌شوند (هر سوال همراه پاسخ خودش جابه‌جا می‌شود).
 */
@Composable
fun QuizScreen(
    exam: Exam,
    count: Int,
    onFinish: () -> Unit,
    bookName: String = "",
    resume: SavedSession? = null,
    onSave: (SavedSession) -> Unit = {},
    onClear: () -> Unit = {}
) {
    var attempt by remember { mutableIntStateOf(0) }
    val settings = LocalAppSettings.current
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // ادامهٔ آزمون ذخیره‌شده فقط برای بار اول است؛ «شروع مجدد» همیشه تازه شروع می‌شود
    val startResume = if (attempt == 0) resume else null
    val order = remember(exam, count, attempt) {
        startResume?.order ?: SessionLogic.pickOrder(exam.questions.size, count, settings.shuffle)
    }
    val questions = remember(order, exam) { order.map { exam.questions[it] } }
    val total = questions.size
    val fingerprint = remember(exam) { SessionLogic.fingerprint(exam) }

    var index by remember(exam, count, attempt) { mutableIntStateOf(startResume?.index ?: 0) }
    var showAnswer by remember(exam, count, attempt) { mutableStateOf(false) }
    var finished by remember(exam, count, attempt) { mutableStateOf(startResume?.finished ?: false) }
    var reviewing by remember(exam, count, attempt) { mutableStateOf(false) }
    var analysisFor by remember(exam, count, attempt) { mutableStateOf<Int?>(null) }
    // نتیجهٔ هر سوال: true = صحیح گفتم، false = اشتباه گفتم
    val results = remember(exam, count, attempt) {
        mutableStateMapOf<Int, Boolean>().also { m -> startResume?.let { m.putAll(it.marks) } }
    }
    var discarded by remember { mutableStateOf(false) }

    // ذخیرهٔ خودکار پیشرفت (برای «ادامهٔ آزمون» در صفحهٔ اصلی)
    val snapshot = rememberUpdatedState(
        SavedSession(
            bookName = bookName,
            chapter = exam.chapter,
            title = exam.title,
            fingerprint = fingerprint,
            mode = SessionLogic.MODE_PRACTICE,
            order = order,
            index = index,
            finished = finished,
            answers = emptyMap(),
            marks = results.toMap(),
            updatedAt = 0L
        )
    )
    LaunchedEffect(index, finished, results.toMap()) {
        if (!discarded && total > 0) onSave(snapshot.value.copy(updatedAt = System.currentTimeMillis()))
    }
    DisposableEffect(Unit) {
        onDispose {
            if (!discarded && total > 0) onSave(snapshot.value.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    // دکمهٔ بازگشت سیستم در صفحهٔ مرور: برگشت به نتیجه
    BackHandler(enabled = finished && reviewing) { reviewing = false }

    if (total == 0) {
        Text("برای این آزمون سوالی وجود ندارد", modifier = Modifier.padding(24.dp))
        return
    }

    if (finished) {
        if (reviewing) {
            QuizReview(questions = questions, results = results, onBack = { reviewing = false })
        } else {
            QuizResult(
                total = total,
                correct = results.values.count { it },
                wrong = results.values.count { !it },
                onReview = { reviewing = true },
                onRetry = {
                    onClear()
                    attempt += 1
                },
                onFinish = {
                    discarded = true
                    onClear()
                    onFinish()
                }
            )
        }
        return
    }

    val current = index.coerceIn(0, total - 1)
    val question = questions[current]
    val isLast = current == total - 1
    val graded = results[current]
    val canGoNext = showAnswer && graded != null

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // بخش قابل اسکرول: سوال، پاسخ و پاسخ تحلیلی
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "${exam.title} — سوال ${current + 1} از $total",
                color = Color.Gray,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    RichText(
                        text = question.displayText,
                        color = Color.Black,
                        fontSize = 18.sp,
                        lineHeight = 28.sp
                    )
                    if (settings.showDate) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "📅 تاریخ بارگذاری: ${PersianDate.format(question.addedAt)}",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // پاسخ زیر سوال نمایش داده می‌شود
            if (showAnswer) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = GreenLight)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("پاسخ:", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        RichText(
                            text = question.answerText,
                            color = GreenDark,
                            fontSize = 18.sp,
                            lineHeight = 28.sp
                        )
                    }
                }

                // پاسخ تحلیلی (فقط اگر معلم برای این سوال نوشته باشد)
                if (question.analysis.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { analysisFor = if (analysisFor == current) null else current },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(if (analysisFor == current) "🔍 بستن پاسخ تحلیلی" else "🔍 پاسخ تحلیلی")
                    }
                    if (analysisFor == current) {
                        Spacer(Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = AnalysisBg)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    "پاسخ تحلیلی:",
                                    color = AnalysisText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                RichText(
                                    text = question.analysis,
                                    color = AnalysisText,
                                    fontSize = 17.sp,
                                    lineHeight = 27.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // دکمهٔ پاسخ
        Button(
            onClick = { showAnswer = true },
            enabled = !showAnswer,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenPrimary,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFBDBDBD),
                disabledContentColor = Color(0xFF616161)
            )
        ) {
            Text("پاسخ", fontSize = 18.sp)
        }

        // دو دکمهٔ جدید: بعد از دیدن پاسخ باز می‌شوند
        if (showAnswer) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { results[current] = true },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (graded == false) Color(0xFFE0E0E0) else GreenPrimary,
                        contentColor = if (graded == false) Color(0xFF757575) else Color.White
                    )
                ) {
                    Text("✅ صحیح گفتم", fontSize = 15.sp)
                }
                Button(
                    onClick = { results[current] = false },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (graded == true) Color(0xFFE0E0E0) else RedWrong,
                        contentColor = if (graded == true) Color(0xFF757575) else Color.White
                    )
                ) {
                    Text("❌ اشتباه گفتم", fontSize = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // سوال بعدی: فقط بعد از دیدن پاسخ و انتخاب «صحیح/اشتباه گفتم» فعال می‌شود
        Button(
            onClick = {
                if (isLast) {
                    finished = true
                } else {
                    index = current + 1
                    showAnswer = false
                    analysisFor = null
                }
            },
            enabled = canGoNext,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BlueSecondary,
                contentColor = Color.Black,
                disabledContainerColor = Color(0xFFE0E0E0),
                disabledContentColor = Color(0xFF9E9E9E)
            )
        ) {
            Text(if (isLast) "پایان آزمون" else "سوال بعدی", fontSize = 18.sp)
        }
        if (!canGoNext) {
            Spacer(Modifier.height(6.dp))
            Text(
                "برای رفتن به مرحلهٔ بعد، «پاسخ» را بزنید و بعد «صحیح گفتم» یا «اشتباه گفتم» را انتخاب کنید.",
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun QuizResult(
    total: Int,
    correct: Int,
    wrong: Int,
    onReview: () -> Unit,
    onRetry: () -> Unit,
    onFinish: () -> Unit
) {
    val percent = if (total == 0) 0 else correct * 100 / total

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("پایان آزمون 🎉", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GreenLight)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("✅ تعداد «صحیح گفتم»", color = GreenDark, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "$correct از $total",
                    color = GreenDark,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text("$percent٪", color = GreenDark, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("❌ اشتباه گفتم: $wrong", fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onReview,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlueSecondary, contentColor = Color.Black)
        ) {
            Text("📋 مرور سوال‌ها و پاسخ‌ها", fontSize = 17.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
        ) {
            Text("🔁 شروع مجدد (سوال‌های تصادفی)", fontSize = 17.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) {
            Text("بازگشت", fontSize = 18.sp)
        }
    }
}

/** مرور بعد از آزمون: سوال‌ها و پاسخ‌های «صحیح گفتم» و «اشتباه گفتم» یکجا (لیست تنبل: فقط موارد دیده‌شده ساخته می‌شوند) */
@Composable
private fun QuizReview(
    questions: List<Question>,
    results: Map<Int, Boolean>,
    onBack: () -> Unit
) {
    // ۰ = همه، ۱ = فقط صحیح، ۲ = فقط اشتباه
    var filter by remember { mutableIntStateOf(0) }
    val correctIdx = questions.indices.filter { results[it] == true }
    val wrongIdx = questions.indices.filter { results[it] == false }
    val labels = listOf(
        "همه (${questions.size})",
        "✅ صحیح (${correctIdx.size})",
        "❌ اشتباه (${wrongIdx.size})"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("→ بازگشت به نتیجه") }
        Text("مرور آزمون", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            labels.forEachIndexed { i, label ->
                if (i == filter) {
                    Button(
                        onClick = { filter = i },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                    ) { Text(label, fontSize = 13.sp, maxLines = 1) }
                } else {
                    OutlinedButton(
                        onClick = { filter = i },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                    ) { Text(label, fontSize = 13.sp, maxLines = 1) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (filter != 2) {
                item {
                    Text(
                        "✅ صحیح گفتم (${correctIdx.size})",
                        color = GreenDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (correctIdx.isEmpty()) {
                    item { Text("موردی نیست.", color = Color.Gray, fontSize = 14.sp) }
                }
                items(correctIdx) { i ->
                    ReviewCard(i + 1, questions[i], GreenLight, GreenDark)
                    Spacer(Modifier.height(8.dp))
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
            if (filter != 1) {
                item {
                    Text(
                        "❌ اشتباه گفتم (${wrongIdx.size})",
                        color = RedWrong,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (wrongIdx.isEmpty()) {
                    item { Text("موردی نیست.", color = Color.Gray, fontSize = 14.sp) }
                }
                items(wrongIdx) { i ->
                    ReviewCard(i + 1, questions[i], RedLight, RedWrong)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(number: Int, q: Question, background: Color, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("سوال $number", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            RichText(text = q.displayText, color = Color.Black, fontSize = 16.sp, lineHeight = 24.sp)
            Spacer(Modifier.height(8.dp))
            RichText(text = "پاسخ: " + q.answerText, color = accent, fontSize = 16.sp, lineHeight = 24.sp)
            if (q.analysis.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                RichText(
                    text = "🔍 تحلیل: " + q.analysis,
                    color = AnalysisText,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}
