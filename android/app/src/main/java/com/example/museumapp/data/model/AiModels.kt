package com.example.museumapp.data.model

import com.squareup.moshi.Json

data class AiHealthResponse(
    val status: String,
    @Json(name = "ai_enabled") val aiEnabled: Boolean,
    val openclip: String,
    @Json(name = "model_name") val modelName: String,
    val pretrained: String,
    val device: String?,
    @Json(name = "embedding_dimension") val embeddingDimension: Int?,
    val qdrant: String,
    val collection: String,
    @Json(name = "collection_status") val collectionStatus: String,
    @Json(name = "indexed_vectors") val indexedVectors: Int,
    @Json(name = "collection_vector_size") val collectionVectorSize: Int? = null,
    @Json(name = "collection_distance") val collectionDistance: String? = null,
    val message: String? = null
)

data class DashboardSummaryResponse(
    @Json(name = "total_artifacts") val totalArtifacts: Int,
    @Json(name = "total_images") val totalImages: Int,
    @Json(name = "total_categories") val totalCategories: Int,
    @Json(name = "indexed_artifacts") val indexedArtifacts: Int,
    @Json(name = "pending_artifacts") val pendingArtifacts: Int,
    @Json(name = "failed_artifacts") val failedArtifacts: Int,
    @Json(name = "indexed_vectors") val indexedVectors: Int,
    @Json(name = "ai_status") val aiStatus: String,
    @Json(name = "database_status") val databaseStatus: String,
    @Json(name = "uploads_status") val uploadsStatus: String,
    @Json(name = "recent_artifacts") val recentArtifacts: List<DashboardRecentArtifactDto> = emptyList()
)

data class DashboardRecentArtifactDto(
    val id: String,
    @Json(name = "artifact_code") val artifactCode: String,
    val name: String,
    val category: String,
    val status: String = "published",
    @Json(name = "primary_image_url") val primaryImageUrl: String?,
    @Json(name = "ai_index_status") val aiIndexStatus: String?,
    @Json(name = "created_at") val createdAt: String
)

data class AiIndexResultResponse(
    @Json(name = "artifact_id") val artifactId: String?,
    @Json(name = "ai_index_status") val aiIndexStatus: String,
    @Json(name = "total_images") val totalImages: Int,
    @Json(name = "indexed_images") val indexedImages: Int,
    @Json(name = "failed_images") val failedImages: Int,
    @Json(name = "skipped_images") val skippedImages: Int,
    val messages: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

data class AiIndexAllResponse(
    @Json(name = "total_artifacts") val totalArtifacts: Int,
    @Json(name = "total_images") val totalImages: Int,
    @Json(name = "indexed_images") val indexedImages: Int,
    @Json(name = "failed_images") val failedImages: Int,
    @Json(name = "skipped_images") val skippedImages: Int,
    val duration: Double,
    val errors: List<String> = emptyList()
)

data class AiIndexStatusResponse(
    @Json(name = "total_artifacts") val totalArtifacts: Int,
    @Json(name = "total_images") val totalImages: Int,
    @Json(name = "indexed_artifacts") val indexedArtifacts: Int,
    @Json(name = "pending_artifacts") val pendingArtifacts: Int,
    @Json(name = "failed_artifacts") val failedArtifacts: Int,
    @Json(name = "partial_artifacts") val partialArtifacts: Int,
    @Json(name = "not_indexed_artifacts") val notIndexedArtifacts: Int,
    @Json(name = "indexed_vectors") val indexedVectors: Int,
    @Json(name = "ai_enabled") val aiEnabled: Boolean,
    val openclip: String,
    val qdrant: String,
    val collection: String,
    @Json(name = "collection_status") val collectionStatus: String,
    @Json(name = "collection_vector_size") val collectionVectorSize: Int? = null,
    @Json(name = "collection_distance") val collectionDistance: String? = null,
    val message: String? = null
)

data class AiWarmupResponse(
    val state: String,
    val message: String,
    @Json(name = "model_name") val modelName: String,
    val pretrained: String,
    val device: String?,
    @Json(name = "embedding_dimension") val embeddingDimension: Int?,
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null,
    @Json(name = "duration_seconds") val durationSeconds: Double? = null,
    val error: String? = null
)

data class RecognizedArtifactDto(
    val id: String,
    @Json(name = "artifact_code") val artifactCode: String,
    val name: String,
    val description: String,
    val category: String,
    val origin: String?,
    @Json(name = "historical_period") val historicalPeriod: String?,
    val material: String?,
    val dimensions: String?,
    val condition: String?,
    @Json(name = "primary_image_url") val primaryImageUrl: String?
)

data class ArtifactMatchDto(
    val artifact: RecognizedArtifactDto,
    @Json(name = "similarity_score") val similarityScore: Double,
    @Json(name = "matched_image_path") val matchedImagePath: String?,
    @Json(name = "supporting_image_hits") val supportingImageHits: Int
)

data class RecognitionResponseDto(
    val matched: Boolean,
    @Json(name = "match_level") val matchLevel: String,
    @Json(name = "best_match") val bestMatch: ArtifactMatchDto?,
    @Json(name = "other_matches") val otherMatches: List<ArtifactMatchDto> = emptyList(),
    val message: String
)
