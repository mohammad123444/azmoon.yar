package com.example.quizapp.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Book
import com.example.quizapp.Exam
import com.example.quizapp.ExamSearch
import com.example.quizapp.ResultCodec
import com.example.quizapp.ResultLogic
import com.example.quizapp.ResultStore
import com.example.quizapp.StoredResult
import com.example.quizapp.ui.theme.GreenDark
import com.example.quizapp.ui.theme.GreenLight
import com.example.quizapp.ui.theme.GreenPrimary

private val ResRed = Color(0xFFC62828)
private val ResRedLight = Color(0xFFFFEBEE)

/**
 * پنل معلم — نتیجهٔ دانش‌آموزان در یک آزمون تستی.
 * دانش‌آموز «متن نتیجه» را می‌فرستد؛ معلم آن را اینجا وارد می‌کند، نمرهٔ خودکار را می‌بیند
 * و در صورت نیاز نمرهٔ دلخواه خودش را ثبت می‌کند.
 */
@Composable
fun TeacherResultsScreen(
    books: List<Book>,
    bookName: String,
    examKey: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val exam = books.firstOrNull { it.name == bookName }?.exams?.firstOrNull { it.key == examKey }
    var tick by remember { mutableIntStateOf(0) }
    var pasteText by remember { mutableStateOf("") }
    var pasteMessage by remember { mutableStateOf<String?>(null) }
    var pasteOk by remember { mutableStateOf(true) }
    var deleteTarget by remember { mutableStateOf<StoredResult?>(null) }

    val results = remember(tick, exam) {
        if (exam == null) emptyList()
        else ResultStore.forExam(ResultStore.loadAll(context), bookName, exam)
            .sortedBy { ExamSearch.normalize(it.result.studentName) }
    }

    if (exam == null) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(onClick = onBack) { Text("→ بازگشت") }
            Text("این آزمون پیدا نشد (شاید حذف یا جابه‌جا شده است).")
        }
        return
    }

    fun effective(s: StoredResult): Double =
        s.scoreOverride ?: ResultLogic.evaluate(s.result, exam).score20

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("→ بازگشت") }
        Text("کتاب: $bookName", color = Color.Gray)
        Text("📊 نتایج: ${exam.title}", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        if (results.isEmpty()) {
            Text("هنوز نتیجه‌ای وارد نشده است.", color = Color.Gray)
        } else {
            val avg = results.map { effective(it) }.average()
            Text(
                "${results.size} نتیجه • میانگین نمره: ${ResultLogic.scoreText(Math.round(avg * 100.0) / 100.0)} از ۲۰",
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(12.dp))

        // ---- افزودن نتیجه ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("📥 افزودن نتیجهٔ دانش‌آموز", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "پیامی که دانش‌آموز فرستاده را کپی کنید و اینجا بچسبانید. ارسال دوباره‌اش نتیجهٔ تکراری نمی‌سازد.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = cm.primaryClip
                        val pasted = if (clip != null && clip.itemCount > 0) {
                            clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
                        } else ""
                        if (pasted.isBlank()) {
                            Toast.makeText(context, "چیزی کپی نشده است", Toast.LENGTH_SHORT).show()
                        } else {
                            pasteText = pasted
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("📋 چسباندن از کلیپ‌بورد") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    label = { Text("متن نتیجه") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                val msg = pasteMessage
                if (msg != null) {
                    Text(
                        (if (pasteOk) "✅ " else "❌ ") + msg,
                        color = if (pasteOk) GreenDark else ResRed,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        val r = ResultCodec.fromText(pasteText)
                        if (r == null) {
                            pasteOk = false
                            pasteMessage = "این متن، نتیجهٔ آزمون‌یار نیست یا ناقص کپی شده. از دانش‌آموز بخواهید دوباره بفرستد."
                        } else {
                            val isNew = ResultStore.add(context, r)
                            val sameExam = ResultStore.forExam(listOf(StoredResult(r, 0L)), bookName, exam).isNotEmpty()
                            pasteOk = sameExam
                            pasteMessage = if (!sameExam) {
                                "ذخیره شد، اما مربوط به آزمون «${r.examTitle}» است، نه این آزمون."
                            } else if (isNew) {
                                "نتیجهٔ ${r.studentName} اضافه شد."
                            } else {
                                "نتیجهٔ ${r.studentName} قبلاً وارد شده بود؛ تکراری ثبت نشد."
                            }
                            if (sameExam) pasteText = ""
                            tick++
                        }
                    },
                    enabled = pasteText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                ) { Text("افزودن نتیجه", fontSize = 16.sp) }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (results.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    val lines = results.joinToString("\n") { s ->
                        "${s.result.studentName}: ${ResultLogic.scoreText(effective(s))}"
                    }
                    copyToClipboard(context, "نمرات «${exam.title}» (از ۲۰)\n$lines")
                    Toast.makeText(context, "فهرست نمرات کپی شد", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("📋 کپی فهرست نمرات") }
            Spacer(Modifier.height(12.dp))
        }

        results.forEach { s ->
            ResultCard(
                stored = s,
                exam = exam,
                onScore = { value ->
                    ResultStore.setScore(context, s.result.id, value)
                    tick++
                },
                onDelete = { deleteTarget = s }
            )
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(16.dp))
    }

    val deleting = deleteTarget
    if (deleting != null) {
        ConfirmDeleteDialog(
            title = "آیا مطمئن هستید؟",
            message = "نتیجهٔ «${deleting.result.studentName}» حذف می‌شود و قابل بازگشت نیست.",
            onConfirm = {
                ResultStore.remove(context, deleting.result.id)
                deleteTarget = null
                tick++
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun ResultCard(
    stored: StoredResult,
    exam: Exam,
    onScore: (Double?) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val r = stored.result
    val ev = ResultLogic.evaluate(r, exam)
    val auto = ev.score20
    val shown = stored.scoreOverride ?: auto
    var scoreText by remember(r.id, stored.scoreOverride, auto) { mutableStateOf(ResultLogic.scoreText(shown)) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (ev.fromExam) GreenLight else ResRedLight)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(r.studentName, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                "درست: ${ev.correct} از ${ev.total} (${ev.percent}٪) • زمان: ${ResultLogic.durationText(r.durationSec)}" +
                    (if (r.timeLimitMin > 0) " از ${r.timeLimitMin} دقیقه" else ""),
                color = Color.Black,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
            if (!ev.fromExam) {
                Text(
                    "⚠️ آزمون بعد از ارسال تغییر کرده؛ عددِ اعلام‌شدهٔ دانش‌آموز نشان داده می‌شود و جزئیات پاسخ‌ها در دسترس نیست.",
                    color = ResRed,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = scoreText,
                    onValueChange = { scoreText = it.take(6) },
                    label = { Text("نمره (از ۲۰)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val v = ResultLogic.parseScore(scoreText)
                        if (v == null) {
                            Toast.makeText(context, "نمره باید عددی بین ۰ تا ۱۰۰ باشد", Toast.LENGTH_SHORT).show()
                        } else {
                            // نمرهٔ برابر با نمرهٔ خودکار، «دستی» حساب نمی‌شود
                            onScore(if (Math.abs(v - auto) < 0.005) null else v)
                            Toast.makeText(context, "نمره ثبت شد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                ) { Text("ثبت") }
            }
            Text(
                if (stored.scoreOverride != null) "نمرهٔ دستی شما ثبت شده • نمرهٔ خودکار: ${ResultLogic.scoreText(auto)}"
                else "نمرهٔ خودکار: ${ResultLogic.scoreText(auto)}",
                color = Color.DarkGray,
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (ev.fromExam) {
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "▲ پنهان کردن پاسخ‌ها" else "▼ دیدن پاسخ‌ها") }
                }
                TextButton(onClick = onDelete) { Text("🗑 حذف", color = ResRed) }
            }

            if (expanded && ev.fromExam) {
                for (i in r.order.indices) {
                    val q = exam.questions[r.order[i]]
                    val chosen = r.chosen[i]
                    val ok = chosen == q.correct
                    val given = if (chosen in q.options.indices) "${chosen + 1}) ${q.options[chosen]}" else "بی‌پاسخ"
                    Text(
                        (if (ok) "✅ " else "❌ ") + "${i + 1}. " + q.q,
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                    )
                    Text(
                        "پاسخ دانش‌آموز: $given" + if (ok) "" else "  |  درست: ${q.answerText}",
                        color = Color.DarkGray,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}
