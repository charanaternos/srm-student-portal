package com.example.srmstudent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

private const val SRM_LOGIN_URL =
    "https://sp.srmist.edu.in/srmiststudentportal/students/loginManager/youLogin.jsp"

private val Navy = Color(0xFF153B63)
private val Teal = Color(0xFF18A999)
private val PageBg = Color(0xFFF4F7FB)
private val Ink = Color(0xFF1B2638)
private val Muted = Color(0xFF718096)
private val PaleBlue = Color(0xFFEAF1FA)
private val Danger = Color(0xFFC24D4D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Navy,
                    secondary = Teal,
                    background = PageBg,
                    surface = Color.White,
                    onSurface = Ink,
                    onPrimary = Color.White
                )
            ) {
                StudentPortalApp()
            }
        }
    }
}

// ─── DATA MODELS ────────────────────────────────────────────────────────────

data class ClassItem(
    val subject: String, val code: String, val day: String,
    val date: String, val time: String, val room: String, val faculty: String
)

data class AttendanceItem(
    val subject: String, val code: String,
    val attended: Int, val total: Int, val rawPct: Double? = null
) {
    val pct: Int
        get() = when {
            rawPct != null -> rawPct.roundToInt()
            total > 0 -> ((attended.toDouble() / total) * 100).roundToInt()
            else -> 0
        }
}

data class MarkItem(
    val subject: String, val code: String, val assessment: String,
    val obtained: Double, val total: Double, val raw: String
) {
    val pct: Int get() = if (total > 0) ((obtained / total) * 100).roundToInt() else 0
}

data class PortalData(
    val name: String, val regNo: String, val dept: String,
    val sem: String, val dayOrder: String, val syncedAt: String,
    val classes: List<ClassItem>,
    val attendance: List<AttendanceItem>,
    val marks: List<MarkItem>
)

// ─── OFFLINE CACHE ──────────────────────────────────────────────────────────

private object Cache {
    private const val FILE = "srm_data.json"

    fun save(ctx: Context, json: String) {
        runCatching { File(ctx.filesDir, FILE).writeText(json) }
    }

    fun load(ctx: Context): PortalData? = runCatching {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return null
        val d = parseJson(JSONObject(f.readText()))
        if (d.attendance.isEmpty() && d.classes.isEmpty() && d.marks.isEmpty()) null else d
    }.getOrNull()

    fun clear(ctx: Context) {
        runCatching { File(ctx.filesDir, FILE).delete() }
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
    }
}

// ─── JSON PARSER ────────────────────────────────────────────────────────────

private fun parseJson(root: JSONObject): PortalData {
    val today = SimpleDateFormat("dd MMM", Locale.ENGLISH).format(Date()).uppercase()
    val synced = root.optString("syncedAt",
        SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date()))

    val name = root.optString("studentName").ifBlank { "SRM Student" }
    val regNo = root.optString("regNumber").ifBlank { "Signed In" }
    val dept = root.optString("department").ifBlank { "" }
    val sem = root.optString("semester").ifBlank { "" }
    val dayOrder = root.optString("dayOrder").ifBlank { "Active" }

    val titleMap = mutableMapOf<String, String>()

    val att = mutableListOf<AttendanceItem>()
    val attArr = root.optJSONArray("attendance") ?: JSONArray()
    for (i in 0 until attArr.length()) {
        val o = attArr.optJSONObject(i) ?: continue
        val code = o.optString("code").trim()
        val subj = o.optString("subject").trim().ifBlank { code }
        if (subj.isBlank()) continue
        if (code.isNotBlank()) titleMap[code] = subj
        val conducted = o.optInt("conducted", 0)
        val absent = o.optInt("absent", 0)
        val attended = o.optInt("attended", (conducted - absent).coerceAtLeast(0))
        val pct = o.optString("percentage").replace("%", "").trim().toDoubleOrNull()
        att.add(AttendanceItem(subj, code, attended, conducted, pct))
    }

    val mk = mutableListOf<MarkItem>()
    val mkArr = root.optJSONArray("marks") ?: JSONArray()
    for (i in 0 until mkArr.length()) {
        val o = mkArr.optJSONObject(i) ?: continue
        val code = o.optString("code").trim()
        val subj = o.optString("subject").trim().ifBlank { titleMap[code] ?: code }
        val assess = o.optString("assessment").trim().ifBlank { "Internal" }
        val got = o.optDouble("obtained", 0.0)
        val max = o.optDouble("total", 0.0)
        val raw = o.optString("rawScore").trim().ifBlank {
            if (max > 0) "${got.toString().removeSuffix(".0")}/${max.toString().removeSuffix(".0")}"
            else got.toString().removeSuffix(".0")
        }
        if (subj.isNotBlank()) mk.add(MarkItem(subj, code, assess, got, max, raw))
    }

    val cls = mutableListOf<ClassItem>()
    val clsArr = root.optJSONArray("classes") ?: JSONArray()
    for (i in 0 until clsArr.length()) {
        val o = clsArr.optJSONObject(i) ?: continue
        val code = o.optString("code").trim()
        val subj = o.optString("subject").trim().ifBlank { titleMap[code] ?: code }
        if (subj.isBlank()) continue
        val slot = o.optString("slot").trim()
        val room = o.optString("room").trim().ifBlank { "TBA" }
        val fac = o.optString("faculty").trim()
        val time = o.optString("time").trim().ifBlank { slotTime(slot) }
        val day = o.optString("day").trim().ifBlank {
            if (dayOrder.any { it.isDigit() }) "DO-$dayOrder" else slot.ifBlank { "SLOT" }
        }
        cls.add(ClassItem(subj, code, day, today, time, room, fac))
    }

    return PortalData(name, regNo, dept, sem, dayOrder, synced, cls, att, mk)
}

