package com.example.quizapp

/**
 * تنظیمات اتصال آنلاین (Firebase). این دو مقدار را یک بار از کنسول Firebase بردارید (راهنما: ONLINE-SETUP.md).
 * تا وقتی خالی باشند، همهٔ بخش‌های آنلاین پنهان می‌ماند و برنامه مثل قبل آفلاین کار می‌کند.
 */
object OnlineConfig {
    /** Project settings ← General ← Web API Key */
    const val API_KEY: String = ""

    /** Realtime Database ← آدرس بالای صفحه؛ مثل https://NAME-default-rtdb.firebaseio.com */
    const val DB_URL: String = ""
}
