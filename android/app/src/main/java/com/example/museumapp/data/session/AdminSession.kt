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

data class VisitorSession(
    val accessToken: String = "",
    val tokenType: String = "",
    val accountType: String = "",
    val id: String = "",
    val firstName: String = "",
    val middleInitial: String = "",
    val lastName: String = "",
    val displayName: String = "",
    val email: String = "",
    val studentId: String = "",
    val yearLevel: String = "",
    val course: String = "",
    val relationshipType: String = "",
    val relationshipDetail: String = "",
    val batchOrGraduationYear: String = "",
    val officeOrDepartment: String = "",
    val loginTimestamp: Long = 0L
) {
    val isGuest: Boolean
        get() = accessToken.isNotBlank() && accountType == "guest"

    val isStudent: Boolean
        get() = accessToken.isNotBlank() && accountType == "student"

    val isVisitorAuthenticated: Boolean
        get() = isGuest || isStudent
}