internal fun slotTime(s: String): String {
    val c = s.uppercase()
    return when {
        c.startsWith("A") -> "08:00 - 08:50 AM ($s)"
        c.startsWith("B") -> "08:50 - 09:40 AM ($s)"
        c.startsWith("C") -> "09:45 - 10:35 AM ($s)"
        c.startsWith("D") -> "10:40 - 11:30 AM ($s)"
        c.startsWith("E") -> "11:35 - 12:25 PM ($s)"
        c.startsWith("F") -> "12:30 - 01:20 PM ($s)"
        c.startsWith("G") -> "01:25 - 02:15 PM ($s)"
        c.startsWith("P") || c.startsWith("L") -> "Lab ($s)"
        s.isNotBlank() -> "Slot $s"
        else -> "Scheduled"
    }
}

// ─── JAVASCRIPT: AUTO-LOGIN + EXTRACT ──────────────────────────────────────


fun getDemoJson(): String {
    return JSONObject().apply {
        put("studentName", "Charan Vadla")
        put("regNumber", "RA2311003010999")
        put("department", "Computer Science")
        put("semester", "4")
        put("dayOrder", "2")
        put("syncedAt", SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date()))
        put("attendance", JSONArray().apply {
            put(JSONObject().apply {
                put("subject", "Design and Analysis of Algorithms")
                put("code", "21CSC204J")
                put("conducted", 32)
                put("attended", 30)
                put("percentage", "93.8%")
            })
            put(JSONObject().apply {
                put("subject", "Operating Systems")
                put("code", "21CSC205J")
                put("conducted", 30)
                put("attended", 26)
                put("percentage", "86.7%")
            })
            put(JSONObject().apply {
                put("subject", "Database Management Systems")
                put("code", "21CSC206J")
                put("conducted", 30)
                put("attended", 29)
                put("percentage", "96.7%")
            })
            put(JSONObject().apply {
                put("subject", "Computer Networks")
                put("code", "21CSC207J")
                put("conducted", 28)
                put("attended", 24)
                put("percentage", "85.7%")
            })
        })
        put("classes", JSONArray().apply {
            put(JSONObject().apply {
                put("subject", "Design and Analysis of Algorithms")
                put("code", "21CSC204J")
                put("slot", "A1")
                put("room", "TP-402")
                put("faculty", "Dr. K. Sharma")
            })
            put(JSONObject().apply {
                put("subject", "Operating Systems")
                put("code", "21CSC205J")
                put("slot", "B2")
                put("room", "TP-501")
                put("faculty", "Prof. R. Raman")
            })
            put(JSONObject().apply {
                put("subject", "Database Management Systems")
                put("code", "21CSC206J")
                put("slot", "C1")
                put("room", "UB-303")
                put("faculty", "Dr. S. Nair")
            })
            put(JSONObject().apply {
                put("subject", "Computer Networks")
                put("code", "21CSC207J")
                put("slot", "D")
                put("room", "TP-405")
                put("faculty", "Dr. A. Verma")
            })
        })
        put("marks", JSONArray().apply {
            put(JSONObject().apply {
                put("subject", "Design and Analysis of Algorithms")
                put("code", "21CSC204J")
                put("assessment", "Cycle Test 1")
                put("obtained", 24.0)
                put("total", 25.0)
                put("rawScore", "24 / 25")
            })
            put(JSONObject().apply {
                put("subject", "Operating Systems")
                put("code", "21CSC205J")
                put("assessment", "Cycle Test 1")
                put("obtained", 22.5)
                put("total", 25.0)
                put("rawScore", "22.5 / 25")
            })
            put(JSONObject().apply {
                put("subject", "Database Management Systems")
                put("code", "21CSC206J")
                put("assessment", "Cycle Test 1")
                put("obtained", 25.0)
                put("total", 25.0)
                put("rawScore", "25 / 25")
            })
            put(JSONObject().apply {
                put("subject", "Computer Networks")
                put("code", "21CSC207J")
                put("assessment", "Cycle Test 1")
                put("obtained", 21.0)
                put("total", 25.0)
                put("rawScore", "21 / 25")
            })
        })
    }.toString()
}

