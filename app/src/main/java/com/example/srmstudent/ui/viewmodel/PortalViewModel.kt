package com.example.srmstudent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.srmstudent.domain.model.AcademicOverview
import com.example.srmstudent.domain.model.StudentProfile
import com.example.srmstudent.domain.repository.PortalAuthRepository
import com.example.srmstudent.domain.repository.SrmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PortalViewModel @Inject constructor(
    val authRepository: PortalAuthRepository,
    private val repository: SrmRepository
) : ViewModel() {

    private val _currentProfile = MutableStateFlow<StudentProfile?>(authRepository.getCurrentProfile())
    val currentProfile: StateFlow<StudentProfile?> = _currentProfile.asStateFlow()

    private val _uiState = MutableStateFlow<PortalUiState>(PortalUiState.Loading)
    val uiState: StateFlow<PortalUiState> = _uiState.asStateFlow()

    private var currentOverview: AcademicOverview? = null
    private var isRefreshing = false

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            repository.getAcademicOverview()
                .catch { e ->
                    _uiState.value = PortalUiState.Error(
                        message = e.localizedMessage ?: "Failed to read student data",
                        cachedOverview = currentOverview
                    )
                }
                .collect { overview ->
                    currentOverview = overview
                    _uiState.value = PortalUiState.Success(
                        overview = overview,
                        isRefreshing = isRefreshing
                    )
                }
        }
    }

    suspend fun signIn(studentId: String, password: String): StudentProfile {
        val profile = authRepository.signIn(studentId, password)
        _currentProfile.value = profile
        return profile
    }

    fun signOut() {
        authRepository.signOut()
        _currentProfile.value = null
        currentOverview = null
        _uiState.value = PortalUiState.Loading
    }

    fun refresh(forceRemote: Boolean = true) {
        viewModelScope.launch {
            isRefreshing = true
            currentOverview?.let {
                _uiState.value = PortalUiState.Success(it, isRefreshing = true)
            }

            val result = repository.refreshData(forceRemote = forceRemote)
            isRefreshing = false

            result.onFailure { error ->
                _uiState.value = PortalUiState.Error(
                    message = error.localizedMessage ?: "Sync from portal failed",
                    cachedOverview = currentOverview
                )
            }
        }
    }

    fun importJson(jsonString: String) {
        viewModelScope.launch {
            isRefreshing = true
            repository.importJsonData(jsonString)
            isRefreshing = false
        }
    }
}
