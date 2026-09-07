package com.example.museumapp.narration

import com.example.museumapp.data.model.PublicArtifactDto
import com.example.museumapp.ui.visitor.artifacts.visitorMetadataSections
import com.example.museumapp.ui.visitor.components.hasMuseumContent

/**
 * Builds spoken narration text for one artifact: name, description, then the same
 * historical-details / physical-details / metadata_sections / custom_fields ("Additional
 * Information") ordering the visitor details screen already renders via
 * [visitorMetadataSections] -- reused here rather than re-derived, so there is only ever one
 * definition of "how sections are built".
 *
 * Any field that fails [hasMuseumContent] (blank, or a "To be configured." placeholder) is
 * skipped, matching what the screen already hides from view. [PublicArtifactDto] carries no id,
 * URL, image filename, or AI/status field, so reading only the fields below naturally excludes
 * them from narration.
 */
object ArtifactNarrationTextBuilder {
    fun build(artifact: PublicArtifactDto): String {
        val parts = mutableListOf<String>()

        if (artifact.name.hasMuseumContent()) parts += artifact.name.trim()
        if (artifact.description.hasMuseumContent()) parts += artifact.description.trim()

        visitorMetadataSections(artifact).forEach { section ->
            if (section.title.hasMuseumContent()) parts += section.title.trim()
            section.fields.forEach { field ->
                if (field.value.hasMuseumContent()) {
                    parts += formatField(field.label, field.value, field.unit)
                }
            }
        }

        return parts.joinToString(separator = "\n\n")
    }

    private fun formatField(label: String, value: String, unit: String?): String {
        val valueWithUnit = listOfNotNull(value.trim(), unit?.takeIf { it.hasMuseumContent() }?.trim())
            .joinToString(" ")
        return if (label.hasMuseumContent()) "${label.trim()}: $valueWithUnit" else valueWithUnit
    }
}
