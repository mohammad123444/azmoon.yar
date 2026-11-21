package com.example.quizapp.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.AppSettingsState
import com.example.quizapp.Book
import com.example.quizapp.ExamPackage
import com.example.quizapp.BackupTarget
import com.example.quizapp.PersianDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** صفحهٔ تنظیمات؛ تغییرها همان لحظه اعمال و ذخیره می‌شوند */
@Composable
fun SettingsScreen(
    settings: AppSettingsState,
    onChange: (AppSettingsState) -> Unit,
    books: List<Book>,
    onRestoreReady: (List<Book>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastBackup by remember { mutableStateOf(BackupTarget.lastTime(context)) }
    var busy by remember { mutableStateOf(false) }

    // پشتیبان‌گیری روی «یک» فایل ثابت: بار اول محل فایل پرسیده می‌شود، دفعات بعد همان فایل به‌روز می‌شود
    fun finishBackup(ok: Boolean, updated: Boolean) {
        busy = false
        if (ok) {
            BackupTarget.markDone(context)
            lastBackup = BackupTarget.lastTime(context)
        }
        Toast.makeText(
            context,
            when {
                !ok -> "ذخیرهٔ پشتیبان انجام نشد"
                updated -> "پشتیبان قبلی به‌روز شد"
                else -> "پشتیبان ذخیره شد"
            },
            Toast.LENGTH_LONG
        ).show()
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                val ok = withContext(Dispatchers.IO) { BackupTarget.write(context, uri, books) }
                if (ok) BackupTarget.set(context, uri)
                finishBackup(ok, updated = false)
            }
        }
    }

    fun newBackupFile() {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        backupLauncher.launch("azmoonyar-backup-$stamp.zip")
    }

    fun runBackup() {
        val saved = BackupTarget.get(context)
        if (saved == null) {
            newBackupFile()
            return
        }
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) { BackupTarget.write(context, saved, books) }
            if (ok) {
                finishBackup(true, updated = true)
            } else {
                // فایل قبلی پاک شده یا دسترسی ندارد: فایل جدید پرسیده می‌شود
                BackupTarget.clear(context)
                busy = false
                Toast.makeText(context, "فایل پشتیبان قبلی پیدا نشد؛ محل جدید را انتخاب کنید", Toast.LENGTH_LONG).show()
                newBackupFile()
            }
        }
    }

    fun sendBackup() {
        scope.launch {
            busy = true
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    File(dir, "azmoonyar-backup.zip").also { f ->
                        f.outputStream().use { out -> ExamPackage.writeBackup(context, books, out) }
                    }
                }
                shareFile(
                    context, file, "application/zip", "ارسال فایل پشتیبان",
                    "پشتیبان کامل آزمون‌یار. فایل را با برنامهٔ آزمون‌یار باز کنید یا در تنظیمات «بازیابی از فایل پشتیبان» را بزنید."
                )
            } catch (e: Exception) {
                Toast.makeText(context, "ساخت فایل پشتیبان ناموفق بود", Toast.LENGTH_LONG).show()
            }
            busy = false
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val restored = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.let { ExamPackage.readBackup(context, it) }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (restored == null) {
                    Toast.makeText(context, "این فایل، پشتیبان معتبر نیست یا خراب است. فایل پشتیبان دیگری انتخاب کنید. آزمون‌های شما بدون تغییر ماندند.", Toast.LENGTH_LONG).show()
                } else {
                    onRestoreReady(restored)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("⚙️ تنظیمات", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        SectionTitle("اندازهٔ نوشته‌ها")
        SegmentedRow(
            options = listOf("کوچک", "متوسط", "بزرگ"),
            selected = settings.fontLevel,
            onSelect = { onChange(settings.copy(fontLevel = it)) }
        )
        Spacer(Modifier.height(20.dp))

        SectionTitle("پوستهٔ برنامه")
        SegmentedRow(
            options = listOf("مطابق سیستم", "روشن", "تاریک"),
            selected = settings.themeMode,
            onSelect = { onChange(settings.copy(themeMode = it)) }
        )
        Spacer(Modifier.height(20.dp))

        SectionTitle("آزمون")
        SwitchRow(
            title = "ترتیب تصادفی سوال‌ها",
            subtitle = "در آزمون‌های نوشتاری، سوال‌ها هر بار به‌صورت تصادفی می‌آیند",
            checked = settings.shuffle,
            onCheckedChange = { onChange(settings.copy(shuffle = it)) }
        )
        SwitchRow(
            title = "نمایش تاریخ بارگذاری سوال",
            subtitle = "زیر هر سوال، تاریخ شمسیِ ثبت آن نشان داده می‌شود",
            checked = settings.showDate,
            onCheckedChange = { onChange(settings.copy(showDate = it)) }
        )
        SwitchRow(
            title = "روشن ماندن صفحه هنگام آزمون",
            subtitle = "صفحه در حین آزمون خاموش نمی‌شود",
            checked = settings.keepScreenOn,
            onCheckedChange = { onChange(settings.copy(keepScreenOn = it)) }
        )
        Spacer(Modifier.height(20.dp))

        SectionTitle("پشتیبان‌گیری")
        // دو دکمهٔ زیر هم و تمام‌عرض: با فونت بزرگ هم نوشته از کادر بیرون نمی‌زند
        Button(
            onClick = { runBackup() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text(if (busy) "در حال پشتیبان‌گیری..." else "💾 گرفتن پشتیبان (جایگزین قبلی)") }
        if (lastBackup > 0L) {
            Spacer(Modifier.height(4.dp))
            Text("آخرین پشتیبان: ${PersianDate.format(lastBackup)}", color = Color.Gray, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { newBackupFile() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("📍 انتخاب محل تازه برای فایل پشتیبان") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { sendBackup() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("📤 ارسال فایل پشتیبان") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("♻️ بازیابی از فایل پشتیبان") }
        Spacer(Modifier.height(6.dp))
        Text(
            "پشتیبان شامل همهٔ کتاب‌ها، آزمون‌ها و فایل‌های PDF و عکس است. هنگام بازیابی، پیش از هر تغییر از شما تأیید گرفته می‌شود.",
            color = Color.Gray,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(24.dp))
        Text("آزمون‌یار — نسخهٔ ۲۰", color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.Gray, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SegmentedRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            if (i == selected) {
                Button(
                    onClick = { onSelect(i) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text(label, maxLines = 1, fontSize = 14.sp) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(i) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text(label, maxLines = 1, fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp, lineHeight = 18.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
