package com.tomhartill.kinzo.data.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KinzoApiTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun sendsTheSessionAndReadsTheAnswer() = runTest {
        var seenAuth: String? = null
        val engine = MockEngine { request ->
            seenAuth = request.headers[HttpHeaders.Authorization]
            respond("""{"users":[{"id":"u1","username":"sam","display_name":"Sam","relationship":"request_received","extra":1}]}""", HttpStatusCode.OK, json)
        }
        val api = KinzoApi("https://kinzo.test", token = { "kz_abc" }, onSignedOut = {}, engine = engine)

        val people = api.searchPeople("sam")

        assertEquals("Bearer kz_abc", seenAuth)
        assertEquals(listOf(Person("u1", "sam", "Sam", null, Relationship.RequestReceived)), people)
    }

    @Test
    fun anEndedSessionSignsOutLocally() = runTest {
        var signedOut = false
        val engine = MockEngine {
            respond("""{"error":{"code":"signed_out","message":"Sign in again."}}""", HttpStatusCode.Unauthorized, json)
        }
        val api = KinzoApi("https://kinzo.test", token = { "kz_old" }, onSignedOut = { signedOut = true }, engine = engine)

        val error = assertFailsWith<ApiException> { api.me() }

        assertEquals(ApiException.SIGNED_OUT, error.code)
        assertEquals("Sign in again.", error.message)
        assertTrue(signedOut)
    }

    @Test
    fun saysWhenNotSetUp() = runTest {
        val api = KinzoApi("", token = { "kz_abc" }, onSignedOut = {}, engine = MockEngine { error("no request expected") })
        assertEquals(ApiException.NOT_CONFIGURED, assertFailsWith<ApiException> { api.friends() }.code)
    }

    @Test
    fun buildsTheSignInLink() {
        val api = KinzoApi("https://kinzo.test/", token = { null }, onSignedOut = {}, engine = MockEngine { error("unused") })
        assertEquals(
            "https://kinzo.test/v1/auth/google/start?redirect_uri=kinzo%3A%2F%2Fauth%2Fcallback&code_challenge=abc&code_challenge_method=S256",
            api.signInStartUrl("google", "kinzo://auth/callback", "abc"),
        )
    }
}
