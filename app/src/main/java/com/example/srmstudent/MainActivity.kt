package com.example.srmstudent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.srmstudent.domain.model.StudentProfile
import com.example.srmstudent.domain.repository.PortalAuthRepository
import com.example.srmstudent.ui.components.*
import com.example.srmstudent.ui.screens.AttendanceScreen
import com.example.srmstudent.ui.screens.ClassesScreen
import com.example.srmstudent.ui.screens.DashboardScreen
import com.example.srmstudent.ui.screens.MarksScreen
import com.example.srmstudent.ui.viewmodel.PortalUiState
import com.example.srmstudent.ui.viewmodel.PortalViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortalTheme {
                val viewModel: PortalViewModel = hiltViewModel()
                StudentPortalApp(
                    authRepository = viewModel.authRepository,
                    viewModel = viewModel
                )
            }
        }
    }
}

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
fun StudentPortalApp(
    authRepository: PortalAuthRepository,
    viewModel: PortalViewModel = hiltViewModel()
) {
    var profile by remember { mutableStateOf<StudentProfile?>(authRepository.getCurrentProfile()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (profile == null) {
        LoginScreen(
            isLoading = loading,
            errorMessage = error,
            onSignIn = { id, password ->
                if (!loading) {
                    loading = true
                    error = null

                    scope.launch {
                        try {
                            profile = authRepository.signIn(id, password)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "Sign-in failed. Check your details or try again."
                        } finally {
                            loading = false
                        }
                    }
                }
            }
        )
    } else {
        StudentDashboard(
            profile = profile!!,
            viewModel = viewModel,
            onSignOut = {
                viewModel.signOut()
                profile = null
                error = null
            }
        )
    }
}

@Composable
private fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn: (String, String) -> Unit
) {
    var studentId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "SRM Student Portal",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(
                        "Sign in to view your academic information.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )

                    OutlinedTextField(
                        value = studentId,
                        onValueChange = { studentId = it },
                        label = { Text("Student ID") },
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
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = { onSignIn(studentId.trim(), password) },
                        enabled = !isLoading &&
                            studentId.isNotBlank() &&
                            password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Sign in")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentDashboard(
    profile: StudentProfile,
    viewModel: PortalViewModel,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(AppTab.Home) }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SRM Student",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                },
                actions = {
                    val isRefreshing = (uiState as? PortalUiState.Success)?.isRefreshing == true
                    IconButton(
                        onClick = { viewModel.refresh(forceRemote = true) },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Navy,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("↻", fontSize = 22.sp, color = Navy, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = onSignOut) {
                        Text("Sign out", color = AlertRed, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
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
                        label = {
                            Text(
                                tab.label,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is PortalUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Navy)
                    }
                }

                is PortalUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Unable to sync latest data",
                            color = AlertRed,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            state.message,
                            color = Muted,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refresh(forceRemote = false) },
                            colors = ButtonDefaults.buttonColors(containerColor = Navy)
                        ) {
                            Text("Retry")
                        }
                    }
                }

                is PortalUiState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No academic records found for this student.",
                            fontSize = 15.sp,
                            color = Muted
                        )
                    }
                }

                is PortalUiState.Success -> {
                    when (selectedTab) {
                        AppTab.Home -> DashboardScreen(overview = state.overview)
                        AppTab.Classes -> ClassesScreen(classes = state.overview.classes)
                        AppTab.Attendance -> AttendanceScreen(attendanceList = state.overview.attendance)
                        AppTab.Marks -> MarksScreen(marksList = state.overview.marks)
                    }
                }
            }
        }
    }
}
