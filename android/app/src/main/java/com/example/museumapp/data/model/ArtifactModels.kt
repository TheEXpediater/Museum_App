package com.example.museumapp.data.model

import com.squareup.moshi.Json

data class ArtifactDto(
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
    @Json(name = "image_paths") val imagePaths: List<String>,
    @Json(name = "image_urls") val imageUrls: List<String>,
    @Json(name = "primary_image_path") val primaryImagePath: String?,
    @Json(name = "primary_image_url") val primaryImageUrl: String?,
    @Json(name = "created_by") val createdBy: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String
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
