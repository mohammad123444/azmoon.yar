package com.example.quizapp

/** یک مورد (سوال یا پاسخ) به‌همراه شمارهٔ آن در متن (اگر شماره‌دار بود) */
data class Item(val number: Int?, val text: String)

data class PairResult(val pairs: List<Pair<String, String>>, val error: String?)

/** values: برای هر سوال یک متن (خالی یعنی تحلیل ندارد) */
data class AlignResult(val values: List<String>, val error: String?)

/**
 * تبدیل متن یکجا به فهرست سوال‌ها (یا پاسخ‌ها).
 * قانون تشخیص:
 *  ۱) اگر خط اول با شماره شروع شود (مثل «1.» «۲)» «3-») هر شمارهٔ بزرگ‌تر از شمارهٔ قبلی یک مورد جدید است
 *     (شماره‌های کوچک‌تر، مثلاً گزینه‌های ۱) ۲) ۳) ۴) داخل سوال، جزو همان سوال می‌مانند)
 *  ۲) در غیر این صورت، موردها با خط خالی جدا می‌شوند
 *  ۳) اگر خط خالی هم نباشد، هر خط یک مورد است
 */
object QuestionParser {

    private val numberPrefix = Regex("^\\s*(\\d{1,4})\\s*[.)\\-–—:،]\\s*")

