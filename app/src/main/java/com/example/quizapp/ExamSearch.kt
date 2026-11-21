package com.example.quizapp

/** یک نتیجهٔ جست‌وجو: آزمون و کتابی که در آن است */
data class SearchHit(val bookName: String, val exam: Exam, val score: Int)

/**
 * جست‌وجوی نام کتاب، فصل و عنوان آزمون.
 * فارسی و عربی (ی/ي، ک/ك، رقم‌ها، نیم‌فاصله، اعراب) یکسان دیده می‌شوند.
 * همهٔ کلمه‌های جست‌وجو باید در نام کتاب یا فصل یا عنوان آزمون پیدا شوند.
 */
object ExamSearch {
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when {
                ch == 'ي' || ch == 'ى' -> sb.append('ی') // ي ى ← ی
                ch == 'ك' -> sb.append('ک') // ك ← ک
                ch == 'ة' -> sb.append('ه') // ة ← ه
                ch == 'آ' || ch == 'أ' || ch == 'إ' -> sb.append('ا') // آ أ إ ← ا
                ch in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                ch in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                ch == '‌' || ch == '‍' || ch == 'ـ' -> Unit // نیم‌فاصله و کشیده
                ch in 'ً'..'ٟ' || ch == 'ٰ' -> Unit // اعراب
                ch.isWhitespace() -> sb.append(' ')
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString().trim().replace(Regex(" +"), " ")
    }

    fun tokens(query: String): List<String> =
        normalize(query).split(' ').filter { it.isNotEmpty() }

    /** نتیجه‌ها به ترتیب بیشترین تطابق؛ برای جست‌وجوی خالی فهرست خالی برمی‌گردد */
    fun search(books: List<Book>, query: String): List<SearchHit> {
        val words = tokens(query)
        if (words.isEmpty()) return emptyList()
        val hits = mutableListOf<SearchHit>()
        for (book in books) {
            val bookN = normalize(book.name)
            for (exam in book.exams) {
                val titleN = normalize(exam.title)
                val chapterN = normalize(exam.chapter)
                var score = 0
                var all = true
                for (w in words) {
                    var s = 0
                    if (titleN.contains(w)) s += 3
                    if (chapterN.contains(w)) s += 2
                    if (bookN.contains(w)) s += 1
                    if (s == 0) {
                        all = false
                        break
                    }
                    score += s
                }
                if (all) hits.add(SearchHit(book.name, exam, score))
            }
        }
        return hits.sortedByDescending { it.score } // مرتب‌سازی پایدار: در امتیاز برابر، ترتیب اصلی حفظ می‌شود
    }

    /** «۳ فصل • ۱۲ آزمون» برای فهرست کتاب‌ها */
    fun bookSummary(book: Book): String {
        val chapters = book.allChapters.count { book.examsIn(it).isNotEmpty() }
        val exams = book.exams.size
        return if (chapters > 0) "$chapters فصل • $exams آزمون" else "$exams آزمون"
    }
}
