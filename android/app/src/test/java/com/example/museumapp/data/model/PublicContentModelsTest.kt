package com.example.museumapp.data.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PublicContentModelsTest {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun model3DFieldsDefaultSafelyWhenAbsent() {
        val adapter = moshi.adapter(PublicArtifactDto::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "id": "1",
              "artifact_code": "ART-1",
              "name": "Jar",
              "description": "Clay jar",
              "category": "Ceramics"
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertFalse(parsed!!.model3dAvailable)
        assertNull(parsed.model3dUrl)
        assertNull(parsed.model3dVersion)
        assertNull(parsed.model3dSha256)
        assertNull(parsed.model3dSizeBytes)
    }

    @Test
    fun parsesModel3DFieldsWhenPublished() {
        val adapter = moshi.adapter(PublicArtifactDto::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "id": "1",
              "artifact_code": "ART-1",
              "name": "Jar",
              "description": "Clay jar",
              "category": "Ceramics",
              "model_3d_available": true,
              "model_3d_url": "http://host:8000/uploads/models3d/1/model-v2.glb",
              "model_3d_version": 2,
              "model_3d_sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcd",
              "model_3d_size_bytes": 4823917
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(true, parsed!!.model3dAvailable)
        assertEquals("http://host:8000/uploads/models3d/1/model-v2.glb", parsed.model3dUrl)
        assertEquals(2, parsed.model3dVersion)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcd", parsed.model3dSha256)
        assertEquals(4823917L, parsed.model3dSizeBytes)
    }
}
