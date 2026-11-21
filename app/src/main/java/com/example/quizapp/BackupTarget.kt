package com.example.quizapp

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * محل فایل پشتیبان که کاربر یک‌بار انتخاب کرده است.
 * دفعات بعد پشتیبان‌گیری روی همان فایل نوشته می‌شود (جایگزین قبلی) و فایل جدید ساخته نمی‌شود.
 */
object BackupTarget {
    private const val PREFS = "backup_target"

    fun get(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("uri", null)?.let { Uri.parse(it) }

    fun lastTime(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("time", 0L)

    /** محل جدید را نگه می‌دارد و دسترسی دائمی به آن می‌گیرد */
    fun set(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            // برخی ارائه‌دهنده‌ها دسترسی دائمی نمی‌دهند؛ دفعهٔ بعد فایل جدید پرسیده می‌شود
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("uri", uri.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("uri").apply()
    }

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("time", System.currentTimeMillis()).apply()
    }

    /**
     * نوشتن پشتیبان روی فایل (محتوای قبلی جایگزین می‌شود)؛ true یعنی موفق.
     * ابتدا پشتیبان کامل در حافظهٔ موقت ساخته می‌شود و فقط اگر سالم بود روی فایل قبلی نوشته می‌شود؛
     * پس خطا هنگام ساخت، پشتیبان قبلی را خراب نمی‌کند.
     */
    fun write(context: Context, uri: Uri, books: List<Book>): Boolean {
        val tmp = java.io.File(context.cacheDir, "backup-tmp.zip")
        return try {
            tmp.outputStream().use { out -> ExamPackage.writeBackup(context, books, out) }
            if (tmp.length() <= 0L) {
                false
            } else {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                    out.flush()
                } != null
            }
        } catch (e: Exception) {
            false
        } finally {
            tmp.delete()
        }
    }
}
