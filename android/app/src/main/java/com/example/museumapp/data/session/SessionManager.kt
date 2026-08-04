package com.example.museumapp.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.museumapp.data.model.LoginResponse
import com.example.museumapp.data.model.VisitorProfileDto
import com.example.museumapp.data.model.VisitorTokenResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.adminSessionDataStore by preferencesDataStore(name = "admin_session")

class SessionManager(private val context: Context) {
    private object Keys {
        val AccessToken = stringPreferencesKey("access_token")
        val TokenType = stringPreferencesKey("token_type")
        val AdminId = stringPreferencesKey("admin_id")
        val AdminEmail = stringPreferencesKey("admin_email")
        val AdminName = stringPreferencesKey("admin_name")
        val Role = stringPreferencesKey("role")
        val AccountType = stringPreferencesKey("account_type")
        val VisitorId = stringPreferencesKey("visitor_id")
        val FirstName = stringPreferencesKey("first_name")
        val MiddleInitial = stringPreferencesKey("middle_initial")
        val LastName = stringPreferencesKey("last_name")
        val DisplayName = stringPreferencesKey("display_name")
        val Email = stringPreferencesKey("email")
        val StudentId = stringPreferencesKey("student_id")
        val YearLevel = stringPreferencesKey("year_level")
        val Course = stringPreferencesKey("course")
        val RelationshipType = stringPreferencesKey("relationship_type")
        val RelationshipDetail = stringPreferencesKey("relationship_detail")
        val BatchOrGraduationYear = stringPreferencesKey("batch_or_graduation_year")
        val OfficeOrDepartment = stringPreferencesKey("office_or_department")
        val LoginTimestamp = longPreferencesKey("login_timestamp")
        val OnboardingCompleted = booleanPreferencesKey("visitor_onboarding_completed")
    }

    val session: Flow<AdminSession> = context.adminSessionDataStore.data.map { preferences ->
        AdminSession(
            accessToken = preferences[Keys.AccessToken].orEmpty(),
            tokenType = preferences[Keys.TokenType].orEmpty(),
            adminId = preferences[Keys.AdminId].orEmpty(),
            adminEmail = preferences[Keys.AdminEmail].orEmpty(),
            adminName = preferences[Keys.AdminName].orEmpty(),
            role = preferences[Keys.Role].orEmpty(),
            loginTimestamp = preferences[Keys.LoginTimestamp] ?: 0L
        )
    }

    val visitorSession: Flow<VisitorSession> = context.adminSessionDataStore.data.map { preferences ->
        VisitorSession(
            accessToken = preferences[Keys.AccessToken].orEmpty(),
            tokenType = preferences[Keys.TokenType].orEmpty(),
            accountType = preferences[Keys.AccountType].orEmpty(),
            id = preferences[Keys.VisitorId].orEmpty(),
            firstName = preferences[Keys.FirstName].orEmpty(),
            middleInitial = preferences[Keys.MiddleInitial].orEmpty(),
            lastName = preferences[Keys.LastName].orEmpty(),
            displayName = preferences[Keys.DisplayName].orEmpty(),
            email = preferences[Keys.Email].orEmpty(),
            studentId = preferences[Keys.StudentId].orEmpty(),
            yearLevel = preferences[Keys.YearLevel].orEmpty(),
            course = preferences[Keys.Course].orEmpty(),
            relationshipType = preferences[Keys.RelationshipType].orEmpty(),
            relationshipDetail = preferences[Keys.RelationshipDetail].orEmpty(),
            batchOrGraduationYear = preferences[Keys.BatchOrGraduationYear].orEmpty(),
            officeOrDepartment = preferences[Keys.OfficeOrDepartment].orEmpty(),
            loginTimestamp = preferences[Keys.LoginTimestamp] ?: 0L
        )
    }

    val onboardingCompleted: Flow<Boolean> = context.adminSessionDataStore.data.map { preferences ->
        preferences[Keys.OnboardingCompleted] ?: false
    }

