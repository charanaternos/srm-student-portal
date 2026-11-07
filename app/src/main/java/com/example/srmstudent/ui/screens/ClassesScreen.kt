package com.example.srmstudent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.srmstudent.domain.model.ClassItem
import com.example.srmstudent.ui.components.*

@Composable
fun ClassesScreen(
    classes: List<ClassItem>,
    modifier: Modifier = Modifier
) {
    val dayOrders = remember(classes) {
        val uniqueDays = classes.map { it.dayOrder }.distinct().sorted()
        listOf("All") + uniqueDays
    }
    var selectedDayOrder by remember { mutableStateOf("All") }

    val filteredClasses = remember(classes, selectedDayOrder) {
        if (selectedDayOrder == "All") classes
        else classes.filter { it.dayOrder == selectedDayOrder }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeading(
            title = "Class Timetable",
            subtitle = "Schedules, day order, faculty and room allocation"
        )

        // Day Order Filter Chips
        if (dayOrders.size > 2) {
            ScrollableTabRow(
                selectedTabIndex = dayOrders.indexOf(selectedDayOrder).coerceAtLeast(0),
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                dayOrders.forEach { dayOrder ->
                    val isSelected = selectedDayOrder == dayOrder
                    Tab(
                        selected = isSelected,
                        onClick = { selectedDayOrder = dayOrder },
                        text = {
                            Text(
                                if (dayOrder == "All") "All Days" else "Day $dayOrder",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Navy else Muted
                            )
                        }
                    )
                }
            }
        }

        if (filteredClasses.isEmpty()) {
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
                    Text("No classes scheduled for this day.", color = Muted, fontSize = 14.sp)
                }
            }
        } else {
            filteredClasses.forEach { item ->
                ClassCard(item = item)
            }
        }
    }
}

@Composable
fun ClassCard(item: ClassItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (item.dayOrder.isNotEmpty()) "DAY ${item.dayOrder}" else item.day,
                    color = Navy,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    item.day,
                    color = Navy,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.width(14.dp))

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

                Spacer(Modifier.height(8.dp))

                Text(
                    "${item.startTime} – ${item.endTime}",
                    color = Navy,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.room.isNotEmpty()) {
                        Text("Room: ${item.room}", color = Muted, fontSize = 11.sp)
                    }
                    if (item.faculty.isNotEmpty()) {
                        Text("•  ${item.faculty}", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

