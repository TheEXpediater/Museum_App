package com.example.museumapp.ui.admin.artifactdetails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPreviewPhotoSelectionTest {
    @Test
    fun effectiveMaxUsesReportedValueWhenPositive() {
        assertEquals(4, AiPreviewPhotoSelection.effectiveMax(4))
        assertEquals(1, AiPreviewPhotoSelection.effectiveMax(1))
    }

    @Test
    fun effectiveMaxFallsBackToDefaultWhenMissingOrInvalid() {
        assertEquals(4, AiPreviewPhotoSelection.effectiveMax(null))
        assertEquals(4, AiPreviewPhotoSelection.effectiveMax(0))
        assertEquals(4, AiPreviewPhotoSelection.effectiveMax(-1))
        assertEquals(7, AiPreviewPhotoSelection.effectiveMax(null, default = 7))
    }

    @Test
    fun isOverLimitTrueOnlyWhenSelectionExceedsMax() {
        assertFalse(AiPreviewPhotoSelection.isOverLimit(selectedCount = 4, maxImages = 4))
        assertTrue(AiPreviewPhotoSelection.isOverLimit(selectedCount = 5, maxImages = 4))
        assertFalse(AiPreviewPhotoSelection.isOverLimit(selectedCount = 0, maxImages = 4))
    }

    @Test
    fun canSubmitRequiresAtLeastOneAndAtMostMax() {
        assertFalse(AiPreviewPhotoSelection.canSubmit(selectedCount = 0, maxImages = 4))
        assertTrue(AiPreviewPhotoSelection.canSubmit(selectedCount = 1, maxImages = 4))
        assertTrue(AiPreviewPhotoSelection.canSubmit(selectedCount = 4, maxImages = 4))
        assertFalse(AiPreviewPhotoSelection.canSubmit(selectedCount = 5, maxImages = 4))
    }

    @Test
    fun selectAllThenOverLimitExplainsRatherThanSilentlyTruncating() {
        // Mirrors the "Select All" flow: selecting every eligible photo (e.g. 30 reconstruction
        // images) when the provider only accepts 4 must surface as "over limit, explain why" -
        // not silently cap the selection or submit only the first 4.
        val selectedFromSelectAll = 30
        val providerMax = 4
        assertTrue(AiPreviewPhotoSelection.isOverLimit(selectedFromSelectAll, providerMax))
        assertFalse(AiPreviewPhotoSelection.canSubmit(selectedFromSelectAll, providerMax))
    }
}
