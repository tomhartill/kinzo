@file:OptIn(ExperimentalForeignApi::class)

package com.tomhartill.kinzo.account

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * Browser sign-in with ASWebAuthenticationSession: Apple's sign-in sheet, which shares Safari's
 * cookies, so someone already signed in to Google or Facebook in Safari just taps Continue.
 */
class IosBrowserSignIn : BrowserSignIn {
    // The session must be kept alive until it finishes.
    private var session: ASWebAuthenticationSession? = null

    private val anchorProvider = object : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
        override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
            keyWindow() ?: UIWindow()
    }

    override suspend fun open(startUrl: (redirectUri: String) -> String): String? = withContext(Dispatchers.Main) {
        val url = NSURL.URLWithString(startUrl(REDIRECT_URI)) ?: return@withContext null
        suspendCancellableCoroutine { continuation ->
            val authSession = ASWebAuthenticationSession(
                uRL = url,
                callbackURLScheme = "kinzo",
                completionHandler = { callbackUrl, _ ->
                    session = null
                    // An error here means the person cancelled or the sheet couldn't show.
                    if (continuation.isActive) continuation.resume(callbackUrl?.absoluteString)
                },
            )
            authSession.presentationContextProvider = anchorProvider
            authSession.prefersEphemeralWebBrowserSession = false
            session = authSession
            if (!authSession.start()) {
                session = null
                continuation.resume(null)
            }
            continuation.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) { authSession.cancel() }
            }
        }
    }

    companion object {
        const val REDIRECT_URI = "kinzo://auth/callback"
    }
}

class IosSharer : Sharer {
    override fun share(text: String) {
        val root = topViewController()
        if (root == null) {
            UIPasteboard.generalPasteboard.string = text
            return
        }
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        // On iPad the sheet is a popover and needs an anchor.
        sheet.popoverPresentationController?.sourceView = root.view
        root.presentViewController(sheet, animated = true, completion = null)
    }
}

@Suppress("DEPRECATION")
private fun keyWindow(): UIWindow? = UIApplication.sharedApplication.keyWindow

private fun topViewController(): UIViewController? {
    var top = keyWindow()?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
