package com.example.srmstudent

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val SRM_PORTAL_URL = "https://academia.srmist.edu.in/"

private val Navy = Color(0xFF153B63)
private val Teal = Color(0xFF18A999)
private val PageBackground = Color(0xFFF4F7FB)
private val Ink = Color(0xFF1B2638)
private val Muted = Color(0xFF718096)
private val PaleBlue = Color(0xFFEAF1FA)
private val Danger = Color(0xFFC24D4D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortalTheme {
                StudentPortalApp()
            }
        }
    }
}

// =========================================================================
// 1. DATA MODELS
// =========================================================================
data class ClassItem(
    val subject: String,
    val code: String,
    val day: String,
    val date: String,
    val time: String,
    val room: String,
    val faculty: String
)

data class AttendanceItem(
    val subject: String,
    val code: String,
    val attended: Int,
    val total: Int,
    val rawPercentage: Double? = null
) {
    val percentage: Int
        get() = when {
            rawPercentage != null -> rawPercentage.roundToInt()
            total > 0 -> ((attended.toDouble() / total.toDouble()) * 100.0).roundToInt()
            else -> 0
        }
}

data class MarkItem(
    val subject: String,
    val code: String,
    val assessment: String,
    val obtained: Double,
    val total: Double,
    val rawScoreText: String
) {
    val percentage: Int
        get() = if (total > 0) ((obtained / total) * 100.0).roundToInt() else 0
}

data class PortalSnapshotUiData(
    val studentName: String,
    val regNumber: String,
    val department: String,
    val semester: String,
    val dayOrder: String,
    val syncedAt: String,
    val classes: List<ClassItem>,
    val attendance: List<AttendanceItem>,
    val marks: List<MarkItem>
)

// =========================================================================
// 2. LOCAL OFFLINE STORAGE (PERSISTS DATA & SESSION COOKIES)
// =========================================================================
internal object LocalPortalCache {
    private const val CACHE_FILE = "srm_portal_data_v3.json"

    fun saveJson(context: Context, rawJson: String) {
        runCatching {
            File(context.filesDir, CACHE_FILE).writeText(rawJson, Charsets.UTF_8)
            CookieManager.getInstance().flush()
        }
    }

    fun loadData(context: Context): PortalSnapshotUiData? {
        return runCatching {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return null
            val parsed = parsePortalJson(JSONObject(file.readText(Charsets.UTF_8)))
            if (parsed.attendance.isEmpty() && parsed.classes.isEmpty() && parsed.marks.isEmpty()) {
                null
            } else {
                parsed
            }
        }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, CACHE_FILE).delete() }
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.removeSessionCookies(null)
            cookieManager.flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
        }
    }
}

