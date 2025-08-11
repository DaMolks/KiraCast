package com.kiracast.web

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime

object GeckoProvider {
    @Volatile private var runtime: GeckoRuntime? = null
    @Volatile private var extensionsInstalled = false

    fun getRuntime(appContext: Context): GeckoRuntime {
        val existing = runtime
        if (existing != null) return existing

        synchronized(this) {
            val again = runtime
            if (again != null) return again

            val rt = GeckoRuntime.create(appContext)
            if (!extensionsInstalled) {
                try {
                    val ctrl = rt.webExtensionController
                    ctrl.installBuiltIn("resource://android/assets/extensions/ublock_origin.xpi")
                    ctrl.installBuiltIn("resource://android/assets/extensions/detector/")
                    extensionsInstalled = true
                } catch (_: Throwable) {}
            }
            runtime = rt
            return rt
        }
    }
}