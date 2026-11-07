package com.example.srmstudent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.srmstudent.domain.model.StudentProfile

@Entity(tableName = "student_profile")
data class ProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val name: String,
    val regNumber: String,
    val program: String,
    val semester: String,
    val avatarUrl: String? = null
) {
    fun toDomain(): StudentProfile {
        return StudentProfile(
            displayName = name,
            studentId = regNumber,
            programme = program,
            semester = semester,
            avatarUrl = avatarUrl
        )
    }
}
