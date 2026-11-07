package com.example.srmstudent.domain.repository

import com.example.srmstudent.domain.model.StudentProfile

interface PortalAuthRepository {
    suspend fun signIn(studentId: String, password: String): StudentProfile
    fun signOut()
    fun getCurrentProfile(): StudentProfile?
}

