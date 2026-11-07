package com.example.srmstudent.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.srmstudent.domain.model.MarkItem

@Entity(
    tableName = "marks",
    indices = [
        Index(value = ["code"]),
        Index(value = ["assessment"])
    ]
)
data class MarkEntity(
    @PrimaryKey
    val id: String,
    val subject: String,
    val code: String,
    val assessment: String,
    val obtained: Int,
    val total: Int,
    val percentage: Int,
    val date: String
) {
    fun toDomain(): MarkItem {
        return MarkItem(
            id = id,
            subject = subject,
            code = code,
            assessment = assessment,
            obtained = obtained,
            total = total,
            percentage = percentage,
            date = date
        )
    }
}

