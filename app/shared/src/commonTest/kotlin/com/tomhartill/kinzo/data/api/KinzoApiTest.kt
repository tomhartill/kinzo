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
            respond("""{"users":[{"id":"u1","username":"sam","display_name":"Sam","following":"requested","follows_you":true,"extra":1}]}""", HttpStatusCode.OK, json)
        }
        val api = KinzoApi("https://kinzo.test", token = { "kz_abc" }, onSignedOut = {}, engine = engine)

        val people = api.searchPeople("sam")

        assertEquals("Bearer kz_abc", seenAuth)
        assertEquals(listOf(Person("u1", "sam", "Sam", following = Following.Requested, followsYou = true)), people)
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
    fun aKnownEmailGetsItsCode() = runTest {
        var body = ""
        val engine = MockEngine { request ->
            body = (request.body as io.ktor.http.content.TextContent).text
            respond(
                """{"next":"code","challenge":{"challenge_id":"c1","phone_hint":"•••• 123","channel":"whatsapp","expires_in":600,"resend_in":30}}""",
                HttpStatusCode.OK,
                json,
            )
        }
        val api = KinzoApi("https://kinzo.test", token = { null }, onSignedOut = {}, engine = engine)

        val start = api.startPhoneSignIn("sam@example.com", null, CodeChannel.WhatsApp)

        assertEquals("""{"email":"sam@example.com","channel":"whatsapp"}""", body)
        assertEquals(PhoneStart.Next.Code, start.next)
        assertEquals(CodeChallenge("c1", "•••• 123", CodeChannel.WhatsApp, 600, 30), start.challenge)
    }

    @Test
    fun aWrongCodeSaysHowManyTriesAreLeft() = runTest {
        val engine = MockEngine {
            respond("""{"error":{"code":"wrong_code","message":"That code isn't right."},"attempts_left":3}""", HttpStatusCode.BadRequest, json)
        }
        val api = KinzoApi("https://kinzo.test", token = { null }, onSignedOut = {}, engine = engine)

        val error = assertFailsWith<ApiException> { api.verifyPhoneSignIn("c1", "123456") }

        assertEquals("wrong_code", error.code)
        assertEquals(3, error.attemptsLeft)
    }

    @Test
    fun saysWhenNotSetUp() = runTest {
        val api = KinzoApi("", token = { "kz_abc" }, onSignedOut = {}, engine = MockEngine { error("no request expected") })
        assertEquals(ApiException.NOT_CONFIGURED, assertFailsWith<ApiException> { api.follows() }.code)
    }

    @Test
    fun buildsTheSignInLink() {
        val api = KinzoApi("https://kinzo.test/", token = { null }, onSignedOut = {}, engine = MockEngine { error("unused") })
        assertEquals(
            "https://kinzo.test/v1/auth/google/start?redirect_uri=kinzo%3A%2F%2Fauth%2Fcallback&code_challenge=abc&code_challenge_method=S256",
            api.signInStartUrl("google", "kinzo://auth/callback", "abc"),
        )
    }

    @Test
    fun aMachineScanSendsThePhotoAsJpeg() = runTest {
        var contentType: String? = null
        var path = ""
        var sent = 0
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            contentType = request.body.contentType?.toString()
            sent = (request.body as io.ktor.http.content.ByteArrayContent).bytes().size
            respond(
                """{"matches":[{"machine_id":"m1","confidence":"high"}],"exercise_id":"ex-incline","description":"An incline press."}""",
                HttpStatusCode.OK,
                json,
            )
        }
        val api = KinzoApi("https://kinzo.test", token = { "kz_abc" }, onSignedOut = {}, engine = engine)

        val scan = api.scanMachine(ByteArray(10))

        assertEquals("/v1/scan/machine", path)
        assertEquals("image/jpeg", contentType)
        assertEquals(10, sent)
        assertEquals(listOf(MachineMatch("m1", "high")), scan.matches)
        assertEquals("ex-incline", scan.exerciseId)
    }
}
