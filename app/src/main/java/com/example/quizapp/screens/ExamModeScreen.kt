package com.example.quizapp.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Exam
import com.example.quizapp.LocalAppSettings
import com.example.quizapp.Question
import com.example.quizapp.ResultCodec
import com.example.quizapp.ResultLogic
import com.example.quizapp.StudentResult
import com.example.quizapp.Evaluation
import com.example.quizapp.SavedSession
import com.example.quizapp.SessionLogic
import com.example.quizapp.ui.theme.GreenDark
import com.example.quizapp.ui.theme.GreenLight
import com.example.quizapp.ui.theme.GreenPrimary
import kotlinx.coroutines.delay

private val ExamRed = Color(0xFFC62828)
private val ExamRedLight = Color(0xFFFFEBEE)
private val ExamAmberLight = Color(0xFFFFF8E1)
private val ExamBlueText = Color(0xFF0D47A1)

/**
 * حالت امتحان: پاسخ صحیح تا پایان دیده نمی‌شود.
 * دانش‌آموز پاسخ هر سوال را می‌نویسد، بین سوال‌ها جابه‌جا می‌شود و در پایان نتیجه را می‌بیند.
 * سوال‌های تشریحی را خودش با پاسخ صحیح مقایسه و «درست» یا «نادرست» می‌کند.
 * پاسخ‌ها خودکار ذخیره می‌شوند و هر سوال فقط یک پاسخ دارد.
 */
