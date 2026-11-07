package com.example.srmstudent.domain.model

data class StudentProfile(
    val displayName: String?,
    val studentId: String,
    val programme: String? = null,
    val semester: String? = null,
    val avatarUrl: String? = null
) {
    val name: String get() = displayName ?: studentId
    val regNumber: String get() = studentId
    val program: String get() = programme ?: ""
}
