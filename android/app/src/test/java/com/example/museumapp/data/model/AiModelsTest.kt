package com.example.museumapp.data.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AiModelsTest {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun parsesRecognitionResponse() {
        val adapter = moshi.adapter(RecognitionResponseDto::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "matched": true,
              "match_level": "strong",
              "best_match": {
                "artifact": {
                  "id": "1",
                  "artifact_code": "ART-1",
                  "name": "Jar",
                  "description": "Clay jar",
                  "category": "Ceramics",
                  "origin": null,
                  "historical_period": null,
                  "material": "Clay",
                  "dimensions": null,
                  "condition": "Good",
                  "primary_image_url": "http://test/image.jpg"
                },
                "similarity_score": 0.9123,
                "matched_image_path": "uploads/images/jar.jpg",
                "supporting_image_hits": 2
              },
              "other_matches": [],
              "message": "ok"
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals("strong", parsed!!.matchLevel)
        assertEquals("Jar", parsed.bestMatch!!.artifact.name)
        assertEquals(2, parsed.bestMatch.supportingImageHits)
    }

    @Test
    fun parsesAiHealthResponse() {
        val adapter = moshi.adapter(AiHealthResponse::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "status": "healthy",
              "ai_enabled": true,
              "openclip": "loaded",
              "model_name": "ViT-B-32",
              "pretrained": "laion2b_s34b_b79k",
              "device": "cpu",
              "embedding_dimension": 512,
              "qdrant": "connected",
              "collection": "artifact_images",
              "collection_status": "ready",
              "indexed_vectors": 7
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals("loaded", parsed!!.openclip)
        assertEquals(512, parsed.embeddingDimension)
        assertEquals(7, parsed.indexedVectors)
    }

    @Test
    fun parsesWarmupResponse() {
        val adapter = moshi.adapter(AiWarmupResponse::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "state": "loaded",
              "message": "OpenCLIP is ready.",
              "model_name": "ViT-B-32",
              "pretrained": "laion2b_s34b_b79k",
              "device": "cpu",
              "embedding_dimension": 512,
              "started_at": "2026-08-03T01:00:00Z",
              "completed_at": "2026-08-03T01:00:02Z",
              "duration_seconds": 2.0,
              "error": null
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals("loaded", parsed!!.state)
        assertEquals(512, parsed.embeddingDimension)
        assertEquals(2.0, parsed.durationSeconds!!, 0.0)
    }

    @Test
    fun parsesDashboardResponse() {
        val adapter = moshi.adapter(DashboardSummaryResponse::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "total_artifacts": 3,
              "total_images": 5,
              "total_categories": 2,
              "published_artifacts": 2,
              "draft_artifacts": 1,
              "ai_library_ready_artifacts": 1,
              "ai_library_pending_artifacts": 1,
              "ai_library_stale_artifacts": 0,
              "indexed_artifacts": 1,
              "pending_artifacts": 1,
              "failed_artifacts": 1,
              "indexed_vectors": 5,
              "ai_status": "healthy",
              "database_status": "connected",
              "uploads_status": "available",
              "recent_artifacts": []
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(3, parsed!!.totalArtifacts)
        assertEquals("healthy", parsed.aiStatus)
        assertEquals(1, parsed.aiLibraryPendingArtifacts)
    }

    @Test
    fun parsesAiLibraryFeedResponse() {
        val adapter = moshi.adapter(AiLibraryFeedResponse::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "artifacts_processed": 20,
              "images_processed": 437,
              "successful_artifacts": 18,
              "failed_artifacts": 2,
              "errors": ["two artifacts failed"]
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(20, parsed!!.artifactsProcessed)
        assertEquals(437, parsed.imagesProcessed)
        assertEquals(18, parsed.successfulArtifacts)
        assertEquals(2, parsed.failedArtifacts)
    }
}
