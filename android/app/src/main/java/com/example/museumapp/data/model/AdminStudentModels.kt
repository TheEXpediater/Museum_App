package com.example.museumapp.data.model

import com.squareup.moshi.Json

data class AdminStudentListItemDto(
    val id: String,
    @Json(name = "student_id") val studentId: String,
    @Json(name = "display_name") val displayName: String,
    val email: String,
    @Json(name = "account_status") val accountStatus: String,
    @Json(name = "created_at") val createdAt: String
)

data class AdminStudentDetailDto(
    val id: String,
    @Json(name = "student_id") val studentId: String,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "middle_initial") val middleInitial: String? = null,
    @Json(name = "last_name") val lastName: String,
    @Json(name = "display_name") val displayName: String,
    val email: String,
    val course: String,
    @Json(name = "year_level") val yearLevel: String,
    @Json(name = "account_status") val accountStatus: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "approved_at") val approvedAt: String? = null,
    @Json(name = "last_login_at") val lastLoginAt: String? = null
)

data class AdminStudentStatusUpdateRequestDto(
    @Json(name = "account_status") val accountStatus: String
)
