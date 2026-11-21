package com.example.quizapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.example.quizapp.screens.ExamEditorScreen
import com.example.quizapp.screens.ExamModeScreen
import com.example.quizapp.screens.ExamTestEditorScreen
import com.example.quizapp.screens.TeacherResultsScreen
import com.example.quizapp.screens.FileExamEditorScreen
import com.example.quizapp.screens.FileExamScreen
import com.example.quizapp.screens.HomeScreen
import com.example.quizapp.screens.ImportScreen
import com.example.quizapp.screens.QuizScreen
import com.example.quizapp.screens.SettingsScreen
import com.example.quizapp.screens.SetupScreen
import com.example.quizapp.screens.StudentBookScreen
import com.example.quizapp.screens.StudentChapterScreen
import com.example.quizapp.screens.TeacherBookScreen
import com.example.quizapp.screens.TeacherChapterScreen
import com.example.quizapp.screens.OnlineExamScreen
import com.example.quizapp.screens.PdfSearchScreen
import com.example.quizapp.screens.PdfSearchState
import com.example.quizapp.screens.RestoreBackupDialog
import com.example.quizapp.screens.TeacherScreen
import com.example.quizapp.ui.theme.QuizAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** پیشوندِ ورودی‌هایی که فایل هستند (zip، JSON یا متن)؛ محتوا بعداً در پس‌زمینه خوانده می‌شود */
private const val FILE_PREFIX = "azmoon-file:"

class MainActivity : ComponentActivity() {
    // لینک یا فایل آزمونی که از بیرون برنامه رسیده است
    private val incomingLink = mutableStateOf<String?>(null)

    private fun linkFrom(intent: Intent?): String? {
        if (intent == null) return null
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
            else -> null
        }
        if (uri == null) return null
        return if (uri.scheme == "content" || uri.scheme == "file") FILE_PREFIX + uri.toString() else uri.toString()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingLink.value = linkFrom(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReport.install(this)
        val lastCrash = CrashReport.pending(this)
        if (lastCrash != null) {
            // دفعهٔ قبل خطایی رخ داده؛ ابتدا متن آن نشان داده می‌شود تا قابل ارسال باشد
            CrashReport.show(this, lastCrash) { recreate() }
            return
        }
        incomingLink.value = linkFrom(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            var settings by remember { mutableStateOf(AppSettings.load(this@MainActivity)) }
            val baseDensity = LocalDensity.current
            // اندازهٔ نوشته‌ها (کوچک / متوسط / بزرگ) برای کل برنامه
            val scaledDensity = remember(baseDensity, settings.fontLevel) {
                Density(baseDensity.density, baseDensity.fontScale * AppSettings.fontFactor(settings.fontLevel))
            }
            val dark = when (settings.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            QuizAppTheme(darkTheme = dark) {
                // اجبار RTL برای کل رابط کاربری
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalDensity provides scaledDensity,
                    LocalAppSettings provides settings
                ) {
                    AppRoot(
                        incomingLink = incomingLink.value,
                        onLinkHandled = { incomingLink.value = null },
                        settings = settings,
                        onSettingsChange = {
                            settings = it
                            AppSettings.save(this@MainActivity, it)
                        }
                    )
                }
            }
        }
    }
}

/** صفحه‌های اپ */
sealed interface Screen {
    object Home : Screen
    object Settings : Screen
    data class Import(val prefill: String = "") : Screen
    object Teacher : Screen
    object Online : Screen
    object Search : Screen
    data class StudentBook(val bookName: String) : Screen
    data class StudentChapter(val bookName: String, val chapter: String) : Screen
    data class Setup(val bookName: String, val exam: Exam) : Screen
    data class Quiz(val bookName: String, val exam: Exam, val count: Int, val resume: SavedSession? = null) : Screen
    data class ExamMode(
        val bookName: String, val exam: Exam, val count: Int,
        val resume: SavedSession? = null, val studentName: String = ""
    ) : Screen
    data class FileExam(val bookName: String, val exam: Exam) : Screen
    data class TeacherBook(val bookName: String) : Screen
    data class TeacherChapter(val bookName: String, val chapter: String) : Screen
    data class ExamEditor(val bookName: String, val chapter: String, val examKey: String?) : Screen
    data class FileExamEditor(val bookName: String, val chapter: String, val examKey: String?) : Screen
    data class TestEditor(val bookName: String, val chapter: String, val examKey: String?) : Screen
    data class TeacherResults(val bookName: String, val chapter: String, val examKey: String) : Screen
}

