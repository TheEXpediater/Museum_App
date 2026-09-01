package com.example.museumapp.data.model

import com.squareup.moshi.Json

data class PublicArtifactDto(
    val id: String,
    @Json(name = "artifact_code") val artifactCode: String,
    val name: String,
    val description: String,
    val category: String,
    val origin: String? = null,
    @Json(name = "historical_period") val historicalPeriod: String? = null,
    val material: String? = null,
    val dimensions: String? = null,
    val condition: String? = null,
    @Json(name = "custom_fields") val customFields: List<PublicArtifactCustomFieldDto> = emptyList(),
    @Json(name = "metadata_sections") val metadataSections: List<PublicArtifactMetadataSectionDto> = emptyList(),
    @Json(name = "image_urls") val imageUrls: List<String> = emptyList(),
    @Json(name = "primary_image_url") val primaryImageUrl: String? = null
)

data class PublicArtifactCustomFieldDto(
    val label: String,
    val value: String,
    val unit: String? = null,
    val type: String
)

data class PublicArtifactMetadataSectionDto(
    val title: String,
    val fields: List<PublicArtifactMetadataFieldDto> = emptyList()
)

data class PublicArtifactMetadataFieldDto(
    val label: String,
    val value: String,
    val unit: String? = null,
    val type: String = "text"
)

data class PublicArtifactListResponseDto(
    val items: List<PublicArtifactDto>,
    val page: Int,
    @Json(name = "page_size") val pageSize: Int,
    @Json(name = "total_items") val totalItems: Int,
    @Json(name = "total_pages") val totalPages: Int
)

data class NewsDto(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
    @Json(name = "cover_image_url") val coverImageUrl: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null
)

data class AnnouncementDto(
    val id: String,
    val title: String,
    val message: String,
    val priority: String = "normal",
    @Json(name = "starts_at") val startsAt: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null
)

data class ArticleDto(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
    @Json(name = "cover_image_url") val coverImageUrl: String? = null,
    val category: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null
)

data class MuseumInformationDto(
    @Json(name = "museum_name") val museumName: String = "To be configured.",
    val description: String = "To be configured.",
    @Json(name = "campus_location") val campusLocation: String = "To be configured.",
    @Json(name = "opening_hours") val openingHours: String = "To be configured.",
    @Json(name = "contact_email") val contactEmail: String = "To be configured.",
    @Json(name = "contact_phone") val contactPhone: String = "To be configured.",
    @Json(name = "visitor_guidelines") val visitorGuidelines: String = "To be configured.",
    @Json(name = "accessibility_information") val accessibilityInformation: String = "To be configured.",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

data class ProgramDto(
    val id: String,
    val name: String
)

data class PublicHomeResponseDto(
    @Json(name = "latest_news") val latestNews: List<NewsDto> = emptyList(),
    val announcements: List<AnnouncementDto> = emptyList(),
    @Json(name = "featured_artifacts") val featuredArtifacts: List<PublicArtifactDto> = emptyList(),
    @Json(name = "museum_information") val museumInformation: MuseumInformationDto = MuseumInformationDto()
)
