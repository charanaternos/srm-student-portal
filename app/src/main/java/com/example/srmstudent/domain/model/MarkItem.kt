package com.example.srmstudent.domain.model

data class MarkItem(
    val id: String,
    val subject: String,
    val code: String,
    val assessment: String,
    val obtained: Int,
    val total: Int,
    val percentage: Int,
    val date: String
)

