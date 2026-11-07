package com.example.srmstudent.data.remote

import com.example.srmstudent.data.model.PortalSnapshotDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SrmApiService {

    /**
     * Fetch latest snapshot from authorized backend or Supabase REST.
     */
    @GET("rest/v1/student_portal_snapshots")
    suspend fun getLatestSnapshot(
        @Query("select") select: String = "*",
        @Query("order") order: String = "snapshot_date.desc",
        @Query("limit") limit: Int = 1,
        @Header("apikey") apiKey: String? = null
    ): Response<List<PortalSnapshotDto>>

    /**
     * Fallback endpoint for custom authorized academic service.
     */
    @GET("api/v1/student/snapshot")
    suspend fun getAcademicSnapshot(): Response<PortalSnapshotDto>
}

