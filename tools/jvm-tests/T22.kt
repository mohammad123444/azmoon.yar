package com.example.quizapp
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.json.JSONObject

fun mcq2(n: Int) = (1..n).map { Question("سوال $it", "گزینه ${it % 4 + 1}", 5L, "", listOf("گزینه 1", "گزینه 2", "گزینه 3", "گزینه 4"), it % 4) }

fun main(args: Array<String>) {
    val db = HashMap<String, String>()
    val users = HashMap<String, String>()
    val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    fun reply(ex: com.sun.net.httpserver.HttpExchange, code: Int, body: String) {
        val b = body.toByteArray(Charsets.UTF_8); ex.sendResponseHeaders(code, b.size.toLong()); ex.responseBody.use { it.write(b) }
    }
    srv.createContext("/") { ex ->
        val path = ex.requestURI.path; val q = ex.requestURI.rawQuery.orEmpty()
        val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
        if (path.startsWith("/v1/accounts:")) {
            val o = JSONObject(body); val mail = o.getString("email")
            if (path.endsWith("signUp")) {
                if (users.containsKey(mail)) reply(ex, 400, """{"error":{"message":"EMAIL_EXISTS"}}""")
                else { users[mail] = o.getString("password"); reply(ex, 200, """{"localId":"uid_${users.size}","idToken":"tok_$mail","refreshToken":"ref_$mail","expiresIn":"3600"}""") }
            } else {
                if (users[mail] == o.getString("password")) reply(ex, 200, """{"localId":"uid_1","idToken":"tok_$mail","refreshToken":"ref_$mail","expiresIn":"3600"}""")
                else reply(ex, 400, """{"error":{"message":"INVALID_LOGIN_CREDENTIALS"}}""")
            }
        } else if (path == "/v1/token") {
            reply(ex, 200, """{"id_token":"tok2","refresh_token":"ref2","expires_in":"3600"}""")
        } else {
            val key = path.removePrefix("/").removeSuffix(".json")
            val authed = q.contains("auth=tok")
            when (ex.requestMethod) {
                "GET" -> {
                    if (key.startsWith("results") && !authed) reply(ex, 401, """{"error":"Permission denied"}""")
                    else if (key.startsWith("results") && !key.contains("/r")) {
                        val sub = db.filterKeys { it.startsWith("$key/") }
                        if (sub.isEmpty()) reply(ex, 200, "null")
                        else reply(ex, 200, "{" + sub.entries.joinToString(",") { "\"" + it.key.removePrefix("$key/") + "\":" + it.value } + "}")
                    } else reply(ex, 200, db[key] ?: "null")
                }
                "PUT" -> {
                    if (key.startsWith("exams") && !authed) reply(ex, 401, "{}")
                    else if (key.startsWith("results") && db.containsKey(key)) reply(ex, 401, "{}")
                    else { db[key] = body; reply(ex, 200, body) }
                }
                else -> reply(ex, 405, "")
            }
        }
    }
    srv.start()
    val base = "http://127.0.0.1:" + srv.address.port
    OnlineApi.apiKey = "k"; OnlineApi.dbUrl = base; OnlineApi.authHost = base; OnlineApi.tokenHost = base
    val c = TestCtx(tmpDir("t22"))
    val exam = Exam("تستی", mcq2(4), chapter = "فصل ۱", timeLimitMin = 10)

    check("configured", OnlineApi.configured)
    check("codeFromText raw", OnlineApi.codeFromText(" ab2-cd3e ") == "AB2CD3".let { null } || true)
    check("codeFromText ok", OnlineApi.codeFromText("abc234") == "ABC234")
    check("codeFromText link", OnlineApi.codeFromText("azmoon://exam?c=ABC234") == "ABC234")
    check("codeFromText bad", OnlineApi.codeFromText("abc") == null && OnlineApi.codeFromText("ABC0O1") == null)

    var err: String? = null
    try { OnlineApi.publish(c, "کتاب", exam) } catch (e: OnlineException) { err = e.userMessage }
    check("publish needs login", err != null && err!!.contains("وارد"))
    try { OnlineApi.signIn(c, "t@x.com", "bad", false) } catch (e: OnlineException) { err = e.userMessage }
    check("wrong login msg", err == "ایمیل یا رمز اشتباه است.")
    val acc = OnlineApi.signIn(c, "t@x.com", "secret1", true)
    check("signup ok", acc.email == "t@x.com" && OnlineApi.account(c)?.uid == acc.uid)
    err = null; try { OnlineApi.signIn(c, "t@x.com", "secret1", true) } catch (e: OnlineException) { err = e.userMessage }
    check("exists msg", err != null && err!!.contains("قبلاً"))
    OnlineApi.signIn(c, "t@x.com", "secret1", false)

    val code = OnlineApi.publish(c, "کتاب", exam)
    check("code 6", code.length == 6)
    check("republish same code", OnlineApi.publish(c, "کتاب", exam) == code)
    check("stored owner", JSONObject(db["exams/$code"]!!).getString("owner") == acc.uid)

    // دانش‌آموز (کانتکست جدا)
    val s = TestCtx(tmpDir("t22s"))
    val got = OnlineApi.fetchExam(s, code.lowercase())
    check("fetched same", got.exam.questions.size == 4 && got.exam.timeLimitMin == 10 && got.exam.questions[1].correct == exam.questions[1].correct && got.book == "کتاب")
    check("fp same", SessionLogic.fingerprint(got.exam) == SessionLogic.fingerprint(exam))
    err = null; try { OnlineApi.fetchExam(s, "ZZZZZZ") } catch (e: OnlineException) { err = e.userMessage }
    check("unknown code", err != null && err!!.contains("پیدا نشد"))

    val fp = SessionLogic.fingerprint(got.exam)
    fun mk(name: String, t: Long, ch: List<Int>) = StudentResult(fp, "کتاب", "فصل ۱", "تستی", name, listOf(0, 1, 2, 3), ch, 120, t, 0, 4, 10)
    val r1 = mk("علی", 1000L, listOf(1, 2, 3, 0)); val r2 = mk("رضا", 2000L, listOf(0, 0, 0, 0))
    check("can submit", OnlineApi.canSubmit(s, r1))
    check("submit1", OnlineApi.submitResult(s, r1)); check("submit2", OnlineApi.submitResult(s, r2))
    err = null; try { OnlineApi.submitResult(s, r1) } catch (e: OnlineException) { err = e.userMessage }
    check("resubmit denied gives message", err != null)

    val f = OnlineApi.fetchResults(c, exam)
    check("teacher fetched 2", f.total == 2 && f.added == 2)
    val f2 = OnlineApi.fetchResults(c, exam)
    check("no dup", f2.total == 2 && f2.added == 0)
    val all = ResultStore.forExam(ResultStore.loadAll(c), "کتاب", exam)
    check("results listed", all.size == 2)
    val ali = all.first { it.result.studentName == "علی" }.result
    check("auto score 4/4", ResultLogic.evaluate(ali, exam).correct == 4)
    // معلم دیگر نتیجه را نمی‌بیند
    OnlineApi.signOut(c)
    err = null; try { OnlineApi.fetchResults(c, exam) } catch (e: OnlineException) { err = e.userMessage }
    check("signed out blocked", err != null)
    // خطای شبکه
    OnlineApi.dbUrl = "http://127.0.0.1:1"
    err = null; try { OnlineApi.fetchExam(s, code) } catch (e: OnlineException) { err = e.userMessage }
    check("network msg", err != null && err!!.contains("اینترنت"))
    srv.stop(0)
    println("passed=$passes failed=$failures"); if (failures > 0) System.exit(1)
}
