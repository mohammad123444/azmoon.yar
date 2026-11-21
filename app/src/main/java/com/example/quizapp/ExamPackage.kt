package com.example.quizapp

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * ارسال آزمون‌های فایل‌دار و پشتیبان‌گیری به‌صورت یک فایل zip (شامل JSON + فایل‌های PDF و عکس).
 * فایل‌ها هنگام خواندن با نام تازه داخل برنامه ذخیره می‌شوند.
 */
object ExamPackage {
    private const val EXAM_ENTRY = "exam.json"
    private const val BACKUP_ENTRY = "backup.json"
    private const val FILES = "files/"
    private val allowedExt = setOf("pdf", "png", "jpg", "jpeg", "webp")

    // ---------- نوشتن ----------

    private fun addFile(context: Context, zip: ZipOutputStream, name: String) {
        val f = ExamFiles.file(context, name)
        if (!f.exists()) return
        zip.putNextEntry(ZipEntry(FILES + name))
        f.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun examJson(bookName: String, exam: Exam): String {
        val qArr = JSONArray()
        for (q in exam.questions) {
            val qo = JSONObject().put("q", q.q).put("a", q.a).put("d", q.addedAt).put("x", q.analysis)
            ExamStore.putTestFields(qo, q)
            qArr.put(qo)
        }
        return JSONObject()
            .put("app", "azmoonsaz")
            .put("v", 2)
            .put("b", bookName)
            .put("t", exam.title)
            .put("q", qArr)
            .put("qf", JSONArray(exam.questionFiles))
            .put("af", JSONArray(exam.answerFiles))
            .put("c", exam.chapter)
            .apply { if (exam.timeLimitMin > 0) put("tl", exam.timeLimitMin) }
            .toString()
    }

    fun writeExam(context: Context, bookName: String, exam: Exam, out: OutputStream) {
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.setLevel(1)
            zip.putNextEntry(ZipEntry(EXAM_ENTRY))
            zip.write(examJson(bookName, exam).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            (exam.questionFiles + exam.answerFiles).distinct().forEach { addFile(context, zip, it) }
        }
    }

    fun writeBackup(context: Context, books: List<Book>, out: OutputStream) {
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.setLevel(1)
            zip.putNextEntry(ZipEntry(BACKUP_ENTRY))
            zip.write(ExamStore.toBackupJson(books).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            ExamFiles.referenced(books).forEach { addFile(context, zip, it) }
        }
    }

    // ---------- خواندن ----------

    private class ZipContent(val json: String, val nameMap: Map<String, String>)

    /** فایل‌هایی که هنگام خواندنِ یک فایل ناقص یا نامعتبر کپی شده‌اند پاک می‌شوند تا چیزی در برنامه نماند */
    private fun deleteExtracted(context: Context, names: Collection<String>) {
        names.forEach { try { ExamFiles.file(context, it).delete() } catch (e: Exception) { /* نادیده گرفته می‌شود */ } }
    }

    private fun readZip(context: Context, input: InputStream, jsonEntry: String): ZipContent? {
        val map = HashMap<String, String>()
        var json: String? = null
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (entryName == jsonEntry) {
                        json = zip.readBytes().toString(Charsets.UTF_8)
                    } else if (entryName.startsWith(FILES) && !entry.isDirectory) {
                        val old = entryName.removePrefix(FILES).substringAfterLast('/')
                        val ext = old.substringAfterLast('.', "").lowercase()
                        if (ext in allowedExt) {
                            val newName = ExamFiles.newName(if (ext == "jpeg") "jpg" else ext)
                            map[old] = newName
                            ExamFiles.file(context, newName).outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            deleteExtracted(context, map.values)
            throw e
        }
        val j = json
        if (j == null) {
            deleteExtracted(context, map.values)
            return null
        }
        return ZipContent(j, map)
    }

    private fun strings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.getString(i))
        return list
    }

