package com.example.srmstudent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val SRM_PORTAL_URL = "https://academia.srmist.edu.in/"
private const val DEFAULT_SRM_DOMAIN = "@srmist.edu.in"

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
// 2. LOCAL OFFLINE STORAGE (WORKS OFFLINE & ON SRM WI-FI)
// =========================================================================
object LocalPortalCache {
    private const val CACHE_FILE = "srm_portal_data_v2.json"

    fun saveJson(context: Context, rawJson: String) {
        runCatching {
            File(context.filesDir, CACHE_FILE).writeText(rawJson, Charsets.UTF_8)
        }
    }

    fun loadData(context: Context): PortalSnapshotUiData? {
        return runCatching {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return null
            parsePortalJson(JSONObject(file.readText(Charsets.UTF_8)))
        }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, CACHE_FILE).delete() }
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }
}

// =========================================================================
// 3. JSON PARSER FOR EXTRACTED SRM PORTAL TABLES
// =========================================================================
fun parsePortalJson(root: JSONObject): PortalSnapshotUiData {
    val todayDate = SimpleDateFormat("dd MMM", Locale.ENGLISH).format(Date()).uppercase()
    val syncedAt = root.optString(
        "syncedAt",
        SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date())
    )

    val studentName = root.optString("studentName").ifBlank { "SRM Student" }
    val regNumber = root.optString("regNumber").ifBlank { "Signed In" }
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

fun slotToTiming(slot: String): String {
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
// 4. JAVASCRIPT DOM EXTRACTOR FOR SRM ACADEMIA (ATTENDANCE, MARKS, CLASSES)
// =========================================================================
private val JS_EXTRACT_SRM_DATA = """
(function() {
    function clean(text) {
        return (text || "").replace(/\s+/g, " ").trim();
    }

    var bodyText = document.body ? clean(document.body.innerText) : "";
    var isLoginPage = !!document.querySelector('input[type="password"], #login_id, #signinFrame, iframe[src*="signin"]');

    var result = {
        url: window.location.href,
        hash: window.location.hash,
        isLoginPage: isLoginPage && bodyText.indexOf("Registration Number") === -1 && bodyText.indexOf("Hours Conducted") === -1,
        studentName: "",
        regNumber: "",
        department: "",
        semester: "",
        dayOrder: "",
        attendance: [],
        marks: [],
        classes: []
    };

    // Extract Day Order if visible anywhere on the page
    var doMatch = bodyText.match(/Day\s*Order\s*[:\-]?\s*([0-9]+|No\s*Day\s*Order|Holiday)/i);
    if (doMatch) {
        result.dayOrder = clean(doMatch[1]);
    }

    // Extract Student Profile fields
    var regMatch = bodyText.match(/Registration\s*Number\s*[:\-]?\s*([A-Z0-9]{10,20})/i);
    if (regMatch) result.regNumber = clean(regMatch[1]);

    var allTables = Array.from(document.querySelectorAll("table"));

    allTables.forEach(function(table) {
        var rows = Array.from(table.querySelectorAll("tr"));
        if (rows.length === 0) return;

        // Check key-value profile cells
        rows.forEach(function(r) {
            var cells = Array.from(r.querySelectorAll("td, th")).map(function(c) { return clean(c.innerText); });
            for (var i = 0; i < cells.length - 1; i++) {
                var k = cells[i].toLowerCase();
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
        for (var rIdx = 0; rIdx < Math.min(rows.length, 4); rIdx++) {
            var candidate = Array.from(rows[rIdx].children).map(function(c) { return clean(c.innerText).toLowerCase(); });
            var joined = candidate.join(" | ");
            if (joined.indexOf("course code") !== -1 || joined.indexOf("hours conducted") !== -1 || joined.indexOf("test performance") !== -1) {
                headerIndex = rIdx;
                headers = candidate;
                break;
            }
        }

        if (headerIndex === -1) return;
        var headerLine = headers.join(" | ");

        var idxCode = headers.findIndex(function(h) { return h.indexOf("course code") !== -1 || h === "code"; });
        var idxTitle = headers.findIndex(function(h) { return h.indexOf("course title") !== -1 || h.indexOf("subject") !== -1 || h === "title"; });
        var idxFaculty = headers.findIndex(function(h) { return h.indexOf("faculty") !== -1; });
        var idxSlot = headers.findIndex(function(h) { return h.indexOf("slot") !== -1; });
        var idxRoom = headers.findIndex(function(h) { return h.indexOf("room") !== -1 || h.indexOf("venue") !== -1; });
        var idxConducted = headers.findIndex(function(h) { return h.indexOf("conducted") !== -1; });
        var idxAbsent = headers.findIndex(function(h) { return h.indexOf("absent") !== -1; });
        var idxAttn = headers.findIndex(function(h) { return h.indexOf("attn") !== -1 || h.indexOf("%") !== -1; });
        var idxPerf = headers.findIndex(function(h) { return h.indexOf("test performance") !== -1 || h.indexOf("performance") !== -1 || h.indexOf("marks") !== -1; });

        // 1. Attendance Table (also contains Course, Slot, Faculty, Room)
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

                // Match patterns like "FT-I/5.00 4.50" or "CT-1 / 15.00 : 12.50"
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

        // 3. Dedicated Timetable / Course Registration Table (Updates Room & Slot details)
        else if (headerLine.indexOf("slot") !== -1 && idxTitle !== -1 && idxConducted === -1) {
            for (var t = headerIndex + 1; t < rows.length; t++) {
                var tCols = Array.from(rows[t].children).map(function(c) { return clean(c.innerText); });
                if (tCols.length <= idxTitle) continue;

                var tCode = idxCode !== -1 ? (tCols[idxCode] || "").split(" ")[0] : "";
                var tTitle = tCols[idxTitle] || "";
                if (!tTitle || tTitle.toLowerCase().indexOf("course title") !== -1) continue;

                var tSlot = idxSlot !== -1 ? (tCols[idxSlot] || "") : "";
                var tRoom = idxRoom !== -1 ? (tCols[idxRoom] || "") : "";
                var tFac = idxFaculty !== -1 ? (tCols[idxFaculty] || "") : "";

                var existing = result.classes.find(function(c) { return c.code === tCode && tCode !== ""; });
                if (existing) {
                    if (tRoom) existing.room = tRoom;
                    if (tSlot) existing.slot = tSlot;
                    if (tFac) existing.faculty = tFac;
                } else {
                    result.classes.push({
                        code: tCode,
                        subject: tTitle,
                        slot: tSlot,
                        room: tRoom,
                        faculty: tFac
                    });
                }
            }
        }
    });

    return JSON.stringify(result);
})();
""".trimIndent()

// =========================================================================
// 5. MAIN APP STATE & DIRECT PORTAL SYNC FLOW
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
    var isSyncingPortal by remember { mutableStateOf(false) }
    var pendingUsername by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf("") }

    if (isSyncingPortal) {
        DirectSrmPortalSyncScreen(
            username = pendingUsername,
            password = pendingPassword,
            onSyncComplete = { rawJson, parsedData ->
                LocalPortalCache.saveJson(context, rawJson)
                portalData = parsedData
                pendingPassword = ""
                isSyncingPortal = false
            },
            onCancel = {
                isSyncingPortal = false
            }
        )
    } else if (portalData == null) {
        LoginScreen(
            onStartPortalSync = { user, pass ->
                pendingUsername = if (user.contains("@")) user.trim() else user.trim() + DEFAULT_SRM_DOMAIN
                pendingPassword = pass
                isSyncingPortal = true
            }
        )
    } else {
        AuthenticatedPortalScaffold(
            data = portalData!!,
            onRefresh = {
                pendingUsername = ""
                pendingPassword = ""
                isSyncingPortal = true
            },
            onSignOut = {
                LocalPortalCache.clear(context)
                portalData = null
            }
        )
    }
}

