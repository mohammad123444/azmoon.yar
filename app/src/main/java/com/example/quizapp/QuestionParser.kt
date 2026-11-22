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
    private const val MAX_NUMERIC_OPTIONS = 4

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
            // گزینه‌های عددی (۱) ۲) ۳) ۴)) داخل سوال: وقتی گزینهٔ «۱» دیده شد، تا گزینهٔ «۴» جزو همان سوال می‌مانند،
            // حتی اگر شماره‌شان از شمارهٔ سوال بزرگ‌تر باشد (مثلاً گزینهٔ ۲ در سوال ۱)
            var optionNext = 0
            for (line in lines) {
                if (line.isBlank()) continue
                val match = numberPrefix.find(normalizeDigits(line))
                val num = match?.groupValues?.get(1)?.toIntOrNull()
                val isOption = optionNext in 2..MAX_NUMERIC_OPTIONS && num == optionNext && texts.isNotEmpty()
                if (isOption) {
                    optionNext = if (optionNext >= MAX_NUMERIC_OPTIONS) 0 else optionNext + 1
                    texts.last().append("\n").append(line.trim())
                } else if (match != null && num != null && (numbers.isEmpty() || num > numbers.last())) {
                    numbers.add(num)
                    optionNext = 0
                    // طول رشته بعد از نرمال‌سازی ارقام تغییر نمی‌کند
                    texts.add(StringBuilder(line.substring(match.range.last + 1).trim()))
                } else if (texts.isNotEmpty()) {
                    if (num == 1 && match != null) optionNext = 2
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

    // ---------- سوال چندگزینه‌ای: تشخیص گزینه‌ها و پیدا کردن گزینهٔ درست ----------

    /** سوالِ جداشده: متن سوال + گزینه‌ها (اگر گزینه نداشته باشد options خالی است) */
    data class Split(val stem: String, val options: List<String>)

    /** سوالِ آمادهٔ ذخیره: options خالی یعنی تشریحی؛ وگرنه correct شمارهٔ گزینهٔ درست (از صفر) */
    data class Built(val q: String, val a: String, val options: List<String>, val correct: Int)

    data class BuildResult(val items: List<Built>, val error: String?)

    private const val MAX_OPTIONS = 6
    private val persianLetters = listOf("الف", "ب", "ج", "د", "ه", "و")
    private val latinLetters = listOf("a", "b", "c", "d", "e", "f")

    /** نشانهٔ گزینه‌ها: ۱) ۲) ... ، الف) ب) ... ، A) B) ... */
    private fun markerRegex(kind: Int, index: Int): Regex {
        val token = when (kind) {
            0 -> (index + 1).toString()
            1 -> persianLetters[index]
            else -> latinLetters[index]
        }
        val opt = if (kind == 2) "(?i)" else ""
        return Regex("$opt^\\s*[(\\[]?\\s*" + token + "\\s*[)\\].:\\-–—]\\s*")
    }

    private fun inlineMarker(kind: Int, index: Int): Regex {
        val token = when (kind) {
            0 -> (index + 1).toString()
            1 -> persianLetters[index]
            else -> latinLetters[index]
        }
        val opt = if (kind == 2) "(?i)" else ""
        return Regex(opt + "(?<![\\p{L}\\p{Nd}])[(\\[]?" + token + "\\s*[)\\].:\\-–—]")
    }

    /**
     * گزینه‌ها را از انتهای متن یک سوال جدا می‌کند. هر دو قالب پشتیبانی می‌شود:
     * هر گزینه در یک خط («۱) ...» «الف) ...» «A) ...») یا همهٔ گزینه‌ها در یک خط.
     * اگر دست‌کم دو گزینهٔ پشت‌سرهم پیدا نشود، سوال گزینه ندارد (تشریحی است).
     */
    fun splitOptions(text: String): Split {
        val raw = text.replace("\r", "").trim()
        val lines = raw.split("\n")
        val norm = lines.map { normalizeDigits(it) }

        // حالت ۱: هر گزینه در یک خط
        for (kind in 0..2) {
            for (start in lines.indices) {
                if (!markerRegex(kind, 0).containsMatchIn(norm[start])) continue
                val opts = mutableListOf<StringBuilder>()
                var next = 0
                for (li in start until lines.size) {
                    val m = if (next < MAX_OPTIONS) markerRegex(kind, next).find(norm[li]) else null
                    if (m != null) {
                        opts.add(StringBuilder(lines[li].substring(m.range.last + 1).trim()))
                        next++
                    } else if (opts.isNotEmpty() && lines[li].isNotBlank()) {
                        opts.last().append(" ").append(lines[li].trim())
                    }
                }
                val cleaned = opts.map { it.toString().trim() }
                if (cleaned.size in 2..MAX_OPTIONS && cleaned.all { it.isNotEmpty() }) {
                    return Split(lines.subList(0, start).joinToString("\n").trim(), cleaned)
                }
            }
        }

        // حالت ۲: همهٔ گزینه‌ها در یک خط («۱) الف ۲) ب ۳) ج ۴) د»)
        val normAll = normalizeDigits(raw)
        for (kind in 0..2) {
            val first = inlineMarker(kind, 0).findAll(normAll).toList()
            for (f in first.reversed()) {
                val positions = mutableListOf(f.range.first to f.range.last + 1)
                var from = f.range.last + 1
                var idx = 1
                while (idx < MAX_OPTIONS) {
                    val m = inlineMarker(kind, idx).find(normAll, from) ?: break
                    positions.add(m.range.first to m.range.last + 1)
                    from = m.range.last + 1
                    idx++
                }
                if (positions.size >= 2) {
                    val opts = positions.indices.map { i ->
                        val s = positions[i].second
                        val e = if (i + 1 < positions.size) positions[i + 1].first else raw.length
                        raw.substring(s, e).trim()
                    }
                    if (opts.all { it.isNotEmpty() }) {
                        return Split(raw.substring(0, positions[0].first).trim(), opts)
                    }
                }
            }
        }
        return Split(raw, emptyList())
    }

    private fun looseCompare(s: String): String =
        normalizeDigits(s).replace("**", "").replace(Regex("[\\s.,،؛;:!؟?\"'«»()\\[\\]\\-–—_/]+"), " ").trim().lowercase()

    /** پاسخ نوشته‌شده («۲»، «ب»، «گزینهٔ ۳»، «ج) متن گزینه»، یا خودِ متن گزینه) را به شمارهٔ گزینه (از صفر) تبدیل می‌کند؛ نشد null */
    fun resolveAnswer(answer: String, options: List<String>): Int? {
        if (options.size < 2) return null
        var a = normalizeDigits(answer.replace("**", "")).trim()
        a = a.replace(Regex("^(?:پاسخ|جواب|گزینه(?:\u200c|\\s)?(?:ی)?\\s*(?:صحیح|درست)?)\\s*[:：]?\\s*"), "")
        a = a.replace(Regex("^(?:صحیح|درست)\\s*[:：]?\\s*"), "").trim()
        a = a.trimStart('(', '[').trim()

        Regex("^(\\d{1,2})(?!\\d)\\s*(?:[)\\].:\\-–—]|\\s|$)").find(a)?.let { m ->
            val n = m.groupValues[1].toInt()
            if (n in 1..options.size) return n - 1
        }
        for (i in options.indices.take(persianLetters.size)) {
            if (Regex("^" + persianLetters[i] + "(?=\\s|$|[)\\].:\\-–—])").containsMatchIn(a)) return i
        }
        Regex("^([A-Fa-f])(?=\\s|$|[)\\].:\\-–—])").find(a)?.let { m ->
            val i = m.groupValues[1].lowercase()[0] - 'a'
            if (i in options.indices) return i
        }
        val loose = looseCompare(a)
        if (loose.isNotEmpty()) {
            val exact = options.indexOfFirst { looseCompare(it) == loose }
            if (exact >= 0) return exact
            val tail = looseCompare(a.replace(Regex("^\\S{1,4}[)\\].:\\-–—]\\s*"), ""))
            if (tail.isNotEmpty()) {
                val byTail = options.indexOfFirst { looseCompare(it) == tail }
                if (byTail >= 0) return byTail
            }
        }
        return null
    }

    /**
     * سوال‌ها و پاسخ‌ها را جفت می‌کند و برای هر سوال تعیین می‌کند چندگزینه‌ای است (گزینه‌ها داخل متن سوال)
     * یا تشریحی. سوال چندگزینه‌ای باید پاسخی داشته باشد که یکی از گزینه‌ها را مشخص کند.
     */
    fun build(questions: List<Item>, answers: List<Item>): BuildResult {
        val pairing = pair(questions, answers)
        if (pairing.error != null) return BuildResult(emptyList(), pairing.error)
        val out = ArrayList<Built>()
        for ((i, p) in pairing.pairs.withIndex()) {
            val (qText, aText) = p
            val split = splitOptions(qText)
            if (split.options.isEmpty() || split.stem.isBlank()) {
                out.add(Built(qText, aText, emptyList(), -1))
                continue
            }
            val label = questions.getOrNull(i)?.number ?: (i + 1)
            val k = resolveAnswer(aText, split.options)
                ?: return BuildResult(
                    emptyList(),
                    "سوال $label گزینه دارد، ولی پاسخ آن («${aText.take(30)}») با هیچ گزینه‌ای نمی‌خواند. " +
                        "شمارهٔ گزینه را بنویسید (مثلاً ۲ یا ب)."
                )
            out.add(Built(split.stem, split.options[k], split.options, k))
        }
        return BuildResult(out, null)
    }
}
