package com.example.quizapp.screens

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Book
import com.example.quizapp.ExamPackage
import com.example.quizapp.ExamText
import com.example.quizapp.FileInspection
import com.example.quizapp.FileInspector
import com.example.quizapp.FileKind
import com.example.quizapp.ResultCodec
import com.example.quizapp.SharedExam
import com.example.quizapp.ui.theme.GreenDark
import com.example.quizapp.ui.theme.GreenLight
import com.example.quizapp.ui.theme.GreenPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ImportRed = Color(0xFFC62828)
private val ImportRedLight = Color(0xFFFFEBEE)

private const val IMPORT_FAILED =
    "وارد کردن انجام نشد. فایل را دوباره انتخاب کنید؛ اگر باز هم نشد، از فرستنده بخواهید دوباره بفرستد. آزمون‌های شما بدون تغییر ماندند."

/**
 * دریافت آزمون. چهار راه جدا:
 * فایل آزمون، فایل PDF، متن آزمون و فایل پشتیبان (+ جست‌وجو در اینترنت).
 * هر فایل ابتدا فقط بررسی می‌شود (نام، نوع، اندازه، سالم بودن) و بعد از تأیید دانش‌آموز وارد می‌شود؛
 * فایل نامعتبر هیچ چیزی را تغییر نمی‌دهد.
 */
@Composable
fun ImportScreen(
    initialText: String = "",
    onImported: (SharedExam) -> Unit,
    onBackupFound: (List<Book>) -> Unit,
    onOpenSearch: () -> Unit,
    onPdfReady: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var busy by remember { mutableStateOf(false) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var inspection by remember { mutableStateOf<FileInspection?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var showText by remember(initialText) { mutableStateOf(initialText.isNotBlank()) }
    var text by remember(initialText) { mutableStateOf(initialText) }

    // بررسی فایل انتخاب‌شده (بدون ذخیره یا کپی)
    fun inspectPicked(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            busy = true
            importError = null
            val result = withContext(Dispatchers.IO) { FileInspector.inspect(context, uri) }
            pickedUri = uri
            inspection = result
            busy = false
        }
    }

    fun clearPicked() {
        pickedUri = null
        inspection = null
        importError = null
    }

    // وارد کردن فایلِ بررسی‌شده
    fun importPicked() {
        val uri = pickedUri ?: return
        scope.launch {
            busy = true
            importError = null
            val incoming = withContext(Dispatchers.IO) {
                try {
                    ExamPackage.readIncomingFromUri(context, uri)
                } catch (e: Exception) {
                    null
                }
            }
            busy = false
            val shared = incoming?.exam
            val backup = incoming?.backup
            if (incoming == null) {
                importError = IMPORT_FAILED
            } else if (incoming.pdf) {
                clearPicked()
                onPdfReady()
            } else if (backup != null) {
                clearPicked()
                onBackupFound(backup)
            } else if (shared != null) {
                clearPicked()
                onImported(shared)
            } else {
                importError = IMPORT_FAILED
            }
        }
    }

    val examPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        inspectPicked(uri)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        inspectPicked(uri)
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        inspectPicked(uri)
    }

    // بعد از انتخاب فایل، نتیجهٔ بررسی بالای صفحه دیده می‌شود
    LaunchedEffect(inspection) {
        if (inspection != null) scroll.animateScrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("📥 دریافت آزمون", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text("چه چیزی دارید؟ یکی را انتخاب کنید.", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))

        val info = inspection
        if (info != null) {
            InspectionCard(
                info = info,
                busy = busy,
                error = importError,
                onImport = { importPicked() },
                onCancel = { clearPicked() }
            )
            Spacer(Modifier.height(14.dp))
        } else if (busy) {
            Text("در حال بررسی فایل...", color = Color.Gray)
            Spacer(Modifier.height(14.dp))
        }

        RouteCard(
            title = "📁 فایل آزمون",
            desc = "فایلی که معلم فرستاده است (zip یا json).",
            button = "انتخاب فایل آزمون",
            enabled = !busy,
            primary = true,
            onClick = { examPicker.launch(arrayOf("*/*")) }
        )
        Spacer(Modifier.height(10.dp))

        RouteCard(
            title = "📄 فایل PDF",
            desc = "PDF سوال‌ها را انتخاب کنید و صفحه‌ها را به آزمون تبدیل کنید.",
            button = "انتخاب فایل PDF",
            enabled = !busy,
            primary = false,
            onClick = { pdfPicker.launch(arrayOf("application/pdf")) }
        )
        Spacer(Modifier.height(10.dp))

        TextRoute(
            open = showText,
            onToggle = { showText = !showText },
            text = text,
            onTextChange = { text = it },
            onSave = onImported
        )
        Spacer(Modifier.height(10.dp))

        RouteCard(
            title = "♻️ فایل پشتیبان",
            desc = "پشتیبانی که قبلاً از آزمون‌یار گرفته‌اید. همهٔ کتاب‌ها و آزمون‌ها برمی‌گردد.",
            button = "انتخاب فایل پشتیبان",
            enabled = !busy,
            primary = false,
            onClick = { backupPicker.launch(arrayOf("*/*")) }
        )
        Spacer(Modifier.height(10.dp))

        RouteCard(
            title = "🔎 جست‌وجو در اینترنت",
            desc = "PDF آزمون را پیدا و دریافت کنید. فقط این راه اینترنت می‌خواهد.",
            button = "جست‌وجوی آزمون",
            enabled = !busy,
            primary = false,
            onClick = onOpenSearch
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RouteCard(
    title: String,
    desc: String,
    button: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(2.dp))
            Text(desc, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(10.dp))
            if (primary) {
                Button(
                    onClick = onClick,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                ) { Text(button, fontSize = 16.sp) }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text(button, fontSize = 16.sp) }
            }
        }
    }
}

