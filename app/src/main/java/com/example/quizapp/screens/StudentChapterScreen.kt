package com.example.quizapp.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Book
import com.example.quizapp.Exam
import com.example.quizapp.examLabel
import com.example.quizapp.ui.theme.BlueSecondary

/**
 * انتخاب آزمون از یک فصل.
 * - زدن روی نام آزمون: شروع همان آزمون
 * - تیک زدن چند آزمونِ نوشتاری و زدن «ترکیب»: سوال‌ها از مجموع آن‌ها تصادفی انتخاب می‌شوند
 *   (آزمون‌های فایل‌دار PDF/عکس قابل ترکیب نیستند)
 */
@Composable
fun StudentChapterScreen(book: Book, chapter: String, onSelect: (Exam) -> Unit, onBack: () -> Unit) {
    var selected by remember(book.name, chapter) { mutableStateOf(setOf<String>()) }
    val exams = book.examsIn(chapter)
    val textExams = exams.filter { !it.isFileExam }
    val chosen = textExams.filter { it.key in selected }
    val chosenQuestions = chosen.sumOf { it.questions.size }
    val allSelected = textExams.isNotEmpty() && chosen.size == textExams.size

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            TextButton(onClick = onBack) { Text("→ بازگشت") }
            Text(
                if (chapter.isBlank()) "کتاب: ${book.name}" else "کتاب: ${book.name} — فصل: $chapter",
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            Text("آزمون را انتخاب کنید", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))

            if (exams.isEmpty()) {
                Text("این فصل هنوز آزمونی ندارد.", color = Color.Gray)
            } else {
                Text(
                    "روی آزمون بزنید تا شروع شود." +
                        if (textExams.size >= 2) " برای ترکیب چند آزمون، تیک بزنید." else "",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                if (textExams.size >= 2) {
                    TextButton(onClick = {
                        selected = if (allSelected) emptySet() else textExams.map { it.key }.toSet()
                    }) {
                        Text(if (allSelected) "لغو انتخاب همه" else "انتخاب همه")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            exams.forEach { exam ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (exam.isFileExam) {
                        Spacer(Modifier.width(48.dp))
                    } else {
                        Checkbox(
                            checked = exam.key in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + exam.key else selected - exam.key
                            }
                        )
                    }
                    Button(
                        onClick = { onSelect(exam) },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                    ) {
                        Text(examLabel(exam))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        // ترکیب چند آزمون
        if (chosen.size >= 2) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val title = if (chosen.size <= 3) {
                        chosen.joinToString(" + ") { it.title }
                    } else {
                        "ترکیب ${chosen.size} آزمون"
                    }
                    onSelect(Exam(title, chosen.flatMap { it.questions }, chapter = chapter))
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlueSecondary,
                    contentColor = Color.Black
                )
            ) {
                Text("🔀 ترکیب ${chosen.size} آزمون ($chosenQuestions سوال)", fontSize = 16.sp)
            }
        }
    }
}
