package com.example.quizapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.Account
import com.example.quizapp.OnlineApi
import com.example.quizapp.OnlineException
import com.example.quizapp.ui.theme.GreenDark
import com.example.quizapp.ui.theme.GreenPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * حساب معلم (ایمیل + رمز). برای انتشار آنلاین آزمون و دیدن نتیجه‌ها لازم است.
 * اگر اتصال آنلاین تنظیم نشده باشد، فقط یک توضیح کوتاه نشان داده می‌شود.
 */
@Composable
fun TeacherAccountCard(onChanged: (Account?) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(OnlineApi.account(context)) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun run(signUp: Boolean) {
        scope.launch {
            busy = true
            message = null
            try {
                val acc = withContext(Dispatchers.IO) { OnlineApi.signIn(context, email, password, signUp) }
                account = acc
                password = ""
                onChanged(acc)
            } catch (e: OnlineException) {
                message = e.userMessage
            } catch (e: Exception) {
                message = "ورود انجام نشد. دوباره امتحان کنید."
            }
            busy = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (!OnlineApi.configured) {
                Text("🌐 اتصال آنلاین", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "ارسال آنلاین آزمون و نتیجهٔ زنده هنوز تنظیم نشده است. (راهنما: فایل ONLINE-SETUP.md). بقیهٔ برنامه بدون آن کار می‌کند.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            } else {
                val acc = account
                if (acc != null) {
                    Text("👤 حساب معلم", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(acc.email, color = GreenDark, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            OnlineApi.signOut(context)
                            account = null
                            onChanged(null)
                        },
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) { Text("خروج از حساب") }
                } else {
                    Text("👤 ورود معلم", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "برای انتشار آنلاین آزمون و دیدن نتیجه‌ها وارد شوید. اگر حساب ندارید «ساخت حساب» را بزنید.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text("ایمیل") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("رمز (دست‌کم ۶ نویسه)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    val msg = message
                    if (msg != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("❌ $msg", color = Color(0xFFC62828), fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { run(false) },
                            enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.White)
                        ) { Text(if (busy) "صبر کنید..." else "ورود") }
                        OutlinedButton(
                            onClick = { run(true) },
                            enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("ساخت حساب") }
                    }
                }
            }
        }
    }
}
