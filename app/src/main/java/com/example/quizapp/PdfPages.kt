package com.example.quizapp

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/** صفحه‌های یک PDF را (با PdfRenderer خودِ اندروید) به تصویر JPEG تبدیل می‌کند تا برای هوش مصنوعی فرستاده شود */
object PdfPages {
    /** شمارهٔ صفحه‌ها از ۱؛ خروجی: (شمارهٔ صفحه، JPEG به‌صورت base64) */
    fun render(file: File, pages: List<Int>, width: Int = 1200, quality: Int = 75): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (p in pages) {
                    if (p < 1 || p > renderer.pageCount) continue
                    renderer.openPage(p - 1).use { page ->
                        val h = (width.toFloat() * page.height / page.width).toInt().coerceIn(1, 4000)
                        val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val bos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
                        bmp.recycle()
                        out.add(p to Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP))
                    }
                }
            }
        }
        return out
    }
}
