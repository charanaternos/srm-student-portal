package com.example.srmstudent.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.srmstudent.domain.model.ClassItem

@Entity(
    tableName = "classes",
    indices = [
        Index(value = ["dayOrder", "startTime"]),
        Index(value = ["code"])
    ]
)
data class ClassEntity(
    @PrimaryKey
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
) {
    fun toDomain(): ClassItem {
        return ClassItem(
            id = id,
            subject = subject,
            code = code,
            dayOrder = dayOrder,
            day = day,
            date = date,
            startTime = startTime,
            endTime = endTime,
            timeSlot = timeSlot,
            room = room,
            faculty = faculty,
            details = details
        )
    }
}

