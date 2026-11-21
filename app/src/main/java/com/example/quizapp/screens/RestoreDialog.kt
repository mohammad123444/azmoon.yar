package com.example.quizapp.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Book
import com.example.quizapp.ExamStore

private val RestoreRed = Color(0xFFC62828)

/** تعداد آزمون‌هایی از پشتیبان که در برنامه هم‌نام (در همان کتاب و فصل) دارند */
internal fun countRestoreConflicts(current: List<Book>, incoming: List<Book>): Int =
    incoming.sumOf { b ->
        val cur = current.firstOrNull { it.name == b.name }
        if (cur == null) 0 else b.exams.count { e -> cur.exams.any { it.key == e.key } }
    }

/**
 * پرسش «ادغام یا جایگزینی» پس از انتخاب (یا دریافت) یک فایل پشتیبان.
 * هر کاری که چیزی را عوض یا پاک کند، یک بار دیگر تأیید می‌گیرد.
 */
@Composable
fun RestoreBackupDialog(
    restoring: List<Book>,
    books: List<Book>,
    onBooksChange: (List<Book>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // ۰ = انتخاب، ۱ = تأیید ادغام (وقتی آزمون هم‌نام هست)، ۲ = تأیید جایگزینی
    var stage by remember { mutableIntStateOf(0) }
    val backupExams = restoring.sumOf { it.exams.size }
    val currentExams = books.sumOf { it.exams.size }
    val conflicts = countRestoreConflicts(books, restoring)

    fun doMerge() {
        onBooksChange(ExamStore.merge(books, restoring))
        onDismiss()
        Toast.makeText(context, "پشتیبان ادغام شد", Toast.LENGTH_SHORT).show()
    }

    fun doReplace() {
        onBooksChange(restoring)
        onDismiss()
        Toast.makeText(context, "پشتیبان بازیابی شد", Toast.LENGTH_SHORT).show()
    }

    if (stage == 1) {
        AlertDialog(
            onDismissRequest = { stage = 0 },
            title = { Text("آزمون هم‌نام عوض می‌شود") },
            text = { Text("$conflicts آزمون از پشتیبان، هم‌نامِ آزمون‌های فعلی است و جای آن‌ها را می‌گیرد. بقیهٔ داده‌ها می‌ماند. ادامه می‌دهید؟") },
            confirmButton = {
                TextButton(onClick = { doMerge() }) { Text("بله، ادغام شود") }
            },
            dismissButton = { TextButton(onClick = { stage = 0 }) { Text("برگشت") } }
        )
        return
    }

    if (stage == 2) {
        AlertDialog(
            onDismissRequest = { stage = 0 },
            title = { Text("همه‌چیز پاک و عوض شود؟") },
            text = {
                Text("همهٔ ${books.size} کتاب و $currentExams آزمونِ فعلی پاک می‌شود و فقط محتوای پشتیبان می‌ماند. این کار قابل بازگشت نیست.")
            },
            confirmButton = {
                TextButton(
                    onClick = { doReplace() },
                    colors = ButtonDefaults.textButtonColors(contentColor = RestoreRed)
                ) { Text("بله، جایگزین شود") }
            },
            dismissButton = { TextButton(onClick = { stage = 0 }) { Text("برگشت") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بازیابی پشتیبان") },
        text = {
            Column {
                Text("این پشتیبان ${restoring.size} کتاب و $backupExams آزمون دارد.")
                Text("الان در برنامه: ${books.size} کتاب و $currentExams آزمون.", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { if (conflicts > 0) stage = 1 else doMerge() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("ادغام (پیشنهادی)") }
                Text(
                    "داده‌های فعلی می‌ماند و موارد پشتیبان اضافه می‌شود.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { if (books.isEmpty()) doReplace() else stage = 2 },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("جایگزینی همه‌چیز") }
                Text(
                    "داده‌های فعلی پاک و با پشتیبان عوض می‌شود.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("انصراف") }
            }
        },
        confirmButton = {}
    )
}