    private fun normalizeDigits(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when (ch) {
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }


    private val keycap = Regex("(\\p{Nd})\\uFE0F?\\u20E3")
    private val bullet = Regex("^\\s*(?:[*•○◦▪]\\s+|[-–—](?=\\s+\\p{Nd})\\s*)")
    private val heading = Regex("^\\s*(?:#{1,6}|>)\\s*")
    private val wordPrefix =
        Regex("^\\s*(?:(?:پاسخ|جواب)\\s*)?(?:(?:سوال|سؤال)\\s*)?(?=\\p{Nd}{1,4}\\s*[.)\\-–—:،])")
    private val parenNumber = Regex("^\\s*[(\\[]\\s*(\\p{Nd}{1,4})\\s*[)\\]]\\s*")
    private const val ENTRY = "\\p{Nd}{1,3}\\s*[-.):]\\s*[^\\s,،;؛.)\\-:|]{1,10}"
    private val compactLine = Regex("^\\s*$ENTRY(?:[\\s,،;؛]+$ENTRY)+\\s*[,،;؛]?\\s*$")
    private val compactEntry = Regex("(\\p{Nd}{1,3})\\s*[-.):]\\s*([^\\s,،;؛.)\\-:|]{1,10})")

    /**
     * متن کپی‌شده (از هوش مصنوعی، سایت یا پیام‌رسان) را به خط‌های ساده «۱. متن» نزدیک می‌کند:
     * مارک‌داون (** # > گلوله)، «سوال ۱:»، «(۱)»، ۱️⃣، ردیف جدول | ۱ | ب |
     * و پاسخ‌نامهٔ فشرده‌ای مثل «۱-ب ۲-ج ۳-الف» که در یک خط آمده باشد.
     */
    private fun normalizeLines(text: String, compact: Boolean): List<String> {
        val out = ArrayList<String>()
        for (raw in text.replace("\r", "").replace("**", "").replace("__", "").split("\n")) {
            var line = raw.replace(keycap) { it.groupValues[1] + "." }
            line = line.replace(heading, "").replace(bullet, "")
            if (line.trim().startsWith("|")) {
                val cells = line.trim().trim('|').split("|").map { it.trim() }
                if (cells.all { c -> c.isEmpty() || c.all { ch -> ch == '-' || ch == ':' || ch == ' ' } }) continue
                val first = normalizeDigits(cells.firstOrNull().orEmpty())
                line = if (first.isNotEmpty() && first.all { it.isDigit() } && cells.size > 1) {
                    cells[0] + ". " + cells.drop(1).filter { it.isNotEmpty() }.joinToString(" - ")
                } else {
                    cells.filter { it.isNotEmpty() }.joinToString(" - ")
                }
            }
            line = line.replace(wordPrefix, "")
            line = line.replace(parenNumber) { it.groupValues[1] + ") " }
            if (compact && compactLine.matches(line)) {
                for (m in compactEntry.findAll(line)) out.add(m.groupValues[1] + ". " + m.groupValues[2])
            } else {
                out.add(line)
            }
        }
        return out
    }

    /** compact: برای فهرست پاسخ‌ها؛ پاسخ‌نامهٔ فشردهٔ «۱-ب ۲-ج» در یک خط را هم جدا می‌کند */
    fun parseItems(text: String, compact: Boolean = false): List<Item> {
        val lines = normalizeLines(text, compact)
        val firstLine = lines.firstOrNull { it.isNotBlank() } ?: return emptyList()

        // حالت ۱: شماره‌دار
        if (numberPrefix.containsMatchIn(normalizeDigits(firstLine))) {
            val numbers = mutableListOf<Int>()
            val texts = mutableListOf<StringBuilder>()
            for (line in lines) {
                if (line.isBlank()) continue
                val match = numberPrefix.find(normalizeDigits(line))
                val num = match?.groupValues?.get(1)?.toIntOrNull()
                if (match != null && num != null && (numbers.isEmpty() || num > numbers.last())) {
                    numbers.add(num)
                    // طول رشته بعد از نرمال‌سازی ارقام تغییر نمی‌کند
                    texts.add(StringBuilder(line.substring(match.range.last + 1).trim()))
                } else if (texts.isNotEmpty()) {
                    texts.last().append("\n").append(line.trim())
                }
            }
            return numbers.indices
                .map { Item(numbers[it], texts[it].toString().trim()) }
                .filter { it.text.isNotEmpty() }
        }

        // حالت ۲: جداشده با خط خالی
        if (lines.any { it.isBlank() }) {
            val items = mutableListOf<Item>()
            val current = mutableListOf<String>()
            for (line in lines) {
                if (line.isBlank()) {
                    if (current.isNotEmpty()) {
                        items.add(Item(null, current.joinToString("\n")))
                        current.clear()
                    }
                } else {
                    current.add(line.trim())
                }
            }
            if (current.isNotEmpty()) items.add(Item(null, current.joinToString("\n")))
            return items
        }

        // حالت ۳: هر خط یک مورد
        return lines.map { it.trim() }.filter { it.isNotEmpty() }.map { Item(null, it) }
    }

    fun parse(text: String): List<String> = parseItems(text).map { it.text }

    /**
     * جفت‌کردن سوال و پاسخ.
     * اگر هر دو فهرست شماره‌دار باشند، پاسخ «شمارهٔ n» دقیقاً به سوال «شمارهٔ n» وصل می‌شود
     * (نه بر اساس جایگاه). اگر شماره‌ها نخوانند، خطا برمی‌گردد تا پاسخ اشتباهی به سوال نچسبد.
     */
    fun pair(questions: List<Item>, answers: List<Item>): PairResult {
        if (questions.isEmpty()) return PairResult(emptyList(), null)

        val numbered = questions.all { it.number != null } &&
            answers.isNotEmpty() && answers.all { it.number != null }
        if (numbered) {
            val answerByNumber = answers.associateBy({ it.number!! }, { it.text })
            val hasDuplicates = answerByNumber.size != answers.size ||
                questions.map { it.number }.toSet().size != questions.size
            if (!hasDuplicates) {
                val missing = questions.mapNotNull { it.number }.filter { it !in answerByNumber }
                val extra = answerByNumber.keys.filter { k -> questions.none { it.number == k } }
                if (missing.isEmpty() && extra.isEmpty()) {
                    return PairResult(
                        questions.map { it.text to answerByNumber[it.number!!]!! },
                        null
                    )
                }
                val parts = mutableListOf<String>()
                if (missing.isNotEmpty()) parts.add("پاسخِ سوال‌های ${missing.joinToString("، ")} نوشته نشده")
                if (extra.isNotEmpty()) parts.add("پاسخ‌های ${extra.joinToString("، ")} سوالی ندارند")
                return PairResult(emptyList(), parts.joinToString(" | "))
            }
        }

        if (questions.size == answers.size) {
            return PairResult(questions.zip(answers) { q, a -> q.text to a.text }, null)
        }
        return PairResult(
            emptyList(),
            "تعداد سوال‌ها (${questions.size}) و پاسخ‌ها (${answers.size}) برابر نیست"
        )
    }

    /**
     * قرار دادن موارد اختیاری (پاسخ تحلیلی) کنار سوال‌ها.
     * اگر شماره‌دار باشند، تحلیل «شمارهٔ n» به سوال «شمارهٔ n» وصل می‌شود و لازم نیست برای همهٔ سوال‌ها تحلیل نوشته شود.
     * اگر بدون شماره باشند، تعدادشان باید با تعداد سوال‌ها برابر باشد.
     */
    fun alignOptional(questions: List<Item>, extras: List<Item>): AlignResult {
        if (extras.isEmpty()) return AlignResult(List(questions.size) { "" }, null)

        val keys = questions.mapIndexed { i, item -> item.number ?: (i + 1) }
        if (extras.all { it.number != null }) {
            val byNumber = extras.associateBy({ it.number!! }, { it.text })
            if (byNumber.size != extras.size) {
                return AlignResult(emptyList(), "شمارهٔ تکراری در پاسخ‌های تحلیلی")
            }
            val orphan = byNumber.keys.filter { it !in keys }
            if (orphan.isNotEmpty()) {
                return AlignResult(emptyList(), "تحلیلِ شمارهٔ ${orphan.joinToString("، ")} سوالی ندارد")
            }
            return AlignResult(keys.map { byNumber[it] ?: "" }, null)
        }
        if (extras.size == questions.size) {
            return AlignResult(extras.map { it.text }, null)
        }
        return AlignResult(
            emptyList(),
            "تحلیل‌های بدون شماره باید به تعداد سوال‌ها (${questions.size}) باشند؛ یا هر تحلیل را با شمارهٔ سوالش شروع کنید"
        )
    }
}
