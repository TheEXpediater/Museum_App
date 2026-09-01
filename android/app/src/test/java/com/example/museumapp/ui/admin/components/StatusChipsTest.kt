package com.example.museumapp.ui.admin.components

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusChipsTest {
    @Test
    fun artifactAiStatusLabelsUseAiLibraryTerminology() {
        assertEquals("Not in AI Library", artifactAiStatusLabel("not_indexed"))
        assertEquals("Feeding to AI Library", artifactAiStatusLabel("pending"))
        assertEquals("In AI Library", artifactAiStatusLabel("indexed"))
        assertEquals("Needs AI Update", artifactAiStatusLabel("stale"))
        assertEquals("Needs AI Update", artifactAiStatusLabel("partial"))
        assertEquals("AI Library Failed", artifactAiStatusLabel("failed"))
    }
}
