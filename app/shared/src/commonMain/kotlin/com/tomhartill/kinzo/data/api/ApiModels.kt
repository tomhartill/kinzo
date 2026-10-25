package com.tomhartill.kinzo.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The API's JSON, as described in openapi/kinzo.yaml.

/** The signed-in person's own account. Also what the app keeps on the phone between launches. */
@Serializable
data class Account(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val email: String? = null,
    /** False for an email typed at a phone sign-up, which nobody has confirmed. */
    @SerialName("email_verified") val emailVerified: Boolean = true,
    /** Their own number in E.164, if they added one. */
    val phone: String? = null,
    /** How they can sign in, e.g. google, facebook, phone. */
    @SerialName("sign_in_methods") val signInMethods: List<String> = emptyList(),
    /** Private accounts approve each new follower. */
    @SerialName("private") val isPrivate: Boolean = false,
) {
    /** The name to show: their display name, or their username if they have none. */
    val name: String get() = displayName.ifBlank { "@$username" }
}

/** Someone else, with how the signed-in person and they follow each other. */
@Serializable
data class Person(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("private") val isPrivate: Boolean = false,
    /** Whether you follow them, or have asked to. */
    val following: Following = Following.None,
    @SerialName("follows_you") val followsYou: Boolean = false,
    /** They've asked to follow your private account. */
    @SerialName("requested_you") val requestedYou: Boolean = false,
) {
    val name: String get() = displayName.ifBlank { "@$username" }
}

@Serializable
enum class Following {
    @SerialName("none") None,

    /** Asked to follow a private account; waiting for them. */
    @SerialName("requested") Requested,

    @SerialName("following") Yes,
}

@Serializable
data class FollowLists(
    val followers: List<Person> = emptyList(),
    /** Includes requests you've sent to private accounts. */
    val following: List<Person> = emptyList(),
    /** People waiting to follow your private account. */
    val requests: List<Person> = emptyList(),
)

@Serializable
data class Invite(val code: String, val url: String)

@Serializable
internal data class InvitePreview(val inviter: Person, @SerialName("is_you") val isYou: Boolean)

@Serializable
internal data class SignInResponse(
    val token: String,
    val user: Account,
    @SerialName("new_user") val newUser: Boolean,
)

@Serializable
internal data class AuthProviders(
    val providers: List<String> = emptyList(),
    @SerialName("google_id_tokens") val googleIdTokens: Boolean = false,
    @SerialName("phone_channels") val phoneChannels: List<CodeChannel> = emptyList(),
)

/** How a sign-in code is sent. */
@Serializable
enum class CodeChannel {
    @SerialName("sms") Sms,
    @SerialName("whatsapp") WhatsApp,
}

/** A code on its way to a phone. */
@Serializable
data class CodeChallenge(
    @SerialName("challenge_id") val challengeId: String,
    /** The end of the number it went to, e.g. "•••• 123". */
    @SerialName("phone_hint") val phoneHint: String,
    val channel: CodeChannel,
    /** Seconds until the code stops working. */
    @SerialName("expires_in") val expiresIn: Int = 600,
    /** Seconds until another code can be sent. */
    @SerialName("resend_in") val resendIn: Int = 30,
)

/** What to show after an email: the code screen, a phone number to ask for, or another provider. */
@Serializable
data class PhoneStart(
    val next: Next,
    val challenge: CodeChallenge? = null,
    val providers: List<String> = emptyList(),
) {
    @Serializable
    enum class Next {
        @SerialName("code") Code,
        @SerialName("phone") Phone,
        @SerialName("provider") Provider,
    }
}

@Serializable
data class UsernameCheck(val username: String, val available: Boolean, val valid: Boolean)

@Serializable
internal data class PeopleResponse(val users: List<Person>)

@Serializable
internal data class ErrorBody(
    val error: Detail,
    @SerialName("attempts_left") val attemptsLeft: Int? = null,
    @SerialName("retry_in") val retryIn: Int? = null,
) {
    @Serializable
    data class Detail(val code: String, val message: String)
}

@Serializable
internal data class CodeExchangeRequest(val code: String, @SerialName("code_verifier") val codeVerifier: String)

@Serializable
internal data class IdTokenRequest(@SerialName("id_token") val idToken: String)

@Serializable
internal data class ProfileUpdate(
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("private") val isPrivate: Boolean? = null,
)

@Serializable
internal data class UserIdRequest(@SerialName("user_id") val userId: String)

@Serializable
internal data class PhoneStartRequest(val email: String, val phone: String? = null, val channel: CodeChannel)

@Serializable
internal data class AddPhoneRequest(val phone: String, val channel: CodeChannel)

@Serializable
internal data class CodeAttempt(@SerialName("challenge_id") val challengeId: String, val code: String)

/** What a machine scan found; see `MachineScan` in openapi/kinzo.yaml. */
@Serializable
data class MachineScan(
    val matches: List<MachineMatch> = emptyList(),
    @SerialName("exercise_id") val exerciseId: String? = null,
    val description: String = "",
)

@Serializable
data class MachineMatch(
    @SerialName("machine_id") val machineId: String,
    val confidence: String,
)
