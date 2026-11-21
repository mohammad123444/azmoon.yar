package com.example.quizapp

/**
 * آماده‌سازی متن درسی (ریاضی، فیزیک، شیمی و...) برای نمایش با KaTeX.
 * فرمول را می‌شود این‌طور نوشت: $ ... $  یا  \( ... \)  یا  $$ ... $$  یا  \[ ... \]
 * دستورهای فرمولِ بدون علامت (مثل -4\frac{1}{5} یا \sqrt{25}) هم خودکار تشخیص داده می‌شوند.
 * شیمی: \ce{H2SO4 + 2NaOH -> Na2SO4 + 2H2O}
 */
object MathText {

    // ---- اجزای الگوی تشخیص فرمولِ بدون علامت ----
    private val group = """\{(?:[^{}]|\{[^{}]*\})*\}"""
    private val scripts = """(?:\s?[\^_](?:$group|[0-9a-zA-Z]))*"""
    private val cmdTerm = """\\[a-zA-Z]+(?:$group|\[[^\]]*\])*$scripts"""
    private val numTerm = """[0-9۰-۹]+(?:[.,٫][0-9۰-۹]+)?$scripts"""
    private val term = """(?:$cmdTerm|$numTerm)"""
    private val joiner = """\s?[-+±=<>×÷*/]\s?[-+]?\s?"""
    private val span = Regex(
        """(?:[-+±]\s?)?(?:[0-9۰-۹]+(?:[.,٫][0-9۰-۹]+)?\s?)?$cmdTerm(?:$joiner$term|\s?$cmdTerm)*"""
    )

    // بخش‌هایی که خودِ کاربر با علامت مشخص کرده است
    private val delimited = Regex(
        """(\$\$[\s\S]+?\$\$|\$[^$\n]+?\$|\\\([\s\S]+?\\\)|\\\[[\s\S]+?\\\])"""
    )

    /** آیا متن شاملِ فرمول است؟ (اگر نه، با Text ساده نمایش داده می‌شود) */
    fun needsMath(text: String): Boolean = text.contains('\\') || text.contains('$')

    private fun asciiDigits(s: String): String {
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

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun wrapPlain(part: String): String {
        val wrapped = span.replace(part) { m ->
            if (m.value.contains('\\')) "\\(" + asciiDigits(m.value.trim()) + "\\)" else m.value
        }
        return escapeHtml(wrapped)
    }

    private fun toBody(text: String): String {
        val sb = StringBuilder()
        var last = 0
        for (m in delimited.findAll(text)) {
            sb.append(wrapPlain(text.substring(last, m.range.first)))
            sb.append(escapeHtml(asciiDigits(m.value)))
            last = m.range.last + 1
        }
        sb.append(wrapPlain(text.substring(last)))
        return sb.toString().replace("\n", "<br>")
    }

    private val template = """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="katex.min.css">
<script src="katex.min.js"></script>
<script src="mhchem.min.js"></script>
<script src="auto-render.min.js"></script>
<style>
html,body{margin:0;padding:0;background:transparent;}
body{direction:rtl;text-align:right;font-family:sans-serif;color:__COLOR__;font-size:__SIZE__px;line-height:1.75;word-wrap:break-word;overflow-wrap:break-word;}
.katex{direction:ltr;unicode-bidi:isolate;font-size:1.08em;}
.katex-display{overflow-x:auto;overflow-y:hidden;direction:ltr;}
</style></head>
<body><div id="c">__BODY__</div>
<script>
function report(){
  try{
    var h = document.getElementById('c').getBoundingClientRect().height;
    Android.onHeight(Math.ceil(h) + 2);
  }catch(e){}
}
try{
  renderMathInElement(document.getElementById('c'), {
    delimiters:[
      {left:'$$', right:'$$', display:true},
      {left:'\\[', right:'\\]', display:true},
      {left:'\\(', right:'\\)', display:false},
      {left:'$', right:'$', display:false}
    ],
    throwOnError:false
  });
}catch(e){}
report();
setTimeout(report, 120);
setTimeout(report, 400);
if (document.fonts && document.fonts.ready) { document.fonts.ready.then(report); }
</script></body></html>"""

    fun buildHtml(text: String, cssColor: String, fontPx: Float): String =
        template
            .replace("__COLOR__", cssColor)
            .replace("__SIZE__", fontPx.toString())
            .replace("__BODY__", toBody(text))
}
