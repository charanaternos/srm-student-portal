package com.example.srmstudent.ui.viewmodel

import com.example.srmstudent.domain.model.AcademicOverview

sealed interface PortalUiState {
    data object Loading : PortalUiState

    data class Success(
        val overview: AcademicOverview,
        val isRefreshing: Boolean = false
    ) : PortalUiState

    data class Error(
        val message: String,
        val cachedOverview: AcademicOverview? = null
    ) : PortalUiState

    data object Empty : PortalUiState
}

