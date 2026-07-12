package com.example.museumapp.data.session

data class AdminSession(
    val accessToken: String = "",
    val tokenType: String = "",
    val adminId: String = "",
    val adminEmail: String = "",
    val adminName: String = "",
    val role: String = "",
    val loginTimestamp: Long = 0L
) {
    val isAuthenticated: Boolean
        get() = accessToken.isNotBlank() && role == "admin"
}
