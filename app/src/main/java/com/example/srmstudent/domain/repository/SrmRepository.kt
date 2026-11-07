package com.example.srmstudent.domain.repository

import com.example.srmstudent.domain.model.AcademicOverview
import com.example.srmstudent.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

interface SrmRepository {
    /**
     * Emits the academic overview for the currently logged-in student.
     */
    fun getAcademicOverview(): Flow<AcademicOverview>

    /**
     * Refreshes data from remote API or local asset fallback into Room cache.
     */
    suspend fun refreshData(forceRemote: Boolean = false): Result<Unit>

    /**
     * Imports custom timetable/snapshot JSON directly into Room cache.
     */
    suspend fun importJsonData(jsonString: String): Result<Unit>

    /**
     * Observes current authenticated user session.
     */
    fun getUserSession(): Flow<UserSession?>

    /**
     * Authenticate student with NetID / Email & Password.
     */
    suspend fun login(
        netIdOrEmail: String,
        password: String,
        supabaseUrl: String? = null,
        supabaseAnonKey: String? = null
    ): Result<UserSession>

    /**
     * Clears user session and all cached student data.
     */
    suspend fun logout()
}
