package com.tomhartill.kinzo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.tomhartill.kinzo.account.BrowserSignIn
import com.tomhartill.kinzo.account.DeepLinks
import com.tomhartill.kinzo.account.NativeGoogleResult
import com.tomhartill.kinzo.account.NativeGoogleSignIn
import com.tomhartill.kinzo.account.Pkce
import com.tomhartill.kinzo.data.api.Account
import com.tomhartill.kinzo.data.api.ApiException
import com.tomhartill.kinzo.data.api.CodeChallenge
import com.tomhartill.kinzo.data.api.CodeChannel
import com.tomhartill.kinzo.data.api.KinzoApi
import com.tomhartill.kinzo.data.api.PhoneStart
import com.tomhartill.kinzo.data.api.SignInResponse
import com.tomhartill.kinzo.data.api.UsernameCheck
import com.tomhartill.kinzo.db.KinzoDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

/**
 * Who is signed in on this phone, kept in the settings table so it survives restarts. Only
 * the session token and the account's public details are stored; never a password, which
 * Kinzo never sees.
 */
class SessionStore(
    private val db: KinzoDatabase,
    scope: CoroutineScope,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val queries get() = db.settingQueries
    private val json = Json { ignoreUnknownKeys = true }

    /** The signed-in account, or null when signed out. */
    val account: StateFlow<Account?> = queries.getSetting(ACCOUNT).asFlow().mapToOneOrNull(io)
        .map { stored -> stored?.let { runCatching { json.decodeFromString(Account.serializer(), it) }.getOrNull() } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun token(): String? = withContext(io) { queries.getSetting(TOKEN).executeAsOneOrNull() }

    suspend fun save(token: String, account: Account) = withContext(io) {
        queries.transaction {
            queries.putSetting(TOKEN, token)
            queries.putSetting(ACCOUNT, json.encodeToString(Account.serializer(), account))
        }
    }

    suspend fun update(account: Account) = withContext(io) {
        queries.putSetting(ACCOUNT, json.encodeToString(Account.serializer(), account))
    }

    suspend fun clear() = withContext(io) {
        queries.transaction {
            queries.deleteSetting(TOKEN)
            queries.deleteSetting(ACCOUNT)
        }
    }

    private companion object {
        const val TOKEN = "session_token"
        const val ACCOUNT = "session_account"
    }
}

enum class SignInProvider(val key: String, val label: String) {
    Google("google", "Google"),
    Facebook("facebook", "Facebook"),
}

sealed interface SignInResult {
    data class SignedIn(val account: Account, val newUser: Boolean) : SignInResult

    /** The person backed out. Not an error: nothing to show. */
    data object Cancelled : SignInResult

    data class Failed(val message: String) : SignInResult
}

/**
 * Signing in and out, and the account itself. Signing in is optional: everything on the phone
 * works without it, and it unlocks friends, backup and (later) the AI coach.
 */
class AccountRepository(
    private val api: KinzoApi,
    private val session: SessionStore,
    private val browser: BrowserSignIn,
    private val nativeGoogle: NativeGoogleSignIn,
    private val googleWebClientId: String,
) {
    val account: StateFlow<Account?> = session.account

    /** False in builds that aren't pointed at a server. */
    val isConfigured: Boolean get() = api.isConfigured

    suspend fun signIn(provider: SignInProvider): SignInResult {
        if (!api.isConfigured) {
            return SignInResult.Failed("Sign-in isn't set up in this build of Kinzo yet.")
        }
        return try {
            if (provider == SignInProvider.Google && googleWebClientId.isNotBlank()) {
                when (val native = nativeGoogle.signIn(googleWebClientId)) {
                    is NativeGoogleResult.Token -> return finish(api.signInWithGoogleIdToken(native.idToken))
                    NativeGoogleResult.Cancelled -> return SignInResult.Cancelled
                    NativeGoogleResult.Unavailable -> Unit // fall through to the browser
                }
            }
            signInWithBrowser(provider)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            SignInResult.Failed(e.message)
        } catch (e: Exception) {
            SignInResult.Failed("Something went wrong signing in. Try again.")
        }
    }

    private suspend fun signInWithBrowser(provider: SignInProvider): SignInResult {
        val verifier = Pkce.newVerifier()
        val challenge = Pkce.challenge(verifier)
        val callback = browser.open { redirectUri -> api.signInStartUrl(provider.key, redirectUri, challenge) }
            ?: return SignInResult.Cancelled
        val params = DeepLinks.queryParameters(callback)
        val code = params["code"]
        return when {
            code != null -> finish(api.exchangeCode(code, verifier))
            params["error"] == "cancelled" -> SignInResult.Cancelled
            else -> SignInResult.Failed("${provider.label} didn't sign you in. Try again.")
        }
    }

    /**
     * How sign-in codes can be sent, or empty when this server doesn't do email and phone
     * sign-in yet (or can't be reached).
     */
    suspend fun codeChannels(): List<CodeChannel> {
        if (!api.isConfigured) return emptyList()
        return try {
            api.providers().let { if ("phone" in it.providers) it.phoneChannels.ifEmpty { listOf(CodeChannel.Sms) } else emptyList() }
        } catch (e: ApiException) {
            emptyList()
        }
    }

    /** Sends a code for [email] (and [phone], for a new email). Throws [ApiException]. */
    suspend fun startCodeSignIn(email: String, phone: String?, channel: CodeChannel): PhoneStart =
        api.startPhoneSignIn(email.trim().lowercase(), phone, channel)

    /** Signs in with the code. Throws [ApiException] for a wrong or expired code. */
    suspend fun finishCodeSignIn(challengeId: String, code: String): SignInResult.SignedIn =
        finish(api.verifyPhoneSignIn(challengeId, code))

    /** Signed in: sends a code to add [phone] to the account. */
    suspend fun startAddPhone(phone: String, channel: CodeChannel): CodeChallenge = api.startAddPhone(phone, channel)

    suspend fun finishAddPhone(challengeId: String, code: String): Account =
        api.verifyAddPhone(challengeId, code).also { session.update(it) }

    suspend fun removePhone(): Account = api.removePhone().also { session.update(it) }

    private suspend fun finish(response: SignInResponse): SignInResult.SignedIn {
        session.save(response.token, response.user)
        return SignInResult.SignedIn(response.user, response.newUser)
    }

    /** Fetches the latest account details. Quietly does nothing offline. */
    suspend fun refresh() {
        if (session.token() == null) return
        try {
            session.update(api.me())
        } catch (e: ApiException) {
            // Offline, or the session ended (the API client has already signed out locally).
        }
    }

    suspend fun signOut() {
        try {
            api.signOut()
        } catch (e: ApiException) {
            // Offline: the session will expire on the server by itself.
        }
        session.clear()
    }

    /** Deletes the account on the server, then signs out. Workouts on this phone stay. */
    suspend fun deleteAccount() {
        api.deleteAccount()
        session.clear()
    }

    suspend fun checkUsername(username: String): UsernameCheck = api.checkUsername(username)

    suspend fun setUsername(username: String): Account = api.updateProfile(username = username).also { session.update(it) }

    suspend fun setDisplayName(name: String): Account = api.updateProfile(displayName = name).also { session.update(it) }
}
