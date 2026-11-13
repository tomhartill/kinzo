package com.tomhartill.kinzo.account

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.lang.ref.WeakReference

/**
 * Keeps track of the activity on screen, for things that need one: the Google sign-in sheet,
 * Custom Tabs and the share sheet. Held weakly, so a closed activity is never kept alive.
 */
class ActivityTracker(application: Application) {
    private var current: WeakReference<Activity>? = null
    private val _resumed = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Fires each time an activity comes back to the front, e.g. after a Custom Tab closes. */
    val resumed: SharedFlow<Unit> = _resumed.asSharedFlow()

    val activity: Activity? get() = current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    init {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                current = WeakReference(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                current = WeakReference(activity)
                _resumed.tryEmit(Unit)
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (current?.get() === activity) current = null
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
    }
}
