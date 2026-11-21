package com.example.quizapp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * اگر برنامه ناگهان بسته شود، متن خطا در یک فایل کوچک نگه داشته می‌شود و دفعهٔ بعد روی صفحه نشان داده می‌شود
 * تا بتوان آن را برای رفع مشکل فرستاد. این صفحه با ابزار ساده‌ٔ خود اندروید ساخته می‌شود (نه Compose)
 * تا حتی اگر Compose خراب باشد کار کند. به داده‌های آزمون‌ها دست نمی‌زند.
 */
object CrashReport {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                File(app.filesDir, FILE).writeText(
                    "نسخه: 2.0\nدستگاه: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (اندروید ${android.os.Build.VERSION.SDK_INT})\n\n" +
                        sw.toString().take(6000)
                )
            } catch (e: Throwable) {
                // ثبت خطا نباید خودش مشکل تازه بسازد
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** اگر خطای قبلی ثبت شده باشد متن آن را برمی‌گرداند */
    fun pending(context: Context): String? = try {
        val f = File(context.filesDir, FILE)
        if (f.exists()) f.readText().ifBlank { null } else null
    } catch (e: Throwable) {
        null
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE).delete()
        } catch (e: Throwable) {
        }
    }

    /** صفحهٔ ساده با دکمه‌های «کپی متن خطا» و «ادامه» */
    fun show(activity: Activity, report: String, onContinue: () -> Unit) {
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(TextView(activity).apply {
            text = "برنامه دفعهٔ قبل ناگهان بسته شد.\nمتن خطا را کپی کنید و برای پشتیبان بفرستید. اطلاعات آزمون‌های شما دست‌نخورده است."
            textSize = 16f
        })
        val scroll = ScrollView(activity)
        scroll.addView(TextView(activity).apply {
            text = report
            textSize = 11f
            typeface = Typeface.MONOSPACE
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.START
            setTextIsSelectable(true)
        })
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(activity).apply {
            text = "📋 کپی متن خطا"
            setOnClickListener {
                val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", report))
            }
        })
        root.addView(Button(activity).apply {
            text = "ادامه و تلاش دوباره"
            setOnClickListener {
                clear(activity)
                onContinue()
            }
        })
        activity.setContentView(root)
    }
}
