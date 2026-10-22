package com.tomhartill.kinzo.account

/**
 * Opens a sign-in page in the platform's secure browser (Custom Tabs on Android,
 * ASWebAuthenticationSession on iOS, the default browser on desktop) and waits for the API to
 * send the person back to the app.
 */
interface BrowserSignIn {
    /**
     * [startUrl] builds the page to open from the redirect URI this platform listens on.
     * Returns the URL the browser came back to, or null if the person closed it.
     */
    suspend fun open(startUrl: (redirectUri: String) -> String): String?
}

/** Google's own sign-in sheet, where the platform has one (Credential Manager on Android). */
interface NativeGoogleSignIn {
    suspend fun signIn(serverClientId: String): NativeGoogleResult
}

sealed interface NativeGoogleResult {
    data class Token(val idToken: String) : NativeGoogleResult

    data object Cancelled : NativeGoogleResult

    /** Not possible here (no Google account on the phone, or not set up); use the browser. */
    data object Unavailable : NativeGoogleResult
}

/** For platforms without a native Google sheet. */
object NoNativeGoogleSignIn : NativeGoogleSignIn {
    override suspend fun signIn(serverClientId: String) = NativeGoogleResult.Unavailable
}

/** The platform's share sheet. Desktop copies to the clipboard instead. */
interface Sharer {
    fun share(text: String)
}
