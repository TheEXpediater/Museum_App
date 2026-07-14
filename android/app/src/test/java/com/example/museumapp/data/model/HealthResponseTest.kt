package com.example.museumapp.data.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HealthResponseTest {
    @Test
    fun parsesHealthResponseUploadsDirectoryField() {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(HealthResponse::class.java)

        val parsed = adapter.fromJson(
            """
            {
              "status": "healthy",
              "database": "connected",
              "uploads_directory": "available"
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals("healthy", parsed!!.status)
        assertEquals("connected", parsed.database)
        assertEquals("available", parsed.uploadsDirectory)
    }
}