/** نتیجهٔ بررسی فایل پیش از وارد کردن: نام، نوع، اندازه و سالم بودن */
@Composable
private fun InspectionCard(
    info: FileInspection,
    busy: Boolean,
    error: String?,
    onImport: () -> Unit,
    onCancel: () -> Unit
) {
    val ok = info.valid
    val bg = if (ok) GreenLight else ImportRedLight
    val fg = if (ok) GreenDark else ImportRed
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("فایل انتخاب‌شده", color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text("نام: ${info.name}", color = Color.Black, fontSize = 14.sp)
            Text("نوع: ${info.kindLabel}", color = Color.Black, fontSize = 14.sp)
            Text("اندازه: ${FileInspector.sizeText(info.sizeBytes)}", color = Color.Black, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            if (ok) {
                Text("✅ " + info.detail, color = fg, fontSize = 14.sp, lineHeight = 21.sp)
            } else {
                Text("❌ " + info.problem, color = fg, fontSize = 14.sp, lineHeight = 21.sp)
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text("❌ $error", color = ImportRed, fontSize = 14.sp, lineHeight = 21.sp)
            }
            Spacer(Modifier.height(12.dp))
            if (ok) {
                Button(
                    onClick = onImport,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                ) {
                    Text(
                        when {
                            busy -> "در حال وارد کردن..."
                            info.kind == FileKind.PDF -> "ادامه: انتخاب صفحه‌ها"
                            info.kind == FileKind.BACKUP -> "ادامه: بازیابی پشتیبان"
                            else -> "ذخیرهٔ آزمون"
                        },
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(if (ok) "انصراف" else "بستن") }
        }
    }
}

/** وارد کردن متن یا لینک آزمون؛ پیش از ذخیره، معتبر بودن متن نشان داده می‌شود */
@Composable
private fun TextRoute(
    open: Boolean,
    onToggle: () -> Unit,
    text: String,
    onTextChange: (String) -> Unit,
    onSave: (SharedExam) -> Unit
) {
    val context = LocalContext.current
    val parsed = remember(text) { if (text.isBlank()) null else ExamText.fromShareText(text) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("📝 متن یا لینک آزمون", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(2.dp))
            Text("متنی که معلم در پیام‌رسان فرستاده را بچسبانید.", fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text(if (open) "▲ بستن" else "وارد کردن متن", fontSize = 16.sp) }

            if (open) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = cm.primaryClip
                        val pasted = if (clip != null && clip.itemCount > 0) {
                            clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
                        } else ""
                        if (pasted.isBlank()) {
                            Toast.makeText(context, "چیزی کپی نشده است", Toast.LENGTH_SHORT).show()
                        } else {
                            onTextChange(pasted)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("📋 چسباندن از کلیپ‌بورد") }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text("متن آزمون") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (text.isNotBlank()) {
                    if (parsed != null) {
                        Text(
                            "✅ آزمون «${parsed.exam.title}» در کتاب «${parsed.book}» — ${parsed.exam.questions.size} سوال",
                            color = GreenDark,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    } else if (ResultCodec.fromText(text) != null) {
                        Text(
                            "ℹ️ این «نتیجهٔ دانش‌آموز» است، نه آزمون. در بخش 👨‍🏫 معلم ← کتاب ← فصل ← «📊 نتایج» همان آزمون، آن را وارد کنید.",
                            color = ImportRed,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    } else {
                        Text(
                            "❌ این متن آزمون معتبر نیست یا ناقص کپی شده. از معلم بخواهید «فایل آزمون» بفرستد.",
                            color = ImportRed,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = { if (parsed != null) onSave(parsed) },
                    enabled = parsed != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                ) { Text("ذخیرهٔ آزمون", fontSize = 16.sp) }
            }
        }
    }
}
