package com.example.museumapp.data.model

object ArtifactValidationLimits {
    const val ArtifactCode = 50
    const val ArtifactName = 255
    const val CategoryName = 150
    const val MetadataLabel = 150
    const val MetadataSectionTitle = 150
    const val ShortMetadataValue = 1000
    const val LongMetadataValue = 10000
    const val Description = 10000
    const val MetadataUnit = 32
    const val VisitorAdditionalImages = 5
}

object ArtifactMetadataSectionIds {
    const val HistoricalDetails = "historical_details"
    const val PhysicalDetails = "physical_details"

    val SystemSections = setOf(HistoricalDetails, PhysicalDetails)
}
