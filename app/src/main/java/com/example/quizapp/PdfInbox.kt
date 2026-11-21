package com.example.quizapp

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * «صندوق ورودیِ PDF»: هر PDFی که از اینترنت (مرورگر داخل برنامه)، از گوشی یا از برنامه‌های دیگر برسد
 * اینجا یک نسخهٔ موقت می‌شود و بعد در صفحهٔ «انتخاب صفحه‌های سوال و پاسخ» به آزمون تبدیل می‌شود.
 */
object PdfInbox {
    /** نامِ پیشنهادی آزمون (از نام فایل) */
    @Volatile
    var suggestedName: String = ""

    fun file(context: Context): File = File(context.cacheDir, "online/download.pdf")

    /** نام فایل را به یک نام خوانا برای آزمون تبدیل می‌کند */
    fun nameFrom(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { raw }
        return decoded.substringAfterLast('/').removeSuffix(".pdf").removeSuffix(".PDF")
            .replace('_', ' ').replace('-', ' ').trim().take(60)
    }

    /** اگر فایلِ انتخاب‌شده PDF باشد، نسخهٔ موقت می‌سازد و true برمی‌گرداند؛ وگرنه false (بدون کپی) */
    fun importFromUri(context: Context, uri: Uri): Boolean = try {
        val input = context.contentResolver.openInputStream(uri)
        if (input == null) {
            false
        } else {
            input.use { ins ->
                val head = ByteArray(4)
                val n = ins.read(head)
                val isPdf = n == 4 && head[0] == 0x25.toByte() && head[1] == 0x50.toByte() &&
                    head[2] == 0x44.toByte() && head[3] == 0x46.toByte()
                if (!isPdf) {
                    false
                } else {
                    val dest = file(context)
                    dest.parentFile?.mkdirs()
                    // ابتدا در فایل موقت نوشته می‌شود تا کپیِ ناقص جای PDF قبلی را نگیرد
                    val tmp = File(dest.parentFile, "download.tmp")
                    try {
                        tmp.outputStream().use { out ->
                            out.write(head)
                            ins.copyTo(out)
                        }
                        if (!tmp.renameTo(dest)) {
                            tmp.copyTo(dest, overwrite = true)
                            tmp.delete()
                        }
                    } catch (e: Exception) {
                        tmp.delete()
                        throw e
                    }
                    suggestedName = nameFrom(uri.lastPathSegment)
                    true
                }
            }
        }
    } catch (e: Exception) {
        false
    }
}
