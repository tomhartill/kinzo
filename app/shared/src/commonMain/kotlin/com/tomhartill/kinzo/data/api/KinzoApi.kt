package com.tomhartill.kinzo.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * A failed API call, with the server's error code and a sentence to show the person. A wrong
 * sign-in code also says how many tries are left; a sending limit says when to try again.
 */
class ApiException(
    val code: String,
    override val message: String,
    val status: Int = 0,
    val attemptsLeft: Int? = null,
    val retryIn: Int? = null,
) : Exception(message) {
    companion object {
        const val OFFLINE = "offline"
        const val NOT_CONFIGURED = "not_configured"
        const val SIGNED_OUT = "signed_out"
    }
}

/**
 * The Kinzo API. [baseUrl] is blank in builds that haven't been pointed at a server, and then
 * every call fails with [ApiException.NOT_CONFIGURED]. [token] supplies the session token;
 * [onSignedOut] runs when the server says that session has ended.
 */
class KinzoApi(
    private val baseUrl: String,
    private val token: suspend () -> String?,
    private val onSignedOut: suspend () -> Unit,
    engine: HttpClientEngine? = null,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    private val client: HttpClient = (if (engine != null) HttpClient(engine) { config() } else HttpClient { config() })

    private fun io.ktor.client.HttpClientConfig<*>.config() {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Sign-in

    internal suspend fun providers(): AuthProviders = get("/v1/auth/providers", auth = false)

    /** The URL that starts a browser sign-in with [provider] ("google" or "facebook"). */
    fun signInStartUrl(provider: String, redirectUri: String, codeChallenge: String): String =
        URLBuilder(baseUrl.trimEnd('/')).apply {
            appendPathSegments("v1", "auth", provider, "start")
            parameters.append("redirect_uri", redirectUri)
            parameters.append("code_challenge", codeChallenge)
            parameters.append("code_challenge_method", "S256")
        }.buildString()

    internal suspend fun exchangeCode(code: String, verifier: String): SignInResponse =
        post("/v1/auth/exchange", CodeExchangeRequest(code, verifier), auth = false)

    internal suspend fun signInWithGoogleIdToken(idToken: String): SignInResponse =
        post("/v1/auth/google/id-token", IdTokenRequest(idToken), auth = false)

    /** Starts an email and phone code sign-in, or resends the code. [phone] only for a new email. */
    internal suspend fun startPhoneSignIn(email: String, phone: String?, channel: CodeChannel): PhoneStart =
        post("/v1/auth/phone/start", PhoneStartRequest(email, phone, channel), auth = false)

    internal suspend fun verifyPhoneSignIn(challengeId: String, code: String): SignInResponse =
        post("/v1/auth/phone/verify", CodeAttempt(challengeId, code), auth = false)

    suspend fun signOut() = send(HttpMethod.Post, "/v1/auth/sign-out")

    // Account

    suspend fun me(): Account = get("/v1/me")

    suspend fun updateProfile(username: String? = null, displayName: String? = null): Account =
        call(HttpMethod.Patch, "/v1/me", encode(ProfileUpdate(username, displayName)), auth = true)

    suspend fun deleteAccount() = send(HttpMethod.Delete, "/v1/me")

    /** Sends a code to add or change the signed-in account's phone. */
    suspend fun startAddPhone(phone: String, channel: CodeChannel): CodeChallenge =
        post("/v1/me/phone/start", AddPhoneRequest(phone, channel))

    suspend fun verifyAddPhone(challengeId: String, code: String): Account =
        post("/v1/me/phone/verify", CodeAttempt(challengeId, code))

    suspend fun removePhone(): Account = call(HttpMethod.Delete, "/v1/me/phone", null, auth = true)

    suspend fun checkUsername(username: String): UsernameCheck = get("/v1/usernames/${username.encodeURLPathPart()}")

    // Friends

    suspend fun searchPeople(query: String): List<Person> =
        get<PeopleResponse>("/v1/users/search?q=${query.encodeURLQueryComponent()}").users

    suspend fun friends(): FriendLists = get("/v1/friends")

    suspend fun sendFriendRequest(userId: String): Relationship =
        post<UserIdRequest, RelationshipResponse>("/v1/friends/requests", UserIdRequest(userId)).relationship

    suspend fun acceptFriendRequest(userId: String) = send(HttpMethod.Post, "/v1/friends/requests/$userId/accept")

    /** Declines a request from [userId], or cancels one sent to them. */
    suspend fun deleteFriendRequest(userId: String) = send(HttpMethod.Delete, "/v1/friends/requests/$userId")

    suspend fun removeFriend(userId: String) = send(HttpMethod.Delete, "/v1/friends/$userId")

    suspend fun blocked(): List<Person> = get<PeopleResponse>("/v1/blocks").users

    suspend fun block(userId: String) = send(HttpMethod.Post, "/v1/blocks", encode(UserIdRequest(userId)))

    suspend fun unblock(userId: String) = send(HttpMethod.Delete, "/v1/blocks/$userId")

    suspend fun myInvite(): Invite = get("/v1/invites/mine")

    suspend fun resetInvite(): Invite = call(HttpMethod.Post, "/v1/invites/mine/reset", null, auth = true)

    internal suspend fun invite(code: String): InvitePreview = get("/v1/invites/${code.encodeURLPathPart()}")

    suspend fun acceptInvite(code: String): Person =
        call(HttpMethod.Post, "/v1/invites/${code.encodeURLPathPart()}/accept", null, auth = true)

    // Machine scan

    /** Asks which machine a photo shows. [jpeg] should be about 1280 pixels on the long edge. */
    suspend fun scanMachine(jpeg: ByteArray): MachineScan = decode(
        serializer(),
        requestText(
            HttpMethod.Post,
            "/v1/scan/machine",
            ByteArrayContent(jpeg, ContentType.Image.JPEG),
            auth = true,
            // The vision model can take a while on a slow connection.
            timeoutMillis = 60_000,
        ),
    )

    // Plumbing

    private suspend inline fun <reified T> get(path: String, auth: Boolean = true): T =
        call(HttpMethod.Get, path, null, auth)

    private suspend inline fun <reified B, reified T> post(path: String, body: B, auth: Boolean = true): T =
        call(HttpMethod.Post, path, encode(body), auth)

    private suspend inline fun <reified T> call(method: HttpMethod, path: String, body: String?, auth: Boolean): T =
        decode(serializer(), requestText(method, path, body, auth))

    /** A call with no response body. */
    private suspend fun send(method: HttpMethod, path: String, body: String? = null) {
        requestText(method, path, body, auth = true)
    }

    private inline fun <reified B> encode(body: B): String = json.encodeToString(serializer<B>(), body)

    private fun <T> decode(serializer: KSerializer<T>, text: String): T = try {
        json.decodeFromString(serializer, text)
    } catch (e: Exception) {
        throw ApiException("bad_response", "Kinzo sent back something unexpected. Try again.")
    }

    private suspend fun requestText(method: HttpMethod, path: String, body: String?, auth: Boolean): String =
        requestText(method, path, body?.let { TextContent(it, ContentType.Application.Json) }, auth)

    private suspend fun requestText(
        method: HttpMethod,
        path: String,
        content: OutgoingContent?,
        auth: Boolean,
        timeoutMillis: Long? = null,
    ): String {
        if (!isConfigured) {
            throw ApiException(ApiException.NOT_CONFIGURED, "This build of Kinzo isn't connected to a server yet.")
        }
        val sessionToken = if (auth) token() ?: throw ApiException(ApiException.SIGNED_OUT, "Sign in to do that.", 401) else null
        val response = try {
            client.request(baseUrl.trimEnd('/') + path) {
                this.method = method
                if (sessionToken != null) header(HttpHeaders.Authorization, "Bearer $sessionToken")
                if (content != null) setBody(content)
                if (timeoutMillis != null) timeout { requestTimeoutMillis = timeoutMillis }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(ApiException.OFFLINE, "Can't reach Kinzo. Check your connection and try again.")
        }
        val text = response.bodyAsText()
        if (response.status.isSuccess()) return text

        val body = runCatching { json.decodeFromString(ErrorBody.serializer(), text) }.getOrNull()
        val code = body?.error?.code ?: "http_${response.status.value}"
        if (auth && code == ApiException.SIGNED_OUT) onSignedOut()
        throw ApiException(
            code = code,
            message = body?.error?.message ?: "Something went wrong. Try again.",
            status = response.status.value,
            attemptsLeft = body?.attemptsLeft,
            retryIn = body?.retryIn,
        )
    }
}
