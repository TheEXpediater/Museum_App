package com.example.museumapp.ui.admin.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.AdminStudentDetailDto
import com.example.museumapp.data.model.AdminStudentListItemDto
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object StudentAccountFilters {
    const val Pending = "pending"
    const val Active = "active"
    const val Inactive = "inactive"
    const val All = "all"

    val Options = listOf(Pending, Active, Inactive, All)
}

data class AdminStudentAccountsUiState(
    val students: List<AdminStudentListItemDto> = emptyList(),
    val selectedStudent: AdminStudentDetailDto? = null,
    val selectedFilter: String = StudentAccountFilters.Pending,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isDetailLoading: Boolean = false,
    val isUpdatingStatus: Boolean = false,
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val pendingStatusChange: String? = null
)

class AdminStudentAccountsViewModel(private val repository: AdminRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminStudentAccountsUiState())
    val uiState: StateFlow<AdminStudentAccountsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadStudents()
    }

    fun updateSearchQuery(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
        loadStudents()
    }

    fun selectFilter(filter: String) {
        if (filter == _uiState.value.selectedFilter) return
        _uiState.update { it.copy(selectedFilter = filter) }
        loadStudents()
    }

    fun refresh() {
        loadStudents(refreshing = true)
    }

    fun openDetails(studentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, errorMessage = null) }
            when (val result = repository.getStudentAccount(studentId)) {
                is RepositoryResult.Success -> _uiState.update { it.copy(selectedStudent = result.data, isDetailLoading = false) }
                is RepositoryResult.Error -> _uiState.update { it.copy(isDetailLoading = false, errorMessage = result.message) }
            }
        }
    }

    fun closeDetails() {
        _uiState.update { it.copy(selectedStudent = null, pendingStatusChange = null) }
    }

    fun requestStatusChange(accountStatus: String) {
        _uiState.update { it.copy(pendingStatusChange = accountStatus) }
    }

    fun dismissStatusChange() {
        _uiState.update { it.copy(pendingStatusChange = null) }
    }

    fun confirmStatusChange() {
        val student = _uiState.value.selectedStudent ?: return
        val accountStatus = _uiState.value.pendingStatusChange ?: return
        if (_uiState.value.isUpdatingStatus) return
        val wasReactivation = accountStatus == StudentAccountFilters.Active && student.accountStatus == StudentAccountFilters.Inactive
        // Flip the flag synchronously (before launching) so a second tap on the same event loop
        // turn is guaranteed to see it, rather than racing the coroutine dispatcher.
        _uiState.update { it.copy(isUpdatingStatus = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.updateStudentAccountStatus(student.id, accountStatus)) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isUpdatingStatus = false,
                            selectedStudent = result.data,
                            pendingStatusChange = null,
                            snackbarMessage = confirmationMessage(accountStatus, wasReactivation)
                        )
                    }
                    loadStudents(refreshing = true)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isUpdatingStatus = false, pendingStatusChange = null, errorMessage = result.message)
                }
            }
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun confirmationMessage(accountStatus: String, wasReactivation: Boolean): String = when {
        accountStatus == StudentAccountFilters.Active && wasReactivation -> "Student account reactivated."
        accountStatus == StudentAccountFilters.Active -> "Student account activated."
        accountStatus == StudentAccountFilters.Inactive -> "Student account deactivated."
        else -> "Student account updated."
    }

    private fun loadStudents(refreshing: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isLoading = !refreshing, isRefreshing = refreshing, errorMessage = null) }
            when (val result = repository.listStudentAccounts(state.selectedFilter, state.searchQuery)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(students = result.data, isLoading = false, isRefreshing = false)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message)
                }
            }
        }
    }

    companion object {
        fun factory(repository: AdminRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AdminStudentAccountsViewModel(repository) as T
        }
    }
}
