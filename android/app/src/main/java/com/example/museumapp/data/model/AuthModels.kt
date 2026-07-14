package com.example.museumapp.data.model

import com.squareup.moshi.Json

data class LoginRequest(
    val email: String,
    val password: String
)

data class UserDto(
    val id: String,
    val email: String,
    @Json(name = "full_name") val fullName: String,
    val role: String
)

data class LoginResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresIn: Int,
    val user: UserDto
)

data class HealthResponse(
    val status: String,
    val database: String,
    @Json(name = "uploads_directory") val uploadsDirectory: String
)
