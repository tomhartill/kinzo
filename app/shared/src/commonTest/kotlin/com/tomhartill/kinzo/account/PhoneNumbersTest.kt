package com.tomhartill.kinzo.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneNumbersTest {
    private val uk = PhoneNumbers.default
    private val us = PhoneNumbers.countries.first { it.iso == "US" }

    @Test
    fun addsTheCountryCodeAndDropsTheTrunkZero() {
        assertEquals("+447700900123", PhoneNumbers.international("07700 900123", uk))
        assertEquals("+12025550123", PhoneNumbers.international("(202) 555-0123", us))
    }

    @Test
    fun keepsANumberThatAlreadyHasOne() {
        assertEquals("+447700900123", PhoneNumbers.international("+44 7700 900123", us))
        assertEquals("+447700900123", PhoneNumbers.international("0044 7700 900123", us))
    }

    @Test
    fun checksTheLength() {
        assertNull(PhoneNumbers.international("  ", uk))
        assertFalse(PhoneNumbers.looksComplete(PhoneNumbers.international("0770", uk)))
        assertTrue(PhoneNumbers.looksComplete(PhoneNumbers.international("07700900123", uk)))
    }
}
