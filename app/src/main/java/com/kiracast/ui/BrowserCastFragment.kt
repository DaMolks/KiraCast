package com.kiracast.ui

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import com.kiracast.ui.WebNavigable
import com.kiracast.R
import com.kiracast.web.GeckoProvider
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class BrowserCastFragment : Fragment(), WebNavigable {


    private var session: GeckoSession? = null

    private val prefs by lazy { requireContext().getSharedPreferences("browser", Context.MODE_PRIVATE) }
    private var addressBar: EditText? = null

    // Historique simple (on garde ta logique pour l’instant)
    private val history = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_browser, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val gv: GeckoView = view.findViewById(R.id.geckoView)
        addressBar = view.findViewById(R.id.address)
        val goBtn: ImageButton = view.findViewById(R.id.btnGo)
        val homeBtn: ImageButton = view.findViewById(R.id.btnHome)

        // ⬇️ récupère le runtime partagé (ne PAS recréer GeckoRuntime ici)
        val runtime = GeckoProvider.getRuntime(requireContext().applicationContext)

        session = GeckoSession().also { s ->
            s.open(runtime)
            gv.setSession(s)
        }

        val home = prefs.getString("home", "https://anime-sama.fr/")!!
        if (history.isEmpty()) {
            addressBar?.setText(home)
            load(home)
        } else {
            addressBar?.setText(history.last())
            load(history.last(), record = false)
        }

        goBtn.setOnClickListener {
            val url = addressBar?.text?.toString()?.trim().orEmpty()
            if (url.isNotEmpty()) load(normalize(url))
        }
        homeBtn.setOnClickListener {
            val u = prefs.getString("home", "https://anime-sama.fr/")!!
            addressBar?.setText(u)
            load(u)
        }
    }

    private fun load(url: String, record: Boolean = true) {
        if (record && (history.isEmpty() || history.last() != url)) {
            history.add(url)
        }
        addressBar?.setText(url)
        session?.loadUri(url)
    }

    private fun normalize(input: String): String =
        if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.browser_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_set_home -> {
                val current = addressBar?.text?.toString()?.trim().orEmpty()
                if (current.isNotEmpty()) {
                    prefs.edit().putString("home", normalize(current)).apply()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Utilise ton historique simple pour "Retour"
    override fun onBackPressedHandled(): Boolean {
        if (history.size > 1) {
            history.removeLast()
            load(history.last(), record = false)
            return true
        }
        return false
    }

    override fun onDestroyView() {
        session?.close()
        session = null
        addressBar = null
        super.onDestroyView()
    }
}
