package com.example.srmstudent.data.model

import com.google.gson.annotations.SerializedName

data class PortalSnapshotDto(
    @SerializedName("owner_id")
    val ownerId: String? = null,

    @SerializedName("snapshot_date")
    val snapshotDate: String? = null,

    @SerializedName("fetched_at")
    val fetchedAt: String? = null,

    @SerializedName("data")
    val data: SnapshotPayloadDto? = null,

    // Also support direct top-level fields
    @SerializedName("classes")
    val directClasses: List<ClassDto>? = null,

    @SerializedName("attendance")
    val directAttendance: List<AttendanceDto>? = null,

    @SerializedName("marks")
    val directMarks: List<MarkDto>? = null,

    @SerializedName("profile")
    val directProfile: StudentProfileDto? = null
) {
    fun getResolvedClasses(): List<ClassDto> =
        data?.classes ?: directClasses ?: emptyList()

    fun getResolvedAttendance(): List<AttendanceDto> =
        data?.attendance ?: directAttendance ?: emptyList()

    fun getResolvedMarks(): List<MarkDto> =
        data?.marks ?: directMarks ?: emptyList()

    fun getResolvedProfile(): StudentProfileDto? =
        data?.profile ?: directProfile
}

data class SnapshotPayloadDto(
    @SerializedName("classes")
    val classes: List<ClassDto>? = null,

    @SerializedName("attendance")
    val attendance: List<AttendanceDto>? = null,

    @SerializedName("marks")
    val marks: List<MarkDto>? = null,

    @SerializedName("profile")
    val profile: StudentProfileDto? = null
)

