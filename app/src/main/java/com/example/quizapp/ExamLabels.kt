package com.example.quizapp

/** عنوان آزمون در فهرست‌ها */
fun examLabel(e: Exam): String {
    if (!e.isFileExam) {
        val kind = if (e.isTest) "تستی" else ""
        val time = if (e.timeLimitMin > 0) "${e.timeLimitMin} دقیقه" else ""
        val extra = listOf(kind, time).filter { it.isNotEmpty() }.joinToString("، ")
        val base = "${e.title} (${e.questions.size} سوال" + (if (extra.isNotEmpty()) "، $extra" else "") + ")"
        return if (e.isTest) "📝 $base" else base
    }
    val answers = if (e.answerFiles.isNotEmpty()) "پاسخ: فایل" else "${e.questions.size} پاسخ"
    return "📎 ${e.title} (${e.questionFiles.size} فایل سوال، $answers)"
}
