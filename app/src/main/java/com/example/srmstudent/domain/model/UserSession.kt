package com.example.srmstudent.domain.model

data class UserSession(
    val netId: String,
    val email: String,
    val name: String,
    val regNumber: String,
    val token: String? = null,
    val userId: String? = null,
    val supabaseUrl: String? = null,
    val supabaseAnonKey: String? = null
)

