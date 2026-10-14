package com.tomhartill.kinzo.account

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Browser sign-in in a Custom Tab. The API's last redirect is to kinzo://auth/callback, which
 * MainActivity hands to [DeepLinks]. If the app comes back to the front without one, the
 * person closed the tab.
 */
class AndroidBrowserSignIn(private val activities: ActivityTracker) : BrowserSignIn {
    override suspend fun open(startUrl: (redirectUri: String) -> String): String? = coroutineScope {
        val activity = activities.activity ?: return@coroutineScope null
        val url = Uri.parse(startUrl(REDIRECT_URI))

        // Listen before opening, so a quick redirect can't be missed.
        val callback = async(start = CoroutineStart.UNDISPATCHED) { DeepLinks.authCallbacks.first() }
        val resumed = async(start = CoroutineStart.UNDISPATCHED) { activities.resumed.first() }
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, url)
        } catch (e: ActivityNotFoundException) {
            // No browser with Custom Tabs: a plain browser works the same way.
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, url))
            } catch (e: ActivityNotFoundException) {
                callback.cancel()
                resumed.cancel()
                return@coroutineScope null
            }
        }
        val result = select {
            callback.onAwait { it }
            resumed.onAwait {
                // Back in the app. The callback arrives just before the app resumes, so give it
                // a moment; with none, the tab was closed.
                withTimeoutOrNull(CLOSE_GRACE_MILLIS) { callback.await() }
            }
        }
        callback.cancel()
        resumed.cancel()
        result
    }

    companion object {
        const val REDIRECT_URI = "kinzo://auth/callback"
        private const val CLOSE_GRACE_MILLIS = 700L
    }
}

/** Google's own sign-in sheet through Credential Manager: one tap, no browser. */
class AndroidGoogleSignIn(private val activities: ActivityTracker) : NativeGoogleSignIn {
    override suspend fun signIn(serverClientId: String): NativeGoogleResult {
        val activity = activities.activity ?: return NativeGoogleResult.Unavailable
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                NativeGoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                NativeGoogleResult.Unavailable
            }
        } catch (e: GetCredentialCancellationException) {
            NativeGoogleResult.Cancelled
        } catch (e: GetCredentialException) {
            // No Play Services, no Google account, or this build isn't registered with Google.
            println("Kinzo: Google sign-in sheet unavailable, using the browser: $e")
            NativeGoogleResult.Unavailable
        }
    }
}

class AndroidSharer(private val activities: ActivityTracker) : Sharer {
    override fun share(text: String) {
        val activity = activities.activity ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(send, null))
    }
}
