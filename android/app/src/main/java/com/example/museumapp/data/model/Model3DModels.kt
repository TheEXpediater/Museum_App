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
    // Draft model awaiting admin Accept/Reject review - never visitor-visible. Populated only
    // while status == PendingReview.
    @Json(name = "draft_version") val draftVersion: Int? = null,
    @Json(name = "draft_sha256") val draftSha256: String? = null,
    @Json(name = "draft_size_bytes") val draftSizeBytes: Long? = null,
    @Json(name = "draft_created_at") val draftCreatedAt: String? = null,
    @Json(name = "draft_model_url") val draftModelUrl: String? = null,
    @Json(name = "source_image_count") val sourceImageCount: Int = 0,
    @Json(name = "registered_image_count") val registeredImageCount: Int? = null,
    @Json(name = "registered_image_ratio") val registeredImageRatio: Double? = null,
    @Json(name = "sparse_point_count") val sparsePointCount: Int? = null,
    @Json(name = "mean_reprojection_error") val meanReprojectionError: Double? = null,
    @Json(name = "failure_message") val failureMessage: String? = null,
    val guidance: List<String> = emptyList(),
    val images: List<Model3DImageDto> = emptyList(),
    @Json(name = "active_job_id") val activeJobId: String? = null,
    @Json(name = "colmap_available") val colmapAvailable: Boolean? = null,
    @Json(name = "model_url") val modelUrl: String? = null
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
    const val PendingReview = "pending_review"
    const val Ready = "ready"
    const val Failed = "failed"
    const val Interrupted = "interrupted"

    /** Statuses reached only while a background reconstruction job is running. */
    val ActiveJobStatuses: Set<String> = setOf(
        Queued,
        SparseReconstruction,
        DenseReconstruction,
        Meshing,
        Texturing,
        Converting
    )
}

/** Origins reported for [Model3DImageDto.origin]. */
object Model3DImageOrigin {
    const val Reused = "reused"
    const val Uploaded = "uploaded"
}

/**
 * True while a reconstruction job is running for this artifact -- either because the reported
 * status is one of the in-progress stages, or because the backend still points at an
 * [Model3DStateDto.activeJobId] (e.g. immediately after `build` returns, before the first status
 * poll lands).
 */
fun Model3DStateDto.isJobActive(): Boolean = status in Model3DStatus.ActiveJobStatuses || activeJobId != null
