package com.example.quizapp.screens

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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Book
import com.example.quizapp.Exam
import com.example.quizapp.ExamSearch
import com.example.quizapp.ExamStore
import com.example.quizapp.Question
import com.example.quizapp.ui.theme.GreenPrimary

/** پیش‌نویس یک سوال تستی در ویرایشگر (فقط تا زمان ذخیره) */
private class TestDraft(q0: String, opts: List<String>, correct0: Int, val addedAt: Long) {
    var q by mutableStateOf(q0)
    val options = mutableStateListOf<String>().also { it.addAll(opts) }
    var correct by mutableIntStateOf(correct0)
}

private fun emptyDraft() = TestDraft("", listOf("", "", "", ""), 0, 0L)

/**
 * پنل معلم — آزمون تستی: سوال‌ها و گزینه‌ها را همین‌جا می‌نویسید، گزینهٔ درست را انتخاب می‌کنید
 * و زمان آزمون را (به دقیقه) تعیین می‌کنید.
 */
@Composable
fun ExamTestEditorScreen(
    books: List<Book>,
    bookName: String,
    chapter: String,
    originalKey: String?,
    onBooksChange: (List<Book>) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val book = books.firstOrNull { it.name == bookName }
    val original = book?.exams?.firstOrNull { it.key == originalKey }

    var title by rememberSaveable(originalKey) { mutableStateOf(original?.title ?: "") }
    var timeText by rememberSaveable(originalKey) {
        mutableStateOf(original?.timeLimitMin?.takeIf { it > 0 }?.toString() ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }
    val drafts = remember(originalKey) {
        mutableStateListOf<TestDraft>().also { list ->
            if (original != null && original.isTest) {
                original.questions.forEach { q ->
                    val padded = q.options + List((4 - q.options.size).coerceAtLeast(0)) { "" }
                    list.add(TestDraft(q.q, padded, q.correct, q.addedAt))
                }
            } else {
                list.add(emptyDraft())
            }
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
        Text(
            if (chapter.isBlank()) "کتاب: $bookName" else "کتاب: $bookName — فصل: $chapter",
            color = Color.Gray
        )
        Text(
            if (originalKey == null) "آزمون تستی جدید" else "ویرایش آزمون تستی",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "۱) نام و زمان آزمون   ۲) سوال و گزینه‌ها، گزینهٔ درست را با دایره انتخاب کنید   ۳) ذخیره و ارسال",
            color = Color.Gray,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("نام آزمون") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = timeText,
            onValueChange = { timeText = it.filter { ch -> ch.isDigit() }.take(3) },
            label = { Text("زمان آزمون (دقیقه)") },
            supportingText = { Text("خالی یا ۰ یعنی بدون محدودیت زمان") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        drafts.forEachIndexed { index, d ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("سوال ${index + 1}", fontSize = 16.sp)
                        if (drafts.size > 1) {
                            TextButton(onClick = { drafts.remove(d) }) { Text("🗑 حذف سوال") }
                        }
                    }
                    OutlinedTextField(
                        value = d.q,
                        onValueChange = { d.q = it },
                        label = { Text("متن سوال") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("۴ گزینه (دایرهٔ کنار گزینهٔ درست را بزنید)", color = Color.Gray, fontSize = 12.sp)
                    d.options.forEachIndexed { oi, text ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = d.correct == oi, onClick = { d.correct = oi })
                            OutlinedTextField(
                                value = text,
                                onValueChange = { d.options[oi] = it },
                                label = { Text("گزینه ${oi + 1}") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        OutlinedButton(
            onClick = { drafts.add(emptyDraft()) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("➕ سوال جدید", fontSize = 16.sp) }
        Spacer(Modifier.height(16.dp))

        val err = error
        if (err != null) {
            Text("❌ $err", color = Color(0xFFC62828), fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val newTitle = title.trim()
                val minutes = ExamSearch.normalize(timeText).ifEmpty { "0" }.toIntOrNull()
                var problem: String? = null
                val now = System.currentTimeMillis()
                val questions = mutableListOf<Question>()
                if (newTitle.isEmpty()) {
                    problem = "نام آزمون را بنویسید."
                } else if (minutes == null || minutes !in 0..600) {
                    problem = "زمان آزمون باید بین ۰ تا ۶۰۰ دقیقه باشد."
                } else {
                    for ((i, d) in drafts.withIndex()) {
                        val n = i + 1
                        val kept = d.options.mapIndexedNotNull { oi, o -> if (o.isBlank()) null else oi to o.trim() }
                        val newCorrect = kept.indexOfFirst { it.first == d.correct }
                        if (d.q.isBlank()) {
                            problem = "سوال $n: متن سوال خالی است."
                        } else if (kept.size < 4) {
                            problem = "سوال $n: هر سوال باید ۴ گزینه داشته باشد؛ هر ۴ گزینه را بنویسید."
                        } else if (newCorrect < 0) {
                            problem = "سوال $n: گزینهٔ درست را انتخاب کنید (گزینهٔ درست نباید خالی باشد)."
                        } else {
                            questions.add(
                                Question(
                                    d.q.trim(),
                                    kept[newCorrect].second,
                                    if (d.addedAt > 0L) d.addedAt else now,
                                    "",
                                    kept.map { it.second },
                                    newCorrect
                                )
                            )
                        }
                        if (problem != null) break
                    }
                }
                val newKey = Exam.keyOf(chapter, newTitle)
                if (problem == null && book?.exams?.any { it.key == newKey && it.key != originalKey } == true) {
                    problem = "آزمونی با این نام در این فصل هست."
                }
                error = problem
                if (problem == null && minutes != null) {
                    val exam = Exam(newTitle, questions, chapter = chapter, timeLimitMin = minutes)
                    onBooksChange(ExamStore.saveExam(books, bookName, originalKey, exam))
                    Toast.makeText(context, "ذخیره شد. برای ارسال، «📤 ارسال به دانش‌آموز» را بزنید.", Toast.LENGTH_LONG).show()
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
        ) { Text("💾 ذخیره", fontSize = 18.sp) }
        Spacer(Modifier.height(24.dp))
    }
}