// =========================================================================
// 3. PARSER FOR EXTRACTED SRM PORTAL DATA
// =========================================================================
internal fun parsePortalJson(root: JSONObject): PortalSnapshotUiData {
    val todayDate = SimpleDateFormat("dd MMM", Locale.ENGLISH).format(Date()).uppercase()
    val syncedAt = root.optString(
        "syncedAt",
        SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date())
    )

    val studentName = root.optString("studentName").ifBlank { "SRM Student" }
    val regNumber = root.optString("regNumber").ifBlank { "SRM Academia" }
    val department = root.optString("department").ifBlank { "SRMIST" }
    val semester = root.optString("semester").ifBlank { "" }
    val dayOrder = root.optString("dayOrder").ifBlank { "Active" }

    val attendanceList = mutableListOf<AttendanceItem>()
    val courseTitleByCode = mutableMapOf<String, String>()

    val attArray = root.optJSONArray("attendance") ?: JSONArray()
    for (i in 0 until attArray.length()) {
        val obj = attArray.optJSONObject(i) ?: continue
        val code = obj.optString("code").trim()
        val subject = obj.optString("subject").trim().ifBlank { code }
        if (subject.isBlank()) continue

        if (code.isNotBlank()) {
            courseTitleByCode[code] = subject
        }

        val conducted = obj.optInt("conducted", 0)
        val absent = obj.optInt("absent", 0)
        val attended = obj.optInt("attended", (conducted - absent).coerceAtLeast(0))
        val pct = obj.optString("percentage").replace("%", "").trim().toDoubleOrNull()

        attendanceList.add(
            AttendanceItem(
                subject = subject,
                code = code,
                attended = attended,
                total = conducted,
                rawPercentage = pct
            )
        )
    }

    val marksList = mutableListOf<MarkItem>()
    val marksArray = root.optJSONArray("marks") ?: JSONArray()
    for (i in 0 until marksArray.length()) {
        val obj = marksArray.optJSONObject(i) ?: continue
        val code = obj.optString("code").trim()
        val subject = obj.optString("subject").trim().ifBlank {
            courseTitleByCode[code] ?: code
        }
        val assessment = obj.optString("assessment").trim().ifBlank { "Internal Assessment" }
        val obtained = obj.optDouble("obtained", 0.0)
        val total = obj.optDouble("total", 0.0)
        val rawText = obj.optString("rawScore").trim().ifBlank {
            if (total > 0) "${obtained.toString().removeSuffix(".0")}/${total.toString().removeSuffix(".0")}"
            else obtained.toString().removeSuffix(".0")
        }

        if (subject.isNotBlank()) {
            marksList.add(
                MarkItem(
                    subject = subject,
                    code = code,
                    assessment = assessment,
                    obtained = obtained,
                    total = total,
                    rawScoreText = rawText
                )
            )
        }
    }

    val classesList = mutableListOf<ClassItem>()
    val classesArray = root.optJSONArray("classes") ?: JSONArray()
    for (i in 0 until classesArray.length()) {
        val obj = classesArray.optJSONObject(i) ?: continue
        val code = obj.optString("code").trim()
        val subject = obj.optString("subject").trim().ifBlank {
            courseTitleByCode[code] ?: code
        }
        if (subject.isBlank()) continue

        val slot = obj.optString("slot").trim()
        val room = obj.optString("room").trim().ifBlank { "TBA" }
        val faculty = obj.optString("faculty").trim()
        val time = obj.optString("time").trim().ifBlank { slotToTiming(slot) }
        val dayLabel = obj.optString("day").trim().ifBlank {
            if (dayOrder.any { it.isDigit() }) "DO-$dayOrder" else (slot.take(4).ifBlank { "SLOT" })
        }

        classesList.add(
            ClassItem(
                subject = subject,
                code = code,
                day = dayLabel,
                date = todayDate,
                time = time,
                room = room,
                faculty = faculty
            )
        )
    }

    return PortalSnapshotUiData(
        studentName = studentName,
        regNumber = regNumber,
        department = department,
        semester = semester,
        dayOrder = dayOrder,
        syncedAt = syncedAt,
        classes = classesList,
        attendance = attendanceList,
        marks = marksList
    )
}

internal fun slotToTiming(slot: String): String {
    val clean = slot.uppercase()
    return when {
        clean.startsWith("A") -> "08:00 AM - 08:50 AM (Slot $slot)"
        clean.startsWith("B") -> "08:50 AM - 09:40 AM (Slot $slot)"
        clean.startsWith("C") -> "09:45 AM - 10:35 AM (Slot $slot)"
        clean.startsWith("D") -> "10:40 AM - 11:30 AM (Slot $slot)"
        clean.startsWith("E") -> "11:35 AM - 12:25 PM (Slot $slot)"
        clean.startsWith("F") -> "12:30 PM - 01:20 PM (Slot $slot)"
        clean.startsWith("G") -> "01:25 PM - 02:15 PM (Slot $slot)"
        clean.startsWith("P") || clean.startsWith("L") -> "Lab Session ($slot)"
        slot.isNotBlank() -> "Slot $slot"
        else -> "Scheduled"
    }
}

