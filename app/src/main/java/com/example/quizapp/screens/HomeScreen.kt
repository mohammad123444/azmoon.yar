package com.example.quizapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Book
import com.example.quizapp.Exam
import com.example.quizapp.ExamSearch
import com.example.quizapp.SavedSession
import com.example.quizapp.SessionLogic
import com.example.quizapp.examLabel
import com.example.quizapp.ui.theme.GreenDark
import com.example.quizapp.ui.theme.GreenLight
import com.example.quizapp.ui.theme.GreenPrimary

/**
 * صفحهٔ اصلی (پنل دانش‌آموز):
 *  - کارت «ادامهٔ آزمون» فقط وقتی وضعیت ذخیره‌شدهٔ واقعی هست
 *  - جست‌وجوی کتاب، فصل و آزمون؛ زدن روی نتیجه مستقیم به صفحهٔ همان آزمون می‌رود
 *  - فهرست کتاب‌ها با تعداد فصل و آزمون
 */
@Composable
fun HomeScreen(
    books: List<Book>,
    query: String,
    onQueryChange: (String) -> Unit,
    resume: Pair<SavedSession, Exam>?,
    onResume: (SavedSession, Exam) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenExam: (String, Exam) -> Unit,
    onGoImport: () -> Unit,
    onGoTeacher: () -> Unit
) {
    // کتاب‌های بدون آزمون به دانش‌آموز نشان داده نمی‌شوند
    val visibleBooks = books.filter { it.exams.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("آزمون‌های من", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        if (visibleBooks.isEmpty()) {
            EmptyHome(onGoImport = onGoImport, onGoTeacher = onGoTeacher)
        } else {
            HomeContent(visibleBooks, query, onQueryChange, resume, onResume, onOpenBook, onOpenExam)
        }
    }
}


@Composable
private fun ColumnScope.HomeContent(
    visibleBooks: List<Book>,
    query: String,
    onQueryChange: (String) -> Unit,
    resume: Pair<SavedSession, Exam>?,
    onResume: (SavedSession, Exam) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenExam: (String, Exam) -> Unit
) {
    val hasQuery = query.isNotBlank()
    val hits = remember(visibleBooks, query) {
        if (hasQuery) ExamSearch.search(visibleBooks, query) else emptyList()
    }

        val clearIcon: (@Composable () -> Unit)? = if (hasQuery) {
            { TextButton(onClick = { onQueryChange("") }) { Text("✕") } }
        } else {
            null
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("🔎 جست‌وجوی کتاب، فصل یا آزمون") },
            singleLine = true,
            trailingIcon = clearIcon,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        if (hasQuery) {
            if (hits.isEmpty()) {
                Text("نتیجه‌ای برای «${query.trim()}» پیدا نشد.", fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("کلمهٔ کوتاه‌تری بنویسید؛ مثلاً فقط نام کتاب.", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("پاک کردن جست‌وجو") }
            } else {
                Text("${hits.size} آزمون پیدا شد. روی آزمون بزنید.", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(hits) { hit ->
                        val where = if (hit.exam.chapter.isBlank()) hit.bookName
                        else hit.bookName + " ‹ " + hit.exam.chapter
                        Button(
                            onClick = { onOpenExam(hit.bookName, hit.exam) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(examLabel(hit.exam), fontWeight = FontWeight.Bold)
                                Text(where, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (resume != null) {
                item {
                    ResumeCard(session = resume.first, exam = resume.second, onResume = { onResume(resume.first, resume.second) })
                }
            }
            item {
                Text("کتاب را انتخاب کنید", color = Color.Gray, fontSize = 14.sp)
            }
            items(visibleBooks) { book ->
                Button(
                    onClick = { onOpenBook(book.name) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(book.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(ExamSearch.bookSummary(book), fontSize = 12.sp)
                    }
                }
            }
        }
        }
}

@Composable
private fun ResumeCard(session: SavedSession, exam: Exam, onResume: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GreenLight)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ادامهٔ آزمون قبلی", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "${session.bookName} — ${exam.title}",
                color = GreenDark,
                fontSize = 14.sp
            )
            Text(SessionLogic.progressText(session), color = GreenDark, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
            ) { Text("▶ ادامه", fontSize = 17.sp) }
        }
    }
}

/** وقتی هنوز هیچ آزمونی نیست: دو مسیر کوتاه و روشن */
@Composable
private fun EmptyHome(onGoImport: () -> Unit, onGoTeacher: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("هنوز آزمونی وجود ندارد.", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("دانش‌آموز: فایل آزمونِ معلم را دریافت کنید.", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onGoImport,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("📥 دریافت آزمون") }
        Spacer(Modifier.height(16.dp))
        Text("معلم: کتاب، فصل و آزمون بسازید.", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = onGoTeacher,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("👨‍🏫 رفتن به پنل معلم") }
    }
}
