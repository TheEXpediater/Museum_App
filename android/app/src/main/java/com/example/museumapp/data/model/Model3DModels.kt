package com.example.museumapp.data.model

import com.squareup.moshi.Json

/**
 * One source photograph used for a 3D reconstruction attempt, either copied from the artifact's
 * own gallery ([Model3DImageOrigin.Reused]) or uploaded specifically for reconstruction
 * ([Model3DImageOrigin.Uploaded]).
 */
data class Model3DImageDto(
    val id: String,
    val origin: String,
    @Json(name = "original_filename") val originalFilename: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

/**
 * Full reconstruction state for one artifact, as returned by the state/preflight/add-images/
 * delete-images endpoints and nested inside [Model3DStatusResponseDto] while a job is active.
 */
data class Model3DStateDto(
    val status: String = Model3DStatus.None,
    val version: Int = 0,
    val sha256: String? = null,
    @Json(name = "size_bytes") val sizeBytes: Long? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    // How the published model was produced - see [Model3DGenerationMethod]. Admin-facing only.
    @Json(name = "generation_method") val generationMethod: String? = null,
    // Draft model awaiting admin Accept/Reject review - never visitor-visible. Populated only
    // while status == PendingReview.
    @Json(name = "draft_version") val draftVersion: Int? = null,
    @Json(name = "draft_sha256") val draftSha256: String? = null,
    @Json(name = "draft_size_bytes") val draftSizeBytes: Long? = null,
    @Json(name = "draft_created_at") val draftCreatedAt: String? = null,
    @Json(name = "draft_model_url") val draftModelUrl: String? = null,
    @Json(name = "draft_generation_method") val draftGenerationMethod: String? = null,
    // Estimated coverage (see backend app/services/model3d/coverage.py) - a heuristic derived
    // from which major regions the admin marked visible in the generation image, never a model
    // confidence score. Null means "coverage estimate unavailable", not 0%.
    @Json(name = "visible_regions") val visibleRegions: List<String> = emptyList(),
    @Json(name = "estimated_supported_percent") val estimatedSupportedPercent: Int? = null,
    @Json(name = "estimated_inferred_percent") val estimatedInferredPercent: Int? = null,
    @Json(name = "draft_visible_regions") val draftVisibleRegions: List<String> = emptyList(),
    @Json(name = "draft_estimated_supported_percent") val draftEstimatedSupportedPercent: Int? = null,
    @Json(name = "draft_estimated_inferred_percent") val draftEstimatedInferredPercent: Int? = null,
    @Json(name = "source_image_count") val sourceImageCount: Int = 0,
    @Json(name = "registered_image_count") val registeredImageCount: Int? = null,
    @Json(name = "registered_image_ratio") val registeredImageRatio: Double? = null,
    @Json(name = "sparse_point_count") val sparsePointCount: Int? = null,
    @Json(name = "mean_reprojection_error") val meanReprojectionError: Double? = null,
    // Coarse "good"/"insufficient" verdict from the backend's COLMAP quality gate, plus why -
    // see [Model3DQuality]. Null for AI drafts, which are reviewed visually instead.
    @Json(name = "quality_assessment") val qualityAssessment: String? = null,
    @Json(name = "quality_reasons") val qualityReasons: List<String> = emptyList(),
    @Json(name = "failure_message") val failureMessage: String? = null,
    val guidance: List<String> = emptyList(),
    val images: List<Model3DImageDto> = emptyList(),
    @Json(name = "active_job_id") val activeJobId: String? = null,
    @Json(name = "colmap_available") val colmapAvailable: Boolean? = null,
    @Json(name = "model_url") val modelUrl: String? = null,
    // Whether the optional AI 3D fallback is configured on this backend, and the provider's
    // per-request photo cap - drives whether/how "Generate AI 3D Preview" is offered.
    @Json(name = "ai_available") val aiAvailable: Boolean = false,
    @Json(name = "ai_max_images") val aiMaxImages: Int? = null
)

/**
 * A single reconstruction job's progress/result, returned by the status-polling endpoint.
 */
data class Model3DJobDto(
    val id: String,
    val status: String,
    @Json(name = "stage_message") val stageMessage: String? = null,
    @Json(name = "target_version") val targetVersion: Int? = null,
    @Json(name = "source_image_count") val sourceImageCount: Int? = null,
    @Json(name = "registered_image_count") val registeredImageCount: Int? = null,
    @Json(name = "registered_image_ratio") val registeredImageRatio: Double? = null,
    @Json(name = "sparse_point_count") val sparsePointCount: Int? = null,
    @Json(name = "mean_reprojection_error") val meanReprojectionError: Double? = null,
    @Json(name = "generation_method") val generationMethod: String? = null,
    val error: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "finished_at") val finishedAt: String? = null
)

