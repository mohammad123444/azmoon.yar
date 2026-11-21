import java.io.InputStream
import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.quizapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.quizapp"
        minSdk = 24
        targetSdk = 34
        // در GitHub Actions با هر بیلد خودکار بالا می‌رود تا نسخهٔ جدید روی قبلی نصب شود
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1) + 1000
        versionName = "2.0"
    }

    // امضای ثابت: بدون آن، هر بیلد با کلید متفاوت امضا می‌شود و به‌روزرسانی ممکن نیست
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// ---------------------------------------------------------------------------
// KaTeX (نمایش فرمول‌های ریاضی، فیزیک و شیمی به‌صورت آفلاین)
// هنگام بیلد یک‌بار دانلود و داخل اپ گذاشته می‌شود؛ بعد از نصب اپ اینترنت لازم ندارد.
// ---------------------------------------------------------------------------
val katexVersion = "0.16.9"
val katexFonts = listOf(
    "KaTeX_AMS-Regular", "KaTeX_Caligraphic-Bold", "KaTeX_Caligraphic-Regular",
    "KaTeX_Fraktur-Bold", "KaTeX_Fraktur-Regular", "KaTeX_Main-Bold", "KaTeX_Main-BoldItalic",
    "KaTeX_Main-Italic", "KaTeX_Main-Regular", "KaTeX_Math-BoldItalic", "KaTeX_Math-Italic",
    "KaTeX_SansSerif-Bold", "KaTeX_SansSerif-Italic", "KaTeX_SansSerif-Regular",
    "KaTeX_Script-Regular", "KaTeX_Size1-Regular", "KaTeX_Size2-Regular", "KaTeX_Size3-Regular",
    "KaTeX_Size4-Regular", "KaTeX_Typewriter-Regular"
)

val downloadKatex by tasks.registering {
    val outDir = file("src/main/assets/katex")
    doLast {
        val bases = listOf(
            "https://cdn.jsdelivr.net/npm/katex@$katexVersion/dist",
            "https://unpkg.com/katex@$katexVersion/dist"
        )
        fun fetch(rel: String, dest: File, required: Boolean) {
            if (dest.exists() && dest.length() > 0L) return
            dest.parentFile.mkdirs()
            var ok = false
            for (base in bases) {
                try {
                    URL("$base/$rel").openStream().use { input: InputStream ->
                        dest.outputStream().use { out -> input.copyTo(out) }
                    }
                    ok = true
                    break
                } catch (e: Exception) {
                    dest.delete()
                }
            }
            if (!ok && required) {
                throw GradleException("دانلود KaTeX ناموفق بود: $rel (اینترنت هنگام بیلد لازم است)")
            }
        }
        fetch("katex.min.css", File(outDir, "katex.min.css"), true)
        fetch("katex.min.js", File(outDir, "katex.min.js"), true)
        fetch("contrib/auto-render.min.js", File(outDir, "auto-render.min.js"), true)
        fetch("contrib/mhchem.min.js", File(outDir, "mhchem.min.js"), true)
        katexFonts.forEach { fetch("fonts/$it.woff2", File(outDir, "fonts/$it.woff2"), false) }
    }
}

tasks.named("preBuild").configure { dependsOn(downloadKatex) }
