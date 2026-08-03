package com.example.museumapp.ui.admin.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AdminShellTest {
    @Test
    fun permanentNavigationContainsExactlyFourItems() {
        assertEquals(
            listOf("Dashboard", "Artifacts", "Recognize", "Settings"),
            AdminTopLevelDestinations.map { it.label }
        )
        assertEquals(4, AdminTopLevelDestinations.size)
    }
}
