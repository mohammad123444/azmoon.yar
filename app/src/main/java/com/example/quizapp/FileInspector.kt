package com.example.quizapp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

enum class FileKind { PDF, EXAM, BACKUP, INVALID }

/**
 * نتیجهٔ بررسی یک فایل پیش از وارد کردن.
 * بررسی هیچ چیزی را ذخیره یا کپی نمی‌کند؛ پس فایل نامعتبر به داده‌های موجود آسیبی نمی‌زند.
 */
class FileInspection(
    val name: String,
    val sizeBytes: Long,
    val kind: FileKind,
    /** خلاصهٔ محتوا برای فایل معتبر (مثلاً «آزمون «ریاضی» — ۱۲ سوال») */
    val detail: String = "",
    /** علت و راه‌حل ساده برای فایل نامعتبر */
    val problem: String = ""
) {
    val valid: Boolean get() = kind != FileKind.INVALID

    val kindLabel: String
        get() = when (kind) {
            FileKind.PDF -> "فایل PDF"
            FileKind.EXAM -> "فایل آزمون"
            FileKind.BACKUP -> "فایل پشتیبان"
            FileKind.INVALID -> "نامعتبر"
        }
}

object FileInspector {
    /** بالاترین حجمی که برای خواندنِ داخل حافظه می‌پذیریم (JSON آزمون یا پشتیبان) */
    private const val MAX_JSON_BYTES = 40L * 1024 * 1024

    fun sizeText(bytes: Long): String = when {
        bytes <= 0L -> "نامشخص"
        bytes < 1024L -> "$bytes بایت"
        bytes < 1024L * 1024L -> "${bytes / 1024} کیلوبایت"
        else -> String.format(java.util.Locale.US, "%.1f مگابایت", bytes / (1024.0 * 1024.0))
    }

