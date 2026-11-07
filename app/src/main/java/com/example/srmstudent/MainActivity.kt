package com.example.srmstudent

import android.content.Context
import android.os.Bundle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import kotlin.math.roundToInt

// =========================================================================
// 1. SUPABASE DEFAULT CONFIGURATION
// (Set your Supabase project URL and anon public key here, or configure them
// directly in the app's settings dialog without rebuilding).
// =========================================================================
private const val DEFAULT_SUPABASE_URL = "https://YOUR_PROJECT_ID.supabase.co/"
private const val DEFAULT_SUPABASE_ANON_KEY = "YOUR_SUPABASE_ANON_PUBLIC_KEY"

// If you log in with a Student ID (like RA2111003010000) instead of an email,
// this domain is appended automatically for Supabase Auth:
private const val DEFAULT_EMAIL_DOMAIN = "@srmist.edu.in"

// =========================================================================
// THEME COLORS
// =========================================================================
private val Navy = Color(0xFF153B63)
private val Teal = Color(0xFF18A999)
private val PageBackground = Color(0xFFF4F7FB)
private val Ink = Color(0xFF1B2638)
private val Muted = Color(0xFF718096)
private val PaleBlue = Color(0xFFEAF1FA)
private val Danger = Color(0xFFC24D4D)

@AndroidEntryPoint
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
// 2. RETROFIT NETWORK API & MODELS
// =========================================================================
data class SupabaseLoginRequest(
    val email: String,
    val password: String
)

data class SupabaseAuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("user") val user: SupabaseUser?
)

data class SupabaseUser(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String?
)

data class SnapshotRow(
    @SerializedName("owner_id") val ownerId: String?,
    @SerializedName("snapshot_date") val snapshotDate: String?,
    @SerializedName("fetched_at") val fetchedAt: String?,
    @SerializedName("data") val data: JsonObject?
)

interface SupabaseApi {
    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(
        @Header("apikey") apiKey: String,
        @Body body: SupabaseLoginRequest
    ): SupabaseAuthResponse

    @GET("rest/v1/student_portal_snapshots")
    suspend fun getLatestSnapshot(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "owner_id,snapshot_date,fetched_at,data",
        @Query("order") order: String = "snapshot_date.desc,fetched_at.desc",
        @Query("limit") limit: Int = 1
    ): List<SnapshotRow>
}

private object ApiClient {
    private var cachedUrl: String? = null
    private var cachedApi: SupabaseApi? = null

    fun get(baseUrl: String): SupabaseApi {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (cachedUrl == cleanUrl && cachedApi != null) {
            return cachedApi!!
        }
        val api = Retrofit.Builder()
            .baseUrl(cleanUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SupabaseApi::class.java)
        cachedUrl = cleanUrl
        cachedApi = api
        return api
    }
}

// =========================================================================
// 3. APP DOMAIN MODELS & FLEXIBLE JSON PARSER
// =========================================================================
data class PortalClassItem(
    val subject: String,
    val code: String,
    val day: String,
    val date: String,
    val time: String,
    val room: String
)

data class PortalAttendanceItem(
    val subject: String,
    val code: String,
    val attended: Int,
    val total: Int,
    val rawPercentage: Int? = null
) {
    val percentage: Int
        get() = when {
            total > 0 -> ((attended.toDouble() / total.toDouble()) * 100.0).roundToInt()
            rawPercentage != null -> rawPercentage
            else -> 0
        }
}

data class PortalMarkItem(
    val subject: String,
    val assessment: String,
    val obtained: Double,
    val total: Double,
    val rawScoreText: String
) {
    val percentage: Int
        get() = if (total > 0) ((obtained / total) * 100.0).roundToInt() else 0
}

data class PortalSnapshotUiData(
    val snapshotDate: String,
    val dayOrder: String?,
    val classes: List<PortalClassItem>,
    val attendance: List<PortalAttendanceItem>,
    val marks: List<PortalMarkItem>
)

