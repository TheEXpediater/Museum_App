package com.example.museumapp.data.model

import com.squareup.moshi.Json

data class GuestSessionRequestDto(
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String,
    @Json(name = "relationship_type") val relationshipType: String,
    @Json(name = "relationship_detail") val relationshipDetail: String? = null,
    @Json(name = "batch_or_graduation_year") val batchOrGraduationYear: String? = null,
    @Json(name = "office_or_department") val officeOrDepartment: String? = null,
    @Json(name = "device_session_id") val deviceSessionId: String? = null
)

data class StudentRegisterRequestDto(
    @Json(name = "student_id") val studentId: String,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "middle_initial") val middleInitial: String? = null,
    @Json(name = "last_name") val lastName: String,
    @Json(name = "year_level") val yearLevel: String,
    val course: String,
    val email: String,
    val password: String,
    @Json(name = "confirm_password") val confirmPassword: String
)

data class StudentLoginRequestDto(
    val identifier: String,
    val password: String
)

data class VisitorProfileDto(
    val id: String,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "middle_initial") val middleInitial: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    val email: String? = null,
    @Json(name = "student_id") val studentId: String? = null,
    @Json(name = "year_level") val yearLevel: String? = null,
    val course: String? = null,
    @Json(name = "relationship_type") val relationshipType: String? = null,
    @Json(name = "relationship_detail") val relationshipDetail: String? = null,
    @Json(name = "batch_or_graduation_year") val batchOrGraduationYear: String? = null,
    @Json(name = "office_or_department") val officeOrDepartment: String? = null,
    val role: String,
    @Json(name = "expires_at") val expiresAt: String? = null
)

data class VisitorTokenResponseDto(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresIn: Int,
    @Json(name = "account_type") val accountType: String,
    val profile: VisitorProfileDto
)

data class VisitorMeResponseDto(
    @Json(name = "account_type") val accountType: String,
    val profile: VisitorProfileDto
)

data class VisitorLogoutResponseDto(
    val message: String
)
