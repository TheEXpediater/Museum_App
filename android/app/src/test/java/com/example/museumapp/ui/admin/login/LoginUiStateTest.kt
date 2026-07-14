package com.example.museumapp.ui.admin.login

import com.example.museumapp.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUiStateTest {
    @Test
    fun debugDefaultsComeFromBuildConfigWithoutShowingPassword() {
        val state = LoginUiState()

        assertTrue(state.email == BuildConfig.DEBUG_ADMIN_EMAIL)
        assertTrue(state.password == BuildConfig.DEBUG_ADMIN_PASSWORD)
        assertFalse(state.isPasswordVisible)
    }
}
