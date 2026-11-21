package com.example.quizapp.screens

import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.ExamFiles
import com.example.quizapp.Question
import com.example.quizapp.QuestionParser
import com.example.quizapp.WebSearch
import com.example.quizapp.ui.theme.GreenLight
import com.example.quizapp.ui.theme.GreenPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * وقتی فایل فقط سوال دارد، پاسخ را از بیرون از برنامه تهیه می‌کنید:
 *  ۱) «ارسال PDF سوال‌ها»: فایل سوال‌ها را با منوی اشتراک‌گذاری به هر برنامه‌ای می‌فرستید
 *  ۲) پاسخ را برمی‌گردانید و یکی از دو راه را می‌زنید:
 *     - نوشتن/چسباندن متن پاسخ‌ها (هر پاسخ با شمارهٔ سوال شروع شود)
 *     - انتخاب عکس یا PDF پاسخ
 */
@Composable
fun AnswerFinder(
    questionPdf: File,
    qFrom: Int?,
    qTo: Int?,
    examTitle: String,
    webCount: Int,
    aiCount: Int,
    onWebAnswers: (List<String>) -> Unit,
    onAiAnswers: (List<Question>) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 0 بسته، 1 پاسخ به‌صورت متن، 2 پاسخ به‌صورت عکس یا PDF
    var mode by remember { mutableStateOf(0) }
    var busyText by remember { mutableStateOf<String?>(null) }
    var answerText by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busyText = "در حال ذخیرهٔ فایل پاسخ..."
            val names = withContext(Dispatchers.IO) {
                uris.mapNotNull { ExamFiles.importFromUri(context, it) }
            }
            busyText = null
            if (names.isEmpty()) {
                Toast.makeText(context, "فایل خوانده نشد؛ عکس یا PDF انتخاب کنید", Toast.LENGTH_LONG).show()
            } else {
                if (names.size < uris.size) {
                    Toast.makeText(context, "بعضی فایل‌ها خوانده نشدند", Toast.LENGTH_LONG).show()
                }
                onWebAnswers(names)
                answerText = ""
                mode = 0
            }
        }
    }

    /** PDF سوال‌ها را می‌سازد و منوی اشتراک‌گذاری را باز می‌کند */
    fun sendQuestions() {
        if (busyText != null) return
        val from = qFrom
        val to = qTo
        if (from == null || to == null || from < 1 || from > to) {
            Toast.makeText(context, "اول صفحه‌های سوال را بالا درست بنویسید", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            busyText = "در حال آماده‌سازی PDF سوال‌ها..."
            val out = withContext(Dispatchers.IO) {
                try {
                    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                    val target = File(dir, "questions.pdf")
                    val total = WebSearch.pageCount(questionPdf)
                    if (from == 1 && to >= total) {
                        questionPdf.copyTo(target, overwrite = true)
                    } else {
                        val doc = PdfDocument()
                        try {
                            for ((i, p) in (from..minOf(to, total)).withIndex()) {
                                val bmp = WebSearch.preview(questionPdf, p, 1240) ?: throw IllegalStateException()
                                val info = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create()
                                val page = doc.startPage(info)
                                page.canvas.drawBitmap(bmp, 0f, 0f, null)
                                doc.finishPage(page)
                                bmp.recycle()
                            }
                            target.outputStream().use { doc.writeTo(it) }
                        } finally {
                            doc.close()
                        }
                    }
                    target
                } catch (e: Throwable) {
                    null
                }
            }
            busyText = null
            if (out == null) {
                Toast.makeText(context, "ساخت PDF انجام نشد", Toast.LENGTH_LONG).show()
            } else {
                try {
                    shareFile(
                        context, out, "application/pdf", "ارسال PDF سوال‌ها",
                        if (examTitle.isBlank()) "سوال‌ها" else examTitle.trim()
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "برنامه‌ای برای ارسال پیدا نشد", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // تحلیل زندهٔ متن: همان قانونی که ویرایشگر آزمون دارد (شماره‌ها به شمارهٔ سوال وصل می‌شوند)
    val items = QuestionParser.parseItems(answerText, compact = true)
    val keyed = items.mapIndexed { i, it -> (it.number ?: (i + 1)) to it.text }
    val maxN = keyed.maxOfOrNull { it.first } ?: 0
    val missing = if (maxN in 1..500) (1..maxN).filter { n -> keyed.none { it.first == n } } else emptyList()

    fun saveText() {
        if (keyed.isEmpty()) {
            Toast.makeText(context, "پاسخی در متن پیدا نشد", Toast.LENGTH_LONG).show()
            return
        }
        if (maxN > 500) {
            Toast.makeText(context, "شمارهٔ سوال نباید از ۵۰۰ بیشتر باشد", Toast.LENGTH_LONG).show()
            return
        }
        val byN = keyed.toMap()
        val now = System.currentTimeMillis()
        onAiAnswers((1..maxN).map { n -> Question("", byN[n].orEmpty(), now, "") })
        Toast.makeText(context, "پاسخ ${keyed.size} سوال اضافه شد", Toast.LENGTH_SHORT).show()
        answerText = ""
        mode = 0
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GreenLight)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("🧩 فایل پاسخ ندارد؟", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1B5E20))
            Spacer(Modifier.height(4.dp))
            Text(
                "PDF سوال‌ها را بفرستید، جای دیگری پاسخش را تهیه کنید و همین‌جا اضافه کنید.",
                fontSize = 13.sp,
                color = Color.DarkGray,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { sendQuestions() },
                enabled = busyText == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
            ) { Text("📤 ارسال PDF سوال‌ها", fontSize = 14.sp) }

            if (webCount > 0 || aiCount > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (webCount > 0) "✅ پاسخ اضافه شد ($webCount فایل)"
                        else "✅ پاسخ $aiCount سوال اضافه شد",
                        fontSize = 14.sp,
                        color = Color(0xFF1B5E20),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onClear() }) { Text("حذف") }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("پاسخ را اضافه کنید:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { mode = if (mode == 1) 0 else 1 },
                    enabled = busyText == null,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("✍️ به‌صورت متن", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { mode = if (mode == 2) 0 else 2 },
                    enabled = busyText == null,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("🖼 عکس یا PDF", fontSize = 13.sp) }
            }

            if (mode == 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "متن پاسخ‌ها را بنویسید یا بچسبانید. هر پاسخ با شمارهٔ سوال شروع شود (در خط جدید، یا فشرده مثل «۱-ب ۲-ج ۳-الف»):\n۱) ب\n۲) الف\n۳) x = ۴",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { answerText = it },
                    label = { Text("متن پاسخ‌ها") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                if (answerText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (keyed.isEmpty()) "پاسخی شناسایی نشد؛ هر پاسخ را با شمارهٔ سوال شروع کنید."
                        else "✅ ${keyed.size} پاسخ شناسایی شد (تا سوال $maxN)" +
                            if (missing.isNotEmpty() && missing.size <= 30) "\n⚠️ بدون پاسخ: " + missing.joinToString("، ")
                            else if (missing.size > 30) "\n⚠️ ${missing.size} سوال بدون پاسخ" else "",
                        fontSize = 12.sp,
                        color = if (keyed.isEmpty()) Color(0xFFC62828) else Color(0xFF1B5E20),
                        lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { saveText() },
                    enabled = busyText == null && answerText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                ) { Text("✅ افزودن پاسخ‌ها") }
            }

            if (mode == 2) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "عکس (یک یا چند تا) یا فایل PDF پاسخ را انتخاب کنید.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { filePicker.launch(arrayOf("image/*", "application/pdf")) },
                    enabled = busyText == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                ) { Text("📁 انتخاب عکس یا PDF پاسخ") }
            }

            val busy = busyText
            if (busy != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(busy, fontSize = 14.sp)
                }
            }
        }
    }
}
