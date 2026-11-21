package com.example.quizapp
import java.io.*
import java.util.zip.*

fun zipOf(vararg e: Pair<String, ByteArray>): ByteArray {
    val bo = ByteArrayOutputStream()
    ZipOutputStream(bo).use { z -> for ((n, b) in e) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
    return bo.toByteArray()
}
fun mk(t: String, n: Int, ch: String = "فصل ۱") = Exam(t, (1..n).map { Question("سوال $it", "پاسخ $it") }, emptyList(), emptyList(), ch)

fun main(args: Array<String>) {
    val fx = File(args[0])
    // ---------- جست‌وجو ----------
    val books = listOf(
        Book("عربی هفتم", listOf(mk("درس اول", 3, "فصل یکم"), mk("درس دوم", 2, "فصل دوم"))),
        Book("ریاضی ۸", listOf(mk("آزمون جمع", 4, "فصل ۱"), mk("آزمون ضرب", 4, "فصل ۲")))
    )
    check("norm ي ك", ExamSearch.normalize("كتاب علي") == ExamSearch.normalize("کتاب علی"))
    check("norm digits", ExamSearch.normalize("ریاضی ۸") == ExamSearch.normalize("ریاضی 8"))
    check("norm zwnj", ExamSearch.normalize("کتاب‌ها") == ExamSearch.normalize("کتابها"))
    check("empty query", ExamSearch.search(books, "   ").isEmpty())
    check("book name", ExamSearch.search(books, "عربی").size == 2)
    check("title", ExamSearch.search(books, "جمع").map { it.exam.title } == listOf("آزمون جمع"))
    check("chapter", ExamSearch.search(books, "فصل دوم").map { it.exam.title } == listOf("درس دوم"))
    check("AND tokens", ExamSearch.search(books, "ریاضی ضرب").size == 1)
    check("digit match", ExamSearch.search(books, "ریاضی 8 فصل 2").size == 1)
    check("no result", ExamSearch.search(books, "فیزیک").isEmpty())
    check("title ranks first", ExamSearch.search(books, "آزمون").size == 2)
    check("summary", ExamSearch.bookSummary(books[0]) == "2 فصل • 2 آزمون")

    // ---------- منطق وضعیت ----------
    val e = mk("آزمون الف", 5)
    val bk = listOf(Book("کتاب", listOf(e)))
    val fp = SessionLogic.fingerprint(e)
    check("fp stable", fp == SessionLogic.fingerprint(e.copy()))
    check("fp changes", fp != SessionLogic.fingerprint(e.copy(questions = e.questions.drop(1))))
    check("fp answer change", fp != SessionLogic.fingerprint(e.copy(questions = e.questions.map { it.copy(a = it.a + "x") })))
    val ord = SessionLogic.pickOrder(5, 3, true)
    check("pick size", ord.size == 3 && ord.toSet().size == 3 && ord.all { it in 0..4 })
    check("pick all", SessionLogic.pickOrder(5, 99, false) == listOf(0, 1, 2, 3, 4))
    check("pick zero", SessionLogic.pickOrder(5, 0, false).isEmpty())
    fun sess(i: Int = 2, fin: Boolean = false, order: List<Int> = listOf(0, 1, 2), ans: Map<Int, String> = mapOf(0 to "x"), mk: Map<Int, Boolean> = emptyMap(), t: Long = 10, mode: Int = 1, f: String = fp) =
        SavedSession("کتاب", e.chapter, e.title, f, mode, order, i, fin, ans, mk, t)
    check("resolve ok", SessionLogic.resolve(sess(), bk) != null)
    check("resolve edited", SessionLogic.resolve(sess(f = "zz"), bk) == null)
    check("resolve missing exam", SessionLogic.resolve(sess(), listOf(Book("کتاب", emptyList()))) == null)
    check("resolve dup order", SessionLogic.resolve(sess(order = listOf(0, 0, 1)), bk) == null)
    check("resolve oob order", SessionLogic.resolve(sess(order = listOf(0, 9)), bk) == null)
    check("resolve idx oob", SessionLogic.resolve(sess(i = 3), bk) == null)
    check("resolve bad answer key", SessionLogic.resolve(sess(ans = mapOf(7 to "a")), bk) == null)
    check("resolve bad mode", SessionLogic.resolve(sess(mode = 5), bk) == null)
    check("meaningful fresh", !SessionLogic.isMeaningful(sess(i = 0, ans = emptyMap())))
    check("meaningful blank ans", !SessionLogic.isMeaningful(sess(i = 0, ans = mapOf(0 to "  "))))
    check("meaningful idx", SessionLogic.isMeaningful(sess(i = 1, ans = emptyMap())))
    check("meaningful fin", SessionLogic.isMeaningful(sess(i = 0, fin = true, ans = emptyMap())))
    check("latest", SessionLogic.latestValid(listOf(sess(t = 1), sess(t = 5).copy(title = "نیست")), bk)!!.first.updatedAt == 1L)
    check("auto true", SessionLogic.autoMark(" پاسخ 1. ", "پاسخ 1") == true)
    check("auto arabic", SessionLogic.autoMark("علي", "علی") == true)
    check("auto null on diff", SessionLogic.autoMark("غلط", "پاسخ 1") == null)
    check("auto null on blank", SessionLogic.autoMark("", "") == null)
    val fm = SessionLogic.finalizeMarks(e.questions.take(3), mapOf(0 to "پاسخ 1", 1 to "چیز دیگر"), mapOf())
    check("finalize", fm[0] == true && fm[1] == null && fm[2] == false)
    val fm2 = SessionLogic.finalizeMarks(e.questions.take(3), mapOf(0 to "پاسخ 1"), mapOf(0 to false))
    check("finalize keeps manual", fm2[0] == false)
    val ty = SessionLogic.tally(3, mapOf(0 to true, 2 to false))
    check("tally", ty.correct == 1 && ty.wrong == 1 && ty.pending == 1)

    // ---------- SessionStore ----------
    val ctx = TestCtx(tmpDir("t20"))
    SessionStore.put(ctx, sess(i = 0, ans = emptyMap()))
    check("fresh not stored", SessionStore.loadAll(ctx).isEmpty())
    SessionStore.put(ctx, sess(t = 1))
    SessionStore.put(ctx, sess(i = 1, t = 2, ans = mapOf(0 to "a", 1 to "b")))
    val all = SessionStore.loadAll(ctx)
    check("no duplicates", all.size == 1 && all[0].index == 1 && all[0].answers.size == 2)
    check("roundtrip", all[0] == sess(i = 1, t = 2, ans = mapOf(0 to "a", 1 to "b")))
    SessionStore.put(ctx, sess(i = 0, ans = emptyMap()))
    check("fresh keeps old", SessionStore.loadAll(ctx).size == 1)
    SessionStore.put(ctx, sess(mk = mapOf(0 to true), fin = true).copy(title = "ب"))
    check("two sessions", SessionStore.loadAll(ctx).size == 2)
    SessionStore.prune(ctx, bk)
    check("prune invalid", SessionStore.loadAll(ctx).size == 1)
    SessionStore.remove(ctx, all[0].key)
    check("remove", SessionStore.loadAll(ctx).isEmpty())
    for (k in 1..15) SessionStore.put(ctx, sess(t = k.toLong()).copy(title = "t$k"))
    check("max 10", SessionStore.loadAll(ctx).size == 10)
    ctx.getSharedPreferences("exam_sessions", 0).edit().putString("sessions_v1", "{{garbage").commit()
    check("corrupt sessions ok", SessionStore.loadAll(ctx).isEmpty())
    ctx.getSharedPreferences("exam_sessions", 0).edit().putString("sessions_v1", "[{\"book\":\"x\"},5,{\"bad\":1}]").commit()
    check("partial corrupt", SessionStore.loadAll(ctx).isEmpty())

    // ---------- ExamStore ----------
    val c2 = TestCtx(tmpDir("t20b"))
    c2.getSharedPreferences("exam_store", 0).edit().putString("books_v2", "[{\"name\":\"x\",\"exams\":[{\"title\":3}").commit()
    check("corrupt load empty", ExamStore.load(c2).isEmpty())
    check("corrupt raw kept", c2.getSharedPreferences("exam_store", 0).getString("books_v2_corrupt", null)!!.startsWith("[{"))
    check("hasExam", ExamStore.hasExam(bk, "کتاب", e) && !ExamStore.hasExam(bk, "کتاب", e.copy(title = "z")))
    check("uniqueTitle", ExamStore.withUniqueTitle(bk, "کتاب", e).title == "آزمون الف (2)")
    val bk2 = ExamStore.saveExam(bk, "کتاب", null, ExamStore.withUniqueTitle(bk, "کتاب", e))
    check("uniqueTitle 3", ExamStore.withUniqueTitle(bk2, "کتاب", e).title == "آزمون الف (3)" && bk2[0].exams.size == 2)
    check("replace in place", ExamStore.saveExam(bk, "کتاب", null, e.copy(questions = listOf(Question("n", "m"))))[0].exams.size == 1)

    // ---------- سازگاری با فایل‌های v19 ----------
    val c3 = TestCtx(tmpDir("t20c"))
    c3.getSharedPreferences("exam_store", 0).edit().putString("books_v2", File(fx, "books_v2.json").readText()).commit()
    val loaded = ExamStore.load(c3)
    check("v19 books load", loaded.size == 1 && loaded[0].exams.size == 2 && loaded[0].exams[0].questions[0].analysis == "جمع" && loaded[0].chapters == listOf("فصل ۱", "فصل ۲"))
    check("v19 backup json", ExamStore.fromBackupJson(File(fx, "backup_legacy.json").readText())!![0].exams.size == 2)
    check("v19 link", ExamText.fromShareText(File(fx, "link.txt").readText())!!.exam.questions.size == 2)
    check("v19 sharetext", ExamText.fromShareText(File(fx, "sharetext.txt").readText())!!.exam.title == "آزمون ۱")
    val u1 = c3.put("exam.zip", File(fx, "exam.zip").readBytes())
    val i1 = FileInspector.inspect(c3, u1)
    check("inspect v19 exam", i1.kind == FileKind.EXAM && i1.detail.contains("2 سوال") && i1.name == "exam.zip")
    val inc1 = ExamPackage.readIncomingFromUri(c3, u1)
    check("import v19 exam", inc1?.exam?.exam?.questions?.size == 2 && inc1.exam!!.book == "ریاضی")
    val u2 = c3.put("fileexam.zip", File(fx, "fileexam.zip").readBytes())
    val i2 = FileInspector.inspect(c3, u2)
    check("inspect v19 file exam", i2.kind == FileKind.EXAM && i2.detail.contains("1 فایل سوال"))
    val inc2 = ExamPackage.readIncomingFromUri(c3, u2)
    check("import v19 file exam + file", inc2?.exam?.exam?.questionFiles?.size == 1 && ExamFiles.file(c3, inc2.exam!!.exam.questionFiles[0]).exists())
    val u3 = c3.put("backup.zip", File(fx, "backup.zip").readBytes())
    val i3 = FileInspector.inspect(c3, u3)
    check("inspect v19 backup", i3.kind == FileKind.BACKUP && i3.detail.contains("1 کتاب"))
    check("import v19 backup", ExamPackage.readIncomingFromUri(c3, u3)?.backup?.get(0)?.exams?.size == 2)

    // ---------- فایل‌های نامعتبر: بدون آسیب و بدون ماندن فایل اضافه ----------
    fun filesCount() = ExamFiles.dir(c3).listFiles()!!.size
    val before = filesCount()
    val bad = mapOf(
        "empty.bin" to ByteArray(0),
        "garbage.zip" to "PK\u0003\u0004 not really".toByteArray(),
        "trunc.zip" to File(fx, "fileexam.zip").readBytes().let { it.copyOf(it.size / 2) },
        "other.zip" to zipOf("a.txt" to "hi".toByteArray()),
        "noq.zip" to zipOf("exam.json" to "{\"t\":\"x\",\"b\":\"y\",\"q\":[]}".toByteArray(), "files/a.pdf" to "%PDF-1".toByteArray()),
        "badjson.zip" to zipOf("exam.json" to "{not json".toByteArray(), "files/a.pdf" to "%PDF-1".toByteArray()),
        "badbackup.zip" to zipOf("backup.json" to "[[[".toByteArray(), "files/a.pdf" to "%PDF-1".toByteArray()),
        "text.txt" to "سلام این یک متن است".toByteArray(),
        "rand.dat" to ByteArray(500) { (it * 7).toByte() }
    )
    for ((n, b) in bad) {
        val u = c3.put(n, b)
        val ins = FileInspector.inspect(c3, u)
        check("inspect invalid $n", ins.kind == FileKind.INVALID && ins.problem.isNotBlank())
        val r = try { ExamPackage.readIncomingFromUri(c3, u) } catch (x: Exception) { null }
        check("import invalid $n -> no result", r == null || (r.exam == null && r.backup == null && !r.pdf))
    }
    check("no orphan files after invalid", filesCount() == filesCount().coerceAtMost(before + 2) && ExamFiles.dir(c3).listFiles()!!.none { it.length() == 6L })
    val pu = c3.put("x.pdf", "%PDF-1.7 hello".toByteArray())
    val pi = FileInspector.inspect(c3, pu)
    check("inspect pdf", pi.kind == FileKind.PDF && pi.valid)
    val gone = FileInspector.inspect(c3, android.net.Uri.parse("content://t/missing"))
    check("inspect missing", gone.kind == FileKind.INVALID)
    c3.resolver.failOpen = true
    check("inspect io error", FileInspector.inspect(c3, u1).kind == FileKind.INVALID)
    c3.resolver.failOpen = false
    check("size text", FileInspector.sizeText(1536) == "1 کیلوبایت" && FileInspector.sizeText(3 * 1024 * 1024).startsWith("3.0"))

    // ---------- v20 می‌نویسد، خودش می‌خواند (رفت‌وبرگشت) ----------
    val c4 = TestCtx(tmpDir("t20d"))
    val bo = ByteArrayOutputStream(); ExamPackage.writeBackup(c4, bk2, bo)
    val ub = c4.put("b.zip", bo.toByteArray())
    check("v20 backup roundtrip", ExamPackage.readIncomingFromUri(c4, ub)?.backup?.get(0)?.exams?.size == 2)
    val sx = ExamText.fromShareText(ExamText.toShareLink("کتاب", e))
    check("v20 link roundtrip", sx?.exam?.questions?.size == 5)

    println("passed=$passes failed=$failures")
    if (failures > 0) System.exit(1)
}
