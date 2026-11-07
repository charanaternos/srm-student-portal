package com.example.srmstudent.data.model

import com.google.gson.annotations.SerializedName

data class MarkDto(
    @SerializedName("subject", alternate = ["Course Title", "course_title", "Subject"])
    val subject: String? = null,

    @SerializedName("course_code", alternate = ["code", "Course Code"])
    val courseCode: String? = null,

    @SerializedName("assessment", alternate = ["Test", "Assessment", "Evaluation", "Exam", "test_name", "Component"])
    val assessment: String? = null,

    @SerializedName("obtained", alternate = ["Mark", "Marks", "marks", "Obtained", "Score", "marks_obtained"])
    val obtained: Any? = null,

    @SerializedName("total", alternate = ["Total", "Max", "Maximum Marks", "max_mark", "total_marks", "Max Marks"])
    val total: Any? = null,

    @SerializedName("date", alternate = ["Date", "Exam Date"])
    val date: String? = null
) {
    fun parseObtained(): Int {
        return parseNumber(obtained)
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

