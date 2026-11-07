package com.example.srmstudent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.srmstudent.domain.model.MarkItem
import com.example.srmstudent.ui.components.*

@Composable
fun MarksScreen(
    marksList: List<MarkItem>,
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
            title = "Internal Marks",
            subtitle = "Continuous assessment, cycle tests and evaluation records"
        )

        if (marksList.isEmpty()) {
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
                    Text("No assessment marks published yet.", color = Muted, fontSize = 14.sp)
                }
            }
        } else {
            marksList.forEach { item ->
                MarkCard(item = item)
            }
        }
    }
}

@Composable
fun MarkCard(item: MarkItem) {
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
                        item.assessment,
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        "${item.subject} • ${item.code}",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }

                Text(
                    "${item.obtained}/${item.total}",
                    color = Navy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            ProgressTrack(
                progress = item.percentage / 100f,
                color = Teal
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${item.percentage}% score",
                    color = Muted,
                    fontSize = 12.sp
                )
                if (item.date.isNotEmpty()) {
                    Text(
                        "Date: ${item.date}",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

