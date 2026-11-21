package com.example.quizapp.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.quizapp.MathText

/**
 * متن درسی: اگر فرمول (ریاضی/فیزیک/شیمی) داشته باشد با KaTeX (آفلاین) نمایش داده می‌شود،
 * وگرنه همان Text معمولی.
 */
@Composable
fun RichText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit = TextUnit.Unspecified,
    modifier: Modifier = Modifier
) {
    if (MathText.needsMath(text)) {
        MathWebView(text = text, color = color, fontSizeSp = fontSize.value, modifier = modifier)
    } else {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            modifier = modifier
        )
    }
}

private class HeightBridge(private val callback: (Int) -> Unit) {
    @JavascriptInterface
    fun onHeight(h: Int) {
        callback(h)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MathWebView(text: String, color: Color, fontSizeSp: Float, modifier: Modifier) {
    val density = LocalDensity.current
    val fontPx = fontSizeSp * density.fontScale
    val argb = color.toArgb()
    val cssColor = "rgb(${(argb shr 16) and 255},${(argb shr 8) and 255},${argb and 255})"
    val html = remember(text, cssColor, fontPx) { MathText.buildHtml(text, cssColor, fontPx) }

    val heightState = remember { mutableIntStateOf(40) }
    val holder = remember { arrayOfNulls<WebView>(1) }
    DisposableEffect(Unit) {
        onDispose {
            holder[0]?.destroy()
            holder[0] = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth().height(heightState.intValue.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                holder[0] = this
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.textZoom = 100
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                webViewClient = WebViewClient()
                addJavascriptInterface(
                    HeightBridge { h ->
                        Handler(Looper.getMainLooper()).post {
                            if (h > 0) heightState.intValue = h
                        }
                    },
                    "Android"
                )
            }
        },
        update = { web ->
            if (web.tag != html) {
                web.tag = html
                web.loadDataWithBaseURL(
                    "file:///android_asset/katex/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}