private val JS_AUTO_LOGIN = """
(function(){
  var rawId = %ID%;
  var pw = %PW%;
  var isSp = location.hostname.indexOf('sp.srmist.edu.in') !== -1;
  var cleanId = isSp ? rawId.replace(/@.*$/, '').trim() : rawId.trim();

  function fill(doc) {
    if (!doc) return;
    var inputs = Array.from(doc.querySelectorAll('input'));
    var idInput = doc.querySelector('#username, input[name="username"], input[name="login_id"]') ||
      inputs.find(function(i) {
        var n = ((i.name||'') + (i.id||'')).toLowerCase();
        var t = (i.type||'text').toLowerCase();
        return (n.indexOf('net') !== -1 || n.indexOf('login') !== -1 ||
                n.indexOf('user') !== -1 || n.indexOf('email') !== -1) &&
               (t === 'text' || t === 'email') && i.offsetParent !== null;
      });

    var pwInput = doc.querySelector('#password, input[name="password"], input[type="password"]') ||
      inputs.find(function(i) {
        return (i.type||'').toLowerCase() === 'password' && i.offsetParent !== null;
      });

    var capInput = doc.querySelector('#captcha, input[name="captcha"]') ||
      inputs.find(function(i) {
        var n = ((i.name||'') + (i.id||'')).toLowerCase();
        return (n.indexOf('cap') !== -1 || n.indexOf('verif') !== -1) && i.offsetParent !== null;
      });

    if (idInput && (!idInput.value || idInput.value !== cleanId)) {
      idInput.value = cleanId;
      idInput.dispatchEvent(new Event('input', {bubbles:true}));
      idInput.dispatchEvent(new Event('change', {bubbles:true}));
    }
    if (pwInput && (!pwInput.value || pwInput.value !== pw)) {
      pwInput.value = pw;
      pwInput.dispatchEvent(new Event('input', {bubbles:true}));
      pwInput.dispatchEvent(new Event('change', {bubbles:true}));
    }

    if (capInput) {
      if (!capInput.value) {
        capInput.focus();
      } else if (capInput.value.length >= 4) {
        var btn = doc.querySelector('#btnLogin, button[type="submit"], input[type="submit"]');
        if (btn && !btn.disabled) btn.click();
      }
    }
  }
  fill(document);
  Array.from(document.querySelectorAll('iframe')).forEach(function(f) {
    try { fill(f.contentDocument); } catch(e) {}
  });
})();
""".trimIndent()

