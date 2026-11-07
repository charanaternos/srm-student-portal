package com.example.srmstudent.data.repository

import android.content.Context
import com.example.srmstudent.data.auth.AuthManager
import com.example.srmstudent.data.local.SrmDatabase
import com.example.srmstudent.data.local.entity.AttendanceEntity
import com.example.srmstudent.data.local.entity.ClassEntity
import com.example.srmstudent.data.local.entity.MarkEntity
import com.example.srmstudent.data.local.entity.ProfileEntity
import com.example.srmstudent.data.local.entity.SyncMetaEntity
import com.example.srmstudent.data.model.PortalSnapshotDto
import com.example.srmstudent.data.remote.SrmApiService
import com.example.srmstudent.domain.model.AcademicOverview
import com.example.srmstudent.domain.model.StudentProfile
import com.example.srmstudent.domain.model.UserSession
import com.example.srmstudent.domain.repository.SrmRepository
import com.example.srmstudent.domain.util.CalculationUtils
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SrmRepositoryImpl @Inject constructor(
    private val database: SrmDatabase,
    private val apiService: SrmApiService,
    private val authManager: AuthManager,
    private val gson: Gson,
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SrmRepository {

    private val classDao = database.classDao()
    private val attendanceDao = database.attendanceDao()
    private val markDao = database.markDao()
    private val profileDao = database.profileDao()
    private val syncMetaDao = database.syncMetaDao()

    override fun getUserSession(): Flow<UserSession?> = authManager.session

    override fun getAcademicOverview(): Flow<AcademicOverview> {
        return combine(
            classDao.getAllClasses(),
            attendanceDao.getAllAttendance(),
            markDao.getAllMarks(),
            profileDao.getProfile()
        ) { classes, attendance, marks, profileEntity ->
            val domainClasses = classes.map { it.toDomain() }
            val domainAttendance = attendance.map { it.toDomain() }
            val domainMarks = marks.map { it.toDomain() }

            val totalAttended = domainAttendance.sumOf { it.attended }
            val totalHours = domainAttendance.sumOf { it.total }
            val overallAttendancePercent = CalculationUtils.calculatePercentage(totalAttended, totalHours)

            val totalObtained = domainMarks.sumOf { it.obtained }
            val totalMarks = domainMarks.sumOf { it.total }
            val overallMarksPercent = CalculationUtils.calculatePercentage(totalObtained, totalMarks)

            val isAttendanceOnTrack = CalculationUtils.isAttendanceSufficient(overallAttendancePercent, 75)

            val currentSession = authManager.session.value
            val studentProfile = profileEntity?.toDomain() ?: StudentProfile(
                displayName = currentSession?.name ?: currentSession?.netId,
                studentId = currentSession?.regNumber ?: currentSession?.netId ?: "",
                programme = null,
                semester = null,
                avatarUrl = null
            )

            val syncMeta = syncMetaDao.getMeta()

            AcademicOverview(
                profile = studentProfile,
                classes = domainClasses,
                attendance = domainAttendance,
                marks = domainMarks,
                overallAttendancePercent = overallAttendancePercent,
                overallMarksPercent = overallMarksPercent,
                isAttendanceOnTrack = isAttendanceOnTrack,
                lastSyncedAt = syncMeta?.lastSyncTime,
                isOfflineData = syncMeta?.source != "REMOTE"
            )
        }
    }

    override suspend fun refreshData(forceRemote: Boolean): Result<Unit> = withContext(ioDispatcher) {
        val session = authManager.session.value
            ?: return@withContext Result.failure(IllegalStateException("No student is currently signed in"))

        var remoteSuccess = false

        if (forceRemote) {
            try {
                val authHeader = session.token?.let { "Bearer $it" }
                val response = apiService.getLatestSnapshot(
                    apiKey = session.supabaseAnonKey,
                    authHeader = authHeader
                )
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    val snapshot = response.body()!!.first()
                    saveSnapshotToDatabase(snapshot, source = "REMOTE", fallbackStudentId = session.regNumber)
                    remoteSuccess = true
                }
            } catch (_: Exception) {
                // Remote sync error; leave existing authenticated data untouched
            }
        }

        if (remoteSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Sync from portal unavailable"))
        }
    }

    override suspend fun importJsonData(jsonString: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val session = authManager.session.value
            val snapshot = gson.fromJson(jsonString, PortalSnapshotDto::class.java)
            saveSnapshotToDatabase(
                snapshot = snapshot,
                source = "USER_IMPORT",
                fallbackStudentId = session?.regNumber ?: ""
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun login(
        netIdOrEmail: String,
        password: String,
        supabaseUrl: String?,
        supabaseAnonKey: String?
    ): Result<UserSession> = withContext(ioDispatcher) {
        val trimmedId = netIdOrEmail.trim()
        val trimmedPassword = password.trim()

        if (trimmedId.isBlank() || trimmedPassword.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Student ID and password must not be blank"))
        }

        logout()

        val email = if (trimmedId.contains("@")) trimmedId else "$trimmedId@srmist.edu.in"
        val session = UserSession(
            netId = trimmedId,
            email = email,
            name = trimmedId,
            regNumber = trimmedId,
            supabaseUrl = supabaseUrl,
            supabaseAnonKey = supabaseAnonKey
        )

        authManager.saveSession(session)

        database.profileDao().insertProfile(
            ProfileEntity(
                id = 1,
                name = trimmedId,
                regNumber = trimmedId,
                program = "",
                semester = "",
                avatarUrl = null
            )
        )

        Result.success(session)
    }

    override suspend fun logout() = withContext(ioDispatcher) {
        authManager.clearSession()
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

    private suspend fun saveSnapshotToDatabase(
        snapshot: PortalSnapshotDto,
        source: String,
        fallbackStudentId: String
    ) {
        val classEntities = snapshot.getResolvedClasses().mapIndexed { index, dto ->
            val code = dto.courseCode ?: "COURSE_${index + 1}"
            val dayOrder = dto.dayOrder ?: "1"
            val start = dto.startTime ?: "09:00"
            ClassEntity(
                id = "${dayOrder}_${code}_${start}_$index",
                subject = dto.subject ?: "Subject",
                code = code,
                dayOrder = dayOrder,
                day = dto.day ?: "MON",
                date = dto.date ?: "",
                startTime = start,
                endTime = dto.endTime ?: "10:00",
                timeSlot = dto.timeSlot ?: "$start – ${dto.endTime ?: "10:00"}",
                room = dto.room ?: "",
                faculty = dto.faculty ?: "",
                details = dto.details ?: "${dto.subject} ($code)"
            )
        }

        val attendanceEntities = snapshot.getResolvedAttendance().mapIndexed { index, dto ->
            val code = dto.courseCode ?: "COURSE_${index + 1}"
            val attended = dto.parseAttended()
            val total = dto.parseTotal()
            val percentage = CalculationUtils.calculatePercentage(attended, total)
            AttendanceEntity(
                id = "${code}_$index",
                subject = dto.subject ?: "Subject $index",
                code = code,
                attended = attended,
                total = total,
                percentage = percentage,
                isShortage = !CalculationUtils.isAttendanceSufficient(percentage, 75)
            )
        }

        val markEntities = snapshot.getResolvedMarks().mapIndexed { index, dto ->
            val code = dto.courseCode ?: "COURSE_${index + 1}"
            val assessment = dto.assessment ?: "Assessment ${index + 1}"
            val obtained = dto.parseObtained()
            val total = dto.parseTotal()
            val percentage = CalculationUtils.calculatePercentage(obtained, total)
            MarkEntity(
                id = "${code}_${assessment}_$index",
                subject = dto.subject ?: "Subject $index",
                code = code,
                assessment = assessment,
                obtained = obtained,
                total = total,
                percentage = percentage,
                date = dto.date ?: ""
            )
        }

        val profileDto = snapshot.getResolvedProfile()
        val profileEntity = ProfileEntity(
            id = 1,
            name = profileDto?.name ?: fallbackStudentId,
            regNumber = profileDto?.regNumber ?: fallbackStudentId,
            program = profileDto?.program ?: "",
            semester = profileDto?.semester ?: "",
            avatarUrl = profileDto?.avatarUrl
        )

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val currentTime = isoFormat.format(Date())

        classDao.replaceClasses(classEntities)
        attendanceDao.replaceAttendance(attendanceEntities)
        markDao.replaceMarks(markEntities)
        profileDao.insertProfile(profileEntity)
        syncMetaDao.setMeta(
            SyncMetaEntity(
                key = "last_sync",
                lastSyncTime = currentTime,
                syncStatus = "SUCCESS",
                source = source
            )
        )
    }
}
