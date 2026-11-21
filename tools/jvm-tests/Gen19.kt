package com.example.quizapp
import java.io.*
// Runs against ORIGINAL v19 sources: writes fixtures
fun main(args: Array<String>) {
    val out = File(args[0]); out.mkdirs()
    val ctx = TestCtx(tmpDirX())
    val pdfName = "q1.pdf"
    ExamFiles.file(ctx, pdfName).writeBytes("%PDF-1.4 fake".toByteArray())
    val textExam = Exam("آزمون ۱", listOf(Question("2+2?", "4", 111L, "جمع"), Question("پایتخت؟", "تهران", 222L)), emptyList(), emptyList(), "فصل ۱")
    val fileExam = Exam("آزمون PDF", emptyList(), listOf(pdfName), emptyList(), "فصل ۱")
    val books = listOf(Book("ریاضی", listOf(textExam, fileExam), listOf("فصل ۱", "فصل ۲")))
    ExamStore.save(ctx, books)
    File(out, "books_v2.json").writeText(ctx.getSharedPreferences("exam_store", 0).getString("books_v2", null)!!)
    FileOutputStream(File(out, "exam.zip")).use { ExamPackage.writeExam(ctx, "ریاضی", textExam, it) }
    FileOutputStream(File(out, "fileexam.zip")).use { ExamPackage.writeExam(ctx, "ریاضی", fileExam, it) }
    FileOutputStream(File(out, "backup.zip")).use { ExamPackage.writeBackup(ctx, books, it) }
    File(out, "link.txt").writeText(ExamText.toShareLink("ریاضی", textExam))
    File(out, "sharetext.txt").writeText(ExamText.toShareText("ریاضی", textExam))
    File(out, "backup_legacy.json").writeText(ExamStore.toBackupJson(books))
    println("fixtures written")
}
fun tmpDirX(): File = java.nio.file.Files.createTempDirectory("g19").toFile()