private val JS_EXTRACT = """
(function(){
  function cl(t){return (t||'').replace(/\\s+/g,' ').trim();}
  var bt = document.body ? cl(document.body.innerText) : '';
  var hasPw = !!document.querySelector('input[type="password"]');
  var hasContent = bt.indexOf('Registration') !== -1 || bt.indexOf('Hours') !== -1 ||
                   bt.indexOf('Attendance') !== -1 || bt.indexOf('Time Table') !== -1 ||
                   bt.indexOf('Sign Out') !== -1 || bt.indexOf('Logout') !== -1 ||
                   bt.indexOf('Day Order') !== -1 || bt.indexOf('Course') !== -1;
  var r = {
    url: location.href, hash: location.hash,
    loggedIn: hasContent && !hasPw,
    studentName:'', regNumber:'', department:'', semester:'', dayOrder:'',
    attendance:[], marks:[], classes:[]
  };
  var dm = bt.match(/Day\\s*Order\\s*[:\\-]?\\s*([0-9]+|No\\s*Day\\s*Order|Holiday)/i);
  if(dm) r.dayOrder = cl(dm[1]);
  var rm = bt.match(/Registration\\s*Number\\s*[:\\-]?\\s*([A-Z0-9]{8,20})/i);
  if(rm) r.regNumber = cl(rm[1]);

  var tables = Array.from(document.querySelectorAll('table'));
  tables.forEach(function(tb){
    var rows = Array.from(tb.querySelectorAll('tr'));
    if(!rows.length) return;
    rows.forEach(function(rw){
      var cs = Array.from(rw.querySelectorAll('td,th')).map(function(c){return cl(c.innerText);});
      for(var i=0;i<cs.length-1;i++){
        var k=cs[i].toLowerCase().replace(/[:\\-]/g,'').trim(), v=cs[i+1];
        if(!v) continue;
        if((k==='name'||k==='student name'||k==='studentname')&&!r.studentName) r.studentName=v;
        if(k.indexOf('registration')!==-1&&!r.regNumber) r.regNumber=v;
        if(k==='department'&&!r.department) r.department=v;
        if(k==='semester'&&!r.semester) r.semester=v;
      }
    });
    var hi=-1,hs=[];
    for(var ri=0;ri<Math.min(rows.length,6);ri++){
      var cand=Array.from(rows[ri].children).map(function(c){return cl(c.innerText).toLowerCase();});
      var j=cand.join('|');
      if(j.indexOf('course')!==-1||j.indexOf('hours')!==-1||j.indexOf('conducted')!==-1||
         j.indexOf('performance')!==-1||j.indexOf('slot')!==-1||j.indexOf('marks')!==-1){
        hi=ri;hs=cand;break;
      }
    }
    if(hi===-1) return;
    var ic=hs.findIndex(function(h){return h.indexOf('course')!==-1&&h.indexOf('code')!==-1||h==='code';});
    var it=hs.findIndex(function(h){return h.indexOf('course')!==-1&&h.indexOf('title')!==-1||
      h.indexOf('subject')!==-1||h==='title'||h.indexOf('course name')!==-1;});
    if(it===-1) it=hs.findIndex(function(h){return h.indexOf('title')!==-1||h.indexOf('subject')!==-1;});
    var if_=hs.findIndex(function(h){return h.indexOf('faculty')!==-1||h.indexOf('teacher')!==-1;});
    var is_=hs.findIndex(function(h){return h.indexOf('slot')!==-1;});
    var ir=hs.findIndex(function(h){return h.indexOf('room')!==-1||h.indexOf('venue')!==-1;});
    var icd=hs.findIndex(function(h){return h.indexOf('conducted')!==-1;});
    var ia=hs.findIndex(function(h){return h.indexOf('absent')!==-1;});
    var ip=hs.findIndex(function(h){return h.indexOf('attn')!==-1||h.indexOf('%')!==-1;});
    var ipf=hs.findIndex(function(h){return h.indexOf('performance')!==-1||
      h.indexOf('marks')!==-1||h.indexOf('score')!==-1;});

    if(icd!==-1&&it!==-1){
      for(var i=hi+1;i<rows.length;i++){
        var cs=Array.from(rows[i].children).map(function(c){return cl(c.innerText);});
        if(cs.length<=icd) continue;
        var code=ic!==-1?(cs[ic]||'').split(' ')[0]:'';
        var title=cs[it]||'';
        if(!title||title.toLowerCase().indexOf('course')!==-1||title.toLowerCase()==='total') continue;
        var cond=parseInt((cs[icd]||'0').replace(/[^0-9]/g,''),10)||0;
        var abs=ia!==-1?(parseInt((cs[ia]||'0').replace(/[^0-9]/g,''),10)||0):0;
        var att=Math.max(0,cond-abs);
        var pct=ip!==-1?(cs[ip]||''):'';
        var slot=is_!==-1?(cs[is_]||''):'';
        var room=ir!==-1?(cs[ir]||''):'';
        var fac=if_!==-1?(cs[if_]||''):'';
        r.attendance.push({code:code,subject:title,conducted:cond,absent:abs,attended:att,percentage:pct});
        r.classes.push({code:code,subject:title,slot:slot,room:room,faculty:fac});
      }
    } else if(ipf!==-1&&ic!==-1){
      for(var m=hi+1;m<rows.length;m++){
        var mc=Array.from(rows[m].children);
        if(mc.length<=ipf) continue;
        var cc=cl(mc[ic].innerText).split(' ')[0];
        if(!cc||cc.toLowerCase().indexOf('course')!==-1) continue;
        var pt=cl(mc[ipf].innerText);
        if(!pt||pt==='-'||pt.toLowerCase()==='nil') continue;
        var rx=/([A-Za-z0-9_\\-]+)\\s*\\/\\s*([0-9.]+)\\s*[:\\-]?\\s*([0-9.]+|Abs)/g;
        var m2,found=false;
        while((m2=rx.exec(pt))!==null){
          found=true;
          var tm=m2[1],mx=parseFloat(m2[2])||0;
          var gm=m2[3].toLowerCase()==='abs'?0:(parseFloat(m2[3])||0);
          r.marks.push({code:cc,subject:'',assessment:tm,obtained:gm,total:mx,rawScore:gm+' / '+mx});
        }
        if(!found) r.marks.push({code:cc,subject:'',assessment:'Internal',obtained:0,total:0,rawScore:pt});
      }
    } else if(is_!==-1&&it!==-1&&icd===-1){
      for(var t=hi+1;t<rows.length;t++){
        var tc=Array.from(rows[t].children).map(function(c){return cl(c.innerText);});
        if(tc.length<=it) continue;
        var tcode=ic!==-1?(tc[ic]||'').split(' ')[0]:'';
        var ttitle=tc[it]||'';
        if(!ttitle||ttitle.toLowerCase().indexOf('course')!==-1) continue;
        var tslot=is_!==-1?(tc[is_]||''):'';
        var troom=ir!==-1?(tc[ir]||''):'';
        var tfac=if_!==-1?(tc[if_]||''):'';
        var ex=r.classes.find(function(c){return c.code===tcode&&tcode!=='';});
        if(ex){if(troom)ex.room=troom;if(tslot)ex.slot=tslot;if(tfac)ex.faculty=tfac;}
        else r.classes.push({code:tcode,subject:ttitle,slot:tslot,room:troom,faculty:tfac});
      }
    }
  });
  return JSON.stringify(r);
})();
""".trimIndent()