    suspend fun saveSession(loginResponse: LoginResponse) {
        context.adminSessionDataStore.edit { preferences ->
            preferences[Keys.AccessToken] = loginResponse.accessToken
            preferences[Keys.TokenType] = loginResponse.tokenType
            preferences[Keys.AdminId] = loginResponse.user.id
            preferences[Keys.AdminEmail] = loginResponse.user.email
            preferences[Keys.AdminName] = loginResponse.user.fullName
            preferences[Keys.AccountType] = "admin"
            preferences[Keys.Role] = loginResponse.user.role
            preferences[Keys.LoginTimestamp] = System.currentTimeMillis()
            preferences.remove(Keys.VisitorId)
            preferences.remove(Keys.FirstName)
            preferences.remove(Keys.MiddleInitial)
            preferences.remove(Keys.LastName)
            preferences.remove(Keys.DisplayName)
            preferences.remove(Keys.Email)
            preferences.remove(Keys.StudentId)
            preferences.remove(Keys.YearLevel)
            preferences.remove(Keys.Course)
            preferences.remove(Keys.RelationshipType)
            preferences.remove(Keys.RelationshipDetail)
            preferences.remove(Keys.BatchOrGraduationYear)
            preferences.remove(Keys.OfficeOrDepartment)
        }
    }

    suspend fun saveVisitorSession(response: VisitorTokenResponseDto) {
        context.adminSessionDataStore.edit { preferences ->
            preferences[Keys.AccessToken] = response.accessToken
            preferences[Keys.TokenType] = response.tokenType
            preferences[Keys.AccountType] = response.accountType
            preferences[Keys.Role] = response.accountType
            preferences[Keys.LoginTimestamp] = System.currentTimeMillis()
            writeVisitorProfile(preferences, response.profile)
            preferences.remove(Keys.AdminId)
            preferences.remove(Keys.AdminEmail)
            preferences.remove(Keys.AdminName)
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.adminSessionDataStore.edit { preferences ->
            preferences[Keys.OnboardingCompleted] = completed
        }
    }

    suspend fun clearSession() {
        context.adminSessionDataStore.edit { preferences ->
            sessionKeys.forEach { key -> preferences.remove(key) }
        }
    }

    private fun writeVisitorProfile(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        profile: VisitorProfileDto
    ) {
        preferences[Keys.VisitorId] = profile.id
        preferences[Keys.FirstName] = profile.firstName.orEmpty()
        preferences[Keys.MiddleInitial] = profile.middleInitial.orEmpty()
        preferences[Keys.LastName] = profile.lastName.orEmpty()
        preferences[Keys.DisplayName] = profile.displayName.orEmpty()
        preferences[Keys.Email] = profile.email.orEmpty()
        preferences[Keys.StudentId] = profile.studentId.orEmpty()
        preferences[Keys.YearLevel] = profile.yearLevel.orEmpty()
        preferences[Keys.Course] = profile.course.orEmpty()
        preferences[Keys.RelationshipType] = profile.relationshipType.orEmpty()
        preferences[Keys.RelationshipDetail] = profile.relationshipDetail.orEmpty()
        preferences[Keys.BatchOrGraduationYear] = profile.batchOrGraduationYear.orEmpty()
        preferences[Keys.OfficeOrDepartment] = profile.officeOrDepartment.orEmpty()
    }

    private val sessionKeys = setOf(
        Keys.AccessToken,
        Keys.TokenType,
        Keys.AdminId,
        Keys.AdminEmail,
        Keys.AdminName,
        Keys.Role,
        Keys.AccountType,
        Keys.VisitorId,
        Keys.FirstName,
        Keys.MiddleInitial,
        Keys.LastName,
        Keys.DisplayName,
        Keys.Email,
        Keys.StudentId,
        Keys.YearLevel,
        Keys.Course,
        Keys.RelationshipType,
        Keys.RelationshipDetail,
        Keys.BatchOrGraduationYear,
        Keys.OfficeOrDepartment,
        Keys.LoginTimestamp
    )
}
