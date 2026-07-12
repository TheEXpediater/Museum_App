package com.example.museumapp.data.repository

data class ArtifactFormData(
    val artifactCode: String,
    val name: String,
    val description: String,
    val category: String,
    val origin: String?,
    val historicalPeriod: String?,
    val material: String?,
    val dimensions: String?,
    val condition: String?,
    val removeImagePaths: List<String> = emptyList(),
    val replaceImages: Boolean = false,
    val primaryImagePath: String? = null
)
