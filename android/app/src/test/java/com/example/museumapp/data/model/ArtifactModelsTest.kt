package com.example.museumapp.data.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ArtifactModelsTest {
    @Test
    fun parsesArtifactAiStatusFields() {
        val adapter = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(ArtifactDto::class.java)

        val parsed = adapter.fromJson(
            """
            {
              "id": "1",
              "artifact_code": "ART-1",
              "name": "Jar",
              "description": "Clay jar",
              "category": "Ceramics",
              "status": "draft",
              "origin": null,
              "historical_period": null,
              "material": "Clay",
              "dimensions": null,
              "condition": "Good",
              "custom_fields": [
                {"id": "weight", "label": "Weight", "value": "3.5", "unit": "kg", "type": "number"}
              ],
              "image_paths": [],
              "image_urls": [],
              "primary_image_path": null,
              "primary_image_url": null,
              "primary_image_needs_review": true,
              "visitor_gallery_image_paths": [],
              "visitor_gallery_image_urls": [],
              "visitor_gallery_configured": true,
              "ai_index_status": "indexed",
              "ai_indexed_image_count": 2,
              "ai_indexed_at": "2026-08-03T12:00:00",
              "ai_index_error": null,
              "created_by": "admin",
              "created_at": "2026-08-03T11:00:00",
              "updated_at": "2026-08-03T12:00:00"
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals("indexed", parsed!!.aiIndexStatus)
        assertEquals(2, parsed.aiIndexedImageCount)
        assertEquals("draft", parsed.status)
        assertEquals(true, parsed.primaryImageNeedsReview)
        assertEquals(true, parsed.visitorGalleryConfigured)
        assertEquals("Weight", parsed.customFields.single().label)
    }
}
