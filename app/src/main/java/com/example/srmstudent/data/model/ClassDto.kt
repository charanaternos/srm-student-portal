package com.example.srmstudent.data.model

import com.google.gson.annotations.SerializedName

data class ClassDto(
    @SerializedName("subject", alternate = ["Course Title", "course_title", "Subject"])
    val subject: String? = null,

    @SerializedName("course_code", alternate = ["code", "Course Code", "course_code_1"])
    val courseCode: String? = null,

    @SerializedName("day_order", alternate = ["dayOrder", "Day Order"])
    val dayOrder: String? = null,

    @SerializedName("day", alternate = ["Day", "weekday"])
    val day: String? = null,

    @SerializedName("date", alternate = ["Date"])
    val date: String? = null,

    @SerializedName("start_time", alternate = ["startTime", "Start Time"])
    val startTime: String? = null,

    @SerializedName("end_time", alternate = ["endTime", "End Time"])
    val endTime: String? = null,

    @SerializedName("time", alternate = ["Time", "Slot", "time_slot"])
    val timeSlot: String? = null,

    @SerializedName("room", alternate = ["Room", "Hall", "room_no"])
    val room: String? = null,

    @SerializedName("faculty", alternate = ["Faculty", "teacher", "Faculty Name"])
    val faculty: String? = null,

    @SerializedName("details", alternate = ["Details"])
    val details: String? = null
)

