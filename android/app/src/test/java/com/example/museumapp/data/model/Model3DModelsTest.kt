package com.example.museumapp.data.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Model3DModelsTest {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun stateDtoDefaultsSafelyWhenFieldsAbsent() {
        val adapter = moshi.adapter(Model3DStateDto::class.java)
        val parsed = adapter.fromJson("{}")

        assertNotNull(parsed)
        assertEquals(Model3DStatus.None, parsed!!.status)
        assertEquals(0, parsed.version)
        assertNull(parsed.sha256)
        assertNull(parsed.sizeBytes)
        assertNull(parsed.activeJobId)
        assertTrue(parsed.guidance.isEmpty())
        assertTrue(parsed.images.isEmpty())
        assertFalse(parsed.isJobActive())
    }

    @Test
    fun parsesFullStateDto() {
        val adapter = moshi.adapter(Model3DStateDto::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "status": "needs_images",
              "version": 0,
              "sha256": null,
              "size_bytes": null,
              "created_at": null,
              "source_image_count": 4,
              "registered_image_count": null,
              "registered_image_ratio": null,
              "sparse_point_count": null,
              "mean_reprojection_error": null,
              "failure_message": null,
              "guidance": ["Add more photographs with overlapping viewpoints between shots."],
              "images": [
                {"id": "img-1", "origin": "reused", "original_filename": "front.jpg", "width": 1200, "height": 900, "created_at": "2026-08-01T00:00:00"}
              ],
              "active_job_id": null,
              "colmap_available": true,
              "model_url": null
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals("needs_images", parsed!!.status)
        assertEquals(4, parsed.sourceImageCount)
        assertEquals(1, parsed.guidance.size)
        assertEquals("img-1", parsed.images.single().id)
        assertEquals(Model3DImageOrigin.Reused, parsed.images.single().origin)
        assertEquals(true, parsed.colmapAvailable)
        assertFalse(parsed.isJobActive())
    }

    @Test
    fun stateWithActiveJobStatusIsJobActive() {
        val queued = Model3DStateDto(status = Model3DStatus.Queued)
        assertTrue(queued.isJobActive())

        val readyWithDanglingJobId = Model3DStateDto(status = Model3DStatus.Ready, activeJobId = "job-1")
        assertTrue(readyWithDanglingJobId.isJobActive())

        val ready = Model3DStateDto(status = Model3DStatus.Ready)
        assertFalse(ready.isJobActive())
    }

    @Test
    fun aiJobStagesAreAlsoConsideredActive() {
        // AI generation stages must poll exactly like COLMAP stages do.
        assertTrue(Model3DStateDto(status = Model3DStatus.AiQueued).isJobActive())
        assertTrue(Model3DStateDto(status = Model3DStatus.AiGenerating).isJobActive())
        assertTrue(Model3DStateDto(status = Model3DStatus.AiDownloading).isJobActive())
        assertTrue(Model3DStateDto(status = Model3DStatus.AiValidating).isJobActive())
    }

    @Test
    fun parsesAiFallbackAndQualityFields() {
        val adapter = moshi.adapter(Model3DStateDto::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "status": "ready_for_build",
              "source_image_count": 30,
              "registered_image_count": 3,
              "registered_image_ratio": 0.1,
              "quality_assessment": "insufficient",
              "quality_reasons": ["Only 3 of 30 photo(s) could be matched (10% registered; 70% is the target for a complete reconstruction)."],
              "ai_available": true,
              "ai_max_images": 4
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(Model3DQuality.Insufficient, parsed!!.qualityAssessment)
        assertEquals(1, parsed.qualityReasons.size)
        assertTrue(parsed.aiAvailable)
        assertEquals(4, parsed.aiMaxImages)
    }

    @Test
    fun aiFallbackFieldsDefaultSafelyWhenAbsent() {
        // Existing (pre-AI-fallback) backend response shape must still parse cleanly.
        val adapter = moshi.adapter(Model3DStateDto::class.java)
        val parsed = adapter.fromJson("""{"status": "ready_for_build"}""")

        assertNotNull(parsed)
        assertFalse(parsed!!.aiAvailable)
        assertNull(parsed.aiMaxImages)
        assertNull(parsed.qualityAssessment)
        assertTrue(parsed.qualityReasons.isEmpty())
    }

    @Test
    fun parsesGenerationMethodOnPublishedAndDraftModel() {
        val adapter = moshi.adapter(Model3DStateDto::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "status": "pending_review",
              "generation_method": "colmap",
              "draft_generation_method": "ai_multiview"
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(Model3DGenerationMethod.Colmap, parsed!!.generationMethod)
        assertEquals(Model3DGenerationMethod.AiMultiview, parsed.draftGenerationMethod)
    }

    @Test
    fun generationMethodLabelsAreAdminFriendlyAndNeverExposeProviderName() {
        assertEquals("Photogrammetry", Model3DGenerationMethod.label(Model3DGenerationMethod.Colmap))
        assertEquals("AI Preview", Model3DGenerationMethod.label(Model3DGenerationMethod.AiMultiview))
        assertEquals("Local AI Preview", Model3DGenerationMethod.label(Model3DGenerationMethod.AiLocal))
        assertEquals("Unknown", Model3DGenerationMethod.label(null))
        assertEquals("Unknown", Model3DGenerationMethod.label("meshy"))
        assertEquals("Unknown", Model3DGenerationMethod.label("triposr"))
    }

    @Test
    fun parsesJobDtoWithGenerationMethod() {
        val adapter = moshi.adapter(Model3DJobDto::class.java)
        val parsed = adapter.fromJson(
            """
            { "id": "job-1", "status": "ai_generating", "generation_method": "ai_multiview" }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals("ai_generating", parsed!!.status)
        assertEquals(Model3DGenerationMethod.AiMultiview, parsed.generationMethod)
    }

    @Test
    fun serializesAiBuildRequest() {
        val adapter = moshi.adapter(Model3DAiBuildRequestDto::class.java)
        val json = adapter.toJson(Model3DAiBuildRequestDto(imageIds = listOf("img-1", "img-2")))

        assertTrue(json.contains("\"image_ids\""))
        val roundTripped = adapter.fromJson(json)
        assertEquals(listOf("img-1", "img-2"), roundTripped!!.imageIds)
        assertTrue(roundTripped.visibleRegions.isEmpty())
    }

    @Test
    fun serializesAiBuildRequestWithVisibleRegions() {
        val adapter = moshi.adapter(Model3DAiBuildRequestDto::class.java)
        val json = adapter.toJson(Model3DAiBuildRequestDto(imageIds = listOf("img-1"), visibleRegions = listOf("front", "top")))

        assertTrue(json.contains("\"visible_regions\""))
        val roundTripped = adapter.fromJson(json)
        assertEquals(listOf("front", "top"), roundTripped!!.visibleRegions)
    }

    @Test
    fun parsesCoverageFieldsOnStateDto() {
        val adapter = moshi.adapter(Model3DStateDto::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "status": "ready",
              "visible_regions": ["front", "right", "top"],
              "estimated_supported_percent": 50,
              "estimated_inferred_percent": 50
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(listOf("front", "right", "top"), parsed!!.visibleRegions)
        assertEquals(50, parsed.estimatedSupportedPercent)
        assertEquals(50, parsed.estimatedInferredPercent)
    }

    @Test
    fun coverageFieldsDefaultSafelyWhenAbsent() {
        val adapter = moshi.adapter(Model3DStateDto::class.java)
        val parsed = adapter.fromJson("""{"status": "ready"}""")

        assertNotNull(parsed)
        assertTrue(parsed!!.visibleRegions.isEmpty())
        assertNull(parsed.estimatedSupportedPercent)
        assertNull(parsed.estimatedInferredPercent)
    }

    @Test
    fun coverageRegionListMatchesTheSixCanonicalRegions() {
        assertEquals(listOf("front", "right", "back", "left", "top", "bottom"), Model3DCoverageRegion.ALL)
        assertEquals("Front", Model3DCoverageRegion.label("front"))
    }

    @Test
    fun readyStateCanCarryAFailedRebuildMessage() {
        // A failed rebuild attempt must not erase a previously published working model: status
        // stays "ready" while failure_message/guidance describe the failed attempt.
        val adapter = moshi.adapter(Model3DStateDto::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "status": "ready",
              "version": 2,
              "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcd",
              "size_bytes": 1024,
              "failure_message": "The last rebuild failed: not enough overlapping images.",
              "guidance": ["Add more photographs."],
              "model_url": "http://host:8000/uploads/models3d/1/model-v2.glb"
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals("ready", parsed!!.status)
        assertEquals("The last rebuild failed: not enough overlapping images.", parsed.failureMessage)
        assertFalse(parsed.isJobActive())
    }

    @Test
    fun parsesPendingReviewStateWithDraftFieldsButNoPublishedFields() {
        // A finished build is a draft awaiting admin review: version/sha256/model_url stay at
        // their "nothing published yet" defaults while the draft_* fields carry the new model.
        val adapter = moshi.adapter(Model3DStateDto::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "status": "pending_review",
              "version": 0,
              "sha256": null,
              "model_url": null,
              "draft_version": 1,
              "draft_sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcd",
              "draft_size_bytes": 2048,
              "draft_model_url": "http://host:8000/uploads/models3d/1/model-v1.glb"
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(Model3DStatus.PendingReview, parsed!!.status)
        assertEquals(0, parsed.version)
        assertNull(parsed.sha256)
        assertNull(parsed.modelUrl)
        assertEquals(1, parsed.draftVersion)
        assertEquals(2048L, parsed.draftSizeBytes)
        assertTrue(parsed.draftModelUrl!!.endsWith(".glb"))
        assertFalse(parsed.isJobActive()) // the build finished; it is just awaiting Accept/Reject
    }

    @Test
    fun parsesJobDto() {
        val adapter = moshi.adapter(Model3DJobDto::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "id": "job-1",
              "status": "dense_reconstruction",
              "stage_message": "Building dense point cloud",
              "target_version": 2,
              "source_image_count": 10,
              "registered_image_count": 9,
              "registered_image_ratio": 0.9,
              "sparse_point_count": 1500,
              "mean_reprojection_error": 0.6,
              "error": null,
              "created_at": "2026-08-01T00:00:00",
              "updated_at": "2026-08-01T00:05:00",
              "started_at": "2026-08-01T00:00:05",
              "finished_at": null
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals("job-1", parsed!!.id)
        assertEquals("Building dense point cloud", parsed.stageMessage)
        assertEquals(0.9, parsed.registeredImageRatio!!, 0.0001)
        assertNull(parsed.finishedAt)
    }

    @Test
    fun parsesStatusResponseWithNullJob() {
        val adapter = moshi.adapter(Model3DStatusResponseDto::class.java)
        val parsed = adapter.fromJson(
            """
            { "state": { "status": "none" }, "job": null }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals("none", parsed!!.state.status)
        assertNull(parsed.job)
    }

    @Test
    fun parsesBuildResponse() {
        val adapter = moshi.adapter(Model3DBuildResponseDto::class.java)
        val parsed = adapter.fromJson(
            """
            { "job_id": "job-42", "status": "queued" }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals("job-42", parsed!!.jobId)
        assertEquals("queued", parsed.status)
    }
}
