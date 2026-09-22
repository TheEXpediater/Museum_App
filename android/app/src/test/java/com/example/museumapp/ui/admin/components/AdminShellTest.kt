package com.example.museumapp.ui.admin.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AdminShellTest {
    @Test
    fun permanentNavigationContainsExactlyFiveItems() {
        assertEquals(
            listOf("Dashboard", "Artifacts", "Recognize", "Accounts", "Settings"),
            AdminTopLevelDestinations.map { it.label }
        )
        assertEquals(5, AdminTopLevelDestinations.size)
    }
}
