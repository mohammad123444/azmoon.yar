package com.example.quizapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.quizapp.ui.theme.BlueSecondary
import com.example.quizapp.ui.theme.GreenDark
import com.example.quizapp.ui.theme.GreenLight
import com.example.quizapp.ui.theme.GreenPrimary

private val FileRed = Color(0xFFC62828)
private val FileAnalysisText = Color(0xFF0D47A1)

/**
 * آزمون با فایل سوال (PDF / عکس):
 *  - پاسخ به‌صورت فایل: یک دکمه برای رفتن به پاسخ‌نامه و برگشت (بدون دکمه‌های صحیح/اشتباه گفتم)
 *  - پاسخ نوشتاری: دکمهٔ «پاسخ» ← شمارهٔ سوال ← نمایش پاسخ همان سوال (+ صحیح/اشتباه گفتم)
 */
@Composable
fun FileExamScreen(exam: Exam, onFinish: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val hasAnswerFiles = exam.answerFiles.isNotEmpty()
    val hasWritten = exam.questions.any { it.a.isNotBlank() }

    var showingAnswers by remember { mutableStateOf(false) }
    // وضعیت جداگانهٔ نمایشگر برای سوال‌ها و پاسخ‌نامه: برگشت از یکی به دیگری، همان صفحه و همان زوم را نشان می‌دهد
    val questionViewer = remember { ViewerState() }
    val answerViewer = remember { ViewerState() }
    // پنل شماره‌های گرد (پاسخ نوشتاری) و پاسخِ بازشده
    var gridOpen by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    var finishDialog by remember { mutableStateOf(false) }
    val results = remember { mutableStateMapOf<Int, Boolean>() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = exam.title + if (showingAnswers) " — پاسخ‌نامه" else "",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            // ارسال فایلی که اکنون دیده می‌شود (PDF یا عکس) به برنامه‌های دیگر
            val currentFiles = if (showingAnswers && hasAnswerFiles) exam.answerFiles else exam.questionFiles
            run {
                TextButton(onClick = {
                    scope.launch {
                        shareExamFiles(
                            context, currentFiles,
                            exam.title + if (showingAnswers) " - پاسخ" else " - سوال",
                            "ارسال فایل"
                        )
                    }
                }) { Text("📤 ارسال") }
            }
            TextButton(onClick = onFinish) { Text("✕ خروج") }
        }

        // نمایشگر: صفحه و زومِ هر طرف (سوال / پاسخ‌نامه) جداگانه به خاطر سپرده می‌شود
        DocumentViewer(
            fileNames = if (showingAnswers) exam.answerFiles else exam.questionFiles,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = if (showingAnswers) answerViewer else questionViewer
        )

        // شماره‌های گرد: به تعداد پاسخ‌ها؛ با لمس هر شماره، پاسخش نمایش داده می‌شود
        if (gridOpen && shown == null && hasWritten && !showingAnswers) {
            run {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("شمارهٔ سوال را لمس کنید", color = Color.Gray, fontSize = 13.sp)
                        TextButton(onClick = {
                            gridOpen = false
                            shown = null
                        }) { Text("✕", color = Color.Gray) }
                    }
                    Column(modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
                        NumberGrid(
                            numbers = exam.questions.indices.filter { exam.questions[it].a.isNotBlank() }.map { it + 1 },
                            selected = shown,
                            results = results,
                            onPick = { shown = if (shown == it) null else it }
                        )
                    }
                }
            }
        }

        // پاسخ نوشتاریِ سوالِ انتخاب‌شده
        val n = shown
        if (n != null && hasWritten && gridOpen && !showingAnswers) {
            val q = exam.questions.getOrNull(n - 1)
            val answer = q?.a.orEmpty()
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = GreenLight)
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 230.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("پاسخ سوال $n", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        TextButton(onClick = { shown = null }) { Text("✕", color = GreenDark) }
                    }
                    if (answer.isBlank()) {
                        Text("برای این شماره پاسخی ثبت نشده است.", color = GreenDark, fontSize = 15.sp)
                    } else {
                        RichText(text = answer, color = GreenDark, fontSize = 17.sp, lineHeight = 26.sp)
                        val note = q?.analysis.orEmpty()
                        if (note.isNotBlank()) {
                            var showNote by remember(n) { mutableStateOf(false) }
                            Spacer(Modifier.height(8.dp))
                            if (showNote) {
                                RichText(
                                    text = "🔍 تحلیل: $note",
                                    color = FileAnalysisText,
                                    fontSize = 15.sp,
                                    lineHeight = 23.sp
                                )
                            }
                            TextButton(onClick = { showNote = !showNote }) {
                                Text(if (showNote) "▲ پنهان کردن پاسخ تحلیلی" else "🔍 پاسخ تحلیلی", color = FileAnalysisText)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        val graded = results[n]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { results[n] = true },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (graded == false) Color(0xFFE0E0E0) else GreenPrimary,
                                    contentColor = if (graded == false) Color(0xFF757575) else Color.White
                                )
                            ) { Text("✅ صحیح گفتم", fontSize = 14.sp) }
                            Button(
                                onClick = { results[n] = false },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (graded == true) Color(0xFFE0E0E0) else FileRed,
                                    contentColor = if (graded == true) Color(0xFF757575) else Color.White
                                )
                            ) { Text("❌ اشتباه گفتم", fontSize = 14.sp) }
                        }
                    }
                }
            }
        }

        // نوار دکمه‌ها
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasAnswerFiles) {
                Button(
                    onClick = { showingAnswers = !showingAnswers },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showingAnswers) BlueSecondary else GreenPrimary,
                        contentColor = if (showingAnswers) Color.Black else Color.White
                    )
                ) {
                    Text(if (showingAnswers) "📝 بازگشت به سوال‌ها" else "✅ مشاهدهٔ پاسخ‌نامه", fontSize = 15.sp)
                }
            }
            if (hasWritten) {
                if (!showingAnswers) {
                    Button(
                        onClick = {
                            // پاسخ باز است ← برگشت به شماره‌ها؛ وگرنه باز/بسته کردن پنل شماره‌ها
                            if (shown != null) {
                                shown = null
                                gridOpen = true
                            } else {
                                gridOpen = !gridOpen
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                    ) { Text("🔢 پاسخ", fontSize = 15.sp) }
                }
                OutlinedButton(
                    onClick = { finishDialog = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("🏁 پایان", fontSize = 14.sp) }
            }
        }
    }

    // خلاصهٔ پایان آزمون
    if (finishDialog) {
        val correct = results.filter { it.value }.keys.sorted()
        val wrong = results.filter { !it.value }.keys.sorted()
        AlertDialog(
            onDismissRequest = { finishDialog = false },
            title = { Text("نتیجهٔ آزمون") },
            text = {
                Column {
                    Text("✅ صحیح گفتم: ${correct.size}", fontWeight = FontWeight.Bold)
                    if (correct.isNotEmpty()) Text("سوال‌های " + correct.joinToString("، "), fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Text("❌ اشتباه گفتم: ${wrong.size}", fontWeight = FontWeight.Bold)
                    if (wrong.isNotEmpty()) Text("سوال‌های " + wrong.joinToString("، "), fontSize = 13.sp, color = Color.Gray)
                    if (correct.isEmpty() && wrong.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("هنوز برای هیچ سوالی «صحیح/اشتباه گفتم» را نزده‌اید.", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            },
            confirmButton = { TextButton(onClick = onFinish) { Text("خروج از آزمون") } },
            dismissButton = { TextButton(onClick = { finishDialog = false }) { Text("ادامهٔ آزمون") } }
        )
    }
}

/** دایره‌های شماره‌دار هم‌رنگ پاسخ؛ حلقهٔ سبز/قرمز یعنی «صحیح/اشتباه گفتم» زده شده */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberGrid(
    numbers: List<Int>,
    selected: Int?,
    results: Map<Int, Boolean>,
    onPick: (Int) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        numbers.forEach { n ->
            val isSel = selected == n
            val ring = when (results[n]) {
                true -> GreenPrimary
                false -> FileRed
                null -> GreenDark.copy(alpha = 0.35f)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSel) GreenPrimary else GreenLight)
                    .border(2.dp, ring, CircleShape)
                    .clickable { onPick(n) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    n.toString(),
                    color = if (isSel) Color.White else GreenDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
