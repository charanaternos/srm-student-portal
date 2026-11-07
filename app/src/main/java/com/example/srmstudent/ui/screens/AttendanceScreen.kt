package com.example.srmstudent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.srmstudent.domain.model.AttendanceItem
import com.example.srmstudent.ui.components.*

@Composable
fun AttendanceScreen(
    attendanceList: List<AttendanceItem>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeading(
            title = "Attendance Summary",
            subtitle = "Subject-wise attendance tracking (75% minimum required)"
        )

        if (attendanceList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No attendance records found.", color = Muted, fontSize = 14.sp)
                }
            }
        } else {
            attendanceList.forEach { item ->
                AttendanceCard(item = item)
            }
        }
    }
}

@Composable
fun AttendanceCard(item: AttendanceItem) {
    val isShortage = item.isShortage
    val statusColor = if (!isShortage) Teal else AlertRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.subject,
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        item.code,
                        color = Muted,
                        fontSize = 12.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${item.percentage}%",
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    if (isShortage) {
                        Text(
                            "Shortage",
                            color = AlertRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AlertRedBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            ProgressTrack(
                progress = item.percentage / 100f,
                color = statusColor
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${item.attended} of ${item.total} hours attended",
                    color = Muted,
                    fontSize = 12.sp
                )
                if (isShortage) {
                    val needed = (3 * item.total - 4 * item.attended).coerceAtLeast(1)
                    Text(
                        "Need $needed more class${if (needed > 1) "es" else ""}",
                        color = AlertRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

