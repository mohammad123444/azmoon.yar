package com.example.quizapp.screens

import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Book
import com.example.quizapp.ExamStore
import com.example.quizapp.ui.theme.GreenPrimary

/** پنل مدیریت — مرحلهٔ ۲: فصل‌های یک کتاب (داخل هر فصل، آزمون‌ها ساخته می‌شوند) */
@Composable
fun TeacherBookScreen(
    books: List<Book>,
    bookName: String,
    onBooksChange: (List<Book>) -> Unit,
    onOpenChapter: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val book = books.firstOrNull { it.name == bookName }
    val chapters = book?.allChapters ?: emptyList()
    val unsorted = book?.unsortedExams ?: emptyList()

    var newChapter by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("→ بازگشت") }
        Text("کتاب: $bookName", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "مرحلهٔ ۲: فصل بسازید. بعد داخل هر فصل آزمون اضافه کنید.",
            color = Color.Gray,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = newChapter,
            onValueChange = { newChapter = it },
            label = { Text("نام فصل (مثلاً فصل ۱)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val name = newChapter.trim()
                if (name in chapters) {
                    Toast.makeText(context, "این فصل قبلاً ساخته شده", Toast.LENGTH_SHORT).show()
                } else {
                    onBooksChange(ExamStore.addChapter(books, bookName, name))
                    newChapter = ""
                }
            },
            enabled = newChapter.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
        ) { Text("➕ افزودن فصل", fontSize = 16.sp) }
        Spacer(Modifier.height(20.dp))

        if (chapters.isEmpty() && unsorted.isEmpty()) {
            Text("هنوز فصلی در این کتاب نیست.", color = Color.Gray)
        } else {
            Text("فصل‌ها — برای ادامه روی فصل بزنید", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        chapters.forEach { chapter ->
            val count = book?.examsIn(chapter)?.size ?: 0
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onOpenChapter(chapter) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text("$chapter ($count آزمون)") }
                TextButton(onClick = {
                    renameTarget = chapter
                    renameText = chapter
                }) { Text("✏️") }
                TextButton(onClick = { deleteTarget = chapter }) { Text("🗑") }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onBooksChange(ExamStore.moveChapter(books, bookName, chapter, -1)) },
                    enabled = chapter != chapters.first()
                ) { Text("▲ بالا") }
                TextButton(
                    onClick = { onBooksChange(ExamStore.moveChapter(books, bookName, chapter, 1)) },
                    enabled = chapter != chapters.last()
                ) { Text("▼ پایین") }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (unsorted.isNotEmpty()) {
            OutlinedButton(
                onClick = { onOpenChapter("") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("📂 آزمون‌های بدون فصل (${unsorted.size} آزمون)") }
        }
    }

    val renaming = renameTarget
    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("ویرایش نام فصل") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val name = renameText.trim()
                    if (name.isEmpty() || (name != renaming && name in chapters)) {
                        Toast.makeText(context, "نام معتبر نیست یا تکراری است", Toast.LENGTH_SHORT).show()
                    } else {
                        onBooksChange(ExamStore.renameChapter(books, bookName, renaming, name))
                        renameTarget = null
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("انصراف") } }
        )
    }

    val deleting = deleteTarget
    if (deleting != null) {
        ConfirmDeleteDialog(
            title = "آیا مطمئن هستید؟",
            message = "فصل «$deleting» با همهٔ ${book?.examsIn(deleting)?.size ?: 0} آزمونِ آن حذف می‌شود و قابل بازگشت نیست.",
            onConfirm = {
                onBooksChange(ExamStore.deleteChapter(books, bookName, deleting))
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}
