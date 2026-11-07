package com.example.srmstudent.data.model

import com.google.gson.annotations.SerializedName

data class StudentProfileDto(
    @SerializedName("name", alternate = ["Student Name", "student_name"])
    val name: String? = null,

    @SerializedName("reg_number", alternate = ["regNumber", "Registration Number", "Register No", "reg_no"])
    val regNumber: String? = null,

    @SerializedName("program", alternate = ["Program", "Department", "Degree", "branch"])
    val program: String? = null,

    @SerializedName("semester", alternate = ["Semester", "sem"])
    val semester: String? = null,

    @SerializedName("avatar_url", alternate = ["avatarUrl", "photo_url"])
    val avatarUrl: String? = null
)

