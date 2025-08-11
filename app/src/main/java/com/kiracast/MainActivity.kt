package com.kiracast

import android.app.Activity
import android.os.Bundle
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoSession.ProgressDelegate

class MainActivity : Activity() {

    private lateinit var runtime: GeckoRuntime
    private lateinit var session: GeckoSession

    // Remplace par ton propre endpoint/self-host si besoin
    private val TRANSLATE_ENDPOINT = "https://libretranslate.com/translate"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val view: GeckoView = findViewById(R.id.geckoview)
        runtime = GeckoRuntime.create(this)
        session = GeckoSession()

        // Extensions (uBlock + éventuel /detector)
        runtime.webExtensionController.installBuiltIn("resource://android/assets/extensions/ublock_origin.xpi")
        try {
            runtime.webExtensionController.installBuiltIn("resource://android/assets/extensions/detector/")
        } catch (_: Throwable) {
            // optionnel, ignore si le dossier n'existe pas
        }

        // Injecte la traduction quand la page est chargée
        session.progressDelegate = object : ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                // rien
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                // Ne trad que les pages AniList
                session.getCurrentUri()?.let { current ->
                    if (current.contains("anilist.co")) {
                        injectTranslateJS()
                    }
                }
            }
        }

        session.open(runtime)
        view.setSession(session)

        // Page d’accueil : calendrier des sorties AniList
        session.loadUri("https://anilist.co/schedule")
    }

    // Back : essaie de revenir si possible
    @Deprecated("Use OnBackPressedDispatcher on newer APIs")
    override fun onBackPressed() {
        try {
            if (session.canGoBack()) {
                session.goBack()
            } else {
                super.onBackPressed()
            }
        } catch (_: Throwable) {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        try { session.close() } catch (_: Throwable) {}
        super.onDestroy()
    }

    /**
     * Injecte un script qui traduit en FR la page, en évitant (autant que possible)
     * les titres courts/romaji (heuristique simple côté JS).
     */
    private fun injectTranslateJS() {
        val js = """
            (function(){
              if (window.__kiracastTranslated) return;
              window.__kiracastTranslated = true;

              const endpoint = "${TRANSLATE_ENDPOINT}";
              const targetLang = "fr";

              function shouldSkip(node) {
                if (node.nodeType !== Node.TEXT_NODE) return false;
                const t = (node.textContent || "").trim();
                if (!t) return true; // vide
                // évite de toucher aux petits fragments typiques (romaji / sigles / épisodes)
                if (/^[\x00-\x7F]{1,20}$/.test(t) && /\b(s1|ep|bd|tv|ova|ona)\b/i.test(t)) return true;
                const p = node.parentElement;
                if (!p) return false;
                const skipClasses = ["title","heading","name","romaji"];
                if (skipClasses.some(c => p.classList.contains(c))) return true;
                if (/^H[1-4]$/.test(p.tagName)) return true;
                return false;
              }

              function collectTextNodes(root) {
                const out = [];
                const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
                  acceptNode: (n) => shouldSkip(n) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT
                });
                let cur;
                while ((cur = walker.nextNode())) {
                  const txt = cur.textContent?.trim();
                  if (txt && txt.length > 1) out.push(cur);
                }
                return out;
              }

              async function translateBatch(texts) {
                const body = { q: texts, source: "en", target: targetLang, format: "text" };
                const res = await fetch(endpoint, {
                  method: "POST",
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify(body)
                });
                if (!res.ok) throw new Error("HTTP " + res.status);
                const data = await res.json();
                if (Array.isArray(data)) return data.map(d => d.translatedText || "");
                if (data.translatedText) return [data.translatedText];
                return [];
              }

              (async () => {
                try {
                  const nodes = collectTextNodes(document.body);
                  if (nodes.length === 0) return;

                  const chunkSize = 30;
                  for (let i = 0; i < nodes.length; i += chunkSize) {
                    const slice = nodes.slice(i, i + chunkSize);
                    const src = slice.map(n => n.textContent);
                    const dst = await translateBatch(src);
                    for (let k = 0; k < slice.length && k < dst.length; k++) {
                      slice[k].textContent = dst[k];
                    }
                  }
                } catch (e) {
                  console.warn("KiraCast translate error:", e);
                }
              })();
            })();
        """.trimIndent()

        // Injection via URL javascript: (compatible toutes versions)
        val jsUrl = "javascript:(function(){try{$js}catch(e){console.warn('inject fail',e);}})()"
        try {
            session.loadUri(jsUrl)
        } catch (_: Throwable) {
            // ignore si navigation en cours
        }
    }
}
