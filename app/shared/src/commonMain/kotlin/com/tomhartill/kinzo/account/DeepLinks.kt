package com.tomhartill.kinzo.account

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Links that open the app: `kinzo://auth/callback?...` at the end of a browser sign-in, and
 * `kinzo://invite/CODE` (or an `https://.../i/CODE` invite link) from a friend.
 */
object DeepLinks {
    private val _authCallbacks = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Sign-in callbacks, for the browser sign-in waiting on one. */
    val authCallbacks: SharedFlow<String> = _authCallbacks.asSharedFlow()

    private val _pendingInvite = MutableStateFlow<String?>(null)

    /**
     * An invite code waiting to be shown. A state rather than an event, so an invite that
     * launches the app is still there when the screens are ready for it.
     */
    val pendingInvite: StateFlow<String?> = _pendingInvite.asStateFlow()

    fun consumeInvite() {
        _pendingInvite.value = null
    }

    /** Routes a link. Returns false if it isn't one of the app's. */
    fun handle(url: String): Boolean = when (val link = parse(url)) {
        is Link.AuthCallback -> _authCallbacks.tryEmit(link.url)
        is Link.Invite -> {
            _pendingInvite.value = link.code
            true
        }
        null -> false
    }

    sealed interface Link {
        data class AuthCallback(val url: String) : Link

        data class Invite(val code: String) : Link
    }

    private val inviteCode = Regex("^[A-Za-z0-9_-]{4,64}$")

    fun parse(url: String): Link? {
        val trimmed = url.trim()
        val scheme = trimmed.substringBefore("://", "").lowercase()
        val rest = trimmed.substringAfter("://", "")
        val path = rest.substringBefore('?').substringBefore('#').trimEnd('/')
        return when {
            scheme == "kinzo" && path == "auth/callback" -> Link.AuthCallback(trimmed)
            scheme == "kinzo" && path.startsWith("invite/") ->
                path.removePrefix("invite/").takeIf { inviteCode.matches(it) }?.let { Link.Invite(it) }
            scheme == "https" && path.substringAfter('/', "").startsWith("i/") ->
                path.substringAfter('/').removePrefix("i/").takeIf { inviteCode.matches(it) }?.let { Link.Invite(it) }
            else -> null
        }
    }

    /** The query parameters of a callback URL, decoded. */
    fun queryParameters(url: String): Map<String, String> {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return emptyMap()
        return query.split('&').filter { it.isNotEmpty() }.associate { pair ->
            decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', ""))
        }
    }

    private fun decode(s: String): String {
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '+' -> bytes.add(' '.code.toByte())
                c == '%' && i + 2 <= s.lastIndex -> {
                    val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex != null) {
                        bytes.add(hex.toByte())
                        i += 2
                    } else {
                        bytes.add(c.code.toByte())
                    }
                }
                else -> c.toString().encodeToByteArray().forEach { bytes.add(it) }
            }
            i++
        }
        return bytes.toByteArray().decodeToString()
    }
}

/** Swift and Android entry point for links that open the app. */
fun handleDeepLink(url: String): Boolean = DeepLinks.handle(url)
