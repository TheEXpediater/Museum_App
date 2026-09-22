package com.example.museumapp.ui.visitor.artifacts

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class VisitorArtifactsScreenTest {
    @Test
    fun compactPhoneWidthsUseOneColumn() {
        assertEquals(1, artifactGridColumnCount(360.dp))
        assertEquals(1, artifactGridColumnCount(393.dp))
        assertEquals(1, artifactGridColumnCount(411.dp))
        assertEquals(1, artifactGridColumnCount(480.dp))
        assertEquals(1, artifactGridColumnCount(599.dp))
    }

    @Test
    fun tabletAndLargerWidthsUseTwoColumns() {
        assertEquals(2, artifactGridColumnCount(600.dp))
        assertEquals(2, artifactGridColumnCount(720.dp))
        assertEquals(2, artifactGridColumnCount(800.dp))
    }
}
