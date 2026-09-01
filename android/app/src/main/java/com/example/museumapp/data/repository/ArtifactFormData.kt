package com.example.museumapp.data.repository

import com.example.museumapp.data.model.ArtifactCustomFieldDto
import com.example.museumapp.data.model.ArtifactMetadataSectionDto

data class ArtifactFormData(
    val artifactCode: String,
    val name: String,
    val description: String,
    val category: String,
    val status: String = "draft",
    val origin: String?,
    val historicalPeriod: String?,
    val material: String?,
    val dimensions: String?,
    val condition: String?,
    val customFields: List<ArtifactCustomFieldDto> = emptyList(),
    val metadataSections: List<ArtifactMetadataSectionDto> = emptyList(),
    val visitorGalleryImagePaths: List<String> = emptyList(),
    val visitorGalleryConfigured: Boolean = false,
    val removeImagePaths: List<String> = emptyList(),
    val replaceImages: Boolean = false,
    val primaryImagePath: String? = null,
    val primaryImageIndex: Int? = null
)