private const val MAX_SAVED_PREFILL = 20000

/**
 * ذخیرهٔ صفحهٔ فعلی برای زمانی که سیستم برنامه را هنگام باز بودن انتخابگر فایل می‌بندد.
 * صفحه‌های دارای آزمون فقط با نام کتاب/فصل/آزمون ذخیره می‌شوند و هنگام بازگشت از روی فهرست کتاب‌ها ساخته می‌شوند.
 */
private fun screenSaver(booksProvider: () -> List<Book>): Saver<Screen, ArrayList<String>> = Saver(
    save = { s ->
        when (s) {
            is Screen.Home -> arrayListOf("home")
            is Screen.Settings -> arrayListOf("settings")
            is Screen.Teacher -> arrayListOf("teacher")
            is Screen.Online -> arrayListOf("online")
            is Screen.Search -> arrayListOf("search")
            is Screen.Import -> arrayListOf("import", if (s.prefill.length <= MAX_SAVED_PREFILL) s.prefill else "")
            is Screen.StudentBook -> arrayListOf("sbook", s.bookName)
            is Screen.StudentChapter -> arrayListOf("schap", s.bookName, s.chapter)
            is Screen.Setup -> arrayListOf("setup", s.bookName, s.exam.chapter, s.exam.title)
            is Screen.Quiz -> arrayListOf("quiz", s.bookName, s.exam.chapter, s.exam.title, s.count.toString())
            is Screen.ExamMode -> arrayListOf("emode", s.bookName, s.exam.chapter, s.exam.title, s.count.toString())
            is Screen.FileExam -> arrayListOf("fexam", s.bookName, s.exam.chapter, s.exam.title)
            is Screen.TeacherBook -> arrayListOf("tbook", s.bookName)
            is Screen.TeacherChapter -> arrayListOf("tchap", s.bookName, s.chapter)
            is Screen.ExamEditor ->
                arrayListOf("ed", s.bookName, s.chapter).also { l -> s.examKey?.let { l.add(it) } }
            is Screen.FileExamEditor ->
                arrayListOf("fed", s.bookName, s.chapter).also { l -> s.examKey?.let { l.add(it) } }
            is Screen.TestEditor ->
                arrayListOf("ted", s.bookName, s.chapter).also { l -> s.examKey?.let { l.add(it) } }
            is Screen.TeacherResults -> arrayListOf("tres", s.bookName, s.chapter, s.examKey)
        }
    },
    restore = { l ->
        val books = booksProvider()
        fun exam(book: String, chapter: String, title: String): Exam? =
            books.firstOrNull { it.name == book }?.exams?.firstOrNull { it.chapter == chapter && it.title == title }
        try {
            when (l[0]) {
                "settings" -> Screen.Settings
                "teacher" -> Screen.Teacher
                "online" -> Screen.Online
                "search" -> Screen.Search
                "import" -> Screen.Import(l.getOrElse(1) { "" })
                "sbook" -> Screen.StudentBook(l[1])
                "schap" -> Screen.StudentChapter(l[1], l[2])
                "setup" -> exam(l[1], l[2], l[3])?.let { Screen.Setup(l[1], it) } ?: Screen.StudentChapter(l[1], l[2])
                "quiz" -> exam(l[1], l[2], l[3])?.let { Screen.Setup(l[1], it) } ?: Screen.StudentChapter(l[1], l[2])
                "emode" -> exam(l[1], l[2], l[3])?.let { Screen.Setup(l[1], it) } ?: Screen.StudentChapter(l[1], l[2])
                "fexam" -> exam(l[1], l[2], l[3])?.let { Screen.FileExam(l[1], it) } ?: Screen.StudentChapter(l[1], l[2])
                "tbook" -> Screen.TeacherBook(l[1])
                "tchap" -> Screen.TeacherChapter(l[1], l[2])
                "ed" -> Screen.ExamEditor(l[1], l[2], l.getOrNull(3))
                "fed" -> Screen.FileExamEditor(l[1], l[2], l.getOrNull(3))
                "ted" -> Screen.TestEditor(l[1], l[2], l.getOrNull(3))
                "tres" -> Screen.TeacherResults(l[1], l[2], l[3])
                else -> Screen.Home
            }
        } catch (e: Exception) {
            Screen.Home
        }
    }
)

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("آزمون‌یار", style = MaterialTheme.typography.titleLarge)
    }
}

