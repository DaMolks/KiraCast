package com.kiracast

import android.app.Activity
import android.os.Bundle
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : Activity() {

    private lateinit var runtime: GeckoRuntime
    private lateinit var session: GeckoSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val view: GeckoView = findViewById(R.id.geckoview)
        runtime = GeckoRuntime.create(this)
        session = GeckoSession()

        val controller = runtime.webExtensionController
        controller.installBuiltIn("resource://android/assets/extensions/ublock_origin.xpi")
        controller.installBuiltIn("resource://android/assets/extensions/detector/")

        session.open(runtime)
        view.setSession(session)           // <- FIX: utiliser setSession(...)
        session.loadUri("https://example.com")
    }

    override fun onBackPressed() {
        // À implémenter proprement via un NavigationDelegate si besoin
        super.onBackPressed()
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }
}
