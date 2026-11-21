package com.example.quizapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Exam
import com.example.quizapp.LocalAppSettings
import com.example.quizapp.SavedSession
import com.example.quizapp.SessionLogic
import com.example.quizapp.ui.theme.GreenPrimary

/**
 * پیش از شروع: تعداد سوال‌ها و نوع آزمون (تمرین یا امتحان).
 * اگر برای همین آزمون وضعیت ذخیره‌شدهٔ واقعی هست، دکمهٔ «ادامه» هم نمایش داده می‌شود.
 */
@Composable
fun SetupScreen(
    bookName: String,
    exam: Exam,
    saved: SavedSession?,
    initialName: String,
    onResume: () -> Unit,
    onStart: (count: Int, mode: Int, name: String) -> Unit,
    onBack: () -> Unit
) {
    val total = exam.questions.size
    var countText by remember(exam) { mutableStateOf(total.toString()) }
    // آزمون تستیِ زمان‌دار به‌طور پیش‌فرض «امتحان» است
    var mode by remember(exam) {
        mutableIntStateOf(
            if (exam.isTest && exam.timeLimitMin > 0) SessionLogic.MODE_EXAM else SessionLogic.MODE_PRACTICE
        )
    }
    var nameText by remember { mutableStateOf(initialName) }
    val needName = exam.isTest && mode == SessionLogic.MODE_EXAM
    val count = countText.toIntOrNull()
    val valid = count != null && count in 1..total && (!needName || nameText.isNotBlank())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("→ بازگشت") }
        Text("کتاب: $bookName", color = Color.Gray)
        Text(exam.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "تعداد کل سوال‌ها: $total" +
                (if (exam.isTest) " • تستی" else "") +
                (if (exam.timeLimitMin > 0) " • زمان: ${exam.timeLimitMin} دقیقه" else ""),
            color = Color.Gray
        )
        Spacer(Modifier.height(16.dp))

        if (saved != null) {
            Button(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
            ) { Text("▶ ادامهٔ آزمون قبلی (${SessionLogic.progressText(saved)})", fontSize = 16.sp) }
            Spacer(Modifier.height(6.dp))
            Text("یا از پایین، آزمون تازه شروع کنید.", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = countText,
            onValueChange = { countText = it.filter { ch -> ch.isDigit() } },
            label = { Text("تعداد سوال (۱ تا $total)") },
            singleLine = true,
            isError = !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Text("نوع آزمون", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(SessionLogic.MODE_PRACTICE to "📖 تمرین", SessionLogic.MODE_EXAM to "📝 امتحان").forEach { (m, label) ->
                if (m == mode) {
                    Button(
                        onClick = { mode = m },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text(label, maxLines = 1, fontSize = 16.sp) }
                } else {
                    OutlinedButton(
                        onClick = { mode = m },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text(label, maxLines = 1, fontSize = 16.sp) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (mode == SessionLogic.MODE_EXAM) {
                if (exam.isTest) "گزینه را انتخاب می‌کنید. پاسخ صحیح و نمره فقط در پایان دیده می‌شود." +
                    (if (exam.timeLimitMin > 0) " با پایان زمان، آزمون خودکار تمام می‌شود." else "")
                else "پاسخ را می‌نویسید. پاسخ صحیح فقط در پایان دیده می‌شود."
            } else "پاسخ را می‌بینید و خودتان «صحیح» یا «اشتباه» می‌زنید.",
            color = Color.Gray,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
        if (needName) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it.take(60) },
                label = { Text("نام و نام خانوادگی (برای معلم)") },
                singleLine = true,
                isError = nameText.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (valid && LocalAppSettings.current.shuffle) {
            Spacer(Modifier.height(4.dp))
            Text("🔀 ترتیب سوال‌ها تصادفی است (در ⚙️ تنظیمات قابل تغییر).", color = Color.Gray, fontSize = 12.sp)
        }
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { if (count != null) onStart(count, mode, nameText.trim()) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
        ) {
            Text(if (mode == SessionLogic.MODE_EXAM) "شروع امتحان" else "شروع تمرین", fontSize = 18.sp)
        }
    }
}
