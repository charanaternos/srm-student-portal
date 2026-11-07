package com.example.srmstudent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.srmstudent.ui.components.*
import com.example.srmstudent.ui.screens.AttendanceScreen
import com.example.srmstudent.ui.screens.ClassesScreen
import com.example.srmstudent.ui.screens.DashboardScreen
import com.example.srmstudent.ui.screens.MarksScreen
import com.example.srmstudent.ui.viewmodel.PortalUiState
import com.example.srmstudent.ui.viewmodel.PortalViewModel
import dagger.hilt.android.AndroidEntryPoint

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentPortalApp(
    viewModel: PortalViewModel = hiltViewModel()
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = Navy)
                            Text(
                                "Loading student data...",
                                color = Muted,
                                fontSize = 14.sp
                            )
                        }
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
                            Text("Retry from Offline Cache")
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "No academic records available",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink
                            )
                            Button(
                                onClick = { viewModel.refresh(forceRemote = false) },
                                colors = ButtonDefaults.buttonColors(containerColor = Navy)
                            ) {
                                Text("Load Local Timetable")
                            }
                        }
                    }
                }

                is PortalUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(visible = state.isRefreshing) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Teal,
                                trackColor = PaleBlue
                            )
                        }

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
}