data class Model3DStatusResponseDto(
    val state: Model3DStateDto,
    val job: Model3DJobDto? = null
)

data class Model3DBuildResponseDto(
    @Json(name = "job_id") val jobId: String,
    val status: String
)

/** Admin's deliberate photo selection for the AI 3D fallback - see [Model3DStateDto.aiMaxImages]
 * for the provider's actual per-request cap. */
data class Model3DAiBuildRequestDto(
    @Json(name = "image_ids") val imageIds: List<String>,
    @Json(name = "visible_regions") val visibleRegions: List<String> = emptyList()
)

/** The 6 major regions an admin can mark as visible in the AI generation input - see
 * [Model3DStateDto.visibleRegions] and backend app/services/model3d/coverage.py. */
object Model3DCoverageRegion {
    val ALL = listOf("front", "right", "back", "left", "top", "bottom")

    fun label(region: String): String = region.replaceFirstChar { it.uppercase() }
}

/** Known values of [Model3DStateDto.status] / [Model3DJobDto.status]. */
object Model3DStatus {
    const val None = "none"
    const val NeedsImages = "needs_images"
    const val ReadyForBuild = "ready_for_build"
    const val Queued = "queued"
    const val SparseReconstruction = "sparse_reconstruction"
    const val DenseReconstruction = "dense_reconstruction"
    const val Meshing = "meshing"
    const val Texturing = "texturing"
    const val Converting = "converting"
    // AI multi-view job stages (see Model3DGenerationMethod.AiMultiview) - distinct from the
    // COLMAP stage names above so status/job copy can tell the two pipelines apart, but they
    // converge on the same PendingReview/Ready/Failed terminal states as COLMAP.
    const val AiQueued = "ai_queued"
    const val AiGenerating = "ai_generating"
    const val AiDownloading = "ai_downloading"
    const val AiValidating = "ai_validating"
    const val PendingReview = "pending_review"
    const val Ready = "ready"
    const val Failed = "failed"
    const val Interrupted = "interrupted"

    /** Statuses reached only while a background reconstruction job (COLMAP or AI) is running. */
    val ActiveJobStatuses: Set<String> = setOf(
        Queued,
        SparseReconstruction,
        DenseReconstruction,
        Meshing,
        Texturing,
        Converting,
        AiQueued,
        AiGenerating,
        AiDownloading,
        AiValidating
    )

    /** Human-readable stage label for the processing modal - real backend state only, never a
     * fabricated progress percentage. Falls back to the backend's own [Model3DJobDto.stageMessage]
     * text (via the caller) when a status isn't recognized here. */
    fun processingLabel(status: String): String = when (status) {
        Queued, AiQueued -> "Queued"
        SparseReconstruction -> "Building sparse reconstruction"
        DenseReconstruction -> "Building dense reconstruction"
        Meshing -> "Building the 3D surface"
        Texturing -> "Applying texture"
        Converting -> "Preparing the mobile model"
        AiGenerating -> "Generating 3D preview"
        AiDownloading -> "Downloading generated model"
        AiValidating -> "Validating generated model"
        PendingReview -> "Ready for review"
        else -> "Processing"
    }
}

/** Origins reported for [Model3DImageDto.origin]. */
object Model3DImageOrigin {
    const val Reused = "reused"
    const val Uploaded = "uploaded"
}

/** How a published/draft model's geometry was produced - see [Model3DStateDto.generationMethod]
 * and [Model3DStateDto.draftGenerationMethod]. Visitor never sees this, only Admin. */
object Model3DGenerationMethod {
    const val Colmap = "colmap"
    const val AiMultiview = "ai_multiview"
    const val AiLocal = "ai_local"

    /** Admin-facing label - never the raw provider/algorithm name. */
    fun label(value: String?): String = when (value) {
        Colmap -> "Photogrammetry"
        AiMultiview -> "AI Preview"
        AiLocal -> "Local AI Preview"
        else -> "Unknown"
    }
}

/** Coarse pass/fail verdict from the backend's COLMAP quality gate - see
 * [Model3DStateDto.qualityAssessment]. */
object Model3DQuality {
    const val Good = "good"
    const val Insufficient = "insufficient"
}

/**
 * True while a reconstruction job is running for this artifact -- either because the reported
 * status is one of the in-progress stages, or because the backend still points at an
 * [Model3DStateDto.activeJobId] (e.g. immediately after `build` returns, before the first status
 * poll lands).
 */
fun Model3DStateDto.isJobActive(): Boolean = status in Model3DStatus.ActiveJobStatuses || activeJobId != null