// =========================================================================
// 6. AUTOMATIC SRM PORTAL ENGINE (LOGS IN & EXTRACTS TABLES AUTOMATICALLY)
// =========================================================================
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DirectSrmPortalSyncScreen(
    username: String,
    password: String,
    onSyncComplete: (String, PortalSnapshotUiData) -> Unit,
    onCancel: () -> Unit
) {
    var statusText by remember { mutableStateOf("Connecting to SRM Student Portal...") }
    var showInteractivePortal by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var mergedJson by remember { mutableStateOf(JSONObject()) }
    var askedForTimetable by remember { mutableStateOf(false) }

    // Background polling loop: auto-fills credentials, navigates to Attendance & Timetable,
    // and extracts the student's live data as soon as the tables appear.
    LaunchedEffect(webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        var elapsedSeconds = 0

        while (true) {
            delay(1500)
            elapsedSeconds += 1

            // If login takes more than 7 seconds (e.g., CAPTCHA or Zoho session limit prompt),
            // automatically reveal the portal window so the student can see/tap it.
            if (elapsedSeconds == 5 && mergedJson.optJSONArray("attendance") == null) {
                showInteractivePortal = true
                statusText = "If SRM shows a CAPTCHA or session prompt below, complete it—data will sync automatically."
            }

            // Inject auto-login script if credentials were provided
            if (username.isNotBlank() && password.isNotBlank()) {
                val safeUser = JSONObject.quote(username)
                val safePass = JSONObject.quote(password)
                val autoLoginJs = """
                    (function() {
                        function tryFill(doc) {
                            if (!doc) return;
                            var u = doc.querySelector('#login_id, input[name="username"], input[type="email"], input[name="LOGIN_ID"]');
                            var p = doc.querySelector('#password, input[name="password"], input[type="password"]');
                            var btn = doc.querySelector('#nextbtn, button[type="submit"], input[type="submit"]');
                            if (u && (!p || p.offsetParent === null)) {
                                if (!u.value) {
                                    u.value = $safeUser;
                                    u.dispatchEvent(new Event('input', { bubbles: true }));
                                    if (btn) btn.click();
                                }
                            } else if (p && p.offsetParent !== null) {
                                if (!p.value) {
                                    p.value = $safePass;
                                    p.dispatchEvent(new Event('input', { bubbles: true }));
                                    if (btn) btn.click();
                                }
                            }
                        }
                        tryFill(document);
                        var frames = document.querySelectorAll('iframe');
                        for (var i = 0; i < frames.length; i++) {
                            try { tryFill(frames[i].contentDocument); } catch (e) {}
                        }
                    })();
                """.trimIndent()
                webView.evaluateJavascript(autoLoginJs, null)
            }

            // Evaluate the DOM extractor
            webView.evaluateJavascript(JS_EXTRACT_SRM_DATA) { rawResult ->
                if (rawResult.isNullOrBlank() || rawResult == "null") return@evaluateJavascript
                runCatching {
                    val unescaped = org.json.JSONTokener(rawResult).nextValue().toString()
                    val current = JSONObject(unescaped)
                    val isLogin = current.optBoolean("isLoginPage", true)
                    val currentHash = current.optString("hash", "")

                    if (!isLogin) {
                        // Merge profile fields
                        for (key in listOf("studentName", "regNumber", "department", "semester", "dayOrder")) {
                            val v = current.optString(key)
                            if (v.isNotBlank()) mergedJson.put(key, v)
                        }

                        val att = current.optJSONArray("attendance")
                        val mrk = current.optJSONArray("marks")
                        val cls = current.optJSONArray("classes")

                        if (att != null && att.length() > 0) {
                            mergedJson.put("attendance", att)
                        }
                        if (mrk != null && mrk.length() > 0) {
                            mergedJson.put("marks", mrk)
                        }
                        if (cls != null && cls.length() > 0) {
                            mergedJson.put("classes", cls)
                        }

                        val hasAttendance = (mergedJson.optJSONArray("attendance")?.length() ?: 0) > 0

                        if (!hasAttendance && !currentHash.contains("My_Attendance", ignoreCase = true)) {
                            statusText = "Signed in! Opening Attendance & Internal Marks..."
                            webView.evaluateJavascript("window.location.hash = '#Page:My_Attendance';", null)
                        } else if (hasAttendance && !askedForTimetable) {
                            askedForTimetable = true
                            statusText = "Attendance & Marks extracted! Fetching Timetable rooms & slots..."
                            webView.evaluateJavascript("window.location.hash = '#Page:My_Time_Table_2023_24';", null)
                        } else if (hasAttendance) {
                            mergedJson.put(
                                "syncedAt",
                                SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date())
                            )
                            val finalJsonString = mergedJson.toString()
                            val parsed = parsePortalJson(mergedJson)
                            onSyncComplete(finalJsonString, parsed)
                        }
                    }
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = PageBackground) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Navy)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Syncing SRM Student Portal",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        statusText,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = { showInteractivePortal = !showInteractivePortal }) {
                    Text(
                        if (showInteractivePortal) "Hide View" else "Show Portal",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false
                            }

                            loadUrl(SRM_PORTAL_URL)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (showInteractivePortal) 1f else 0.01f)
                )

                if (!showInteractivePortal) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = Navy)
                            Text(
                                "Fetching Your SRM Data...",
                                color = Ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                statusText,
                                color = Muted,
                                fontSize = 13.sp
                            )
                            OutlinedButton(onClick = { showInteractivePortal = true }) {
                                Text("Show Portal Window (for CAPTCHA / OTP)")
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 7. LOGIN SCREEN & NATIVE DASHBOARD SCREENS
// =========================================================================
@Composable
private fun LoginScreen(
    onStartPortalSync: (String, String) -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = PageBackground) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
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
                    Text(
                        "SRM STUDENT PORTAL",
                        color = Navy,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Sign in with your SRM NetID",
                        color = Ink,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Connects directly to SRM Academia to load your Attendance, Timetable, Day Order, and Internal Marks.",
                        color = Muted,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { identifier = it },
                        label = { Text("SRM Email or NetID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onStartPortalSync(identifier, password) },
                        enabled = identifier.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) {
                        Text("Sign In & Fetch My Data")
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
                    val subline = listOf(data.department, data.semester.let { if (it.isNotBlank()) "Sem $it" else "" })
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    if (subline.isNotBlank()) {
                        Text(subline, color = Muted, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onRefresh) {
                    Text("↻ Sync", color = Teal, fontWeight = FontWeight.Bold)
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
        data.classes.take(5).forEach { ClassCard(it) }
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