// ─── MAIN APP ───────────────────────────────────────────────────────────────

private enum class Tab(val label: String, val icon: String) {
    Home("Home", "⌂"), Classes("Classes", "▦"),
    Attendance("Attendance", "◉"), Marks("Marks", "★")
}

@Composable
fun StudentPortalApp() {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf(Cache.load(ctx)) }
    var showLogin by remember { mutableStateOf(data == null) }
    var showWebView by remember { mutableStateOf(false) }
    var netId by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }

    if (showWebView) {
        AutoLoginWebView(
            netId = netId,
            password = pw,
            onDone = { json, parsed ->
                Cache.save(ctx, json)
                data = parsed
                showWebView = false
                showLogin = false
            },
            onCancel = { showWebView = false }
        )
    } else if (showLogin) {
        LoginScreen(
            onLogin = { id, pass ->
                netId = id.trim()
                pw = pass
                showWebView = true
            },
            onDemo = {
                val demo = getDemoJson()
                Cache.save(ctx, demo)
                data = parseJson(JSONObject(demo))
                showLogin = false
            }
        )
    } else if (data != null) {
        DashboardScaffold(
            data = data!!,
            onSync = { showWebView = true },
            onSignOut = {
                Cache.clear(ctx)
                data = null
                showLogin = true
            }
        )
    }
}

// ─── NATIVE LOGIN SCREEN ────────────────────────────────────────────────────

