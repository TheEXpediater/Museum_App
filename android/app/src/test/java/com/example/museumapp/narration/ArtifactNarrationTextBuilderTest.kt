package com.example.museumapp.narration

import com.example.museumapp.data.model.PublicArtifactCustomFieldDto
import com.example.museumapp.data.model.PublicArtifactDto
import com.example.museumapp.data.model.PublicArtifactMetadataFieldDto
import com.example.museumapp.data.model.PublicArtifactMetadataSectionDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactNarrationTextBuilderTest {
    @Test
    fun ordersNameDescriptionThenDerivedHistoricalAndPhysicalFields() {
        val artifact = PublicArtifactDto(
            id = "1",
            artifactCode = "ART-1",
            name = "Wooden Plow",
            description = "A traditional farming tool.",
            category = "Farm Tools",
            origin = "Pampanga",
            historicalPeriod = "American Colonial Period",
            material = "Narra wood",
            dimensions = "120cm x 40cm",
            condition = "Good"
        )

        val text = ArtifactNarrationTextBuilder.build(artifact)

        val nameIndex = text.indexOf("Wooden Plow")
        val descriptionIndex = text.indexOf("A traditional farming tool.")
        val originIndex = text.indexOf("Pampanga")
        val periodIndex = text.indexOf("American Colonial Period")
        val materialIndex = text.indexOf("Narra wood")
        val dimensionsIndex = text.indexOf("120cm x 40cm")
        val conditionIndex = text.indexOf("Good")

        listOf(nameIndex, descriptionIndex, originIndex, periodIndex, materialIndex, dimensionsIndex, conditionIndex)
            .forEach { assertTrue("expected field to be present, index was $it", it >= 0) }
        assertTrue(nameIndex < descriptionIndex)
        assertTrue(descriptionIndex < originIndex)
        assertTrue(originIndex < periodIndex)
        assertTrue(periodIndex < materialIndex)
        assertTrue(materialIndex < dimensionsIndex)
        assertTrue(dimensionsIndex < conditionIndex)
    }

    @Test
    fun excludesBlankAndPlaceholderFields() {
        val artifact = PublicArtifactDto(
            id = "1",
            artifactCode = "ART-1",
            name = "Wooden Plow",
            description = "A traditional farming tool.",
            category = "Farm Tools",
            origin = "",
            historicalPeriod = "To be configured.",
            material = null,
            dimensions = "   ",
            condition = "Good"
        )

        val text = ArtifactNarrationTextBuilder.build(artifact)

        assertFalse(text.contains("To be configured."))
        assertTrue(text.contains("Good"))
        // Both Historical Details fields (origin, historical period) were blank/placeholder, so
        // the whole section is dropped -- matching what the visitor screen itself would show.
        assertFalse(text.contains("Historical Details"))
    }

    @Test
    fun includesBackendSuppliedMetadataSectionsInOrder() {
        val artifact = PublicArtifactDto(
            id = "1",
            artifactCode = "ART-1",
            name = "Ceremonial Jar",
            description = "Used in local rituals.",
            category = "Ceramics",
            metadataSections = listOf(
                PublicArtifactMetadataSectionDto(
                    title = "Provenance",
                    fields = listOf(
                        PublicArtifactMetadataFieldDto(label = "Acquired From", value = "Local donor"),
                        PublicArtifactMetadataFieldDto(label = "Acquisition Year", value = "1998")
                    )
                )
            ),
            customFields = listOf(
                PublicArtifactCustomFieldDto(label = "Local Name", value = "Banga", type = "text")
            )
        )

        val text = ArtifactNarrationTextBuilder.build(artifact)

        val provenanceIndex = text.indexOf("Provenance")
        val acquiredFromIndex = text.indexOf("Acquired From: Local donor")
        val acquisitionYearIndex = text.indexOf("Acquisition Year: 1998")

        assertTrue(provenanceIndex >= 0)
        assertTrue(acquiredFromIndex > provenanceIndex)
        assertTrue(acquisitionYearIndex > acquiredFromIndex)
    }

    @Test
    fun includesCustomFieldsAsAdditionalInformationWhenNoBackendSectionsExist() {
        val artifact = PublicArtifactDto(
            id = "1",
            artifactCode = "ART-1",
            name = "Ceremonial Jar",
            description = "Used in local rituals.",
            category = "Ceramics",
            customFields = listOf(
                PublicArtifactCustomFieldDto(label = "Local Name", value = "Banga", type = "text")
            )
        )

        val text = ArtifactNarrationTextBuilder.build(artifact)

        assertTrue(text.contains("Additional Information"))
        assertTrue(text.contains("Local Name: Banga"))
    }

    @Test
    fun neverIncludesIdsOrImageUrls() {
        val artifact = PublicArtifactDto(
            id = "artifact-secret-id",
            artifactCode = "ART-1",
            name = "Jar",
            description = "A jar.",
            category = "Ceramics",
            imageUrls = listOf("http://host/secret-image.jpg"),
            primaryImageUrl = "http://host/secret-image.jpg"
        )

        val text = ArtifactNarrationTextBuilder.build(artifact)

        assertFalse(text.contains("artifact-secret-id"))
        assertFalse(text.contains("http://"))
    }
}
