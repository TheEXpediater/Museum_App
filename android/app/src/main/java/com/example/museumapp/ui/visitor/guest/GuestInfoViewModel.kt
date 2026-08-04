package com.example.museumapp.ui.visitor.guest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.GuestSessionRequestDto
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.VisitorFormValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class GuestInfoUiState(
    val firstName: String = "",
    val lastName: String = "",
    val relationship: String = "",
    val otherDetail: String = "",
    val batchOrGraduationYear: String = "",
    val officeOrDepartment: String = "",
    val errors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isComplete: Boolean = false
)

class GuestInfoViewModel(private val repository: VisitorRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(GuestInfoUiState())
    val uiState: StateFlow<GuestInfoUiState> = _uiState.asStateFlow()
    private val deviceSessionId = UUID.randomUUID().toString()

    fun updateFirstName(value: String) = update { it.copy(firstName = value, errors = it.errors - "firstName", errorMessage = null) }
    fun updateLastName(value: String) = update { it.copy(lastName = value, errors = it.errors - "lastName", errorMessage = null) }
    fun updateRelationship(value: String) = update {
        it.copy(
            relationship = value,
            otherDetail = if (value == "Other") it.otherDetail else "",
            batchOrGraduationYear = if (value == "Alumni or Former Student") it.batchOrGraduationYear else "",
            officeOrDepartment = if (value in setOf("Current Employee", "Former Employee")) it.officeOrDepartment else "",
            errors = it.errors - "relationship" - "otherDetail",
            errorMessage = null
        )
    }
    fun updateOtherDetail(value: String) = update { it.copy(otherDetail = value, errors = it.errors - "otherDetail", errorMessage = null) }
    fun updateBatchOrGraduationYear(value: String) = update { it.copy(batchOrGraduationYear = value, errorMessage = null) }
    fun updateOfficeOrDepartment(value: String) = update { it.copy(officeOrDepartment = value, errorMessage = null) }

    fun continueToMuseum() {
        val state = _uiState.value
        if (state.isLoading) return
        val errors = VisitorFormValidation.guestErrors(state.firstName, state.lastName, state.relationship, state.otherDetail)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }

        val request = GuestSessionRequestDto(
            firstName = VisitorFormValidation.clean(state.firstName, 80),
            lastName = VisitorFormValidation.clean(state.lastName, 80),
            relationshipType = state.relationship,
            relationshipDetail = state.otherDetail.takeIf { state.relationship == "Other" }?.let { VisitorFormValidation.clean(it, 120) },
            batchOrGraduationYear = state.batchOrGraduationYear.takeIf { state.relationship == "Alumni or Former Student" }?.let { VisitorFormValidation.clean(it, 40) },
            officeOrDepartment = state.officeOrDepartment.takeIf { state.relationship in setOf("Current Employee", "Former Employee") }?.let { VisitorFormValidation.clean(it, 120) },
            deviceSessionId = deviceSessionId
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.createGuestSession(request)) {
                is RepositoryResult.Success -> _uiState.update { it.copy(isLoading = false, isComplete = true) }
                is RepositoryResult.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    private fun update(block: (GuestInfoUiState) -> GuestInfoUiState) {
        _uiState.update(block)
    }

    companion object {
        fun factory(repository: VisitorRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GuestInfoViewModel(repository) as T
        }
    }
}
