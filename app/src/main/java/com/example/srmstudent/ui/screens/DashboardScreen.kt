package com.example.srmstudent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.srmstudent.domain.model.AcademicOverview
import com.example.srmstudent.ui.components.*

@Composable
fun DashboardScreen(
    overview: AcademicOverview,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SRM STUDENT PORTAL",
                    color = Navy,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    overview.profile.name,
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${overview.profile.regNumber} • ${overview.profile.semester}",
                    color = Muted,
                    fontSize = 13.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(PaleBlue),
                contentAlignment = Alignment.Center
            ) {
                if (!overview.profile.avatarUrl.isNullOrEmpty()) {
                    SubcomposeAsyncImage(
                        model = overview.profile.avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            Text(
                                overview.profile.name.take(1).uppercase(),
                                color = Navy,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    )
                } else {
                    Text(
                        overview.profile.name.take(1).uppercase(),
                        color = Navy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
        }

        if (overview.isOfflineData) {
            OfflineBanner(lastSynced = overview.lastSyncedAt)
        }

        // Overall Attendance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Navy),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "OVERALL ATTENDANCE",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${overview.overallAttendancePercent}%",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (overview.isAttendanceOnTrack) "ON TRACK" else "SHORTAGE ALERT",
                        color = if (overview.isAttendanceOnTrack) Color.White else AlertRedBg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (overview.isAttendanceOnTrack)
                                    Color.White.copy(alpha = 0.16f)
                                else
                                    AlertRed.copy(alpha = 0.65f)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                ProgressTrack(
                    progress = overview.overallAttendancePercent / 100f,
                    color = if (overview.isAttendanceOnTrack) Teal else AlertRed,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                Text(
                    "75% minimum university requirement",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
            }
        }

        // Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                label = "Enrolled Courses",
                value = overview.attendance.size.toString(),
                detail = "current semester",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Marks Average",
                value = "${overview.overallMarksPercent}%",
                detail = "${overview.marks.size} assessments",
                modifier = Modifier.weight(1f)
            )
        }

        // Upcoming Classes
        SectionHeading(title = "Upcoming Classes", badge = "TIMETABLE")
        if (overview.classes.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No scheduled classes for today.", color = Muted, fontSize = 14.sp)
                }
            }
        } else {
            overview.classes.take(3).forEach { item ->
                ClassCard(item = item)
            }
        }
    }
}

