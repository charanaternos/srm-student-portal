package com.example.srmstudent

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class LoginErrorHandlingTest {

    @Test
    fun normalizeBaseUrl_cleansMalformedUrlsAndSubpaths() {
        // Trailing slashes
        assertEquals("https://myproject.supabase.co/", ApiClient.normalizeBaseUrl("https://myproject.supabase.co"))
        assertEquals("https://myproject.supabase.co/", ApiClient.normalizeBaseUrl("https://myproject.supabase.co/"))

        // Missing protocol
        assertEquals("https://myproject.supabase.co/", ApiClient.normalizeBaseUrl("myproject.supabase.co"))

        // Accidental /rest/v1 subpath
        assertEquals("https://myproject.supabase.co/", ApiClient.normalizeBaseUrl("https://myproject.supabase.co/rest/v1"))
        assertEquals("https://myproject.supabase.co/", ApiClient.normalizeBaseUrl("https://myproject.supabase.co/rest/v1/"))

        // Accidental /auth/v1 subpath
        assertEquals("https://myproject.supabase.co/", ApiClient.normalizeBaseUrl("https://myproject.supabase.co/auth/v1"))
        assertEquals("https://myproject.supabase.co/", ApiClient.normalizeBaseUrl("https://myproject.supabase.co/auth/v1/"))

        // Empty URL
        assertEquals("", ApiClient.normalizeBaseUrl("   "))
    }

    @Test
    fun formatNetworkError_handles404TableNotFoundError() {
        val errorJson = """{"code":"PGRST200","message":"Could not find the table 'student_portal_snapshots' in the schema cache"}"""
        val response = Response.error<Any>(
            404,
            errorJson.toResponseBody("application/json".toMediaTypeOrNull())
        )
        val httpException = HttpException(response)

        val message = formatNetworkError(httpException)
        assertTrue(message.contains("HTTP 404"))
        assertTrue(message.contains("student_portal_snapshots"))
        assertTrue(message.contains("Supabase SQL editor"))
    }

    @Test
    fun formatNetworkError_handles404RouteNotFoundError() {
        val errorHtml = "Cannot GET /auth/v1/token"
        val response = Response.error<Any>(
            404,
            errorHtml.toResponseBody("text/html".toMediaTypeOrNull())
        )
        val httpException = HttpException(response)

        val message = formatNetworkError(httpException)
        assertTrue(message.contains("HTTP 404"))
        assertTrue(message.contains("Endpoint not found"))
    }

    @Test
    fun formatNetworkError_handles400InvalidCredentials() {
        val errorJson = """{"error":"invalid_grant","error_description":"Invalid login credentials"}"""
        val response = Response.error<Any>(
            400,
            errorJson.toResponseBody("application/json".toMediaTypeOrNull())
        )
        val httpException = HttpException(response)

        val message = formatNetworkError(httpException)
        assertTrue(message.contains("HTTP 400"))
        assertTrue(message.contains("Invalid login credentials"))
    }

    @Test
    fun formatNetworkError_handlesNetworkExceptions() {
        val unknownHost = formatNetworkError(UnknownHostException("Unable to resolve host"))
        assertTrue(unknownHost.contains("Cannot resolve server host"))

        val timeout = formatNetworkError(SocketTimeoutException("Timeout"))
        assertTrue(timeout.contains("timed out"))
    }

    @Test
    fun getDemoSnapshot_producesCompleteStudentData() {
        val snapshot = getDemoSnapshot()
        assertEquals("2026-11-07 (Demo Preview)", snapshot.snapshotDate)
        assertEquals("1", snapshot.dayOrder)
        assertEquals(4, snapshot.classes.size)
        assertEquals(4, snapshot.attendance.size)
        assertEquals(4, snapshot.marks.size)

        // Verify percentage calculations
        assertTrue(snapshot.attendance[0].percentage > 0)
        assertTrue(snapshot.marks[0].percentage > 0)
    }
}

