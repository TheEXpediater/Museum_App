package com.example.museumapp.data.repository

import com.example.museumapp.data.model.ArtifactCustomFieldDto

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
    val removeImagePaths: List<String> = emptyList(),
    val replaceImages: Boolean = false,
    val primaryImagePath: String? = null,
    val primaryImageIndex: Int? = null
)
