package com.example.srmstudent.domain.model

data class StudentProfile(
    val name: String,
    val regNumber: String,
    val program: String,
    val semester: String,
    val avatarUrl: String? = null
)

