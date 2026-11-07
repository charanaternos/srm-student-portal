package com.example.srmstudent.data.model

import com.google.gson.annotations.SerializedName

data class AttendanceDto(
    @SerializedName("subject", alternate = ["Course Title", "course_title", "Course", "Subject"])
    val subject: String? = null,

    @SerializedName("course_code", alternate = ["code", "Course Code", "Course_Code"])
    val courseCode: String? = null,

    @SerializedName("attended", alternate = ["Attended", "hours_present", "Hours Present", "Present", "Classes Attended"])
    val attended: Any? = null,

    @SerializedName("total", alternate = ["Total", "total_hours", "Hours Conducted", "Total Hours", "Total Classes"])
    val total: Any? = null,

    @SerializedName("percentage", alternate = ["Percentage", "Attendance %", "attendance_percentage", "Percent"])
    val percentage: Any? = null
) {
    fun parseAttended(): Int {
        return parseNumber(attended)
    }

    fun parseTotal(): Int {
        return parseNumber(total)
    }

    private fun parseNumber(value: Any?): Int {
        if (value == null) return 0
        if (value is Number) return value.toInt()
        val str = value.toString().trim()
        val digits = str.replace(Regex("[^0-9]"), "")
        return digits.toIntOrNull() ?: 0
    }
}

