package com.kiracast.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.kiracast.R
import com.kiracast.web.GeckoProvider
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class AniListWebFragment : Fragment(), WebNavigable {

    private var session: GeckoSession? = null
    private val history = mutableListOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_anilist_web, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val gv: GeckoView = view.findViewById(R.id.geckoViewAni)
        val runtime = GeckoProvider.getRuntime(requireContext())

        session = GeckoSession().also {
            it.open(runtime)
            gv.setSession(it)
        }

        val url = "https://anilist.co/schedule"
        if (history.isEmpty()) history.add(url)
        session?.loadUri(url)
    }

    override fun onDestroyView() {
        session?.close()
        super.onDestroyView()
    }

    override fun onBackPressedHandled(): Boolean {
        if (history.size > 1) {
            history.removeLast()
            session?.loadUri(history.last())
            return true
        }
        return false
    }
}
