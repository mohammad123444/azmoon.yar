package com.example.quizapp

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Random

/** خطایی که متنش مستقیم به کاربر نشان داده می‌شود */
class OnlineException(val userMessage: String) : Exception(userMessage)

data class Account(
    val email: String,
    val uid: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAt: Long
)

/**
 * ارتباط با Firebase فقط از راه REST (بدون کتابخانه و بدون تغییر Gradle):
 *  - حساب معلم: Firebase Authentication (ایمیل + رمز)
 *  - انتشار آزمون با «کد» و دریافت آن توسط دانش‌آموز
 *  - ارسال خودکار نتیجهٔ دانش‌آموز و دریافت آن توسط معلم
 * همهٔ تابع‌ها مسدودکننده‌اند؛ باید روی Dispatchers.IO صدا زده شوند.
 */
object OnlineApi {
    var apiKey: String = OnlineConfig.API_KEY
    var dbUrl: String = OnlineConfig.DB_URL
    var authHost: String = "https://identitytoolkit.googleapis.com"
    var tokenHost: String = "https://securetoken.googleapis.com"

    val configured: Boolean get() = apiKey.isNotBlank() && dbUrl.isNotBlank()

    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val ACCOUNT_PREFS = "online_account"
    private const val CODES_PREFS = "online_codes"

    // ---------- HTTP ----------

    private class Reply(val status: Int, val body: String)

