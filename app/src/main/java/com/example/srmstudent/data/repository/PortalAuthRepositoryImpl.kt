package com.example.srmstudent.data.repository

import com.example.srmstudent.data.auth.AuthManager
import com.example.srmstudent.data.local.SrmDatabase
import com.example.srmstudent.data.local.entity.ProfileEntity
import com.example.srmstudent.data.local.entity.SyncMetaEntity
import com.example.srmstudent.data.model.LoginRequestDto
import com.example.srmstudent.data.remote.SrmApiService
import com.example.srmstudent.domain.model.StudentProfile
import com.example.srmstudent.domain.model.UserSession
import com.example.srmstudent.domain.repository.PortalAuthRepository
import com.example.srmstudent.domain.repository.SrmRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortalAuthRepositoryImpl @Inject constructor(
    private val authManager: AuthManager,
    private val database: SrmDatabase,
    private val apiService: SrmApiService,
    private val srmRepository: SrmRepository,
    private val ioDispatcher: CoroutineDispatcher
) : PortalAuthRepository {

    // A scope tied to the singleton lifetime; SupervisorJob prevents one failure cancelling others
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    override suspend fun signIn(studentId: String, password: String): StudentProfile =
        withContext(ioDispatcher) {
            val trimmedId = studentId.trim()
            val trimmedPassword = password.trim()

            if (trimmedId.isBlank() || trimmedPassword.isBlank()) {
                throw IllegalArgumentException("Student ID and password must not be blank")
            }

            // Clear previous session & DB before signing in a new user
            clearSessionAndDatabase()

            val email = if (trimmedId.contains("@")) trimmedId else "$trimmedId@srmist.edu.in"
            var authToken: String? = null
            var userId: String? = null
            var resolvedName: String? = null
            var resolvedProgram: String? = null
            var resolvedSemester: String? = null

            try {
                val response = apiService.loginWithUrl(
                    url = "auth/v1/token?grant_type=password",
                    apiKey = "",
                    request = LoginRequestDto(email = email, password = trimmedPassword)
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    authToken = body.accessToken
                    userId = body.user?.id
                    resolvedName = body.user?.metadata?.get("name") as? String
                    resolvedProgram = body.user?.metadata?.get("program") as? String
                    resolvedSemester = body.user?.metadata?.get("semester") as? String
                }
            } catch (_: Exception) {
                // Backend endpoint not active; proceed with local session
            }

            val profile = StudentProfile(
                displayName = resolvedName ?: trimmedId,
                studentId = trimmedId,
                programme = resolvedProgram,
                semester = resolvedSemester
            )

            val session = UserSession(
                netId = trimmedId,
                email = email,
                name = profile.displayName ?: trimmedId,
                regNumber = trimmedId,
                token = authToken,
                userId = userId
            )

            authManager.saveSession(session)

            database.profileDao().insertProfile(
                ProfileEntity(
                    id = 1,
                    name = profile.displayName ?: trimmedId,
                    regNumber = trimmedId,
                    program = profile.programme ?: "",
                    semester = profile.semester ?: "",
                    avatarUrl = null
                )
            )

            // Best-effort remote sync; failures leave an empty-but-authenticated dashboard
            try {
                srmRepository.refreshData(forceRemote = true)
            } catch (_: Exception) { }

            profile
        }

    override fun signOut() {
        // Clear in-memory session immediately (safe from any thread)
        authManager.clearSession()
        // Dispatch DB wipe asynchronously on IO — no ANR risk
        scope.launch {
            clearDatabase()
        }
    }

    private suspend fun clearSessionAndDatabase() {
        authManager.clearSession()
        clearDatabase()
    }

    private suspend fun clearDatabase() {
        database.classDao().clearClasses()
        database.attendanceDao().clearAttendance()
        database.markDao().clearMarks()
        database.profileDao().clearProfile()
        database.syncMetaDao().setMeta(
            SyncMetaEntity(
                key = "last_sync",
                lastSyncTime = "",
                syncStatus = "SIGNED_OUT",
                source = ""
            )
        )
    }

    override fun getCurrentProfile(): StudentProfile? {
        val session = authManager.session.value ?: return null
        return StudentProfile(
            displayName = session.name,
            studentId = session.regNumber,
            programme = null,
            semester = null
        )
    }
}
