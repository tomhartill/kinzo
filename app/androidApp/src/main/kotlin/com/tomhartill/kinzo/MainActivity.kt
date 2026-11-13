package com.tomhartill.kinzo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tomhartill.kinzo.account.handleDeepLink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch: after a rotation the link has already been handled.
        if (savedInstanceState == null) intent?.dataString?.let(::handleDeepLink)
        setContent {
            App()
        }
    }

    // singleTask: links (sign-in coming back, an invite) arrive here when Kinzo is already open.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(::handleDeepLink)
    }
}