@Composable
fun ExamModeScreen(
    bookName: String,
    exam: Exam,
    count: Int,
    resume: SavedSession?,
    onSave: (SavedSession) -> Unit,
    onClear: () -> Unit,
    onFinish: () -> Unit,
    studentName: String = ""
) {
    var attempt by remember { mutableIntStateOf(0) }
    val settings = LocalAppSettings.current
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val startResume = if (attempt == 0) resume else null
    val order = remember(exam, count, attempt) {
        startResume?.order ?: SessionLogic.pickOrder(exam.questions.size, count, settings.shuffle)
    }
    val total = order.size
    val questions = remember(order, exam) { order.map { exam.questions[it] } }
    val fingerprint = remember(exam) { SessionLogic.fingerprint(exam) }

    val answers = remember(exam, count, attempt) {
        mutableStateMapOf<Int, String>().also { m -> startResume?.let { m.putAll(it.answers) } }
    }
    val marks = remember(exam, count, attempt) {
        mutableStateMapOf<Int, Boolean>().also { m -> startResume?.let { m.putAll(it.marks) } }
    }
    var index by remember(exam, count, attempt) { mutableIntStateOf(startResume?.index ?: 0) }
    var finished by remember(exam, count, attempt) { mutableStateOf(startResume?.finished ?: false) }
    var askFinish by remember { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }
    var discarded by remember { mutableStateOf(false) }

    // ---- آزمون زمان‌دار: لحظهٔ شروع ذخیره می‌شود تا خروج و ورود دوباره، ساعت را از اول نکند ----
    val limitMin = exam.timeLimitMin
    val limitMs = limitMin * 60_000L
    val name = (resume?.studentName ?: "").ifBlank { studentName }
    var startedAt by remember(exam, count, attempt) {
        mutableLongStateOf(startResume?.startedAt?.takeIf { it > 0L } ?: System.currentTimeMillis())
    }
    var finishedAt by remember(exam, count, attempt) { mutableLongStateOf(startResume?.finishedAt ?: 0L) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(finished, attempt, limitMin) {
        while (limitMin > 0 && !finished) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }
    val remainingSec: Long =
        if (limitMin > 0) ((startedAt + limitMs - nowMs) / 1000).coerceAtLeast(0L) else -1L
    val timeUp = limitMin > 0 && remainingSec == 0L && !finished

    fun finishNow() {
        marks.putAll(SessionLogic.finalizeMarks(questions, answers.toMap(), marks.toMap()))
        val end = System.currentTimeMillis()
        finishedAt = if (limitMin > 0) minOf(end, startedAt + limitMs) else end
        finished = true
    }

    // پایان زمان: آزمون خودکار تمام می‌شود (حتی اگر پنجرهٔ پرسش باز باشد)
    LaunchedEffect(timeUp) {
        if (timeUp) {
            askFinish = false
            askLeave = false
            finishNow()
        }
    }

    val snapshot = rememberUpdatedState(
        SavedSession(
            bookName = bookName,
            chapter = exam.chapter,
            title = exam.title,
            fingerprint = fingerprint,
            mode = SessionLogic.MODE_EXAM,
            order = order,
            index = index,
            finished = finished,
            answers = answers.toMap(),
            marks = marks.toMap(),
            updatedAt = 0L,
            startedAt = startedAt,
            finishedAt = finishedAt,
            studentName = name,
            timed = limitMin > 0
        )
    )

    // ذخیرهٔ خودکار (با کمی تأخیر تا با هر حرف نوشتن ذخیره نشود) و ذخیره هنگام خروج از صفحه
    LaunchedEffect(index, finished, answers.toMap(), marks.toMap()) {
        delay(500)
        if (!discarded && total > 0) onSave(snapshot.value.copy(updatedAt = System.currentTimeMillis()))
    }
    DisposableEffect(Unit) {
        onDispose {
            if (!discarded && total > 0) onSave(snapshot.value.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    BackHandler(enabled = !finished) { askLeave = true }

    if (total == 0) {
        Text("برای این آزمون سوالی وجود ندارد", modifier = Modifier.padding(24.dp))
        return
    }

    if (finished) {
        val tally = SessionLogic.tally(total, marks)
        val result: StudentResult? = if (exam.isTest) {
            StudentResult(
                fingerprint = fingerprint,
                bookName = bookName,
                chapter = exam.chapter,
                examTitle = exam.title,
                studentName = name.ifBlank { "دانش‌آموز" },
                order = order,
                chosen = order.indices.map { answers[it]?.trim()?.toIntOrNull() ?: -1 },
                durationSec = ((finishedAt - startedAt) / 1000).toInt().coerceAtLeast(0),
                finishedAt = if (finishedAt > 0L) finishedAt else System.currentTimeMillis(),
                reportedCorrect = tally.correct,
                reportedTotal = total,
                timeLimitMin = limitMin
            )
        } else null
        ExamResultView(
            questions = questions,
            answers = answers,
            marks = marks,
            result = result,
            onMark = { i, ok -> marks[i] = ok },
            onRetry = {
                onClear()
                attempt += 1
            },
            onBack = {
                discarded = true
                onClear()
                onFinish()
            }
        )
        return
    }

    val current = index.coerceIn(0, total - 1)
    val question = questions[current]
    val answeredCount = (0 until total).count { answers[it].orEmpty().isNotBlank() }

    Column(modifier = Modifier.fillMaxSize().imePadding().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${exam.title} — امتحان", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (limitMin > 0) {
                Text(
                    "⏱ " + ResultLogic.durationText(remainingSec.toInt()),
                    color = if (remainingSec < 60L) ExamRed else Color.Unspecified,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            "سوال ${current + 1} از $total  •  پاسخ داده‌اید: $answeredCount",
            fontSize = 14.sp
        )
        Spacer(Modifier.height(8.dp))

        // شمارهٔ سوال‌ها: زدن روی هر شماره، همان سوال را باز می‌کند
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(count = total) { i ->
                val answered = answers[i].orEmpty().isNotBlank()
                val colors = when {
                    i == current -> ButtonDefaults.buttonColors()
                    answered -> ButtonDefaults.buttonColors(containerColor = GreenLight, contentColor = GreenDark)
                    else -> ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0E0E0),
                        contentColor = Color(0xFF424242)
                    )
                }
                Button(
                    onClick = { index = i },
                    modifier = Modifier.size(44.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = colors
                ) { Text("${i + 1}", fontSize = 14.sp, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    RichText(text = question.q, color = Color.Black, fontSize = 18.sp, lineHeight = 28.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (question.isTest) {
                // سوال تستی: یک گزینه انتخاب می‌شود؛ زدن دوباره روی همان گزینه، انتخاب را برمی‌دارد
                question.options.forEachIndexed { oi, opt ->
                    val selected = answers[current] == oi.toString()
                    if (selected) {
                        Button(
                            onClick = { answers.remove(current) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                        ) { Text("✔ ${oi + 1}) $opt", fontSize = 16.sp) }
                    } else {
                        OutlinedButton(
                            onClick = { answers[current] = oi.toString() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("${oi + 1}) $opt", fontSize = 16.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                OutlinedTextField(
                    value = answers[current].orEmpty(),
                    onValueChange = { answers[current] = it },
                    label = { Text("پاسخ شما") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { index = current - 1 },
                enabled = current > 0,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
            ) { Text("→ قبلی", fontSize = 16.sp) }
            Button(
                onClick = { index = current + 1 },
                enabled = current < total - 1,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
            ) { Text("بعدی ←", fontSize = 16.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { askFinish = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
        ) { Text("پایان امتحان", fontSize = 17.sp) }
    }

    if (askFinish) {
        val unanswered = total - answeredCount
        AlertDialog(
            onDismissRequest = { askFinish = false },
            title = { Text("پایان امتحان؟") },
            text = {
                Text(
                    if (unanswered > 0) "$unanswered سوال را بدون پاسخ گذاشته‌اید. بعد از پایان، پاسخ‌ها تغییر نمی‌کنند."
                    else "بعد از پایان، پاسخ‌ها تغییر نمی‌کنند."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askFinish = false
                    finishNow()
                }) { Text("بله، پایان") }
            },
            dismissButton = { TextButton(onClick = { askFinish = false }) { Text("ادامهٔ امتحان") } }
        )
    }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("خروج از امتحان؟") },
            text = { Text("پاسخ‌های شما ذخیره می‌شود. از صفحهٔ اصلی می‌توانید ادامه دهید.") },
            confirmButton = {
                TextButton(onClick = {
                    askLeave = false
                    onFinish()
                }) { Text("خروج") }
            },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text("ماندن") } }
        )
    }
}

/** نتیجه و بررسی: پاسخ دانش‌آموز کنار پاسخ صحیح؛ هر سوال با دکمهٔ «درست / نادرست» قابل بررسی دستی است */
@Composable
private fun ExamResultView(
    questions: List<Question>,
    answers: Map<Int, String>,
    marks: Map<Int, Boolean>,
    result: StudentResult?,
    onMark: (Int, Boolean) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    // ۰ = همه، ۱ = بررسی‌نشده، ۲ = نادرست
    var filter by remember { mutableIntStateOf(0) }
    val total = questions.size
    val tally = SessionLogic.tally(total, marks)
    val percent = if (total == 0) 0 else tally.correct * 100 / total
    val shown = questions.indices.filter {
        when (filter) {
            1 -> marks[it] == null
            2 -> marks[it] == false
            else -> true
        }
    }
    val labels = listOf("همه ($total)", "⏳ بررسی‌نشده (${tally.pending})", "❌ نادرست (${tally.wrong})")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = GreenLight)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("پایان امتحان 🎉", color = GreenDark, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${tally.correct} از $total",
                            color = GreenDark,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("$percent٪ درست", color = GreenDark, fontSize = 16.sp)
                        if (result != null) {
                            val ev = Evaluation(tally.correct, total, true)
                            Text(
                                "نمره: ${ResultLogic.scoreText(ev.score20)} از ۲۰  •  زمان: ${ResultLogic.durationText(result.durationSec)}",
                                color = GreenDark,
                                fontSize = 16.sp
                            )
                        }
                        if (tally.pending > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "⏳ ${tally.pending} سوال هنوز بررسی نشده. پاسخ خود را با پاسخ صحیح مقایسه کنید و «درست» یا «نادرست» را بزنید.",
                                color = GreenDark,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
            if (result != null) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "نتیجه را برای معلم بفرستید تا نمره‌ات را ببیند.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { shareTextMessage(context, ResultCodec.toShareText(result), "ارسال نتیجه به معلم") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                        ) { Text("📤 ارسال نتیجه به معلم", fontSize = 16.sp) }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                com.example.quizapp.screens.copyToClipboard(context, ResultCodec.toShareText(result))
                                android.widget.Toast.makeText(context, "کپی شد؛ برای معلم بفرستید", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("📋 کپی متن نتیجه") }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEachIndexed { i, label ->
                        if (i == filter) {
                            Button(
                                onClick = { filter = i },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text(label, fontSize = 12.sp, maxLines = 1) }
                        } else {
                            OutlinedButton(
                                onClick = { filter = i },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text(label, fontSize = 12.sp, maxLines = 1) }
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item { Text("موردی نیست.", color = Color.Gray, fontSize = 14.sp) }
            }
            items(shown) { i ->
                ExamReviewCard(
                    number = i + 1,
                    question = questions[i],
                    given = givenText(questions[i], answers[i].orEmpty()),
                    mark = marks[i],
                    onMark = { ok -> onMark(i, ok) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
        ) { Text("🔁 امتحان دوباره", fontSize = 16.sp) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("بازگشت", fontSize = 16.sp) }
    }
}

@Composable
private fun ExamReviewCard(
    number: Int,
    question: Question,
    given: String,
    mark: Boolean?,
    onMark: (Boolean) -> Unit
) {
    val bg = when (mark) {
        true -> GreenLight
        false -> ExamRedLight
        null -> ExamAmberLight
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "سوال $number" + when (mark) {
                    true -> "  ✅"
                    false -> "  ❌"
                    null -> "  ⏳ بررسی نشده"
                },
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            RichText(text = question.displayText, color = Color.Black, fontSize = 16.sp, lineHeight = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text("پاسخ شما:", color = Color.DarkGray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                if (given.isBlank()) "— بی‌پاسخ —" else given,
                color = Color.Black,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
            Spacer(Modifier.height(8.dp))
            Text("پاسخ صحیح:", color = GreenDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            RichText(text = question.answerText, color = GreenDark, fontSize = 16.sp, lineHeight = 24.sp)
            if (question.analysis.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                RichText(
                    text = "🔍 تحلیل: " + question.analysis,
                    color = ExamBlueText,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            // سوال تستی خودکار تصحیح می‌شود؛ دکمهٔ «درست/نادرست» فقط برای سوال تشریحی است
            if (!question.isTest) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onMark(true) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mark == false) Color(0xFFE0E0E0) else GreenPrimary,
                        contentColor = if (mark == false) Color(0xFF757575) else Color.White
                    )
                ) { Text("✅ درست", fontSize = 15.sp) }
                Button(
                    onClick = { onMark(false) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mark == true) Color(0xFFE0E0E0) else ExamRed,
                        contentColor = if (mark == true) Color(0xFF757575) else Color.White
                    )
                ) { Text("❌ نادرست", fontSize = 15.sp) }
            }
        }
    }
}

/** پاسخ دانش‌آموز برای نمایش در مرور: برای سوال تستی «شماره) متن گزینه» */
private fun givenText(q: Question, raw: String): String {
    if (!q.isTest) return raw
    val idx = raw.trim().toIntOrNull() ?: return ""
    return if (idx in q.options.indices) "${idx + 1}) ${q.options[idx]}" else ""
}

/** ارسال یک متن با منوی اشتراک‌گذاری (واتساپ، تلگرام، ایتا، بله...) */
internal fun shareTextMessage(context: Context, text: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, title))
}
