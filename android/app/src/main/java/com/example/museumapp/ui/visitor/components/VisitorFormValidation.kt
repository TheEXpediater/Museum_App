package com.example.museumapp.ui.visitor.components

object VisitorFormValidation {
    val GuestRelationships = listOf(
        "Alumni or Former Student",
        "Current Employee",
        "Former Employee",
        "General Visitor",
        "Other"
    )

    val YearLevels = listOf(
        "First Year",
        "Second Year",
        "Third Year",
        "Fourth Year",
        "Fifth Year",
        "Graduate Student"
    )

    fun clean(value: String, maxLength: Int): String {
        val cleaned = value.trim().replace(Regex("\\s+"), " ")
        require(cleaned.none { it.isISOControl() }) { "Control characters are not allowed." }
        require(cleaned.length <= maxLength) { "Must be $maxLength characters or fewer." }
        return cleaned
    }

    fun guestErrors(
        firstName: String,
        lastName: String,
        relationship: String,
        otherDetail: String
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        validateRequired("firstName", firstName, "First name is required.", errors, 80)
        validateRequired("lastName", lastName, "Last name is required.", errors, 80)
        val cleanedRelationship = runCatching { clean(relationship, 80) }.getOrDefault(relationship.trim())
        if (cleanedRelationship.isBlank()) {
            errors["relationship"] = "Select your PSAU relationship."
        } else if (cleanedRelationship !in GuestRelationships) {
            errors["relationship"] = "Select a valid PSAU relationship."
        }
        if (cleanedRelationship == "Other") {
            validateRequired("otherDetail", otherDetail, "Please specify your relationship.", errors, 120)
        }
        return errors
    }

    fun studentRegistrationErrors(
        studentId: String,
        firstName: String,
        middleInitial: String,
        lastName: String,
        yearLevel: String,
        course: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        validateRequired("studentId", studentId, "Student ID is required.", errors, 40)
        validateRequired("firstName", firstName, "First name is required.", errors, 80)
        validateRequired("lastName", lastName, "Last name is required.", errors, 80)
        if (middleInitial.isNotBlank() && !middleInitial.trim().matches(Regex("[A-Za-z]"))) {
            errors["middleInitial"] = "Use one letter."
        }
        if (yearLevel !in YearLevels) errors["yearLevel"] = "Select your year level."
        validateRequired("course", course, "Course or program is required.", errors, 120)
        val cleanedEmail = email.trim()
        if (cleanedEmail.isBlank() || !cleanedEmail.contains("@") || !cleanedEmail.substringAfter("@").contains(".")) {
            errors["email"] = "Enter a valid email."
        }
        val passwordError = passwordError(password)
        if (passwordError != null) errors["password"] = passwordError
        if (confirmPassword != password) errors["confirmPassword"] = "Passwords do not match."
        return errors
    }

    fun studentLoginErrors(identifier: String, password: String): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        validateRequired("identifier", identifier, "Student ID or email is required.", errors, 120)
        if (password.isBlank()) errors["password"] = "Password is required."
        return errors
    }

    private fun passwordError(password: String): String? {
        return when {
            password.length < 8 -> "Use at least 8 characters."
            password.none { it.isUpperCase() } -> "Add one uppercase letter."
            password.none { it.isLowerCase() } -> "Add one lowercase letter."
            password.none { it.isDigit() } -> "Add one number."
            else -> null
        }
    }

    private fun validateRequired(
        key: String,
        value: String,
        message: String,
        errors: MutableMap<String, String>,
        maxLength: Int
    ) {
        try {
            if (clean(value, maxLength).isBlank()) errors[key] = message
        } catch (exception: IllegalArgumentException) {
            errors[key] = exception.message ?: message
        }
    }
}
