package com.example.quizapp
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.json.JSONObject
import java.util.Random

fun main(args: Array<String>) {
    val long = (1..300).joinToString("\n") { "این جملهٔ شمارهٔ $it از متن کتاب است و چیزی دربارهٔ موضوع می‌گوید." }
    val ch = AiQuestions.splitText(long, 2000)
    check("split sizes", ch.isNotEmpty() && ch.all { it.length <= 2000 } && ch.size > 5)
    check("split keeps all text", ch.joinToString("\n").filter { it.isDigit() }.length >= long.filter { it.isDigit() }.length - 5)
    check("split empty", AiQuestions.splitText("  \n ").isEmpty())
    val huge = "الف".repeat(15000)
    check("split huge paragraph", AiQuestions.splitText(huge, 6000).all { it.length <= 6000 })
    val p = AiQuestions.plan(100, 10)
    check("plan sums", p.sumOf { it.second } == 10 && p.size == 10 && p.first().first == 0 && p.last().first == 99)
    check("plan few chunks", AiQuestions.plan(2, 7).sumOf { it.second } == 7 && AiQuestions.plan(2, 7).size == 2)
    check("plan 1 chunk", AiQuestions.plan(1, 5) == listOf(0 to 5))
    check("plan limits", AiQuestions.plan(500, 100).size == AiQuestions.MAX_CHUNKS && AiQuestions.plan(500, 100).sumOf { it.second } == 100)
    check("plan zero", AiQuestions.plan(0, 5).isEmpty() && AiQuestions.plan(3, 0).isEmpty())

    val good = """```json
[{"q":"پایتخت ایران؟","o":["تهران","شیراز","تبریز","اصفهان"],"k":0},
 {"q":"بد: سه گزینه","o":["a","b","c"],"k":1},
 {"q":"بد: تکراری","o":["a","a","b","c"],"k":1},
 {"q":"بد: k","o":["a","b","c","d"],"k":7},
 {"q":"","o":["a","b","c","d"],"k":1},
 {"q":"دو و دو؟","o":["۳","۴","۵","۶"],"k":1}]
```"""
    val qs = AiQuestions.parse(good, Random(5))
    check("parse keeps valid only", qs.size == 2)
    check("parse correct preserved", qs[0].options[qs[0].correct] == "تهران" && qs[1].options[qs[1].correct] == "۴")
    check("parse isTest", qs.all { it.isTest && it.options.size == 4 && it.a == it.options[it.correct] })
    var positions = HashSet<Int>()
    for (s in 0..40) positions.add(AiQuestions.parse(good, Random(s.toLong()))[0].correct)
    check("shuffle varies position", positions.size >= 3)
    check("parse garbage", AiQuestions.parse("نمی‌دانم").isEmpty() && AiQuestions.parse("[ not json").isEmpty())

    var lastAuth = ""; var lastBody = ""; var mode = 0
    val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    srv.createContext("/") { ex ->
        lastAuth = ex.requestHeaders.getFirst("Authorization").orEmpty()
        lastBody = ex.requestBody.readBytes().toString(Charsets.UTF_8)
        val (code, resp) = when (mode) {
            0 -> 200 to JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", good)))).toString()
            1 -> 401 to "{}"
            2 -> 402 to "{}"
            else -> 200 to "{\"x\":1}"
        }
        val b = resp.toByteArray(Charsets.UTF_8); ex.sendResponseHeaders(code, b.size.toLong()); ex.responseBody.use { it.write(b) }
    }
    srv.start()
    val cfg = AiConfig("sk-test", "http://127.0.0.1:${srv.address.port}/", "deepseek-chat")
    val r = AiQuestions.generate(cfg, "متن درس", 3)
    check("generate ok", r.size == 2)
    check("auth header", lastAuth == "Bearer sk-test")
    val sent = JSONObject(lastBody)
    check("body model+text", sent.getString("model") == "deepseek-chat" && lastBody.contains("متن درس") && sent.getJSONArray("messages").length() == 2)
    mode = 1; var e: String? = null; try { AiQuestions.generate(cfg, "x", 1) } catch (x: OnlineException) { e = x.userMessage }
    check("401 msg", e != null && e!!.contains("کلید"))
    mode = 2; e = null; try { AiQuestions.generate(cfg, "x", 1) } catch (x: OnlineException) { e = x.userMessage }
    check("402 msg", e != null && e!!.contains("اعتبار"))
    mode = 3; e = null; try { AiQuestions.generate(cfg, "x", 1) } catch (x: OnlineException) { e = x.userMessage }
    check("bad reply msg", e != null)
    e = null; try { AiQuestions.generate(cfg.copy(key = ""), "x", 1) } catch (x: OnlineException) { e = x.userMessage }
    check("no key", e != null)
    srv.stop(0)
    val c = TestCtx(tmpDir("t23"))
    check("config default", AiQuestions.loadConfig(c).baseUrl == AiQuestions.DEFAULT_BASE)
    AiQuestions.saveConfig(c, AiConfig(" k ", "http://x", "m"))
    check("config roundtrip", AiQuestions.loadConfig(c) == AiConfig("k", "http://x", "m"))
    println("passed=$passes failed=$failures"); if (failures > 0) System.exit(1)
}
