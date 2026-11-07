package com.example.srmstudent.domain.model

data class AttendanceItem(
    val id: String,
    val subject: String,
    val code: String,
    val attended: Int,
    val total: Int,
    val percentage: Int,
    val isShortage: Boolean
)

