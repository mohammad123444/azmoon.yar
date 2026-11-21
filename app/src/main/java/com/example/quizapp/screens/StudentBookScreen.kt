package com.example.quizapp.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.quizapp.Book

/** دانش‌آموز: انتخاب فصل از یک کتاب (فصل‌های بدون آزمون نشان داده نمی‌شوند) */
@Composable
fun StudentBookScreen(book: Book, onOpenChapter: (String) -> Unit, onBack: () -> Unit) {
    val chapters = book.allChapters.filter { book.examsIn(it).isNotEmpty() }
    val unsorted = book.unsortedExams

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("→ بازگشت") }
        Text("کتاب: ${book.name}", color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Text("فصل را انتخاب کنید", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (chapters.isEmpty() && unsorted.isEmpty()) {
            Text("این کتاب هنوز آزمونی ندارد.", color = Color.Gray)
        }
        chapters.forEach { chapter ->
            Button(
                onClick = { onOpenChapter(chapter) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text("$chapter (${book.examsIn(chapter).size} آزمون)") }
            Spacer(Modifier.height(12.dp))
        }
        if (unsorted.isNotEmpty()) {
            OutlinedButton(
                onClick = { onOpenChapter("") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text("📂 آزمون‌های بدون فصل (${unsorted.size} آزمون)") }
        }
    }
}
