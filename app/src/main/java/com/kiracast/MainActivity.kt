package com.kiracast

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kiracast.ui.AniListWebFragment
import com.kiracast.ui.BrowserCastFragment
import com.kiracast.ui.CalendarFragment
import com.kiracast.ui.WebNavigable

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottom = findViewById<BottomNavigationView>(R.id.bottomBar)
        bottom.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tab_calendar -> switch(CalendarFragment())
                R.id.tab_anilist  -> switch(AniListWebFragment())
                R.id.tab_browser  -> switch(BrowserCastFragment())
            }
            true
        }

        if (savedInstanceState == null) bottom.selectedItemId = R.id.tab_calendar

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val f = supportFragmentManager.findFragmentById(R.id.fragment_container)
                val handled = (f as? WebNavigable)?.onBackPressedHandled() ?: false
                if (!handled) finish()
            }
        })
    }

    private fun switch(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