private fun JsonObject.findString(vararg keys: String): String? {
    val normalizedMap = entrySet().associate { (k, v) ->
        k.lowercase().replace(Regex("[^a-z0-9]"), "") to v
    }
    for (key in keys) {
        val target = key.lowercase().replace(Regex("[^a-z0-9]"), "")
        val element = normalizedMap[target]
        if (element != null && !element.isJsonNull && element.isJsonPrimitive) {
            val text = element.asString.trim()
            if (text.isNotEmpty() && text != "-" && text.lowercase() != "n/a") {
                return text
            }
        }
    }
    return null
}

private fun String?.toIntSafe(): Int? =
    this?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()?.roundToInt()

private fun String?.toDoubleSafe(): Double? =
    this?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()

private fun parseSnapshotRow(row: SnapshotRow): PortalSnapshotUiData {
    val root = row.data ?: JsonObject()

    // Extract Day Order if present
    val dayOrder = root.findString("day_order", "dayorder", "dayOrder")
        ?: root.getAsJsonArray("day_order_mapping")
            ?.firstOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.findString("day_order", "dayorder", "order")

    // 1. Parse Classes / Timetable
    val classesArray = root.getAsJsonArray("classes")
        ?: root.getAsJsonArray("timetable")
        ?: root.getAsJsonArray("schedule")

    val classes = classesArray?.mapNotNull { element ->
        if (!element.isJsonObject) return@mapNotNull null
        val obj = element.asJsonObject
        val subject = obj.findString(
            "subject", "course_title", "coursetitle", "course_name", "title", "details", "column_2"
        ) ?: return@mapNotNull null

        val code = obj.findString("course_code", "code", "subject_code", "slot", "column_1") ?: ""
        val day = obj.findString("day", "day_order", "weekday", "dayorder") ?: (dayOrder ?: "DAY")
        val date = obj.findString("date", "class_date") ?: (row.snapshotDate ?: "")
        val start = obj.findString("start_time", "starttime", "from")
        val end = obj.findString("end_time", "endtime", "to")
        val time = obj.findString("time", "timing", "time_slot", "slot_time")
            ?: listOfNotNull(start, end).joinToString(" - ").ifEmpty { "Scheduled" }
        val room = obj.findString("room", "room_no", "venue", "location") ?: "TBA"

        PortalClassItem(subject = subject, code = code, day = day, date = date, time = time, room = room)
    } ?: emptyList()

    // 2. Parse Attendance (Supports both standard keys and SRM table headers)
    val attendanceArray = root.getAsJsonArray("attendance")
    val attendance = attendanceArray?.mapNotNull { element ->
        if (!element.isJsonObject) return@mapNotNull null
        val obj = element.asJsonObject

        val subject = obj.findString(
            "subject", "course_title", "coursetitle", "course_name", "course", "column_2"
        ) ?: return@mapNotNull null

        val code = obj.findString("code", "course_code", "subject_code", "column_1") ?: ""

        val totalConducted = obj.findString(
            "total", "hours_conducted", "hoursconducted", "conducted", "total_classes", "max_hours"
        ).toIntSafe() ?: 0

        val absentHours = obj.findString(
            "absent", "hours_absent", "hoursabsent"
        ).toIntSafe()

        val directAttended = obj.findString(
            "attended", "hours_attended", "hoursattended", "present", "attended_classes"
        ).toIntSafe()

        val attended = when {
            directAttended != null -> directAttended
            totalConducted > 0 && absentHours != null -> (totalConducted - absentHours).coerceAtLeast(0)
            else -> 0
        }

        val rawPercent = obj.findString("percentage", "attn", "attn_percent", "attn%").toIntSafe()

        PortalAttendanceItem(
            subject = subject,
            code = code,
            attended = attended,
            total = totalConducted,
            rawPercentage = rawPercent
        )
    } ?: emptyList()

    // 3. Parse Marks
    val marksArray = root.getAsJsonArray("marks") ?: root.getAsJsonArray("internal_marks")
    val marks = marksArray?.mapNotNull { element ->
        if (!element.isJsonObject) return@mapNotNull null
        val obj = element.asJsonObject

        val subject = obj.findString(
            "subject", "course_title", "coursetitle", "course_name", "course_code", "column_1"
        ) ?: return@mapNotNull null

        val assessment = obj.findString(
            "assessment", "exam", "test", "course_type", "category", "column_2"
        ) ?: "Assessment"

        val obtained = obj.findString("obtained", "marks", "score", "scored").toDoubleSafe()
        val total = obj.findString("total", "max_marks", "max", "out_of").toDoubleSafe()
        val rawPerf = obj.findString("test_performance", "performance", "total_marks", "column_3")

        if (obtained != null && total != null && total > 0) {
            PortalMarkItem(
                subject = subject,
                assessment = assessment,
                obtained = obtained,
                total = total,
                rawScoreText = "${obtained.toString().removeSuffix(".0")}/${total.toString().removeSuffix(".0")}"
            )
        } else {
            // Fallback if SRM formats marks like "FT-I / 5.00 : 4.50"
            PortalMarkItem(
                subject = subject,
                assessment = assessment,
                obtained = obtained ?: 0.0,
                total = total ?: 0.0,
                rawScoreText = rawPerf ?: (obtained?.toString() ?: "N/A")
            )
        }
    } ?: emptyList()

    return PortalSnapshotUiData(
        snapshotDate = row.snapshotDate ?: "Latest",
        dayOrder = dayOrder,
        classes = classes,
        attendance = attendance,
        marks = marks
    )
}