    private fun queryName(context: Context, uri: Uri): String? {
        val fromProvider: String? = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        }
        return fromProvider ?: uri.lastPathSegment
    }

    private fun querySize(context: Context, uri: Uri): Long = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L } ?: -1L
    } catch (e: Exception) {
        -1L
    }

    private fun bad(name: String, size: Long, problem: String) =
        FileInspection(name, size, FileKind.INVALID, problem = problem)

    fun inspect(context: Context, uri: Uri): FileInspection {
        val name = queryName(context, uri)?.takeIf { it.isNotBlank() } ?: "فایل بدون نام"
        val size = querySize(context, uri)
        try {
            val raw = context.contentResolver.openInputStream(uri)
                ?: return bad(name, size, "فایل باز نشد. آن را از «حافظهٔ گوشی» یا «Download» دوباره انتخاب کنید.")
            return BufferedInputStream(raw).use { input ->
                input.mark(16)
                val head = ByteArray(4)
                val n = input.read(head)
                input.reset()
                if (n <= 0) {
                    return@use bad(name, size, "فایل خالی است. از فرستنده بخواهید دوباره بفرستد.")
                }
                val isPdf = n == 4 && head[0] == 0x25.toByte() && head[1] == 0x50.toByte() &&
                    head[2] == 0x44.toByte() && head[3] == 0x46.toByte()
                val isZip = n >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
                when {
                    isPdf -> {
                        FileInspection(name, size, FileKind.PDF, detail = "فایل PDF سالم است و برای ساخت آزمون آماده است.")
                    }
                    isZip -> inspectZip(name, size, input)
                    else -> inspectText(name, size, input)
                }
            }
        } catch (e: OutOfMemoryError) {
            return bad(name, size, "فایل برای این گوشی زیادی بزرگ است. فایل کوچک‌تری انتخاب کنید.")
        } catch (e: Exception) {
            return bad(name, size, "خواندن فایل ممکن نشد. دوباره انتخاب کنید یا فایل را دوباره دریافت کنید.")
        }
    }

    private fun inspectZip(name: String, size: Long, input: BufferedInputStream): FileInspection {
        var examJson: String? = null
        var backupJson: String? = null
        var fileCount = 0
        val present = HashSet<String>()
        try {
            val zip = ZipInputStream(input)
            var entry = zip.nextEntry
            while (entry != null) {
                val en = entry.name
                if (en == "exam.json" || en == "backup.json") {
                    val bytes = zip.readBytes()
                    if (bytes.size > MAX_JSON_BYTES) {
                        return bad(name, size, "فایل خیلی بزرگ است و باز نمی‌شود.")
                    }
                    if (en == "exam.json") examJson = bytes.toString(Charsets.UTF_8)
                    else backupJson = bytes.toString(Charsets.UTF_8)
                } else if (en.startsWith("files/") && !entry.isDirectory) {
                    // محتوا کامل خوانده می‌شود تا فایلِ نیمه‌دانلودشده همین‌جا شناخته شود
                    val buf = ByteArray(8192)
                    while (zip.read(buf) != -1) { /* فقط خواندن */ }
                    fileCount++
                    present.add(en.removePrefix("files/").substringAfterLast('/'))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        } catch (e: Exception) {
            return bad(name, size, "فایل خراب است یا کامل دانلود نشده. دوباره دریافت کنید یا از فرستنده بخواهید دوباره بفرستد.")
        }
        val backup = backupJson
        if (backup != null) {
            val books = ExamStore.fromBackupJson(backup)
                ?: return bad(name, size, "فایل پشتیبان خراب است. فایل پشتیبان دیگری را امتحان کنید.")
            val exams = books.sumOf { it.exams.size }
            return FileInspection(name, size, FileKind.BACKUP, detail = "پشتیبان کامل: ${books.size} کتاب، $exams آزمون، $fileCount فایل PDF/عکس")
        }
        val examText = examJson
            ?: return bad(name, size, "این فایل، فایل آزمون‌یار نیست. فایل آزمون یا پشتیبانِ آزمون‌یار را انتخاب کنید.")
        return try {
            val obj = JSONObject(examText)
            val q = obj.optJSONArray("q")?.length() ?: 0
            val qf = obj.optJSONArray("qf")?.length() ?: 0
            val wanted = ArrayList<String>()
            for (key in listOf("qf", "af")) {
                val arr = obj.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) wanted.add(arr.getString(i))
            }
            if (wanted.any { it !in present }) {
                bad(name, size, "بعضی از فایل‌های PDF/عکسِ این آزمون داخل بسته نیست؛ احتمالاً ناقص دانلود شده. دوباره دریافت کنید.")
            } else if (q == 0 && qf == 0) {
                bad(name, size, "این آزمون هیچ سوالی ندارد. از معلم بخواهید آزمون را دوباره بفرستد.")
            } else {
                val title = obj.optString("t", "").trim().ifEmpty { "آزمون دریافتی" }
                val book = obj.optString("b", "").trim().ifEmpty { "کتاب دریافتی" }
                val what = if (qf > 0) "$qf فایل سوال" else "$q سوال"
                FileInspection(name, size, FileKind.EXAM, detail = "آزمون «$title» در کتاب «$book» — $what")
            }
        } catch (e: Exception) {
            bad(name, size, "محتوای آزمون خراب است. از معلم بخواهید آزمون را دوباره بفرستد.")
        }
    }

    private fun inspectText(name: String, size: Long, input: BufferedInputStream): FileInspection {
        if (size > MAX_JSON_BYTES) return bad(name, size, "این فایل آزمون نیست (خیلی بزرگ است).")
        val text = input.readBytes().toString(Charsets.UTF_8)
        val exam = ExamText.fromShareText(text)
        if (exam != null) {
            return FileInspection(
                name, size, FileKind.EXAM,
                detail = "آزمون «${exam.exam.title}» در کتاب «${exam.book}» — ${exam.exam.questions.size} سوال"
            )
        }
        if (text.contains("\"books\"")) {
            val books = ExamStore.fromBackupJson(text)
            if (books != null) {
                return FileInspection(
                    name, size, FileKind.BACKUP,
                    detail = "پشتیبان قدیمی: ${books.size} کتاب، ${books.sumOf { it.exams.size }} آزمون"
                )
            }
        }
        return bad(name, size, "این فایل، فایل آزمون‌یار نیست. «فایل آزمون» یا «پشتیبان» یا PDF انتخاب کنید.")
    }
}
