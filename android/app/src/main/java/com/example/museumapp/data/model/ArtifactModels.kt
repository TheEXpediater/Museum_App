package com.example.museumapp.data.model

import com.squareup.moshi.Json

data class ArtifactDto(
    val id: String,
    @Json(name = "artifact_code") val artifactCode: String,
    val name: String,
    val description: String,
    val category: String,
    val status: String = "published",
    val origin: String?,
    @Json(name = "historical_period") val historicalPeriod: String?,
    val material: String?,
    val dimensions: String?,
    val condition: String?,
    @Json(name = "custom_fields") val customFields: List<ArtifactCustomFieldDto> = emptyList(),
    @Json(name = "metadata_sections") val metadataSections: List<ArtifactMetadataSectionDto> = emptyList(),
    @Json(name = "image_paths") val imagePaths: List<String>,
    @Json(name = "image_urls") val imageUrls: List<String>,
    @Json(name = "primary_image_path") val primaryImagePath: String?,
    @Json(name = "primary_image_url") val primaryImageUrl: String?,
    @Json(name = "primary_image_needs_review") val primaryImageNeedsReview: Boolean = false,
    @Json(name = "visitor_gallery_image_paths") val visitorGalleryImagePaths: List<String> = emptyList(),
    @Json(name = "visitor_gallery_image_urls") val visitorGalleryImageUrls: List<String> = emptyList(),
    @Json(name = "visitor_gallery_configured") val visitorGalleryConfigured: Boolean = false,
    @Json(name = "ai_index_status") val aiIndexStatus: String? = null,
    @Json(name = "ai_indexed_image_count") val aiIndexedImageCount: Int? = null,
    @Json(name = "ai_indexed_at") val aiIndexedAt: String? = null,
    @Json(name = "ai_index_error") val aiIndexError: String? = null,
    @Json(name = "created_by") val createdBy: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String
)

data class ArtifactCustomFieldDto(
    val id: String,
    val label: String,
    val value: String,
    val unit: String? = null,
    val type: String
)

data class ArtifactMetadataSectionDto(
    val id: String,
    val title: String,
    val order: Int = 0,
    val fields: List<ArtifactMetadataFieldDto> = emptyList()
)

data class ArtifactMetadataFieldDto(
    val id: String,
    val label: String = "",
    val value: String = "",
    val type: String = "text",
    val unit: String? = null,
    val order: Int = 0
)

data class ArtifactListResponse(
    val items: List<ArtifactDto>,
    val page: Int,
    @Json(name = "page_size") val pageSize: Int,
    @Json(name = "total_items") val totalItems: Int,
    @Json(name = "total_pages") val totalPages: Int
)

data class DeleteResponse(
    val message: String
)

data class PrimaryImageRequest(
    @Json(name = "image_path") val imagePath: String
)

data class ArtifactCategoryDto(
    val id: String,
    val name: String,
    @Json(name = "normalized_name") val normalizedName: String,
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "artifact_count") val artifactCount: Int = 0,
    @Json(name = "suggested_fields") val suggestedFields: List<ArtifactCategorySuggestedFieldDto> = emptyList(),
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String
)

data class ArtifactCategorySuggestedFieldDto(
    val label: String,
    val type: String = "text",
    val unit: String? = null
)

data class ArtifactCategoryCreateRequest(
    val name: String,
    @Json(name = "suggested_fields") val suggestedFields: List<ArtifactCategorySuggestedFieldDto> = emptyList()
)

data class ArtifactCategoryUpdateRequest(
    val name: String? = null,
    @Json(name = "is_active") val isActive: Boolean? = null,
    @Json(name = "suggested_fields") val suggestedFields: List<ArtifactCategorySuggestedFieldDto>? = null
)
