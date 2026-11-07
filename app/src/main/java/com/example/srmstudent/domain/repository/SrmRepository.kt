package com.example.srmstudent.domain.repository

import com.example.srmstudent.domain.model.AcademicOverview
import kotlinx.coroutines.flow.Flow

interface SrmRepository {
    /**
     * Emits the academic overview combining classes, attendance, marks, profile,
     * and calculating overall percentages safely.
     */
    fun getAcademicOverview(): Flow<AcademicOverview>

    /**
     * Refreshes data from remote API or local asset fallback into Room cache.
     */
    suspend fun refreshData(forceRemote: Boolean = false): Result<Unit>
}

