package com.example.museumapp.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.museumapp.data.model.LoginResponse
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
        val LoginTimestamp = longPreferencesKey("login_timestamp")
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

    suspend fun saveSession(loginResponse: LoginResponse) {
        context.adminSessionDataStore.edit { preferences ->
            preferences[Keys.AccessToken] = loginResponse.accessToken
            preferences[Keys.TokenType] = loginResponse.tokenType
            preferences[Keys.AdminId] = loginResponse.user.id
            preferences[Keys.AdminEmail] = loginResponse.user.email
            preferences[Keys.AdminName] = loginResponse.user.fullName
            preferences[Keys.Role] = loginResponse.user.role
            preferences[Keys.LoginTimestamp] = System.currentTimeMillis()
        }
    }

    suspend fun clearSession() {
        context.adminSessionDataStore.edit { it.clear() }
    }
}