// =========================================================================
// 4. UI STATE & MAIN APP COMPOSABLE
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
    val prefs = remember { context.getSharedPreferences("srm_portal_settings", Context.MODE_PRIVATE) }

    var supabaseUrl by remember {
        mutableStateOf(prefs.getString("supabase_url", DEFAULT_SUPABASE_URL) ?: DEFAULT_SUPABASE_URL)
    }
    var supabaseAnonKey by remember {
        mutableStateOf(prefs.getString("supabase_anon_key", DEFAULT_SUPABASE_ANON_KEY) ?: DEFAULT_SUPABASE_ANON_KEY)
    }

    var accessToken by remember { mutableStateOf<String?>(prefs.getString("cached_access_token", null)) }
    var loggedInIdentifier by remember { mutableStateOf(prefs.getString("cached_identifier", "") ?: "") }
    var loggedInUserId by remember { mutableStateOf<String?>(prefs.getString("cached_user_uuid", null)) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Load cached snapshot data on launch for instant offline support
    var portalData by remember {
        mutableStateOf<PortalSnapshotUiData?>(
            prefs.getString("cached_snapshot_json", null)?.let { json ->
                try {
                    val row = Gson().fromJson(json, SnapshotRow::class.java)
                    parseSnapshotRow(row)
                } catch (_: Exception) { null }
            }
        )
    }

    val scope = rememberCoroutineScope()

    fun fetchPortalSnapshot(token: String) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val api = ApiClient.get(supabaseUrl)
                val rows = api.getLatestSnapshot(
                    apiKey = supabaseAnonKey,
                    bearerToken = "Bearer $token"
                )
                if (rows.isEmpty()) {
                    portalData = null
                    errorMessage = "Connected to Supabase, but 0 rows were found in " +
                        "'student_portal_snapshots'. Run your Python sync script or check Supabase RLS policies."
                } else {
                    val latestRow = rows.first()
                    portalData = parseSnapshotRow(latestRow)
                    // Save to offline cache
                    prefs.edit()
                        .putString("cached_snapshot_json", Gson().toJson(latestRow))
                        .apply()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Failed to load data: ${e.localizedMessage ?: "Network error"}"
            } finally {
                isLoading = false
            }
        }
    }

    if (accessToken == null) {
        LoginScreen(
            isLoading = isLoading,
            errorMessage = errorMessage,
            onOpenSettings = { showSettingsDialog = true },
            onSignIn = { rawInput, password ->
                if (supabaseUrl.contains("YOUR_PROJECT_ID") || supabaseAnonKey.contains("YOUR_SUPABASE")) {
                    errorMessage = "Please configure your Supabase URL & Anon Key (click ⚙️ Server Settings below)."
                    return@LoginScreen
                }
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        val email = if (rawInput.contains("@")) {
                            rawInput.trim()
                        } else {
                            rawInput.trim() + DEFAULT_EMAIL_DOMAIN
                        }

                        val api = ApiClient.get(supabaseUrl)
                        val authResponse = api.signIn(
                            apiKey = supabaseAnonKey,
                            body = SupabaseLoginRequest(email = email, password = password)
                        )
                        val token = authResponse.accessToken
                        accessToken = token
                        loggedInIdentifier = rawInput.trim()
                        loggedInUserId = authResponse.user?.id

                        prefs.edit()
                            .putString("cached_access_token", token)
                            .putString("cached_identifier", loggedInIdentifier)
                            .putString("cached_user_uuid", loggedInUserId)
                            .apply()

                        fetchPortalSnapshot(token)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // If Supabase Auth users aren't created yet, fallback to reading with anon key
                        try {
                            val api = ApiClient.get(supabaseUrl)
                            val fallbackRows = api.getLatestSnapshot(
                                apiKey = supabaseAnonKey,
                                bearerToken = "Bearer $supabaseAnonKey"
                            )
                            val token = supabaseAnonKey
                            accessToken = token
                            loggedInIdentifier = rawInput.trim()

                            prefs.edit()
                                .putString("cached_access_token", token)
                                .putString("cached_identifier", loggedInIdentifier)
                                .apply()

                            if (fallbackRows.isEmpty()) {
                                errorMessage = "Signed in, but 'student_portal_snapshots' table is empty. Verify that your Python sync script uploaded rows."
                            } else {
                                val latestRow = fallbackRows.first()
                                portalData = parseSnapshotRow(latestRow)
                                prefs.edit().putString("cached_snapshot_json", Gson().toJson(latestRow)).apply()
                            }
                        } catch (_: Exception) {
                            errorMessage = "Sign-in failed. Verify your email/ID & password in Supabase Auth or check RLS select policy."
                        }
                    } finally {
                        isLoading = false
                    }
                }
            }
        )
    } else {
        AuthenticatedPortalScaffold(
            studentIdentifier = loggedInIdentifier,
            userUuid = loggedInUserId,
            data = portalData,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onRefresh = { accessToken?.let { fetchPortalSnapshot(it) } },
            onSignOut = {
                accessToken = null
                portalData = null
                errorMessage = null
                prefs.edit()
                    .remove("cached_access_token")
                    .remove("cached_snapshot_json")
                    .remove("cached_identifier")
                    .remove("cached_user_uuid")
                    .apply()
            }
        )
    }

    if (showSettingsDialog) {
        var tempUrl by remember { mutableStateOf(supabaseUrl) }
        var tempKey by remember { mutableStateOf(supabaseAnonKey) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Supabase Configuration", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Configure your Supabase Project URL and public 'anon' key. These are saved on device without reinstalling the app.",
                        fontSize = 12.sp,
                        color = Muted
                    )
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("Supabase URL") },
                        placeholder = { Text("https://your-project.supabase.co") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("Supabase Anon Key") },
                        placeholder = { Text("eyJhbGciOi...") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanUrl = tempUrl.trim()
                        val cleanKey = tempKey.trim()
                        supabaseUrl = cleanUrl
                        supabaseAnonKey = cleanKey
                        prefs.edit()
                            .putString("supabase_url", cleanUrl)
                            .putString("supabase_anon_key", cleanKey)
                            .apply()
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// =========================================================================
// 5. SCREENS (LOGIN, DASHBOARD, TIMETABLE, ATTENDANCE, MARKS)
// =========================================================================
@Composable
private fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onOpenSettings: () -> Unit,
    onSignIn: (String, String) -> Unit
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
                        "Sign in to your account",
                        color = Ink,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { identifier = it },
                        label = { Text("Email or Student ID") },
                        singleLine = true,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorMessage?.let {
                        Text(it, color = Danger, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { onSignIn(identifier, password) },
                        enabled = !isLoading && identifier.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Sign In")
                        }
                    }

                    TextButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("⚙️ Configure Supabase Server", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedPortalScaffold(
    studentIdentifier: String,
    userUuid: String?,
    data: PortalSnapshotUiData?,
    isLoading: Boolean,
    errorMessage: String?,
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
            // Top Bar with Live Refresh and Sign Out
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "SRM STUDENT",
                        color = Navy,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        studentIdentifier,
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = onRefresh, enabled = !isLoading) {
                    Text(if (isLoading) "Syncing..." else "↻ Refresh", color = Teal, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onSignOut) {
                    Text("Sign out", color = Muted)
                }
            }

            if (isLoading && data == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Navy)
                }
                return@Column
            }

            errorMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEC))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Data Sync Notice", color = Danger, fontWeight = FontWeight.Bold)
                        Text(msg, color = Ink, fontSize = 13.sp)
                        userUuid?.let {
                            Text("Logged-in User UUID: $it", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (data != null) {
                when (selectedTab) {
                    AppTab.Home -> DashboardScreen(data)
                    AppTab.Classes -> ClassesScreen(data.classes)
                    AppTab.Attendance -> AttendanceScreen(data.attendance)
                    AppTab.Marks -> MarksScreen(data.marks)
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(data: PortalSnapshotUiData) {
    val totalAttended = data.attendance.sumOf { it.attended }
    val totalClasses = data.attendance.sumOf { it.total }
    val overallAttendance = when {
        totalClasses > 0 -> ((totalAttended.toDouble() / totalClasses) * 100).roundToInt()
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
            "Snapshot: ${data.snapshotDate}" + (data.dayOrder?.let { " • Day Order: $it" } ?: ""),
            color = Muted,
            fontSize = 13.sp
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
                Text(
                    "$overallAttendance%",
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
                ProgressTrack(
                    progress = overallAttendance / 100f,
                    color = Teal,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                Text(
                    "${data.attendance.size} subjects tracked • ${data.classes.size} classes • ${data.marks.size} mark entries",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }

        Text("Classes", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (data.classes.isEmpty()) {
            EmptySectionCard("No classes found in the 'classes' JSON array.")
        } else {
            data.classes.take(4).forEach { ClassCard(it) }
        }
    }
}

@Composable
private fun ClassesScreen(classes: List<PortalClassItem>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Class Timetable", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (classes.isEmpty()) {
            EmptySectionCard("No timetable records found in Supabase JSON.")
        } else {
            classes.forEach { ClassCard(it) }
        }
    }
}

@Composable
private fun AttendanceScreen(attendance: List<PortalAttendanceItem>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Subject Attendance", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (attendance.isEmpty()) {
            EmptySectionCard("No attendance records found in Supabase JSON.")
        } else {
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
                        if (item.total > 0) {
                            Text("${item.attended} / ${item.total} hours attended", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarksScreen(marks: List<PortalMarkItem>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Marks & Assessments", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (marks.isEmpty()) {
            EmptySectionCard("No marks records found in Supabase JSON.")
        } else {
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
                                Text(item.assessment, color = Muted, fontSize = 12.sp)
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
}

@Composable
private fun ClassCard(item: PortalClassItem) {
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
                if (item.date.isNotEmpty()) {
                    Text(item.date, color = Navy, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.subject, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (item.code.isNotEmpty()) {
                    Text(item.code, color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(item.time, color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Room: ${item.room}", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptySectionCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Text(message, color = Muted, modifier = Modifier.padding(18.dp), fontSize = 13.sp)
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
