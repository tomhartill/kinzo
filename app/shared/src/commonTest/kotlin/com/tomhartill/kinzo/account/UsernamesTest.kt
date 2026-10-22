package com.tomhartill.kinzo.account

import com.tomhartill.kinzo.ui.components.initials
import com.tomhartill.kinzo.ui.screens.account.Usernames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsernamesTest {
    @Test
    fun normalisesWhatPeopleType() {
        assertEquals("tom.h", Usernames.normalize("  @Tom.H "))
    }

    @Test
    fun checksTheShape() {
        assertTrue(Usernames.isValid("tom_h.92"))
        assertFalse(Usernames.isValid("to"))
        assertFalse(Usernames.isValid("tom hartill"))
        assertFalse(Usernames.isValid("a".repeat(31)))
    }

    @Test
    fun avatarInitials() {
        assertEquals("TH", initials("Tom Hartill"))
        assertEquals("T", initials("@tom.hartill"))
        assertEquals("?", initials("  "))
    }
}
