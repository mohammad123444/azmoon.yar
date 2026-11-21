package com.example.quizapp.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.quizapp.Exam
import com.example.quizapp.ExamFiles
import com.example.quizapp.ExamPackage
import com.example.quizapp.ExamText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("exam", text))
}

private fun safeFileName(title: String): String =
    title.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_').ifEmpty { "azmoon-exam" }

/** فایل را با FileProvider در اختیار برنامهٔ انتخاب‌شده (واتساپ، تلگرام، ایتا، بله...) می‌گذارد */
private fun launchShare(context: Context, file: File, mime: String, bookName: String, exam: Exam) {
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(
            Intent.EXTRA_TEXT,
            "📚 $bookName\n📝 ${exam.title}\n\n" +
                "فایل را در برنامهٔ آزمون‌یار باز کنید، یا در برنامه: «📥 دریافت آزمون» ← «📁 انتخاب فایل آزمون»."
        )
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "ارسال آزمون"))
}

/** ارسال یک فایل دلخواه (مثل فایل پشتیبان) از طریق منوی اشتراک‌گذاری */
fun shareFile(context: Context, file: File, mime: String, title: String, text: String) {
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}


private fun mimeOf(f: File): String = when (f.extension.lowercase()) {
    "pdf" -> "application/pdf"
    "png" -> "image/png"
    "webp" -> "image/webp"
    else -> "image/jpeg"
}

/** یک یا چند فایل (PDF یا عکس) را با منوی اشتراک‌گذاری به برنامه‌های دیگر می‌فرستد */
fun shareFiles(context: Context, files: List<File>, title: String, text: String = "") {
    if (files.isEmpty()) return
    val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
    val mimes = files.map { mimeOf(it) }.distinct()
    val mime = when {
        mimes.size == 1 -> mimes[0]
        mimes.all { it.startsWith("image/") } -> "image/*"
        else -> "*/*"
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
    }
    intent.apply {
        type = mime
        if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
        val clip = ClipData.newRawUri("", uris[0])
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        clipData = clip
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, title))
    } catch (e: Exception) {
        Toast.makeText(context, "برنامه‌ای برای ارسال پیدا نشد", Toast.LENGTH_LONG).show()
    }
}

/** فایل‌های داخل حافظهٔ آزمون (PDF / عکس) را با نام خوانا کپی می‌کند و برای برنامه‌های دیگر می‌فرستد */
suspend fun shareExamFiles(context: Context, names: List<String>, baseName: String, title: String) {
    val files = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val base = safeFileName(baseName)
            names.mapIndexedNotNull { i, n ->
                val src = ExamFiles.file(context, n)
                if (!src.exists()) null
                else File(dir, base + (if (names.size > 1) "-${i + 1}" else "") + "." + src.extension)
                    .also { src.copyTo(it, overwrite = true) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    if (files.isEmpty()) {
        Toast.makeText(context, "فایلی برای ارسال پیدا نشد", Toast.LENGTH_LONG).show()
    } else {
        shareFiles(context, files, title)
    }
}

/** ارسال یک فایل PDF موقت (مثلاً فایلی که از اینترنت یا گوشی گرفته شده) */
suspend fun shareLooseFile(context: Context, src: File, baseName: String, title: String) {
    val out = withContext(Dispatchers.IO) {
        try {
            if (!src.exists()) null
            else File(File(context.cacheDir, "shared").apply { mkdirs() }, safeFileName(baseName.ifBlank { "azmoon" }) + "." + src.extension)
                .also { src.copyTo(it, overwrite = true) }
        } catch (e: Exception) {
            null
        }
    }
    if (out == null) Toast.makeText(context, "فایلی برای ارسال پیدا نشد", Toast.LENGTH_LONG).show()
    else shareFiles(context, listOf(out), title)
}

/**
 * ارسال آزمون به‌صورت فایل (برای هر اندازه):
 *  - آزمون نوشتاری: فایل JSON
 *  - آزمون فایل‌دار (PDF/عکس): یک فایل zip شامل همهٔ فایل‌ها
 * لینک فقط برای آزمون‌های نوشتاریِ کوتاه کاربرد دارد.
 */
@Composable
fun ShareExamDialog(bookName: String, exam: Exam, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val link = remember(bookName, exam) { if (exam.isFileExam) "" else ExamText.toShareLink(bookName, exam) }
    val linkIsShort = !exam.isFileExam && link.length <= ExamText.MAX_SHARE_LINK_LENGTH

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("ارسال آزمون") },
        text = {
            Column {
                Text(
                    if (exam.isFileExam) "«${exam.title}» — آزمون فایل‌دار (PDF / عکس)"
                    else "«${exam.title}» — ${exam.questions.size} سوال",
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (exam.isFileExam) {
                        "فایل‌های سوال و پاسخ داخل یک zip می‌روند و ممکن است حجیم باشد.\n" +
                            "دانش‌آموز فایل را باز می‌کند و «باز کردن با آزمون‌یار» را می‌زند؛ یا در برنامه: «دریافت آزمون ← فایل آزمون»."
                    } else {
                        "آزمون به‌صورت یک فایل ارسال می‌شود.\n" +
                            "دانش‌آموز فایل را باز می‌کند و «باز کردن با آزمون‌یار» را می‌زند؛ یا در برنامه: «دریافت آزمون ← فایل آزمون»."
                    },
                    fontSize = 13.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
                if (!exam.isFileExam && !linkIsShort) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "این آزمون برای لینک بیش از حد بزرگ است، پس فقط ارسال فایل ممکن است.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                if (exam.isFileExam) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                shareExamFiles(context, exam.questionFiles, exam.title + " - سوال", "ارسال فایل سوال‌ها")
                            }
                        }
                    ) { Text("📄 ارسال فقط PDF / عکس سوال‌ها") }
                    if (exam.answerFiles.isNotEmpty()) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                scope.launch {
                                    shareExamFiles(context, exam.answerFiles, exam.title + " - پاسخ", "ارسال فایل پاسخ‌ها")
                                }
                            }
                        ) { Text("📄 ارسال فقط PDF / عکس پاسخ‌ها") }
                    }
                }
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    Text("در حال آماده‌سازی فایل...", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Row {
                if (linkIsShort) {
                    TextButton(onClick = {
                        copyToClipboard(context, ExamText.toShareText(bookName, exam))
                        Toast.makeText(context, "متن آزمون کپی شد", Toast.LENGTH_SHORT).show()
                    }) { Text("کپی متن آزمون") }
                }
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                                    // فایل‌های ارسالیِ قبلی پاک می‌شوند
                                    dir.listFiles()?.forEach { it.delete() }
                                    val name = safeFileName(exam.title)
                                    if (exam.isFileExam) {
                                        File(dir, "$name.zip").also { f ->
                                            f.outputStream().use { out -> ExamPackage.writeExam(context, bookName, exam, out) }
                                        }
                                    } else {
                                        File(dir, "$name.json").also { f ->
                                            f.writeText(ExamText.toShareFileJson(bookName, exam), Charsets.UTF_8)
                                        }
                                    }
                                }
                                launchShare(
                                    context, file,
                                    if (exam.isFileExam) "application/zip" else "application/json",
                                    bookName, exam
                                )
                                busy = false
                                onDismiss()
                            } catch (e: Exception) {
                                busy = false
                                Toast.makeText(context, "ساخت فایل آزمون ناموفق بود", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) { Text("📤 ارسال فایل") }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("بستن") }
        }
    )
}
