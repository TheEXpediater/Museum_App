package com.example.museumapp.data.api

import com.example.museumapp.BuildConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class BuildConfigApiBaseUrlTest {
    @Test
    fun apiBaseUrlIsAbsoluteHttpUrlWithTrailingSlash() {
        val uri = URI(BuildConfig.API_BASE_URL)

        assertTrue(BuildConfig.API_BASE_URL.endsWith("/"))
        assertTrue(uri.scheme == "http" || uri.scheme == "https")
        assertNotNull(uri.host)
    }
}
