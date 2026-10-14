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
) {
    /** The name to show: their display name, or their username if they have none. */
    val name: String get() = displayName.ifBlank { "@$username" }
}

/** Someone else, with how the signed-in person relates to them. */
@Serializable
data class Person(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val relationship: Relationship = Relationship.None,
) {
    val name: String get() = displayName.ifBlank { "@$username" }
}

@Serializable
enum class Relationship {
    @SerialName("none") None,
    @SerialName("friends") Friends,

    /** You asked; waiting for them. */
    @SerialName("request_sent") RequestSent,

    /** They asked; waiting for you. */
    @SerialName("request_received") RequestReceived,
}

@Serializable
data class FriendLists(
    val friends: List<Person> = emptyList(),
    val incoming: List<Person> = emptyList(),
    val outgoing: List<Person> = emptyList(),
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
)

@Serializable
data class UsernameCheck(val username: String, val available: Boolean, val valid: Boolean)

@Serializable
internal data class PeopleResponse(val users: List<Person>)

@Serializable
internal data class RelationshipResponse(val relationship: Relationship)

@Serializable
internal data class ErrorBody(val error: Detail) {
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
)

@Serializable
internal data class UserIdRequest(@SerialName("user_id") val userId: String)
