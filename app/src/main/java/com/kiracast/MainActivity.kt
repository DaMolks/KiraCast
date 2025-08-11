package com.kiracast

import android.app.Activity
import android.os.Bundle
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : Activity() {

    private lateinit var runtime: GeckoRuntime
    private lateinit var session: GeckoSession

    private val anilistScheduleUrl = "https://anilist.co/schedule"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val view: GeckoView = findViewById(R.id.geckoview)
        runtime = GeckoRuntime.create(this)
        session = GeckoSession()

        // Extensions locales (uBlock + detector) si présentes
        val controller = runtime.webExtensionController
        controller.installBuiltIn("resource://android/assets/extensions/ublock_origin.xpi")
        controller.installBuiltIn("resource://android/assets/extensions/detector/")

        // Injecte la traduction à chaque fin de chargement
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                val url = session.currentUri ?: return
                if (url.startsWith(anilistScheduleUrl)) {
                    injectTranslateScript()
                }
            }
        }

        session.open(runtime)
        view.setSession(session)
        session.loadUri(anilistScheduleUrl)
    }

    // Injection JS via scheme javascript: (simple, pas d’extension nécessaire)
    private fun injectTranslateScript() {
        // JS compact : marque les titres comme non traduisibles, puis traduit le reste via LibreTranslate
        val js = """
            (function(){
              const LT_URL = ${json(BuildConfig.LIBRETRANSLATE_URL)};

              // 1) Marquer les éléments "titre" pour ne jamais les traduire
              function markTitles(root=document){
                const selectors = [
                  // Titres courants sur AniList (garde large et prudente)
                  '[class*="title"]',
                  '.media-card h3, .media-card a[href*="/anime/"] h3, .media-card a[href*="/manga/"] h3',
                  'a[href*="/anime/"][class*="title"], a[href*="/manga/"][class*="title"]'
                ];
                selectors.forEach(sel=>{
                  root.querySelectorAll(sel).forEach(el=>{
                    el.setAttribute('translate','no');
                    el.classList.add('notranslate');
                  });
                });
              }

              // 2) Récupère tous les TextNodes traduisibles
              function textNodesUnder(el){
                const nodes=[]; const walker=document.createTreeWalker(el, NodeFilter.SHOW_TEXT, {
                  acceptNode(n){
                    if(!n.nodeValue) return NodeFilter.FILTER_REJECT;
                    const t = n.nodeValue.trim();
                    if(t.length < 2) return NodeFilter.FILTER_REJECT;
                    const p = n.parentElement;
                    if(!p) return NodeFilter.FILTER_REJECT;
                    // Ignore ce qui est déjà marqué non traduisible
                    if(p.closest('.notranslate,[translate="no"]')) return NodeFilter.FILTER_REJECT;
                    // Ignore les <script>, <style>, inputs, etc.
                    const tn = p.tagName;
                    if(/^(SCRIPT|STYLE|NOSCRIPT|CODE|PRE|TEXTAREA|INPUT|SELECT|OPTION)$/i.test(tn)) return NodeFilter.FILTER_REJECT;
                    // Heuristique: ignore textes très courts souvent "romaji" (ex: "S1", "OVA", etc.)
                    if(t.length <= 3) return NodeFilter.FILTER_REJECT;
                    return NodeFilter.FILTER_ACCEPT;
                  }
                });
                while(walker.nextNode()) nodes.push(walker.currentNode);
                return nodes;
              }

              // 3) Découpe en batchs pour l’API
              function chunk(arr, size){ const out=[]; for(let i=0;i<arr.length;i+=size) out.push(arr.slice(i,i+size)); return out;}

              async function translateBatch(texts){
                // LibreTranslate format
                const body = {
                  q: texts,
                  source: "auto",
                  target: "fr",
                  format: "text"
                };
                const res = await fetch(LT_URL, {
                  method: "POST",
                  headers: { "Content-Type":"application/json" },
                  body: JSON.stringify(body),
                  // Beaucoup d'instances LibreTranslate ont CORS ouvert; si ce n'est pas le cas, on verra un échec (à proxyser côté natif).
                });
                if(!res.ok) throw new Error("LibreTranslate HTTP "+res.status);
                const data = await res.json();
                // data = [{translatedText:"..."}, ...] ou string si une seule entrée
                if (Array.isArray(texts)) {
                  if (Array.isArray(data)) return data.map(d=>d.translatedText ?? d);
                  // fallback (certains déploiements renvoient un objet unique)
                  return [data.translatedText ?? String(data)];
                } else {
                  return [data.translatedText ?? String(data)];
                }
              }

              async function run(){
                markTitles(document);

                const nodes = textNodesUnder(document.body);
                if(nodes.length === 0) return;

                // Map des valeurs originales (pour idempotence)
                const originals = nodes.map(n=>n.nodeValue);

                // Batching (évite des requêtes trop grosses)
                const BATCH_SIZE = 30;
                const batches = chunk(originals, BATCH_SIZE);

                let idx = 0;
                for(const batch of batches){
                  try{
                    const translated = await translateBatch(batch);
                    for(let i=0;i<translated.length;i++){
                      const n = nodes[idx+i];
                      if(n && n.nodeValue === originals[idx+i]) {
                        n.nodeValue = translated[i];
                      }
                    }
                    idx += batch.length;
                  } catch(e){
                    console.warn("Traduction batch échouée:", e);
                    idx += batch.length; // continue
                  }
                }
              }

              // Observer pour le contenu chargé dynamiquement (scroll infini)
              const mo = new MutationObserver((muts)=>{
                let need = false;
                for(const m of muts){
                  for(const node of m.addedNodes){
                    if(node.nodeType === 1){ // ELEMENT_NODE
                      markTitles(node);
                      need = true;
                    }
                  }
                }
                if(need) run();
              });
              mo.observe(document.documentElement, {childList:true, subtree:true});

              // Premier passage
              run();
            })();
        """.trimIndent()

        session.loadUri("javascript:" + js.encodeForJsUrl())
    }

    // Petit utilitaire pour embarquer des guillemets/retours à la ligne proprement
    private fun String.encodeForJsUrl(): String =
        this.replace("%", "%25")
            .replace("\n", "%0A")
            .replace("\r", "")
            .replace("\"", "%22")
            .replace("#", "%23")
            .replace("'", "%27")
            .replace(" ", "%20")

    override fun onBackPressed() {
        // Basé sur l’historique, si dispo
        // On tente un goBack via JS car GeckoView n’expose pas canGoBack directement.
        session.loadUri("javascript:(history.length>1)?history.back():void(0)")
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }

    // Util pour insérer proprement une string JSON dans le JS
    private fun json(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
