package com.bililisten.android

import android.app.Application
import com.bililisten.android.data.AppGraph
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.atomic.AtomicReference

/** Each test gets a fresh Android application; process singletons must use that same context. */
class IsolatedTestApplication : Application() {
    override fun onCreate() {
        listOf("repositoryRef", "loggerRef", "playerSettingsRef").forEach { name ->
            ReflectionHelpers.getStaticField<AtomicReference<*>>(AppGraph::class.java, name).set(null)
        }
        super.onCreate()
    }
}