    private fun call(method: String, url: String, body: String?, contentType: String = "application/json"): Reply {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw OnlineException("آدرس سرور درست نیست.")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "$contentType; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Reply(code, text)
        } catch (e: OnlineException) {
            throw e
        } catch (e: Exception) {
            throw OnlineException("اتصال به اینترنت برقرار نشد. اینترنت را بررسی کنید و دوباره امتحان کنید.")
        } finally {
            conn.disconnect()
        }
    }

    private fun need() {
        if (!configured) throw OnlineException("اتصال آنلاین هنوز تنظیم نشده است.")
    }

    private fun authMessage(raw: String): String {
        val m = try {
            JSONObject(raw).optJSONObject("error")?.optString("message", "") ?: ""
        } catch (e: Exception) {
            ""
        }
        return when {
            m.startsWith("EMAIL_EXISTS") -> "با این ایمیل قبلاً حساب ساخته شده؛ «ورود» را بزنید."
            m.startsWith("WEAK_PASSWORD") -> "رمز باید دست‌کم ۶ نویسه باشد."
            m.startsWith("INVALID_EMAIL") -> "ایمیل را درست بنویسید."
            m.startsWith("EMAIL_NOT_FOUND") || m.startsWith("INVALID_PASSWORD") ||
                m.startsWith("INVALID_LOGIN_CREDENTIALS") -> "ایمیل یا رمز اشتباه است."
            m.startsWith("TOO_MANY_ATTEMPTS") -> "تلاش‌های زیاد؛ چند دقیقه بعد دوباره امتحان کنید."
            m.startsWith("OPERATION_NOT_ALLOWED") -> "ورود با ایمیل در Firebase فعال نشده است (ONLINE-SETUP.md)."
            else -> "ورود انجام نشد. دوباره امتحان کنید."
        }
    }

    // ---------- حساب معلم ----------

    fun account(context: Context): Account? {
        val raw = context.getSharedPreferences(ACCOUNT_PREFS, Context.MODE_PRIVATE).getString("acc", null) ?: return null
        return try {
            val o = JSONObject(raw)
            Account(o.getString("e"), o.getString("u"), o.getString("i"), o.getString("r"), o.optLong("x", 0L))
        } catch (e: Exception) {
            null
        }
    }

    private fun saveAccount(context: Context, a: Account?) {
        val ed = context.getSharedPreferences(ACCOUNT_PREFS, Context.MODE_PRIVATE).edit()
        if (a == null) ed.remove("acc")
        else ed.putString(
            "acc",
            JSONObject().put("e", a.email).put("u", a.uid).put("i", a.idToken).put("r", a.refreshToken).put("x", a.expiresAt).toString()
        )
        ed.apply()
    }

    fun signOut(context: Context) = saveAccount(context, null)

    /** ساخت حساب (signUp=true) یا ورود */
    fun signIn(context: Context, email: String, password: String, signUp: Boolean): Account {
        need()
        val mail = email.trim()
        if (mail.isEmpty() || password.isEmpty()) throw OnlineException("ایمیل و رمز را بنویسید.")
        val path = if (signUp) "accounts:signUp" else "accounts:signInWithPassword"
        val body = JSONObject().put("email", mail).put("password", password).put("returnSecureToken", true).toString()
        val r = call("POST", "$authHost/v1/$path?key=" + URLEncoder.encode(apiKey, "UTF-8"), body)
        if (r.status !in 200..299) throw OnlineException(authMessage(r.body))
        return try {
            val o = JSONObject(r.body)
            val acc = Account(
                mail,
                o.getString("localId"),
                o.getString("idToken"),
                o.getString("refreshToken"),
                System.currentTimeMillis() + o.optString("expiresIn", "3600").toLong() * 1000L
            )
            saveAccount(context, acc)
            acc
        } catch (e: Exception) {
            throw OnlineException("پاسخ سرور نامعتبر بود.")
        }
    }

    /** توکن معتبر؛ در صورت نیاز تازه می‌شود */
    private fun token(context: Context): Pair<String, String> {
        val acc = account(context) ?: throw OnlineException("ابتدا وارد حساب معلم شوید.")
        if (acc.expiresAt - System.currentTimeMillis() > 60_000L) return acc.idToken to acc.uid
        val form = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(acc.refreshToken, "UTF-8")
        val r = call("POST", "$tokenHost/v1/token?key=" + URLEncoder.encode(apiKey, "UTF-8"), form, "application/x-www-form-urlencoded")
        if (r.status !in 200..299) {
            saveAccount(context, null)
            throw OnlineException("نشست شما تمام شده؛ دوباره وارد شوید.")
        }
        return try {
            val o = JSONObject(r.body)
            val fresh = acc.copy(
                idToken = o.getString("id_token"),
                refreshToken = o.optString("refresh_token", acc.refreshToken),
                expiresAt = System.currentTimeMillis() + o.optString("expires_in", "3600").toLong() * 1000L
            )
            saveAccount(context, fresh)
            fresh.idToken to fresh.uid
        } catch (e: Exception) {
            throw OnlineException("نشست شما تمام شده؛ دوباره وارد شوید.")
        }
    }

    // ---------- نگاشت «اثر انگشت آزمون ← کد» روی همین دستگاه ----------

    fun codeFor(context: Context, fingerprint: String): String? =
        context.getSharedPreferences(CODES_PREFS, Context.MODE_PRIVATE).getString(fingerprint, null)

    private fun rememberCode(context: Context, fingerprint: String, code: String) {
        context.getSharedPreferences(CODES_PREFS, Context.MODE_PRIVATE).edit().putString(fingerprint, code).apply()
    }

    /** از متن کاربر (کد خام یا لینک azmoon://exam?c=CODE) کد را بیرون می‌کشد؛ نامعتبر = null */
    fun codeFromText(text: String): String? {
        var t = ExamSearch.normalize(text).trim()
        val i = t.indexOf("c=")
        if (t.startsWith("azmoon://") && i >= 0) t = t.substring(i + 2)
        t = t.uppercase().filter { it in ALPHABET }
        return if (t.length == 6) t else null
    }

    // ---------- آزمون ----------

    private fun dbPath(path: String, token: String?): String =
        dbUrl.trimEnd('/') + "/" + path + ".json" + if (token != null) "?auth=" + URLEncoder.encode(token, "UTF-8") else ""

    private fun newCode(random: Random): String {
        val sb = StringBuilder()
        repeat(6) { sb.append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        return sb.toString()
    }

    /** آزمون را آنلاین منتشر می‌کند و کد ۶ نویسه‌ای برمی‌گرداند (اگر قبلاً منتشر شده، همان کد) */
    fun publish(context: Context, bookName: String, exam: Exam, random: Random = Random()): String {
        need()
        if (!exam.isTest) throw OnlineException("فقط آزمون تستی منتشر می‌شود.")
        val (tok, uid) = token(context)
        val fp = SessionLogic.fingerprint(exam)
        var code = codeFor(context, fp)
        if (code == null) {
            var tries = 0
            while (code == null && tries < 8) {
                val c = newCode(random)
                val r = call("GET", dbPath("exams/$c", null), null)
                if (r.status in 200..299 && r.body.trim() == "null") code = c
                else if (r.status !in 200..299) throw OnlineException("ارتباط با سرور ناموفق بود (${r.status}).")
                tries++
            }
            if (code == null) throw OnlineException("ساخت کد انجام نشد؛ دوباره امتحان کنید.")
        }
        val payload = JSONObject()
            .put("owner", uid)
            .put("title", exam.title)
            .put("t", System.currentTimeMillis())
            .put("data", ExamText.toShareFileJson(bookName, exam))
            .toString()
        val r = call("PUT", dbPath("exams/$code", tok), payload)
        if (r.status !in 200..299) {
            throw OnlineException(if (r.status == 401 || r.status == 403) "اجازهٔ انتشار نیست (قوانین Firebase را بررسی کنید)." else "انتشار انجام نشد (${r.status}).")
        }
        rememberCode(context, fp, code)
        return code
    }

    /** دریافت آزمون با کد (برای دانش‌آموز؛ بدون ورود) */
    fun fetchExam(context: Context, rawCode: String): SharedExam {
        need()
        val code = codeFromText(rawCode) ?: throw OnlineException("کد باید ۶ حرف/عدد باشد.")
        val r = call("GET", dbPath("exams/$code", null), null)
        if (r.status !in 200..299) throw OnlineException("ارتباط با سرور ناموفق بود (${r.status}).")
        if (r.body.trim() == "null") throw OnlineException("آزمونی با این کد پیدا نشد. کد را دوباره بررسی کنید.")
        val shared = try {
            ExamText.fromFileJson(JSONObject(r.body).getString("data"))
        } catch (e: Exception) {
            null
        } ?: throw OnlineException("داده‌ٔ این آزمون خراب است. از معلم بخواهید دوباره منتشر کند.")
        rememberCode(context, SessionLogic.fingerprint(shared.exam), code)
        return shared
    }

    // ---------- نتیجه ----------

    private fun resultKey(r: StudentResult): String {
        val md = MessageDigest.getInstance("SHA-256").digest(r.id.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder("r")
        for (i in 0 until 10) sb.append(String.format("%02x", md[i].toInt() and 0xFF))
        return sb.toString()
    }

    /** آیا برای این نتیجه کدِ آنلاین هست؟ */
    fun canSubmit(context: Context, r: StudentResult): Boolean = configured && codeFor(context, r.fingerprint) != null

    /** ارسال نتیجه به معلم؛ true یعنی ثبت شد (ارسال دوباره همان نتیجه تکراری نمی‌سازد) */
    fun submitResult(context: Context, r: StudentResult): Boolean {
        need()
        val code = codeFor(context, r.fingerprint) ?: return false
        val body = JSONObject().put("c", ResultCodec.toShareText(r)).put("t", System.currentTimeMillis()).toString()
        val reply = call("PUT", dbPath("results/$code/" + resultKey(r), null), body)
        if (reply.status in 200..299) return true
        if (reply.status == 401 || reply.status == 403) {
            throw OnlineException("نتیجه ثبت نشد (شاید قبلاً ثبت شده یا قوانین Firebase اجازه نمی‌دهد). برای اطمینان، نتیجه را دستی هم بفرستید.")
        }
        throw OnlineException("نتیجه فرستاده نشد (${reply.status}).")
    }

    class Fetched(val total: Int, val added: Int)

    /** نتیجه‌های آنلاین این آزمون را می‌گیرد و در ذخیرهٔ نتیجه‌ها می‌ریزد */
    fun fetchResults(context: Context, exam: Exam): Fetched {
        need()
        val code = codeFor(context, SessionLogic.fingerprint(exam)) ?: throw OnlineException("این آزمون هنوز منتشر نشده است.")
        val (tok, _) = token(context)
        val r = call("GET", dbPath("results/$code", tok), null)
        if (r.status == 401 || r.status == 403) throw OnlineException("اجازهٔ دیدن نتیجه‌ها فقط با حساب همان معلمی است که آزمون را منتشر کرده.")
        if (r.status !in 200..299) throw OnlineException("ارتباط با سرور ناموفق بود (${r.status}).")
        if (r.body.trim() == "null") return Fetched(0, 0)
        val obj = try {
            JSONObject(r.body)
        } catch (e: Exception) {
            throw OnlineException("پاسخ سرور نامعتبر بود.")
        }
        var total = 0
        var added = 0
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val text = obj.optJSONObject(k)?.optString("c", "").orEmpty()
            val sr = ResultCodec.fromText(text) ?: continue
            total++
            if (ResultStore.add(context, sr)) added++
        }
        return Fetched(total, added)
    }
}
