package com.kiracast

import android.app.Activity
import android.os.Bundle
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
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

        // Extensions (uBlock + répertoire detector facultatif)
        runtime.webExtensionController.installBuiltIn("resource://android/assets/extensions/ublock_origin.xpi")
        runtime.webExtensionController.installBuiltIn("resource://android/assets/extensions/detector/")

        // Delegate navigation : injecte la trad quand l’URL change
        session.navigationDelegate = object : NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String,
                triggeredByRedirect: Boolean
            ) {
                maybeTranslate(url)
            }
        }

        session.open(runtime)
        view.setSession(session)

        // Page d’accueil : calendrier des sorties AniList
        session.loadUri("https://anilist.co/schedule")
    }

    // Back : on tente un retour, sinon on ferme
    @Deprecated("Use OnBackPressedDispatcher on newer APIs")
    override fun onBackPressed() {
        // Certaines versions n’exposent pas canGoBack : on essaie, et si pas d’historique, on finit
        try {
            session.goBack()
        } catch (_: Throwable) {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }

    /**
     * Détermine si on traduit la page en français, en évitant de toucher aux titres romaji
     * (la logique pour détecter les blocs à exclure est gérée côté JS injecté).
     */
    private fun maybeTranslate(url: String) {
        // Exemple : on traduit anilist.co en FR
        if (!url.contains("anilist.co")) return

        val endpoint = BuildConfig.LIBRETRANSLATE_URL // défini dans build.gradle.kts
        val js = """
            (function(){
              // Ne retraduis pas si déjà fait
              if (window.__kiracastTranslated) return;
              window.__kiracastTranslated = true;

              const endpoint = "${endpoint}";
              const targetLang = "fr";

              // Collecte le texte à traduire sauf les titres romaji (heuristique simple)
              function shouldSkip(node) {
                // Heuristique: classes/tags typiques de titres ou éléments courts style romaji
                const skipClasses = ["title", "heading", "name", "romaji"];
                if (node.nodeType !== Node.TEXT_NODE) return false;
                const t = node.textContent?.trim() || "";
                if (!t) return true;                         // rien à traduire
                if (/^[\x00-\x7F]{1,20}$/.test(t) && /\b(s1|ep|bd|tv|ova|ona)\b/i.test(t)) return true; // trucs courts
                const p = node.parentElement;
                if (!p) return false;
                if (skipClasses.some(c => p.classList.contains(c))) return true;
                if (/^H[1-4]$/.test(p.tagName)) return true;
                return false;
              }

              function walkAndCollect(root) {
                const nodes = [];
                const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
                  acceptNode: (n) => shouldSkip(n) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT
                });
                let cur;
                while ((cur = walker.nextNode())) {
                  const txt = cur.textContent?.trim();
                  if (txt && txt.length > 1) {
                    nodes.push(cur);
                  }
                }
                return nodes;
              }

              async function translateBatch(texts) {
                const body = {
                  q: texts,
                  source: "en",
                  target: targetLang,
                  format: "text"
                };
                const res = await fetch(endpoint, {
                  method: "POST",
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify(body)
                });
                if (!res.ok) throw new Error("HTTP " + res.status);
                const data = await res.json();
                // LibreTranslate renvoie un tableau d'objets { translatedText }
                if (Array.isArray(data)) return data.map(d => d.translatedText || "");
                // Certains proxys renvoient { translatedText } pour une seule entrée
                if (data.translatedText) return [data.translatedText];
                return [];
              }

              (async () => {
                try {
                  const nodes = walkAndCollect(document.body);
                  if (nodes.length === 0) return;

                  // Découpe en petits lots pour éviter de surcharger
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

        try {
            session.evaluateJS(js, null)
        } catch (_: Throwable) {
            // Silencieux si l’injection échoue (navigation en cours, etc.)
        }
    }
}
