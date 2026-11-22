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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
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
    onAi: () -> Unit,
    onBack: () -> Unit
) {
    val exams = books.firstOrNull { it.name == bookName }?.examsIn(chapter) ?: emptyList()
    var shareTarget by remember { mutableStateOf<Exam?>(null) }
    var deleteTarget by remember { mutableStateOf<Exam?>(null) }
    var moveTarget by remember { mutableStateOf<Exam?>(null) }
    var showOtherNew by remember { mutableStateOf(false) }
    var moreFor by remember { mutableStateOf<String?>(null) }
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
            onClick = { onEdit(null) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenPrimary,
                contentColor = Color.White
            )
        ) {
            Text("➕ ساخت آزمون (سوال‌ها، پاسخ‌ها و تحلیل)", fontSize = 16.sp)
        }
        Text(
            "سوال‌های گزینه‌دار خودکار چهارگزینه‌ای می‌شوند؛ بقیه تشریحی‌اند.",
            color = Color.Gray,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onAi,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("🤖 ساخت سوال از متن کتاب (هوش مصنوعی)", fontSize = 16.sp) }
        TextButton(onClick = { showOtherNew = !showOtherNew }) {
            Text(if (showOtherNew) "▲ پنهان" else "▼ روش‌های دیگر ساخت آزمون")
        }
        if (showOtherNew) {
            OutlinedButton(
                onClick = { onEditTest(null) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("📝 آزمون تستی با فرم (هر گزینه جدا)", fontSize = 15.sp) }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { onEditFile(null) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("📎 آزمون از PDF یا عکس", fontSize = 15.sp) }
        }
        Spacer(Modifier.height(16.dp))

        if (exams.isEmpty()) {
            Text("هنوز آزمونی در این فصل نیست. با دکمهٔ بالا اولین آزمون را بسازید.", color = Color.Gray)
        }
        exams.forEach { exam ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(examLabel(exam), fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(onClick = {
                            if (exam.isFileExam) onEditFile(exam.key)
                            else onEdit(exam.key)
                        }) { Text("✏️ ویرایش") }
                        if (exam.isTest) {
                            TextButton(onClick = { onResults(exam.key) }) { Text("📤 ارسال و نتایج") }
                        } else {
                            TextButton(onClick = { shareTarget = exam }) { Text("📤 ارسال") }
                        }
                        TextButton(onClick = { moreFor = if (moreFor == exam.key) null else exam.key }) { Text("⋯ بیشتر") }
                    }
                    if (moreFor == exam.key) {
                        val pos = exams.indexOf(exam)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(
                                onClick = { onBooksChange(ExamStore.moveExam(books, bookName, exam.key, -1)) },
                                enabled = pos > 0
                            ) { Text("▲") }
                            TextButton(
                                onClick = { onBooksChange(ExamStore.moveExam(books, bookName, exam.key, 1)) },
                                enabled = pos < exams.lastIndex
                            ) { Text("▼") }
                            TextButton(onClick = { moveTarget = exam }) { Text("📁 انتقال") }
                            if (exam.isTest) {
                                TextButton(onClick = { shareTarget = exam }) { Text("📎 فایل") }
                            }
                            TextButton(onClick = { deleteTarget = exam }) { Text("🗑 حذف", color = Color(0xFFC62828)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
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
