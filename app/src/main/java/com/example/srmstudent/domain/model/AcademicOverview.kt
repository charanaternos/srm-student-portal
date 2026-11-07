package com.example.srmstudent.domain.model

data class AcademicOverview(
    val profile: StudentProfile,
    val classes: List<ClassItem>,
    val attendance: List<AttendanceItem>,
    val marks: List<MarkItem>,
    val overallAttendancePercent: Int,
    val overallMarksPercent: Int,
    val isAttendanceOnTrack: Boolean,
    val lastSyncedAt: String? = null,
    val isOfflineData: Boolean = false
)

