package com.example.quizapp

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/** تنظیمات برنامه */
data class AppSettingsState(
    /** اندازهٔ نوشته‌ها: ۰ کوچک، ۱ متوسط، ۲ بزرگ */
    val fontLevel: Int = 1,
    /** پوسته: ۰ مطابق سیستم، ۱ روشن، ۲ تاریک */
    val themeMode: Int = 0,
    /** ترتیب تصادفی سوال‌ها در آزمون‌های نوشتاری */
    val shuffle: Boolean = true,
    /** نمایش تاریخ بارگذاری هر سوال */
    val showDate: Boolean = true,
    /** روشن ماندن صفحه هنگام آزمون */
    val keepScreenOn: Boolean = false
)

val LocalAppSettings = staticCompositionLocalOf { AppSettingsState() }

object AppSettings {
    private const val PREFS = "app_settings"

    fun load(context: Context): AppSettingsState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppSettingsState(
            fontLevel = p.getInt("font_level", 1).coerceIn(0, 2),
            themeMode = p.getInt("theme_mode", 0).coerceIn(0, 2),
            shuffle = p.getBoolean("shuffle", true),
            showDate = p.getBoolean("show_date", true),
            keepScreenOn = p.getBoolean("keep_screen_on", false)
        )
    }

    fun save(context: Context, s: AppSettingsState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("font_level", s.fontLevel)
            .putInt("theme_mode", s.themeMode)
            .putBoolean("shuffle", s.shuffle)
            .putBoolean("show_date", s.showDate)
            .putBoolean("keep_screen_on", s.keepScreenOn)
            .apply()
    }

    fun fontFactor(level: Int): Float = when (level) {
        0 -> 0.85f
        2 -> 1.25f
        else -> 1.0f
    }
}