// =========================================================================
// 4. JAVASCRIPT EXTRACTOR FOR SRM ACADEMIA
// =========================================================================
private val JS_EXTRACT_SRM_DATA = """
(function() {
    function clean(text) {
        return (text || "").replace(/\s+/g, " ").trim();
    }

    var bodyText = document.body ? clean(document.body.innerText) : "";
    var hasLoginInput = !!document.querySelector('input[type="password"], #login_id, #signinFrame, iframe[src*="signin"], iframe[src*="accounts"]');
    var hasPortalContent = bodyText.indexOf("Registration Number") !== -1 ||
                           bodyText.indexOf("Hours Conducted") !== -1 ||
                           bodyText.indexOf("Day Order") !== -1 ||
                           bodyText.indexOf("My Attendance") !== -1 ||
                           bodyText.indexOf("Sign Out") !== -1;

    var result = {
        url: window.location.href,
        hash: window.location.hash,
        isLoggedIn: hasPortalContent && !hasLoginInput,
        studentName: "",
        regNumber: "",
        department: "",
        semester: "",
        dayOrder: "",
        attendance: [],
        marks: [],
        classes: []
    };

    var doMatch = bodyText.match(/Day\s*Order\s*[:\-]?\s*([0-9]+|No\s*Day\s*Order|Holiday)/i);
    if (doMatch) result.dayOrder = clean(doMatch[1]);

    var regMatch = bodyText.match(/Registration\s*Number\s*[:\-]?\s*([A-Z0-9]{10,20})/i);
    if (regMatch) result.regNumber = clean(regMatch[1]);

    var allTables = Array.from(document.querySelectorAll("table"));

    allTables.forEach(function(table) {
        var rows = Array.from(table.querySelectorAll("tr"));
        if (rows.length === 0) return;

        rows.forEach(function(r) {
            var cells = Array.from(r.querySelectorAll("td, th")).map(function(c) { return clean(c.innerText); });
            for (var i = 0; i < cells.length - 1; i++) {
                var k = cells[i].toLowerCase().replace(/[:\-]/g, "").trim();
                var v = cells[i + 1];
                if (!v) continue;
                if ((k === "name" || k === "student name") && !result.studentName) result.studentName = v;
                if ((k.indexOf("registration number") !== -1 || k === "reg.no") && !result.regNumber) result.regNumber = v;
                if (k === "department" && !result.department) result.department = v;
                if (k === "semester" && !result.semester) result.semester = v;
            }
        });

        var headerIndex = -1;
        var headers = [];
        for (var rIdx = 0; rIdx < Math.min(rows.length, 5); rIdx++) {
            var candidate = Array.from(rows[rIdx].children).map(function(c) { return clean(c.innerText).toLowerCase(); });
            var joined = candidate.join(" | ");
            if (joined.indexOf("course code") !== -1 || joined.indexOf("hours conducted") !== -1 || joined.indexOf("test performance") !== -1) {
                headerIndex = rIdx;
                headers = candidate;
                break;
            }
        }

        if (headerIndex === -1) return;

        var idxCode = headers.findIndex(function(h) { return h.indexOf("course code") !== -1 || h === "code"; });
        var idxTitle = headers.findIndex(function(h) { return h.indexOf("course title") !== -1 || h.indexOf("subject") !== -1 || h === "title"; });
        var idxFaculty = headers.findIndex(function(h) { return h.indexOf("faculty") !== -1; });
        var idxSlot = headers.findIndex(function(h) { return h.indexOf("slot") !== -1; });
        var idxRoom = headers.findIndex(function(h) { return h.indexOf("room") !== -1 || h.indexOf("venue") !== -1; });
        var idxConducted = headers.findIndex(function(h) { return h.indexOf("conducted") !== -1; });
        var idxAbsent = headers.findIndex(function(h) { return h.indexOf("absent") !== -1; });
        var idxAttn = headers.findIndex(function(h) { return h.indexOf("attn") !== -1 || h.indexOf("%") !== -1; });
        var idxPerf = headers.findIndex(function(h) { return h.indexOf("test performance") !== -1 || h.indexOf("performance") !== -1 || h.indexOf("marks") !== -1; });

        // 1. Attendance & Course Schedule Table
        if (idxConducted !== -1 && idxTitle !== -1) {
            for (var i = headerIndex + 1; i < rows.length; i++) {
                var cols = Array.from(rows[i].children).map(function(c) { return clean(c.innerText); });
                if (cols.length <= idxConducted) continue;

                var rawCode = idxCode !== -1 ? (cols[idxCode] || "").split(" ")[0] : "";
                var title = cols[idxTitle] || "";
                if (!title || title.toLowerCase().indexOf("course title") !== -1 || title.toLowerCase() === "total") continue;

                var conducted = parseInt((cols[idxConducted] || "0").replace(/[^0-9]/g, ""), 10) || 0;
                var absent = idxAbsent !== -1 ? (parseInt((cols[idxAbsent] || "0").replace(/[^0-9]/g, ""), 10) || 0) : 0;
                var attended = Math.max(0, conducted - absent);
                var pct = idxAttn !== -1 ? (cols[idxAttn] || "") : "";
                var slot = idxSlot !== -1 ? (cols[idxSlot] || "") : "";
                var room = idxRoom !== -1 ? (cols[idxRoom] || "") : "";
                var faculty = idxFaculty !== -1 ? (cols[idxFaculty] || "") : "";

                result.attendance.push({
                    code: rawCode,
                    subject: title,
                    conducted: conducted,
                    absent: absent,
                    attended: attended,
                    percentage: pct
                });

                result.classes.push({
                    code: rawCode,
                    subject: title,
                    slot: slot,
                    room: room,
                    faculty: faculty
                });
            }
        }

        // 2. Internal Marks Table
        else if (idxPerf !== -1 && idxCode !== -1) {
            for (var m = headerIndex + 1; m < rows.length; m++) {
                var mCols = Array.from(rows[m].children);
                if (mCols.length <= idxPerf) continue;

                var cCode = clean(mCols[idxCode].innerText).split(" ")[0];
                if (!cCode || cCode.toLowerCase().indexOf("course") !== -1) continue;

                var perfText = clean(mCols[idxPerf].innerText);
                if (!perfText || perfText === "-" || perfText.toLowerCase() === "nil") continue;

                var regex = /([A-Za-z0-9_\-]+)\s*\/\s*([0-9.]+)\s*[:\-]?\s*([0-9.]+|Abs)/g;
                var match;
                var foundSubTests = false;

                while ((match = regex.exec(perfText)) !== null) {
                    foundSubTests = true;
                    var testName = match[1];
                    var maxM = parseFloat(match[2]) || 0;
                    var gotM = match[3].toLowerCase() === "abs" ? 0 : (parseFloat(match[3]) || 0);
                    result.marks.push({
                        code: cCode,
                        subject: "",
                        assessment: testName,
                        obtained: gotM,
                        total: maxM,
                        rawScore: gotM + " / " + maxM
                    });
                }

                if (!foundSubTests) {
                    result.marks.push({
                        code: cCode,
                        subject: "",
                        assessment: "Internal Marks",
                        obtained: 0,
                        total: 0,
                        rawScore: perfText
                    });
                }
            }
        }
    });

    return JSON.stringify(result);
})();
""".trimIndent()

