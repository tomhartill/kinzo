package com.tomhartill.kinzo.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PkceTest {
    @Test
    fun matchesTheRfcExample() {
        // RFC 7636, appendix B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun hashesLikeSha256() {
        val hex = Sha256.digest("abc".encodeToByteArray()).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex)
        // Longer than one block.
        val long = Sha256.digest(ByteArray(1000) { 'a'.code.toByte() }).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        assertEquals("41edece42d63e8d9bf515a9ba6932e1c20cbc9f5a5d134645adb5db1b9737ea3", long)
    }

    @Test
    fun verifiersAreLongEnoughAndDifferent() {
        val a = Pkce.newVerifier()
        assertTrue(a.length in 43..128, "RFC 7636 wants 43 to 128 characters, got ${a.length}")
        assertNotEquals(a, Pkce.newVerifier())
    }
}
