package com.example.srmstudent.domain.model

data class ClassItem(
    val id: String,
    val subject: String,
    val code: String,
    val dayOrder: String,
    val day: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val timeSlot: String,
    val room: String,
    val faculty: String,
    val details: String
)

