package com.example.quizapp.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Book
import com.example.quizapp.Exam
import com.example.quizapp.ExamStore
import com.example.quizapp.examLabel
import com.example.quizapp.ui.theme.GreenPrimary

/** پنل مدیریت — مرحلهٔ ۳: آزمون‌های یک فصل */
@Composable
fun TeacherChapterScreen(
    books: List<Book>,
    bookName: String,
    chapter: String,
    onBooksChange: (List<Book>) -> Unit,
    onEdit: (String?) -> Unit,
    onEditFile: (String?) -> Unit,
    onEditTest: (String?) -> Unit,
    onResults: (String) -> Unit,
    onBack: () -> Unit
) {
    val exams = books.firstOrNull { it.name == bookName }?.examsIn(chapter) ?: emptyList()
    var shareTarget by remember { mutableStateOf<Exam?>(null) }
    var deleteTarget by remember { mutableStateOf<Exam?>(null) }
    var moveTarget by remember { mutableStateOf<Exam?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("→ بازگشت") }
        Text("کتاب: $bookName", color = Color.Gray)
        Text(
            if (chapter.isBlank()) "آزمون‌های بدون فصل" else "فصل: $chapter",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onEditTest(null) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenPrimary,
                contentColor = Color.White
            )
        ) {
            Text("📝 آزمون تستی زمان‌دار (برای ارسال به دانش‌آموز)", fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { onEdit(null) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Text("➕ آزمون جدید (سوال و پاسخ متنی)", fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { onEditFile(null) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Text("📎 آزمون جدید از PDF یا عکس", fontSize = 16.sp)
        }
        Spacer(Modifier.height(20.dp))

        if (exams.isEmpty()) {
            Text("هنوز آزمونی در این فصل نیست.", color = Color.Gray)
        }
        exams.forEach { exam ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(examLabel(exam), fontSize = 16.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        if (exam.isFileExam) onEditFile(exam.key)
                        else if (exam.isTest) onEditTest(exam.key)
                        else onEdit(exam.key)
                    }) { Text("✏️ ویرایش") }
                    TextButton(onClick = { shareTarget = exam }) { Text("📤 ارسال به دانش‌آموز") }
                    TextButton(onClick = { deleteTarget = exam }) { Text("🗑 حذف") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val pos = exams.indexOf(exam)
                    TextButton(
                        onClick = { onBooksChange(ExamStore.moveExam(books, bookName, exam.key, -1)) },
                        enabled = pos > 0
                    ) { Text("▲ بالا") }
                    TextButton(
                        onClick = { onBooksChange(ExamStore.moveExam(books, bookName, exam.key, 1)) },
                        enabled = pos < exams.lastIndex
                    ) { Text("▼ پایین") }
                    TextButton(onClick = { moveTarget = exam }) { Text("📁 انتقال") }
                    if (exam.isTest) {
                        TextButton(onClick = { onResults(exam.key) }) { Text("📊 نتایج") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    val sharing = shareTarget
    if (sharing != null) {
        ShareExamDialog(bookName = bookName, exam = sharing, onDismiss = { shareTarget = null })
    }

    val deleting = deleteTarget
    if (deleting != null) {
        ConfirmDeleteDialog(
            title = "آیا مطمئن هستید؟",
            message = "آزمون «${deleting.title}» با همهٔ سوال‌ها و پاسخ‌هایش حذف می‌شود و قابل بازگشت نیست.",
            onConfirm = {
                onBooksChange(ExamStore.deleteExam(books, bookName, deleting.key))
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    val moving = moveTarget
    if (moving != null) {
        // مقصدها: همهٔ فصل‌های همهٔ کتاب‌ها، به‌جز جایی که آزمون هم‌اکنون در آن است
        val destinations = books.flatMap { b ->
            b.allChapters.filter { !(b.name == bookName && it == chapter) }.map { b.name to it }
        }
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("انتقال «${moving.title}» به...") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (destinations.isEmpty()) {
                        Text("فصل دیگری برای انتقال وجود ندارد. ابتدا یک فصل بسازید.", color = Color.Gray)
                    }
                    destinations.forEach { (toBook, toChapter) ->
                        OutlinedButton(
                            onClick = {
                                val result = ExamStore.moveExamTo(books, bookName, moving.key, toBook, toChapter)
                                if (result == null) {
                                    Toast.makeText(context, "در مقصد آزمونی با همین نام هست", Toast.LENGTH_LONG).show()
                                } else {
                                    onBooksChange(result)
                                    Toast.makeText(context, "منتقل شد", Toast.LENGTH_SHORT).show()
                                    moveTarget = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text(if (toBook == bookName) toChapter else "$toBook ← $toChapter") }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("انصراف") } }
        )
    }
}