// =========================================================================
// 5. MAIN APP STATE
// =========================================================================
private enum class AppTab(val label: String, val symbol: String) {
    Home("Home", "⌂"),
    Classes("Classes", "▦"),
    Attendance("Attendance", "◉"),
    Marks("Marks", "★")
}

@Composable
private fun PortalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Navy,
            secondary = Teal,
            background = PageBackground,
            surface = Color.White,
            onSurface = Ink,
            onPrimary = Color.White
        ),
        content = content
    )
}

@Composable
fun StudentPortalApp() {
    val context = LocalContext.current
    var portalData by remember { mutableStateOf(LocalPortalCache.loadData(context)) }
    var showPortalWebView by remember { mutableStateOf(false) }
    var loginHint by remember { mutableStateOf("") }

    if (showPortalWebView) {
        SrmOfficialPortalSignInAndSyncScreen(
            loginHint = loginHint,
            onSyncComplete = { rawJson, parsedData ->
                LocalPortalCache.saveJson(context, rawJson)
                portalData = parsedData
                showPortalWebView = false
            },
            onCancel = { showPortalWebView = false }
        )
    } else if (portalData == null) {
        WelcomeStartScreen(
            onOpenSrmSignIn = { id ->
                loginHint = id
                showPortalWebView = true
            }
        )
    } else {
        AuthenticatedPortalScaffold(
            data = portalData!!,
            onRefresh = { loginHint = ""; showPortalWebView = true },
            onSignOut = {
                LocalPortalCache.clear(context)
                portalData = null
            }
        )
    }
}

