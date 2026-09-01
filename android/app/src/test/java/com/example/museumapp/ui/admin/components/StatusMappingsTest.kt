package com.example.museumapp.ui.admin.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StatusMappingsTest {
    @Test
    fun mapsMatchLevelLabels() {
        assertEquals("Strong", matchLevelLabel("strong"))
        assertEquals("Possible", matchLevelLabel("possible"))
        assertEquals("No match", matchLevelLabel("no_match"))
    }

    @Test
    fun mapsArtifactAiStatusLabels() {
        assertEquals("In AI Library", artifactAiStatusLabel("indexed"))
        assertEquals("Needs AI Update", artifactAiStatusLabel("partial"))
        assertEquals("Needs AI Update", artifactAiStatusLabel("stale"))
        assertEquals("AI Library Failed", artifactAiStatusLabel("failed"))
        assertEquals("Not in AI Library", artifactAiStatusLabel(null))
    }

    @Test
    fun mapsAiHealthSemantics() {
        assertEquals("Enabled", healthStatusLabel("enabled"))
        assertEquals("Disabled", healthStatusLabel("disabled"))
        assertEquals("Not installed", healthStatusLabel("not_installed"))
        assertEquals("Ready to load", healthStatusLabel("idle"))
        assertEquals("Ready to load", healthStatusLabel("not_loaded"))
        assertEquals("Loading", healthStatusLabel("loading"))
        assertEquals("Loaded", healthStatusLabel("loaded"))
        assertEquals("Load failed", healthStatusLabel("failed"))
        assertEquals(StatusTone.Neutral, healthStatusTone("not_loaded"))
        assertNotEquals(StatusTone.Error, healthStatusTone("not_loaded"))
    }
}