@Composable
private fun LoginScreen(
    onLogin: (String, String) -> Unit,
    onDemo: () -> Unit
) {
    var id by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = PageBg) {
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("SRM STUDENT PORTAL", color = Navy, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Sign In", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = id,
                        onValueChange = { id = it },
                        label = { Text("NetID") },
                        placeholder = { Text("e.g. vv1234") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onLogin(id, pass) },
                        enabled = id.isNotBlank() && pass.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) {
                        Text("Auto-Fill & Sign In")
                    }

                    OutlinedButton(
                        onClick = onDemo,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Navy)
                    ) {
                        Text("Preview Demo Mode (Offline)")
                    }

                    Text(
                        "The app automatically pre-fills your NetID and password on SRM's official login page. " +
                            "Type the CAPTCHA letters shown on screen to complete sign-in. Your credentials are never stored.",
                        color = Muted, fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// ─── AUTO-LOGIN WEBVIEW ─────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AutoLoginWebView(
    netId: String,
    password: String,
    onDone: (String, PortalData) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler { onCancel() }
    var status by remember { mutableStateOf("Opening SRM portal...") }
    var wv by remember { mutableStateOf<WebView?>(null) }
    val merged = remember { JSONObject() }
    var attC by remember { mutableStateOf(0) }
    var mkC by remember { mutableStateOf(0) }
    var clsC by remember { mutableStateOf(0) }
    var logged by remember { mutableStateOf(false) }
    var autoLoginSent by remember { mutableStateOf(false) }

    fun merge(cur: JSONObject) {
        if (cur.optBoolean("loggedIn", false)) logged = true
        for (k in listOf("studentName", "regNumber", "department", "semester", "dayOrder")) {
            val v = cur.optString(k)
            if (v.isNotBlank()) merged.put(k, v)
        }
        for (k in listOf("attendance", "marks", "classes")) {
            val a = cur.optJSONArray(k)
            if (a != null && a.length() > 0) merged.put(k, a)
        }
        attC = merged.optJSONArray("attendance")?.length() ?: 0
        mkC = merged.optJSONArray("marks")?.length() ?: 0
        clsC = merged.optJSONArray("classes")?.length() ?: 0
    }

    fun extract() {
        wv?.evaluateJavascript(JS_EXTRACT) { raw ->
            if (raw.isNullOrBlank() || raw == "null") return@evaluateJavascript
            runCatching {
                merge(JSONObject(JSONTokener(raw).nextValue().toString()))
            }
        }
    }

    fun sendAutoLogin() {
        val js = JS_AUTO_LOGIN
            .replace("%ID%", JSONObject.quote(netId))
            .replace("%PW%", JSONObject.quote(password))
        wv?.evaluateJavascript(js, null)
        autoLoginSent = true
        status = "Credentials sent. If CAPTCHA appears, solve it below."
    }

    fun navigateTo(keyword: String) {
        wv?.evaluateJavascript(
            "(function(){var k=" + JSONObject.quote(keyword) + ";" +
            "var a=Array.from(document.querySelectorAll('a'));" +
            "var t=a.find(function(x){return (x.innerText||'').toLowerCase().indexOf(k)!==-1;});" +
            "if(t)t.click();})()"
        , null)
    }

    fun saveAndFinish() {
        if (attC + mkC + clsC == 0) {
            status = "No data found. Open Attendance or Timetable page first."
            return
        }
        merged.put("syncedAt",
            SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date()))
        onDone(merged.toString(), parseJson(merged))
    }

    LaunchedEffect(wv) {
        val w = wv ?: return@LaunchedEffect
        var tick = 0
        while (true) {
            delay(2000)
            tick++
            if (!autoLoginSent && tick >= 2) {
                sendAutoLogin()
            }
            extract()
            if (logged && (merged.optJSONArray("attendance")?.length() ?: 0) == 0) {
                if (tick % 3 == 0) {
                    navigateTo("attendance")
                }
            }
            if ((merged.optJSONArray("attendance")?.length() ?: 0) > 0 &&
                (merged.optJSONArray("classes")?.length() ?: 0) == 0) {
                if (tick % 3 == 0) {
                    navigateTo("time table")
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = PageBg) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().background(Navy)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White, strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        status,
                        color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCancel) {
                        Text("Close", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
                Text(
                    "Collected: $attC attendance  •  $clsC classes  •  $mkC marks",
                    color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = { wv?.loadUrl(SRM_LOGIN_URL) }) {
                        Text("SRM Portal", color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(onClick = { wv?.loadUrl("https://academia.srmist.edu.in/") }) {
                        Text("Academia", color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(onClick = { navigateTo("attendance") }) {
                        Text("Attendance", color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(onClick = { navigateTo("time table") }) {
                        Text("Timetable", color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(onClick = { extract() }) {
                        Text("Import", color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(onClick = { saveAndFinish() }) {
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
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
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
                                if (url?.contains("srmist.edu.in") == true) {
                                    if (!autoLoginSent) sendAutoLogin()
                                    extract()
                                }
                            }
                        }
                        loadUrl(SRM_LOGIN_URL)
                        wv = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ─── DASHBOARD & SCREENS ────────────────────────────────────────────────────

@Composable
private fun DashboardScaffold(
    data: PortalData,
    onSync: () -> Unit,
    onSignOut: () -> Unit
) {
    var tab by remember { mutableStateOf(Tab.Home) }

    Scaffold(
        containerColor = PageBg,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Text(t.icon, fontSize = 18.sp,
                                color = if (tab == t) Navy else Muted)
                        },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(data.regNo, color = Navy, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(data.name, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    val sub = listOf(data.dept,
                        data.sem.let { if (it.isNotBlank()) "Sem $it" else "" })
                        .filter { it.isNotBlank() }.joinToString(" • ")
                    if (sub.isNotBlank()) Text(sub, color = Muted, fontSize = 12.sp)
                }
                TextButton(onClick = onSync) {
                    Text("Sync", color = Teal, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onSignOut) {
                    Text("Out", color = Muted)
                }
            }
            when (tab) {
                Tab.Home -> HomeScreen(data)
                Tab.Classes -> ClassesScreen(data.classes)
                Tab.Attendance -> AttScreen(data.attendance)
                Tab.Marks -> MarksScreen(data.marks)
            }
        }
    }
}

@Composable
private fun HomeScreen(d: PortalData) {
    val totAtt = d.attendance.sumOf { it.attended }
    val totCond = d.attendance.sumOf { it.total }
    val overall = when {
        totCond > 0 -> ((totAtt.toDouble() / totCond) * 100).roundToInt()
        d.attendance.isNotEmpty() -> d.attendance.map { it.pct }.average().roundToInt()
        else -> 0
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Synced: ${d.syncedAt}  •  Day Order: ${d.dayOrder}",
            color = Muted, fontSize = 12.sp)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Navy)
        ) {
            Column(modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("OVERALL ATTENDANCE",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("$overall%", color = Color.White,
                    fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Track(overall / 100f, Teal, Color.White.copy(alpha = 0.2f))
                Text("$totAtt / $totCond hours  •  ${d.attendance.size} courses",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }

        Text("Courses", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        d.classes.forEach { ClassCard(it) }
    }
}

@Composable
private fun ClassesScreen(list: List<ClassItem>) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Classes & Timetable", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        list.forEach { ClassCard(it) }
    }
}

@Composable
private fun AttScreen(list: List<AttendanceItem>) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Attendance", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        list.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.subject, color = Ink, fontWeight = FontWeight.Bold)
                            if (item.code.isNotEmpty())
                                Text(item.code, color = Muted, fontSize = 12.sp)
                        }
                        Text("${item.pct}%",
                            color = if (item.pct >= 75) Teal else Danger,
                            fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Track(item.pct / 100f,
                        if (item.pct >= 75) Teal else Danger)
                    Text("${item.attended} / ${item.total} hours",
                        color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MarksScreen(list: List<MarkItem>) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Marks", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        list.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.subject, color = Ink, fontWeight = FontWeight.Bold)
                            Text("${item.assessment} (${item.code})",
                                color = Muted, fontSize = 12.sp)
                        }
                        Text(item.raw, color = Navy,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    if (item.total > 0) Track(item.pct / 100f, Teal)
                }
            }
        }
    }
}

@Composable
private fun ClassCard(item: ClassItem) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.width(72.dp).clip(RoundedCornerShape(14.dp))
                    .background(PaleBlue).padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(item.day, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(item.date, color = Navy, fontSize = 10.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.subject, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                val sub = listOf(item.code, item.faculty)
                    .filter { it.isNotBlank() }.joinToString(" • ")
                if (sub.isNotBlank()) Text(sub, color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(item.time, color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Room: ${item.room}", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Track(progress: Float, color: Color, track: Color = Color(0xFFE8EDF3)) {
    Box(
        modifier = Modifier.fillMaxWidth().height(8.dp)
            .clip(CircleShape).background(track)
    ) {
        Box(
            modifier = Modifier.fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color)
        )
    }
}