package com.tomhartill.kinzo.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinksTest {
    @Test
    fun recognisesSignInCallbacks() {
        val url = "kinzo://auth/callback?code=abc"
        assertEquals(DeepLinks.Link.AuthCallback(url), DeepLinks.parse(url))
    }

    @Test
    fun recognisesInvites() {
        assertEquals(DeepLinks.Link.Invite("aB3dE5fG"), DeepLinks.parse("kinzo://invite/aB3dE5fG"))
        assertEquals(DeepLinks.Link.Invite("aB3dE5fG"), DeepLinks.parse("https://api.kinzo.app/i/aB3dE5fG"))
        assertEquals(DeepLinks.Link.Invite("aB3dE5fG"), DeepLinks.parse("kinzo://invite/aB3dE5fG/"))
    }

    @Test
    fun ignoresEverythingElse() {
        assertNull(DeepLinks.parse("kinzo://invite/"))
        assertNull(DeepLinks.parse("kinzo://invite/has spaces"))
        assertNull(DeepLinks.parse("https://example.com/other/aB3dE5fG"))
        assertNull(DeepLinks.parse("http://example.com/i/aB3dE5fG"))
        assertNull(DeepLinks.parse("not a link"))
    }

    @Test
    fun decodesQueryParameters() {
        val params = DeepLinks.queryParameters("kinzo://auth/callback?code=a%2Fb%3D&error=&note=caf%C3%A9+bar#frag")
        assertEquals(mapOf("code" to "a/b=", "error" to "", "note" to "café bar"), params)
        assertEquals(emptyMap(), DeepLinks.queryParameters("kinzo://auth/callback"))
    }

    @Test
    fun anInviteWaitsUntilShown() {
        DeepLinks.handle("kinzo://invite/aB3dE5fG")
        assertEquals("aB3dE5fG", DeepLinks.pendingInvite.value)
        DeepLinks.consumeInvite()
        assertNull(DeepLinks.pendingInvite.value)
    }
}
