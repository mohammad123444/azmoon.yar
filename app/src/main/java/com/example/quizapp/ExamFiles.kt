package com.example.quizapp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale
import java.util.UUID

/** فایل‌های PDF و عکسِ آزمون‌ها؛ داخل حافظهٔ خصوصی برنامه نگه‌داری می‌شوند */
object ExamFiles {
    private const val DIR = "exam_files"

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    fun isPdf(name: String): Boolean = name.endsWith(".pdf", ignoreCase = true)

    fun newName(ext: String): String = UUID.randomUUID().toString() + "." + ext

    fun label(name: String, index: Int): String =
        (if (isPdf(name)) "📄 PDF" else "🖼 عکس") + " ${index + 1}"

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    /** نوع فایل را از روی محتوای آن (چند بایت اول) تشخیص می‌دهد؛ مستقل از نام و mime */
    private fun sniffExtension(head: ByteArray, n: Int): String? {
        fun b(i: Int): Int = if (i < n) head[i].toInt() and 0xFF else -1
        return when {
            b(0) == 0x25 && b(1) == 0x50 && b(2) == 0x44 && b(3) == 0x46 -> "pdf"
            b(0) == 0xFF && b(1) == 0xD8 -> "jpg"
            b(0) == 0x89 && b(1) == 0x50 && b(2) == 0x4E && b(3) == 0x47 -> "png"
            b(0) == 0x52 && b(1) == 0x49 && b(2) == 0x46 && b(3) == 0x46 &&
                b(8) == 0x57 && b(9) == 0x45 && b(10) == 0x42 && b(11) == 0x50 -> "webp"
            else -> null
        }
    }

    /** پسوند از روی mime یا نام فایل (روش پشتیبان) */
    private fun extensionFor(context: Context, uri: Uri): String? {
        val mime = context.contentResolver.getType(uri)?.lowercase(Locale.US).orEmpty()
        if (mime == "application/pdf") return "pdf"
        if (mime.startsWith("image/")) {
            return when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                else -> "jpg"
            }
        }
        val name = (displayName(context, uri) ?: uri.lastPathSegment ?: return null).lowercase(Locale.US)
        return when {
            name.endsWith(".pdf") -> "pdf"
            name.endsWith(".png") -> "png"
            name.endsWith(".webp") -> "webp"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "jpg"
            else -> null
        }
    }

    /**
     * کپی فایل انتخاب‌شده داخل حافظهٔ برنامه؛ نام ذخیره‌شده را برمی‌گرداند (یا null).
     * نوع فایل از محتوا تشخیص داده می‌شود و فایلِ خالی یا ناقص ذخیره نمی‌شود.
     */
    fun importFromUri(context: Context, uri: Uri): String? {
        val tmp = File(dir(context), "tmp-" + UUID.randomUUID().toString())
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            var sniffed: String? = null
            var total = 0L
            input.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var first = true
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        if (first) {
                            sniffed = sniffExtension(buf, n)
                            first = false
                        }
                        out.write(buf, 0, n)
                        total += n
                    }
                    out.flush()
                    try { out.fd.sync() } catch (e: Exception) { /* نادیده گرفته می‌شود */ }
                }
            }
            val ext = sniffed ?: extensionFor(context, uri)
            if (ext == null || total <= 0L) {
                tmp.delete()
                return null
            }
            val target = file(context, newName(ext))
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            if (target.length() <= 0L) {
                target.delete()
                null
            } else {
                target.name
            }
        } catch (e: Exception) {
            tmp.delete()
            null
        }
    }

    fun referenced(books: List<Book>): Set<String> =
        books.flatMap { it.exams }.flatMap { it.questionFiles + it.answerFiles }.toSet()

    /** فایل‌های تازه (کمتر از ۲۴ ساعت) پاک نمی‌شوند: ممکن است پیش‌نویسِ آزمونی باشند که هنوز ذخیره نشده */
    private const val GRACE_MS = 24L * 60 * 60 * 1000

    /** فایل‌هایی که هیچ آزمونی از آن‌ها استفاده نمی‌کند (و تازه نیستند) حذف می‌شوند */
    fun cleanup(context: Context, books: List<Book>) {
        val keep = referenced(books)
        val now = System.currentTimeMillis()
        dir(context).listFiles()?.forEach {
            if (it.name !in keep && now - it.lastModified() > GRACE_MS) it.delete()
        }
    }

    /** حذف فایل‌های پیش‌نویسی که کاربر ذخیره نکرد (هنگام انصراف) */
    fun discard(context: Context, names: Collection<String>, keep: Set<String>) {
        names.forEach { if (it !in keep) file(context, it).delete() }
    }
}