// =========================================================================
// 6. OFFICIAL SRM SIGN-IN & AUTO-EXTRACTION SCREEN
// =========================================================================
private fun prefillIdJs(rawId: String): String {
    val id = if (rawId.contains("@")) rawId else "$rawId@srmist.edu.in"
    return "(function(){var v=" + JSONObject.quote(id) + ";" +
        "function f(d){try{var u=d.querySelector('#login_id,input[name=\"LOGIN_ID\"],input[type=\"email\"]');" +
        "if(u&&!u.value){u.value=v;u.dispatchEvent(new Event('input',{bubbles:true}));}}catch(e){}}" +
        "f(document);" +
        "Array.from(document.querySelectorAll('iframe')).forEach(function(fr){" +
        "try{f(fr.contentDocument);}catch(e){}});})();"
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SrmOfficialPortalSignInAndSyncScreen(
    loginHint: String,
    onSyncComplete: (String, PortalSnapshotUiData) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val collected = remember { JSONObject() }
    var attendanceCount by remember { mutableStateOf(0) }
    var marksCount by remember { mutableStateOf(0) }
    var classesCount by remember { mutableStateOf(0) }
    var signedIn by remember { mutableStateOf(false) }

    fun mergeFrom(current: JSONObject) {
        signedIn = current.optBoolean("isLoggedIn", false) || signedIn
        for (key in listOf("studentName", "regNumber", "department", "semester", "dayOrder")) {
            val v = current.optString(key)
            if (v.isNotBlank()) collected.put(key, v)
        }
        for (key in listOf("attendance", "marks", "classes")) {
            val arr = current.optJSONArray(key)
            if (arr != null && arr.length() > 0) collected.put(key, arr)
        }
        attendanceCount = collected.optJSONArray("attendance")?.length() ?: 0
        marksCount = collected.optJSONArray("marks")?.length() ?: 0
        classesCount = collected.optJSONArray("classes")?.length() ?: 0
    }

    fun extract(webView: WebView) {
        webView.evaluateJavascript(JS_EXTRACT_SRM_DATA) { raw ->
            if (raw.isNullOrBlank() || raw == "null") return@evaluateJavascript
            runCatching {
                val json = JSONTokener(raw).nextValue().toString()
                mergeFrom(JSONObject(json))
            }
        }
    }

    fun openMenu(webView: WebView, keyword: String) {
        val js = "(function(){var k=" + JSONObject.quote(keyword) + ";" +
            "var links=Array.from(document.querySelectorAll('a'));" +
            "var t=links.find(function(a){return (a.innerText||'').toLowerCase().indexOf(k)!==-1;});" +
            "if(t){t.click();}})();"
        webView.evaluateJavascript(js, null)
    }

    fun finishAndSave() {
        if (attendanceCount + marksCount + classesCount == 0) {
            Toast.makeText(
                context,
                "No data found yet. Open the Attendance or Timetable page first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        collected.put(
            "syncedAt",
            SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date())
        )
        onSyncComplete(collected.toString(), parsePortalJson(collected))
    }

    fun openInChrome() {
        val uri = Uri.parse(SRM_PORTAL_URL)
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.chrome")
            )
        } catch (e: ActivityNotFoundException) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }
    }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) wv.goBack() else onCancel()
    }

    // Passive reader: only reads the page, never types or clicks for you.
    LaunchedEffect(webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            extract(wv)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = PageBackground) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Navy)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SRM Academia",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCancel) {
                        Text("Close", color = Color.White.copy(alpha = 0.85f))
                    }
                }
                Text(
                    if (signedIn) {
                        "Signed in. Collected: $attendanceCount attendance, " +
                            "$classesCount classes, $marksCount marks"
                    } else {
                        "Sign in below. Then open Attendance and Timetable."
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = { openInChrome() }) {
                        Text("Open in Chrome", color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(onClick = { webViewRef?.let { openMenu(it, "attendance") } }) {
                        Text("Attendance", color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(onClick = { webViewRef?.let { openMenu(it, "time table") } }) {
                        Text("Timetable", color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(onClick = { webViewRef?.let { extract(it) } }) {
                        Text("Import this page", color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(onClick = { finishAndSave() }) {
                        Text("Save & Finish", color = Teal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val cookies = CookieManager.getInstance()
                        cookies.setAcceptCookie(true)
                        cookies.setAcceptThirdPartyCookies(this, true)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                CookieManager.getInstance().flush()
                                if (loginHint.isNotBlank()) {
                                    view?.evaluateJavascript(prefillIdJs(loginHint), null)
                                }
                                view?.let { extract(it) }
                            }
                        }
                        loadUrl(SRM_PORTAL_URL)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// =========================================================================
// 7. WELCOME SCREEN & NATIVE DASHBOARD SCREENS
// =========================================================================
@Composable
private fun WelcomeStartScreen(
    onOpenSrmSignIn: (String) -> Unit
) {
    var loginId by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = PageBackground) {
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("SRM STUDENT PORTAL", color = Navy, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Connect your SRM account", color = Ink, fontSize = 24.sp,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "Enter your ID (optional) and it will be filled in on SRM's sign-in page. " +
                            "You type the password and CAPTCHA on SRM's page yourself.",
                        color = Muted, fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = loginId,
                        onValueChange = { loginId = it.trim() },
                        label = { Text("SRM ID") },
                        placeholder = { Text("e.g. vv1234") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onOpenSrmSignIn(loginId) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) {
                        Text("Open SRM Sign-In")
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedPortalScaffold(
    data: PortalSnapshotUiData,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(AppTab.Home) }

    Scaffold(
        containerColor = PageBackground,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Text(
                                tab.symbol,
                                fontSize = 18.sp,
                                color = if (selectedTab == tab) Navy else Muted
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        data.regNumber,
                        color = Navy,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        data.studentName,
                        color = Ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val subline = listOf(
                        data.department,
                        data.semester.let { if (it.isNotBlank()) "Sem $it" else "" }
                    ).filter { it.isNotBlank() }.joinToString(" • ")
                    if (subline.isNotBlank()) {
                        Text(subline, color = Muted, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onRefresh) {
                    Text("🌐 Live Portal", color = Teal, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onSignOut) {
                    Text("Sign out", color = Muted)
                }
            }

            when (selectedTab) {
                AppTab.Home -> DashboardScreen(data)
                AppTab.Classes -> ClassesScreen(data.classes)
                AppTab.Attendance -> AttendanceScreen(data.attendance)
                AppTab.Marks -> MarksScreen(data.marks)
            }
        }
    }
}

@Composable
private fun DashboardScreen(data: PortalSnapshotUiData) {
    val totalAttended = data.attendance.sumOf { it.attended }
    val totalConducted = data.attendance.sumOf { it.total }
    val overallAttendance = when {
        totalConducted > 0 -> ((totalAttended.toDouble() / totalConducted) * 100.0).roundToInt()
        data.attendance.isNotEmpty() -> data.attendance.map { it.percentage }.average().roundToInt()
        else -> 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Last synced: ${data.syncedAt} • Day Order: ${data.dayOrder}",
            color = Muted,
            fontSize = 12.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Navy)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "OVERALL ATTENDANCE",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$overallAttendance%",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (overallAttendance >= 75) "ON TRACK" else "LOW (<75%)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                ProgressTrack(
                    progress = overallAttendance / 100f,
                    color = Teal,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                Text(
                    "$totalAttended / $totalConducted total hours attended across ${data.attendance.size} courses",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }
        }

        Text("Your Courses & Schedule", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        data.classes.forEach { ClassCard(it) }
    }
}

@Composable
private fun ClassesScreen(classes: List<ClassItem>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Classes & Timetable", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        classes.forEach { ClassCard(it) }
    }
}

@Composable
private fun AttendanceScreen(attendance: List<AttendanceItem>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Subject Attendance", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        attendance.forEach { item ->
            val pct = item.percentage
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.subject, color = Ink, fontWeight = FontWeight.Bold)
                            if (item.code.isNotEmpty()) {
                                Text(item.code, color = Muted, fontSize = 12.sp)
                            }
                        }
                        Text(
                            "$pct%",
                            color = if (pct >= 75) Teal else Danger,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    ProgressTrack(
                        progress = pct / 100f,
                        color = if (pct >= 75) Teal else Danger
                    )
                    Text(
                        "${item.attended} of ${item.total} hours attended",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MarksScreen(marks: List<MarkItem>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Marks & Assessments", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        marks.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.subject, color = Ink, fontWeight = FontWeight.Bold)
                            Text("${item.assessment} (${item.code})", color = Muted, fontSize = 12.sp)
                        }
                        Text(
                            item.rawScoreText,
                            color = Navy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    if (item.total > 0) {
                        ProgressTrack(progress = item.percentage / 100f, color = Teal)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassCard(item: ClassItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PaleBlue)
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(item.day, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(item.date, color = Navy, fontSize = 10.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.subject, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                val codeAndFac = listOf(item.code, item.faculty).filter { it.isNotBlank() }.joinToString(" • ")
                if (codeAndFac.isNotBlank()) {
                    Text(codeAndFac, color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(item.time, color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Room: ${item.room}", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ProgressTrack(
    progress: Float,
    color: Color,
    trackColor: Color = Color(0xFFE8EDF3)
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color)
        )
    }
}