    fun readExam(context: Context, input: InputStream): SharedExam? {
        val content = readZip(context, input, EXAM_ENTRY) ?: return null
        val result: SharedExam? = try {
            val obj = JSONObject(content.json)
            val qArr = obj.optJSONArray("q") ?: JSONArray()
            val now = System.currentTimeMillis()
            val questions = mutableListOf<Question>()
            for (i in 0 until qArr.length()) {
                val o = qArr.getJSONObject(i)
                val d = o.optLong("d", 0L)
                val (opts, k) = ExamStore.readOptions(o)
                questions.add(
                    Question(o.getString("q"), o.getString("a"), if (d > 0L) d else now, o.optString("x", ""), opts, k)
                )
            }
            val qf = strings(obj.optJSONArray("qf")).mapNotNull { content.nameMap[it] }
            val af = strings(obj.optJSONArray("af")).mapNotNull { content.nameMap[it] }
            if (questions.isEmpty() && qf.isEmpty()) {
                null
            } else {
                val book = obj.optString("b", "").trim().ifEmpty { "کتاب دریافتی" }
                val title = obj.optString("t", "").trim().ifEmpty { "آزمون دریافتی" }
                SharedExam(book, Exam(title, questions, qf, af, obj.optString("c", "").trim(), obj.optLong("tl", 0L).toInt().coerceIn(0, 600)))
            }
        } catch (e: Exception) {
            null
        }
        if (result == null) deleteExtracted(context, content.nameMap.values)
        return result
    }

    private fun isZip(input: BufferedInputStream): Boolean {
        input.mark(8)
        val head = ByteArray(2)
        val n = input.read(head)
        input.reset()
        return n == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
    }

    /** فایل پشتیبان: zip (جدید) یا JSON (قدیمی) */
    fun readBackup(context: Context, input: InputStream): List<Book>? {
        val buffered = BufferedInputStream(input)
        if (!isZip(buffered)) {
            return ExamStore.fromBackupJson(buffered.readBytes().toString(Charsets.UTF_8))
        }
        val content = readZip(context, buffered, BACKUP_ENTRY) ?: return null
        val books = ExamStore.fromBackupJson(content.json)
        if (books == null) {
            deleteExtracted(context, content.nameMap.values)
            return null
        }
        return books.map { b ->
            b.copy(
                exams = b.exams.map { e ->
                    e.copy(
                        questionFiles = e.questionFiles.mapNotNull { content.nameMap[it] },
                        answerFiles = e.answerFiles.mapNotNull { content.nameMap[it] }
                    )
                }
            )
        }
    }

    /** نتیجهٔ خواندن فایلِ رسیده: یا یک آزمون است یا یک فایل پشتیبان کامل */
    class Incoming(val exam: SharedExam? = null, val backup: List<Book>? = null, val pdf: Boolean = false)

    /** نام اولین فایل داخل zip (برای تشخیص آزمون یا پشتیبان)؛ اگر zip نباشد null */
    private fun firstEntryName(context: Context, uri: Uri): String? {
        val raw = context.contentResolver.openInputStream(uri) ?: return null
        return BufferedInputStream(raw).use { b ->
            if (!isZip(b)) null else ZipInputStream(b).nextEntry?.name
        }
    }

    /** فایلی که از بیرون رسیده یا انتخاب شده: آزمون یا پشتیبان؛ اگر هیچ‌کدام نبود null */
    fun readIncomingFromUri(context: Context, uri: Uri): Incoming? {
        // فایل PDF: برای انتخاب صفحه‌های سوال و پاسخ به صندوق PDF می‌رود
        if (PdfInbox.importFromUri(context, uri)) return Incoming(pdf = true)
        if (firstEntryName(context, uri) == BACKUP_ENTRY) {
            val books = context.contentResolver.openInputStream(uri)?.use { readBackup(context, it) } ?: return null
            return Incoming(backup = books)
        }
        val exam = readSharedFromUri(context, uri)
        if (exam != null) return Incoming(exam = exam)
        // پشتیبانِ قدیمیِ JSON
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }
        if (text != null && text.contains("\"books\"")) {
            val books = ExamStore.fromBackupJson(text)
            if (books != null) return Incoming(backup = books)
        }
        return null
    }

    /** آزمونِ ارسالی (zip، JSON یا متن) را از یک فایل می‌خواند */
    fun readSharedFromUri(context: Context, uri: Uri): SharedExam? {
        val raw = context.contentResolver.openInputStream(uri) ?: return null
        val buffered = BufferedInputStream(raw)
        return buffered.use { ins ->
            if (isZip(ins)) readExam(context, ins)
            else ExamText.fromShareText(ins.readBytes().toString(Charsets.UTF_8))
        }
    }
}
