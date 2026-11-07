package com.example.srmstudent.data.remote

import com.example.srmstudent.data.model.AuthResponseDto
import com.example.srmstudent.data.model.LoginRequestDto
import com.example.srmstudent.data.model.PortalSnapshotDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface SrmApiService {

    /**
     * Authenticate user with Supabase / authorized backend.
     */
    @POST
    suspend fun loginWithUrl(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Body request: LoginRequestDto
    ): Response<AuthResponseDto>

    /**
     * Dynamic URL snapshot fetch for custom backend URL.
     */
    @GET
    suspend fun getSnapshotsWithUrl(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): Response<List<PortalSnapshotDto>>

    /**
     * Fetch latest snapshot from authorized backend or Supabase REST.
     */
    @GET("rest/v1/student_portal_snapshots")
    suspend fun getLatestSnapshot(
        @Query("select") select: String = "*",
        @Query("order") order: String = "snapshot_date.desc",
        @Query("limit") limit: Int = 1,
        @Header("apikey") apiKey: String? = null,
        @Header("Authorization") authHeader: String? = null
    ): Response<List<PortalSnapshotDto>>

    /**
     * Fallback endpoint for custom authorized academic service.
     */
    @GET("api/v1/student/snapshot")
    suspend fun getAcademicSnapshot(): Response<PortalSnapshotDto>
}
