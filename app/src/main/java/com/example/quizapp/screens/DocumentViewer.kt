package com.example.quizapp.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.ExamFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * وضعیت نمایشگر (شمارهٔ صفحه، میزان زوم و جای صفحه).
 * بیرون از نمایشگر نگه داشته می‌شود تا با رفتن از سوال به پاسخ و برگشت، همان صفحه و همان حالت دیده شود.
 */
class ViewerState {
    var pageNo by mutableIntStateOf(0)
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun goTo(page: Int) {
        pageNo = page
        scale = 1f
        offset = Offset.Zero
    }
}

/** یک صفحه از فایل‌ها: یک عکس، یا یک صفحه از PDF */
private class DocPage(val file: File, val isPdf: Boolean, val pageIndex: Int)

/** نگه‌داری PdfRenderer ها (فقط یک ترد همزمان می‌تواند رندر کند) */
private class PdfPool {
    private val opened = HashMap<String, Pair<ParcelFileDescriptor, PdfRenderer>>()

    @Synchronized
    fun render(file: File, pageIndex: Int, boxW: Int, boxH: Int): Bitmap? {
        val pair = opened.getOrPut(file.absolutePath) {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd to PdfRenderer(pfd)
        }
        val page = pair.second.openPage(pageIndex)
        try {
            val pw = page.width.toFloat().coerceAtLeast(1f)
            val ph = page.height.toFloat().coerceAtLeast(1f)
            // اندازه‌ای که کل صفحه در کادر جا شود، با کیفیتِ ۲٫۵ برابر برای زوم
            val fit = minOf(boxW / pw, boxH / ph)
            var w = pw * fit * 2.5f
            var h = ph * fit * 2.5f
            val longest = maxOf(w, h)
            if (longest > 3600f) {
                val k = 3600f / longest
                w *= k
                h *= k
            }
            val bmp = Bitmap.createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } finally {
            page.close()
        }
    }

    @Synchronized
    fun closeAll() {
        for ((_, pair) in opened) {
            try {
                pair.second.close()
                pair.first.close()
            } catch (e: Exception) {
                // نادیده گرفته می‌شود
            }
        }
        opened.clear()
    }
}

private fun buildPages(context: android.content.Context, names: List<String>): List<DocPage> {
    val pages = mutableListOf<DocPage>()
    for (name in names) {
        val file = ExamFiles.file(context, name)
        if (!file.exists()) continue
        if (ExamFiles.isPdf(name)) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val count = renderer.pageCount
                renderer.close()
                pfd.close()
                for (i in 0 until count) pages.add(DocPage(file, true, i))
            } catch (e: Exception) {
                // PDF خراب یا رمزدار: نادیده گرفته می‌شود
            }
        } else {
            pages.add(DocPage(file, false, 0))
        }
    }
    return pages
}

private fun decodeImage(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3600) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
    // عکس‌های دوربین ممکن است با زاویه ذخیره شده باشند
    val degrees = try {
        when (ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }
    if (degrees == 0f) return bmp
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

/**
 * نمایشگر فایل‌های آزمون (PDF و عکس) با زوم دو انگشتی و دابل‌تپ.
 * با چرخاندن گوشی (عمودی/افقی) اندازهٔ کادر عوض می‌شود و صفحه دوباره با کیفیت مناسب رندر می‌شود.
 */
@Composable
fun DocumentViewer(
    fileNames: List<String>,
    modifier: Modifier = Modifier,
    state: ViewerState = remember { ViewerState() }
) {
    val context = LocalContext.current
    var pages by remember(fileNames) { mutableStateOf<List<DocPage>?>(null) }
    val pool = remember { PdfPool() }

    DisposableEffect(Unit) { onDispose { pool.closeAll() } }
    LaunchedEffect(fileNames) {
        pages = withContext(Dispatchers.IO) { buildPages(context, fileNames) }
    }

    val list = pages
    if (list == null) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (list.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("فایلی برای نمایش پیدا نشد.", color = Color.Gray)
        }
    } else {
        PageViewer(list, pool, state, modifier)
    }
}

@Composable
private fun PageViewer(pages: List<DocPage>, pool: PdfPool, state: ViewerState, modifier: Modifier) {
    // اگر تعداد صفحه‌ها کمتر از صفحهٔ ذخیره‌شده باشد، به آخرین صفحه می‌رود
    val pageNo = state.pageNo.coerceIn(0, pages.lastIndex)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var bitmap by remember(pages) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(pages) { mutableStateOf(false) }

    // هر بار که صفحه یا اندازهٔ کادر (چرخش گوشی) عوض شود، دوباره رندر می‌شود
    LaunchedEffect(pageNo, boxSize, pages) {
        if (boxSize.width > 0 && boxSize.height > 0) {
            val page = pages[pageNo.coerceIn(0, pages.lastIndex)]
            val result = withContext(Dispatchers.IO) {
                try {
                    if (page.isPdf) pool.render(page.file, page.pageIndex, boxSize.width, boxSize.height)
                    else decodeImage(page.file)
                } catch (e: Throwable) {
                    null
                }
            }
            failed = result == null
            if (result != null) bitmap = result
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .onSizeChanged { boxSize = it }
                .pointerInput(pageNo) {
                    detectTapGestures(onDoubleTap = {
                        if (state.scale > 1f) {
                            state.scale = 1f
                            state.offset = Offset.Zero
                        } else {
                            state.scale = 2.5f
                        }
                    })
                }
                .pointerInput(pageNo, boxSize) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (state.scale * zoom).coerceIn(1f, 6f)
                        state.scale = newScale
                        val maxX = boxSize.width * (newScale - 1f) / 2f
                        val maxY = boxSize.height * (newScale - 1f) / 2f
                        state.offset = if (newScale <= 1f) {
                            Offset.Zero
                        } else {
                            Offset(
                                (state.offset.x + pan.x).coerceIn(-maxX, maxX),
                                (state.offset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = state.scale
                            scaleY = state.scale
                            translationX = state.offset.x
                            translationY = state.offset.y
                        }
                )
            } else if (failed) {
                Text("نمایش این صفحه ممکن نشد.", color = Color.Gray)
            } else {
                CircularProgressIndicator()
            }
        }

        if (pages.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { if (pageNo > 0) state.goTo(pageNo - 1) }, enabled = pageNo > 0) {
                    Text("▶ صفحهٔ قبل", fontSize = 13.sp)
                }
                Text("صفحهٔ ${pageNo + 1} از ${pages.size}", fontSize = 13.sp)
                TextButton(onClick = { if (pageNo < pages.lastIndex) state.goTo(pageNo + 1) }, enabled = pageNo < pages.lastIndex) {
                    Text("صفحهٔ بعد ◀", fontSize = 13.sp)
                }
            }
        }
    }
}
