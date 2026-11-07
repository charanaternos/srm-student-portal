package com.example.srmstudent.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.srmstudent.domain.model.AttendanceItem

@Entity(
    tableName = "attendance",
    indices = [
        Index(value = ["code"])
    ]
)
data class AttendanceEntity(
    @PrimaryKey
    val id: String,
    val subject: String,
    val code: String,
    val attended: Int,
    val total: Int,
    val percentage: Int,
    val isShortage: Boolean
) {
    fun toDomain(): AttendanceItem {
        return AttendanceItem(
            id = id,
            subject = subject,
            code = code,
            attended = attended,
            total = total,
            percentage = percentage,
            isShortage = isShortage
        )
    }
}

