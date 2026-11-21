package com.example.quizapp.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Book
import com.example.quizapp.Exam
import com.example.quizapp.ExamFiles
import com.example.quizapp.ExamStore
import com.example.quizapp.ExamText
import com.example.quizapp.Question
import com.example.quizapp.QuestionParser
import com.example.quizapp.ui.theme.GreenPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_ANSWER_NUMBER = 500

/** پنل مدیریت: ساخت یا ویرایش آزمون با فایل سوال (PDF / عکس) */
@Composable
fun FileExamEditorScreen(
    books: List<Book>,
    bookName: String,
    chapter: String,
    originalKey: String?,
    onBooksChange: (List<Book>) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val book = books.firstOrNull { it.name == bookName }
    val original = book?.exams?.firstOrNull { it.key == originalKey }

    var title by rememberSaveable(originalKey) { mutableStateOf(original?.title ?: "") }
    val qFiles = rememberFileList(originalKey, original?.questionFiles ?: emptyList(), "q")
    val aFiles = rememberFileList(originalKey, original?.answerFiles ?: emptyList(), "a")
    // ۰ = پاسخ به‌صورت فایل، ۱ = پاسخ نوشتاری
    var answerMode by rememberSaveable(originalKey) {
        mutableStateOf(if (original != null && original.answerFiles.isEmpty() && original.questions.isNotEmpty()) 1 else 0)
    }
    var answersText by rememberSaveable(originalKey) {
        mutableStateOf(original?.let { ExamText.numberedSparse(it.questions.map { q -> q.a }) } ?: "")
    }
    var analysisText by rememberSaveable(originalKey) {
        mutableStateOf(original?.let { ExamText.numberedSparse(it.questions.map { q -> q.analysis }) } ?: "")
    }
    var loading by remember { mutableStateOf(false) }
    // فایل‌هایی که در همین ویرایش اضافه شده‌اند (برای پاک کردن هنگام انصراف)
    val originalFiles = remember(originalKey) { (original?.questionFiles.orEmpty() + original?.answerFiles.orEmpty()).toSet() }

    fun pick(uris: List<Uri>, target: MutableList<String>) {
        if (uris.isEmpty()) return
        scope.launch {
            loading = true
            val names = withContext(Dispatchers.IO) { uris.mapNotNull { ExamFiles.importFromUri(context, it) } }
            target.addAll(names)
            loading = false
            if (names.size < uris.size) {
                Toast.makeText(context, "${uris.size - names.size} فایل ذخیره نشد (فقط PDF و عکس معتبر پذیرفته می‌شود)", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "${names.size} فایل ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val mimeTypes = arrayOf("application/pdf", "image/*")
    val questionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pick(uris, qFiles)
    }
    val answerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pick(uris, aFiles)
    }

    // ---- پاسخ‌های نوشتاری: به شمارهٔ سوال وصل می‌شوند ----
    val answerItems = QuestionParser.parseItems(answersText, compact = true)
    val keyed = answerItems.mapIndexed { i, item -> (item.number ?: (i + 1)) to item.text }
    val maxNumber = keyed.maxOfOrNull { it.first } ?: 0
    val duplicate = keyed.groupBy { it.first }.any { it.value.size > 1 }
    val aligned = QuestionParser.alignOptional(answerItems, QuestionParser.parseItems(analysisText))
    val textError: String? = when {
        answerMode != 1 -> null
        duplicate -> "شمارهٔ تکراری در پاسخ‌ها"
        maxNumber > MAX_ANSWER_NUMBER -> "شمارهٔ سوال نباید از $MAX_ANSWER_NUMBER بیشتر باشد"
        aligned.error != null -> aligned.error
        else -> null
    }

    val canSave = title.isNotBlank() && qFiles.isNotEmpty() && textError == null && !loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = {
            ExamFiles.discard(context, qFiles + aFiles, originalFiles)
            onBack()
        }) { Text("→ بازگشت") }
        Text(
            if (chapter.isBlank()) "کتاب: $bookName" else "کتاب: $bookName — فصل: $chapter",
            color = Color.Gray
        )
        Text(
            if (originalKey == null) "افزودن آزمون از فایل" else "ویرایش آزمون فایل‌دار",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("نام آزمون") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        // ---- فایل سوال‌ها ----
        Text("۱) فایل سوال‌ها", style = MaterialTheme.typography.titleMedium)
        Text(
            "یک یا چند فایل PDF یا عکس. ترتیب نمایش همین ترتیب است.",
            color = Color.Gray,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        FileList(qFiles)
        OutlinedButton(
            onClick = { questionPicker.launch(mimeTypes) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("📎 انتخاب PDF یا عکس سوال‌ها") }
        Spacer(Modifier.height(24.dp))

        // ---- پاسخ‌ها ----
        Text("۲) پاسخ‌ها", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("📎 فایل (PDF / عکس)", "✍️ نوشتاری").forEachIndexed { i, label ->
                if (i == answerMode) {
                    Button(
                        onClick = { answerMode = i },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(label, fontSize = 13.sp) }
                } else {
                    OutlinedButton(
                        onClick = { answerMode = i },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(label, fontSize = 13.sp) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (answerMode == 0) {
            Text(
                "دانش‌آموز با یک دکمه بین سوال‌ها و پاسخ‌نامه جابه‌جا می‌شود.",
                color = Color.Gray,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(8.dp))
            FileList(aFiles)
            OutlinedButton(
                onClick = { answerPicker.launch(mimeTypes) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("📎 انتخاب PDF یا عکس پاسخ‌ها") }
            Text("اگر پاسخی ندارید، خالی بگذارید.", color = Color.Gray, fontSize = 12.sp)
        } else {
            Text(
                "هر پاسخ را با شمارهٔ سوالش شروع کنید (مثل ۱. یا ۲.).",
                color = Color.Gray,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = answersText,
                onValueChange = { answersText = it },
                label = { Text("پاسخ‌ها (شماره‌دار)") },
                minLines = 6,
                maxLines = 14,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = analysisText,
                onValueChange = { analysisText = it },
                label = { Text("پاسخ‌های تحلیلی (اختیاری)") },
                supportingText = { Text("با شمارهٔ سوال شروع شود؛ لازم نیست برای همه بنویسید.") },
                minLines = 3,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "تعداد پاسخ‌های واردشده: ${answerItems.size}",
                color = if (textError == null) GreenPrimary else Color(0xFFC62828),
                fontSize = 14.sp
            )
            if (textError != null) {
                Text(textError, color = Color(0xFFC62828), fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        if (loading) {
            Text("در حال کپی فایل‌ها...", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val newTitle = title.trim()
                val newKey = Exam.keyOf(chapter, newTitle)
                val dup = book?.exams?.any { it.key == newKey && it.key != originalKey } == true
                if (dup) {
                    Toast.makeText(context, "آزمونی با این نام در این فصل هست", Toast.LENGTH_LONG).show()
                } else {
                    val now = System.currentTimeMillis()
                    val written: List<Question> = if (answerMode == 1 && maxNumber > 0) {
                        val byNumber = keyed.mapIndexed { idx, (n, t) ->
                            n to Pair(t, aligned.values.getOrElse(idx) { "" })
                        }.toMap()
                        (1..maxNumber).map { n ->
                            val entry = byNumber[n]
                            val date = original?.questions?.getOrNull(n - 1)?.addedAt?.takeIf { it > 0L } ?: now
                            if (entry != null) Question("", entry.first, date, entry.second) else Question("", "", date)
                        }
                    } else {
                        emptyList()
                    }
                    val exam = Exam(
                        title = newTitle,
                        questions = written,
                        questionFiles = qFiles.toList(),
                        answerFiles = if (answerMode == 0) aFiles.toList() else emptyList(),
                        chapter = chapter
                    )
                    onBooksChange(ExamStore.saveExam(books, bookName, originalKey, exam))
                    Toast.makeText(context, "ذخیره شد", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
        ) {
            Text("💾 ذخیره", fontSize = 18.sp)
        }
    }
}

/**
 * فهرست نام فایل‌ها که با بسته شدن/ساخت دوبارهٔ برنامه (مثلاً هنگام باز شدن انتخابگر فایل) از بین نمی‌رود.
 * خودِ فایل‌ها هنگام انتخاب داخل حافظهٔ برنامه کپی شده‌اند و فقط نامشان اینجا نگه داشته می‌شود.
 */
@Composable
private fun rememberFileList(key: Any?, initial: List<String>, slot: String): SnapshotStateList<String> =
    rememberSaveable(
        key, slot,
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { initial.toMutableStateList() }

/** فهرست فایل‌های انتخاب‌شده با حذف و جابه‌جایی */
@Composable
private fun FileList(files: MutableList<String>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    files.forEachIndexed { i, name ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(ExamFiles.label(name, i), modifier = Modifier.weight(1f), fontSize = 15.sp)
            if (ExamFiles.isPdf(name)) {
                TextButton(onClick = {
                    scope.launch { shareExamFiles(context, listOf(name), "azmoon-pdf-${i + 1}", "ارسال PDF") }
                }) { Text("📤") }
            }
            TextButton(
                onClick = {
                    if (i > 0) {
                        val t = files[i]
                        files[i] = files[i - 1]
                        files[i - 1] = t
                    }
                },
                enabled = i > 0
            ) { Text("▲") }
            TextButton(
                onClick = {
                    if (i < files.lastIndex) {
                        val t = files[i]
                        files[i] = files[i + 1]
                        files[i + 1] = t
                    }
                },
                enabled = i < files.lastIndex
            ) { Text("▼") }
            TextButton(onClick = { files.removeAt(i) }) { Text("✕", color = Color(0xFFC62828)) }
        }
    }
    Spacer(Modifier.height(4.dp))
}