/** چهار تب پایین صفحه: معلم، دانش‌آموز (صفحهٔ اصلی)، دریافت آزمون، تنظیمات */
@Composable
private fun BottomTabs(screen: Screen, onGo: (Screen) -> Unit) {
    NavigationBar(windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)) {
        val tabs = listOf(
            Triple("👨‍🏫", "معلم", Screen.Teacher as Screen),
            Triple("🎓", "دانش‌آموز", Screen.Home as Screen),
            Triple("📥", "دریافت آزمون", Screen.Import() as Screen),
            Triple("⚙️", "تنظیمات", Screen.Settings as Screen)
        )
        tabs.forEach { (icon, label, target) ->
            val selected = when (target) {
                is Screen.Home -> screen is Screen.Home
                is Screen.Teacher -> screen is Screen.Teacher
                is Screen.Import -> screen is Screen.Import
                else -> screen is Screen.Settings
            }
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onGo(target) },
                icon = { Text(icon) },
                label = { Text(label, maxLines = 1, fontSize = 11.sp, overflow = TextOverflow.Ellipsis) },
                alwaysShowLabel = true
            )
        }
    }
}

@Composable
fun AppRoot(
    incomingLink: String?,
    onLinkHandled: () -> Unit,
    settings: AppSettingsState,
    onSettingsChange: (AppSettingsState) -> Unit
) {
    val context = LocalContext.current
    var books by remember { mutableStateOf(ExamStore.load(context)) }
    // فایل پشتیبانی که منتظر انتخابِ «ادغام یا جایگزینی» است
    var pendingRestore by remember { mutableStateOf<List<Book>?>(null) }
    // آزمونِ دریافتی که هم‌نامش از قبل هست و منتظر تصمیم کاربر است
    var pendingImport by remember { mutableStateOf<SharedExam?>(null) }
    var screen by rememberSaveable(stateSaver = screenSaver { books }) { mutableStateOf<Screen>(Screen.Home) }
    // عبارت جستجو و نتیجه‌ها اینجا نگه داشته می‌شود تا با رفتن به صفحهٔ دیگر و برگشتن پاک نشود
    val searchState = remember { PdfSearchState() }
    // جست‌وجوی صفحهٔ اصلی (کتاب، فصل، آزمون) هم همین‌طور
    var homeQuery by rememberSaveable { mutableStateOf("") }
    // با هر ذخیره یا پاک شدنِ وضعیت آزمون بالا می‌رود تا کارت «ادامهٔ آزمون» تازه شود
    var sessionVersion by remember { mutableIntStateOf(0) }
    // صفحهٔ «انتخاب صفحه‌ها» با دکمهٔ برگشت به همان جایی برمی‌گردد که از آن آمده (جستجو یا دریافت آزمون)
    var onlineParent by remember { mutableStateOf<Screen>(Screen.Import()) }

    fun updateBooks(newBooks: List<Book>) {
        ExamStore.save(context, newBooks)
        books = newBooks
        // فایل‌های PDF/عکسی که دیگر هیچ آزمونی از آن‌ها استفاده نمی‌کند پاک می‌شوند (فایل‌های تازه نگه داشته می‌شوند)
        ExamFiles.cleanup(context, newBooks)
        // وضعیت آزمون‌هایی که حذف یا ویرایش شده‌اند دیگر قابل ادامه نیست
        SessionStore.prune(context, newBooks)
        sessionVersion++
    }

    fun finishImport(shared: SharedExam) {
        updateBooks(ExamStore.saveExam(books, shared.book, null, shared.exam))
        Toast.makeText(context, "آزمون «${shared.exam.title}» ذخیره شد", Toast.LENGTH_LONG).show()
        screen = Screen.Home
    }

    // آزمونِ دریافتی: اگر هم‌نام دارد، بدون پرسیدن جای آزمون قبلی نمی‌نشیند
    fun requestImport(shared: SharedExam) {
        if (ExamStore.hasExam(books, shared.book, shared.exam)) pendingImport = shared else finishImport(shared)
    }

    fun saveSession(bookName: String, exam: Exam, session: SavedSession) {
        // آزمونِ ترکیبی (ساخته‌شده از چند آزمون) در کتاب نیست و ذخیره نمی‌شود
        if (!ExamStore.hasExam(books, bookName, exam)) return
        SessionStore.put(context, session)
        sessionVersion++
    }

    fun clearSession(bookName: String, exam: Exam) {
        SessionStore.remove(context, SessionLogic.keyOf(bookName, exam.chapter, exam.title))
        sessionVersion++
    }

    fun resumeScreenFor(session: SavedSession, exam: Exam): Screen =
        if (session.mode == SessionLogic.MODE_EXAM) Screen.ExamMode(session.bookName, exam, session.order.size, session)
        else Screen.Quiz(session.bookName, exam, session.order.size, session)

    // فایل یا لینکی که از بیرون رسیده: اگر معتبر باشد ذخیره می‌شود، وگرنه صفحهٔ دریافت باز می‌شود
    LaunchedEffect(incomingLink) {
        val link = incomingLink
        if (link != null) {
            if (link.startsWith(FILE_PREFIX)) {
                val uri = Uri.parse(link.removePrefix(FILE_PREFIX))
                val incoming = withContext(Dispatchers.IO) {
                    try {
                        ExamPackage.readIncomingFromUri(context, uri)
                    } catch (e: Exception) {
                        null
                    }
                }
                val shared = incoming?.exam
                if (incoming?.pdf == true) {
                    onlineParent = Screen.Import()
                    screen = Screen.Online
                } else if (incoming?.backup != null) {
                    pendingRestore = incoming.backup
                    screen = Screen.Settings
                } else if (shared != null) {
                    requestImport(shared)
                } else {
                    Toast.makeText(
                        context,
                        "این فایل، فایل آزمون‌یار نیست یا خراب است. آزمون‌های شما بدون تغییر ماندند.",
                        Toast.LENGTH_LONG
                    ).show()
                    screen = Screen.Import()
                }
            } else {
                val shared = ExamText.fromShareText(link)
                val studentResult = if (shared == null) ResultCodec.fromText(link) else null
                if (studentResult != null) {
                    // نتیجهٔ دانش‌آموز (فقط در دستگاه معلم کاربرد دارد): ذخیره و نمایش در صفحهٔ نتایج
                    ResultStore.add(context, studentResult)
                    var target: Screen? = null
                    for (b in books) for (e in b.exams) {
                        if (target == null && ResultStore.forExam(listOf(StoredResult(studentResult, 0L)), b.name, e).isNotEmpty()) {
                            target = Screen.TeacherResults(b.name, e.chapter, e.key)
                        }
                    }
                    if (target != null) {
                        Toast.makeText(context, "نتیجهٔ ${studentResult.studentName} ذخیره شد", Toast.LENGTH_LONG).show()
                        screen = target
                    } else {
                        Toast.makeText(
                            context,
                            "این نتیجه مربوط به آزمونی است که در این دستگاه نیست. آزمون را بسازید یا وارد کنید و نتیجه را دوباره باز کنید.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else if (shared != null) {
                    requestImport(shared)
                } else {
                    screen = Screen.Import(link)
                }
            }
            onLinkHandled()
        }
    }

    // آزمون‌هایی که فصل‌بندی دارند؛ اگر نه (آزمون‌های قدیمی)، مستقیم فهرست آزمون‌ها باز می‌شود
    fun hasChapters(bookName: String): Boolean {
        val book = books.firstOrNull { it.name == bookName } ?: return false
        return book.allChapters.any { book.examsIn(it).isNotEmpty() }
    }

    // اگر کتاب فقط یک فصل دارد (و آزمونِ بدون فصل ندارد)، انتخاب فصل حذف می‌شود تا مسیر کوتاه‌تر شود
    fun onlyChapter(bookName: String): String? {
        val book = books.firstOrNull { it.name == bookName } ?: return null
        val chapters = book.allChapters.filter { book.examsIn(it).isNotEmpty() }
        return if (chapters.size == 1 && book.unsortedExams.isEmpty()) chapters[0] else null
    }

    fun parentOf(s: Screen): Screen = when (s) {
        is Screen.Home -> Screen.Home
        is Screen.Settings -> Screen.Home
        is Screen.Teacher -> Screen.Home
        is Screen.Import -> Screen.Home
        is Screen.Online -> onlineParent
        is Screen.Search -> Screen.Import()
        is Screen.StudentBook -> Screen.Home
        is Screen.StudentChapter ->
            if (hasChapters(s.bookName) && onlyChapter(s.bookName) == null) Screen.StudentBook(s.bookName) else Screen.Home
        is Screen.Setup -> Screen.StudentChapter(s.bookName, s.exam.chapter)
        is Screen.Quiz -> Screen.Setup(s.bookName, s.exam)
        is Screen.ExamMode -> Screen.StudentChapter(s.bookName, s.exam.chapter)
        is Screen.FileExam -> Screen.StudentChapter(s.bookName, s.exam.chapter)
        is Screen.TeacherBook -> Screen.Teacher
        is Screen.TeacherChapter -> Screen.TeacherBook(s.bookName)
        is Screen.ExamEditor -> Screen.TeacherChapter(s.bookName, s.chapter)
        is Screen.FileExamEditor -> Screen.TeacherChapter(s.bookName, s.chapter)
        is Screen.TestEditor -> Screen.TeacherChapter(s.bookName, s.chapter)
        is Screen.TeacherResults -> Screen.TeacherChapter(s.bookName, s.chapter)
    }

    BackHandler(enabled = screen !is Screen.Home) {
        screen = parentOf(screen)
    }

    // نام و آیکون برنامه بالا و سه تب پایین، فقط در صفحه‌های اصلی دیده می‌شوند
    val showChrome = screen is Screen.Home || screen is Screen.Teacher ||
        screen is Screen.Import || screen is Screen.Settings
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // آزمونِ نیمه‌تمامِ واقعی برای کارت «ادامه» (فقط در صفحهٔ اصلی لازم است)
    val resumeInfo = remember(books, screen, sessionVersion) {
        if (screen is Screen.Home) SessionLogic.latestValid(SessionStore.loadAll(context), books) else null
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            if (showChrome && !keyboardOpen) AppHeader()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val s = screen) {
                    is Screen.Home -> HomeScreen(
                        books = books,
                        query = homeQuery,
                        onQueryChange = { homeQuery = it },
                        resume = resumeInfo,
                        onResume = { session, exam -> screen = resumeScreenFor(session, exam) },
                        onOpenBook = { name ->
                            val only = onlyChapter(name)
                            screen = when {
                                only != null -> Screen.StudentChapter(name, only)
                                hasChapters(name) -> Screen.StudentBook(name)
                                else -> Screen.StudentChapter(name, "")
                            }
                        },
                        onOpenExam = { bookName, exam ->
                            screen = if (exam.isFileExam) Screen.FileExam(bookName, exam)
                            else Screen.Setup(bookName, exam)
                        },
                        onGoImport = { screen = Screen.Import() },
                        onGoTeacher = { screen = Screen.Teacher }
                    )
                    is Screen.Settings -> SettingsScreen(
                        settings = settings,
                        onChange = onSettingsChange,
                        books = books,
                        onRestoreReady = { pendingRestore = it }
                    )
                    is Screen.StudentBook -> StudentBookScreen(
                        book = books.firstOrNull { it.name == s.bookName } ?: Book(s.bookName, emptyList()),
                        onOpenChapter = { screen = Screen.StudentChapter(s.bookName, it) },
                        onBack = { screen = Screen.Home }
                    )
                    is Screen.StudentChapter -> StudentChapterScreen(
                        book = books.firstOrNull { it.name == s.bookName } ?: Book(s.bookName, emptyList()),
                        chapter = s.chapter,
                        onSelect = { exam ->
                            screen = if (exam.isFileExam) Screen.FileExam(s.bookName, exam)
                            else Screen.Setup(s.bookName, exam)
                        },
                        onBack = { screen = parentOf(s) }
                    )
                    is Screen.Setup -> {
                        // وضعیت ذخیره‌شدهٔ واقعیِ همین آزمون (اگر هست) برای دکمهٔ «ادامه»
                        val saved = remember(s, sessionVersion) {
                            SessionStore.loadAll(context).firstOrNull {
                                it.key == SessionLogic.keyOf(s.bookName, s.exam.chapter, s.exam.title) &&
                                    SessionLogic.resolve(it, books) != null
                            }
                        }
                        SetupScreen(
                            bookName = s.bookName,
                            exam = s.exam,
                            saved = saved,
                            initialName = remember {
                                context.getSharedPreferences("exam_student", Context.MODE_PRIVATE)
                                    .getString("name", "").orEmpty()
                            },
                            onResume = { if (saved != null) screen = resumeScreenFor(saved, s.exam) },
                            onStart = { count, mode, name ->
                                if (name.isNotBlank()) {
                                    context.getSharedPreferences("exam_student", Context.MODE_PRIVATE)
                                        .edit().putString("name", name).apply()
                                }
                                screen = if (mode == SessionLogic.MODE_EXAM) Screen.ExamMode(s.bookName, s.exam, count, null, name)
                                else Screen.Quiz(s.bookName, s.exam, count)
                            },
                            onBack = { screen = Screen.StudentChapter(s.bookName, s.exam.chapter) }
                        )
                    }
                    is Screen.Quiz -> QuizScreen(
                        exam = s.exam,
                        count = s.count,
                        bookName = s.bookName,
                        resume = s.resume,
                        onSave = { saveSession(s.bookName, s.exam, it) },
                        onClear = { clearSession(s.bookName, s.exam) },
                        onFinish = { screen = Screen.StudentChapter(s.bookName, s.exam.chapter) }
                    )
                    is Screen.ExamMode -> ExamModeScreen(
                        bookName = s.bookName,
                        exam = s.exam,
                        count = s.count,
                        resume = s.resume,
                        onSave = { saveSession(s.bookName, s.exam, it) },
                        onClear = { clearSession(s.bookName, s.exam) },
                        onFinish = { screen = Screen.StudentChapter(s.bookName, s.exam.chapter) },
                        studentName = s.studentName
                    )
                    is Screen.FileExam -> FileExamScreen(
                        exam = s.exam,
                        onFinish = { screen = Screen.StudentChapter(s.bookName, s.exam.chapter) }
                    )
                    is Screen.Import -> ImportScreen(
                        initialText = s.prefill,
                        onImported = { shared -> requestImport(shared) },
                        onBackupFound = { pendingRestore = it },
                        onOpenSearch = { screen = Screen.Search },
                        onPdfReady = {
                            onlineParent = Screen.Import()
                            screen = Screen.Online
                        }
                    )
                    is Screen.Search -> PdfSearchScreen(
                        state = searchState,
                        onPdfReady = {
                            onlineParent = Screen.Search
                            screen = Screen.Online
                        },
                        onBack = { screen = Screen.Import() }
                    )
                    is Screen.Online -> OnlineExamScreen(
                        books = books,
                        onBooksChange = { updateBooks(it) },
                        onSaved = { screen = Screen.Home },
                        onBack = { screen = onlineParent }
                    )
                    is Screen.Teacher -> TeacherScreen(
                        books = books,
                        onBooksChange = { updateBooks(it) },
                        onOpenBook = { screen = Screen.TeacherBook(it) }
                    )
                    is Screen.TeacherBook -> TeacherBookScreen(
                        books = books,
                        bookName = s.bookName,
                        onBooksChange = { updateBooks(it) },
                        onOpenChapter = { screen = Screen.TeacherChapter(s.bookName, it) },
                        onBack = { screen = Screen.Teacher }
                    )
                    is Screen.TeacherChapter -> TeacherChapterScreen(
                        books = books,
                        bookName = s.bookName,
                        chapter = s.chapter,
                        onBooksChange = { updateBooks(it) },
                        onEdit = { key -> screen = Screen.ExamEditor(s.bookName, s.chapter, key) },
                        onEditFile = { key -> screen = Screen.FileExamEditor(s.bookName, s.chapter, key) },
                        onEditTest = { key -> screen = Screen.TestEditor(s.bookName, s.chapter, key) },
                        onResults = { key -> screen = Screen.TeacherResults(s.bookName, s.chapter, key) },
                        onBack = { screen = Screen.TeacherBook(s.bookName) }
                    )
                    is Screen.ExamEditor -> ExamEditorScreen(
                        books = books,
                        bookName = s.bookName,
                        chapter = s.chapter,
                        originalKey = s.examKey,
                        onBooksChange = { updateBooks(it) },
                        onDone = { screen = Screen.TeacherChapter(s.bookName, s.chapter) },
                        onBack = { screen = Screen.TeacherChapter(s.bookName, s.chapter) }
                    )
                    is Screen.TestEditor -> ExamTestEditorScreen(
                        books = books,
                        bookName = s.bookName,
                        chapter = s.chapter,
                        originalKey = s.examKey,
                        onBooksChange = { updateBooks(it) },
                        onDone = { screen = Screen.TeacherChapter(s.bookName, s.chapter) },
                        onBack = { screen = Screen.TeacherChapter(s.bookName, s.chapter) }
                    )
                    is Screen.TeacherResults -> TeacherResultsScreen(
                        books = books,
                        bookName = s.bookName,
                        examKey = s.examKey,
                        onBack = { screen = Screen.TeacherChapter(s.bookName, s.chapter) }
                    )
                    is Screen.FileExamEditor -> FileExamEditorScreen(
                        books = books,
                        bookName = s.bookName,
                        chapter = s.chapter,
                        originalKey = s.examKey,
                        onBooksChange = { updateBooks(it) },
                        onDone = { screen = Screen.TeacherChapter(s.bookName, s.chapter) },
                        onBack = { screen = Screen.TeacherChapter(s.bookName, s.chapter) }
                    )
                }
            }
            if (showChrome && !keyboardOpen) {
                BottomTabs(screen = screen, onGo = { screen = it })
            }
        }
    }

    val restoring = pendingRestore
    if (restoring != null) {
        RestoreBackupDialog(
            restoring = restoring,
            books = books,
            onBooksChange = { updateBooks(it) },
            onDismiss = { pendingRestore = null }
        )
    }

    // آزمونِ دریافتیِ هم‌نام: جایگزینی فقط با تأیید روشن؛ «نگه داشتن هر دو» پیش‌فرض و بی‌خطر است
    val importing = pendingImport
    if (importing != null) {
        fun cancelImport() {
            // فایل‌های PDF/عکسِ همین آزمون که هنوز در هیچ آزمونی نیستند پاک می‌شوند
            ExamFiles.discard(
                context,
                importing.exam.questionFiles + importing.exam.answerFiles,
                ExamFiles.referenced(books)
            )
            pendingImport = null
        }
        AlertDialog(
            onDismissRequest = { cancelImport() },
            title = { Text("آزمونی با این نام هست") },
            text = {
                Column {
                    Text("در کتاب «${importing.book}» قبلاً آزمونی به نام «${importing.exam.title}» ذخیره شده است. چه کنیم؟")
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            pendingImport = null
                            finishImport(ExamStore.withUniqueTitle(books, importing.book, importing.exam).let {
                                SharedExam(importing.book, it)
                            })
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("نگه داشتن هر دو") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            pendingImport = null
                            finishImport(importing)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("جایگزین کردن (آزمون قبلی پاک می‌شود)") }
                    TextButton(
                        onClick = { cancelImport() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("انصراف") }
                }
            },
            confirmButton = {}
        )
    }
}
