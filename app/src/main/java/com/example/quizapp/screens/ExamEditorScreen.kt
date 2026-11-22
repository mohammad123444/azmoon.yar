package com.example.quizapp.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Book
import com.example.quizapp.Exam
import com.example.quizapp.PersianDate
import com.example.quizapp.ExamSearch
import com.example.quizapp.ExamStore
import com.example.quizapp.ExamText
import com.example.quizapp.Question
import com.example.quizapp.QuestionParser
import com.example.quizapp.ui.theme.GreenPrimary

/** پنل مدیریت — مرحلهٔ ۳: افزودن یا ویرایش آزمون (نام، سوال‌ها، پاسخ‌ها) */
@Composable
fun ExamEditorScreen(
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
    var questionsText by rememberSaveable(originalKey) {
        mutableStateOf(original?.let { ExamText.numbered(it.questions.map { q -> q.displayText }) } ?: "")
    }
    // سوال گزینه‌دار: شمارهٔ گزینهٔ درست نوشته می‌شود؛ سوال تشریحی: متن پاسخ
    var answersText by rememberSaveable(originalKey) {
        mutableStateOf(
            original?.let {
                ExamText.numbered(it.questions.map { q -> if (q.isTest) (q.correct + 1).toString() else q.a })
            } ?: ""
        )
    }
    var timeText by rememberSaveable(originalKey) {
        mutableStateOf(original?.timeLimitMin?.takeIf { it > 0 }?.toString() ?: "")
    }

    var analysisText by rememberSaveable(originalKey) {
        mutableStateOf(original?.let { ExamText.numberedSparse(it.questions.map { q -> q.analysis }) } ?: "")
    }

    val questionItems = QuestionParser.parseItems(questionsText)
    val answerItems = QuestionParser.parseItems(answersText, compact = true)
    // جفت‌کردن بر اساس شماره: پاسخ n فقط به سوال n وصل می‌شود
    // سوالی که گزینه‌هایش داخل متنش باشد «چندگزینه‌ای» می‌شود؛ بقیه تشریحی‌اند (کادر پاسخ خالی هنگام آزمون)
    val built = QuestionParser.build(questionItems, answerItems)
    val items = built.items
    val pairError = built.error
    // پاسخ تحلیلی اختیاری: به شمارهٔ سوال وصل می‌شود
    val aligned = QuestionParser.alignOptional(questionItems, QuestionParser.parseItems(analysisText))
    val analysisError = aligned.error
    val canSave = title.isNotBlank() && items.isNotEmpty() && pairError == null && analysisError == null

    // تاریخ بارگذاری: سوالی که قبلاً ذخیره شده تاریخ خودش را نگه می‌دارد، سوال تازه «جدید» است
    fun savedDate(q: String): Long? = original?.questions?.firstOrNull { it.q == q }?.addedAt

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
            if (originalKey == null) "افزودن آزمون" else "ویرایش آزمون",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "۱) نام آزمون را بنویسید.\n" +
                "۲) سوال‌ها را در کادر اول و پاسخ‌ها را در کادر دوم بنویسید (هر مورد با شماره: ۱. ۲. ...).\n" +
                "• اگر زیر سوال گزینه‌ها (۱) ۲) ۳) ۴) یا الف) ب) ج) د)) نوشتید، سوال «چهارگزینه‌ای» می‌شود و در امتحان انتخابی است؛ پاسخ آن را فقط شمارهٔ گزینه بنویسید (مثلاً ۲ یا ب).\n" +
                "• سوالی که گزینه ندارد «تشریحی» است و در امتحان یک کادر خالی برای نوشتن پاسخ دارد.\n" +
                "۳) پاسخ تحلیلی اختیاری است. پیش‌نمایش پایین را ببینید و ذخیره کنید.",
            color = Color.Gray,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("نام آزمون (مثلاً درس ۱)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = timeText,
            onValueChange = { timeText = it.filter { ch -> ch.isDigit() }.take(3) },
            label = { Text("زمان آزمون (دقیقه) — اختیاری") },
            supportingText = { Text("خالی یعنی بدون محدودیت زمان") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = questionsText,
            onValueChange = { questionsText = it },
            label = { Text("همهٔ سوال‌ها (با گزینه‌ها، اگر چهارگزینه‌ای است)") },
            minLines = 6,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = answersText,
            onValueChange = { answersText = it },
            label = { Text("همهٔ پاسخ‌ها") },
            supportingText = { Text("چهارگزینه‌ای: فقط شمارهٔ گزینه (۲ یا ب). تشریحی: متن پاسخ.") },
            minLines = 6,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = analysisText,
            onValueChange = { analysisText = it },
            label = { Text("پاسخ‌های تحلیلی (اختیاری)") },
            supportingText = {
                Text("هر تحلیل را با شمارهٔ سوالش شروع کنید؛ لازم نیست برای همهٔ سوال‌ها بنویسید.")
            },
            minLines = 4,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Text(
            "تعداد سوال‌ها: ${questionItems.size} | تعداد پاسخ‌ها: ${answerItems.size}",
            color = if (pairError == null) GreenPrimary else Color(0xFFC62828),
            fontSize = 14.sp
        )
        if (pairError != null) {
            Text(pairError, color = Color(0xFFC62828), fontSize = 13.sp)
        }
        if (analysisError != null) {
            Text(analysisError, color = Color(0xFFC62828), fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))

        // پیش‌نمایش سوال‌ها (نوع هر سوال مشخص است) + تاریخ بارگذاری
        if (items.isNotEmpty()) {
            val mcq = items.count { it.options.isNotEmpty() }
            Text(
                "پیش‌نمایش: $mcq سوال چهارگزینه‌ای • ${items.size - mcq} سوال تشریحی. بررسی کنید پاسخ هر سوال درست باشد:",
                fontSize = 13.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            items.forEachIndexed { i, b ->
                val date = savedDate(b.q)
                val note = aligned.values.getOrElse(i) { "" }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        RichText("${i + 1}. ${b.q}", Color.Black, 14.sp)
                        Spacer(Modifier.height(4.dp))
                        if (b.options.isNotEmpty()) {
                            Text("🔘 چهارگزینه‌ای", fontSize = 11.sp, color = Color.Gray)
                            b.options.forEachIndexed { oi, o ->
                                val right = oi == b.correct
                                RichText(
                                    (if (right) "✔ " else "◯ ") + "${oi + 1}) $o",
                                    if (right) GreenPrimary else Color.DarkGray,
                                    14.sp
                                )
                            }
                        } else {
                            Text("✍️ تشریحی — در امتحان کادر پاسخِ خالی دارد", fontSize = 11.sp, color = Color.Gray)
                            RichText("پاسخ: ${b.a}", GreenPrimary, 14.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        if (note.isNotBlank()) {
                            RichText("🔍 تحلیل: $note", Color(0xFF0D47A1), 13.sp)
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            "📅 تاریخ بارگذاری: " + if (date != null) PersianDate.format(date) else "جدید (هنگام ذخیره ثبت می‌شود)",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
        }

        Button(
            onClick = {
                val newTitle = title.trim()
                val newKey = Exam.keyOf(chapter, newTitle)
                val duplicate = book?.exams?.any { it.key == newKey && it.key != originalKey } == true
                val minutes = ExamSearch.normalize(timeText).ifEmpty { "0" }.toIntOrNull()
                if (duplicate) {
                    Toast.makeText(context, "آزمونی با این نام در این فصل هست", Toast.LENGTH_LONG).show()
                } else if (minutes == null || minutes !in 0..600) {
                    Toast.makeText(context, "زمان آزمون باید بین ۰ تا ۶۰۰ دقیقه باشد", Toast.LENGTH_LONG).show()
                } else {
                    val now = System.currentTimeMillis()
                    val exam = Exam(
                        newTitle,
                        items.mapIndexed { i, b ->
                            Question(b.q, b.a, savedDate(b.q) ?: now, aligned.values.getOrElse(i) { "" }, b.options, b.correct)
                        },
                        chapter = chapter,
                        timeLimitMin = minutes
                    )
                    onBooksChange(ExamStore.saveExam(books, bookName, originalKey, exam))
                    Toast.makeText(context, "ذخیره شد", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenPrimary,
                contentColor = Color.White
            )
        ) {
            Text("💾 ذخیره", fontSize = 18.sp)
        }
    }
}
