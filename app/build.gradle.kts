import java.net.HttpURLConnection
import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.kiracast"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kiracast"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // ⬇️ Expose l’endpoint LibreTranslate au code (BuildConfig.LIBRETRANSLATE_URL)
        buildConfigField("String", "LIBRETRANSLATE_URL", "\"https://libretranslate.de/translate\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

/**
 * fetchUBlock : télécharge uBlock Origin (XPI) au build, sans le versionner.
 * 1) GitHub latest
 * 2) Fallback AMO API (extraction URL par regex)
 */
val uboOutput = file("src/main/assets/extensions/ublock_origin.xpi")

tasks.register("fetchUBlock") {
    outputs.file(uboOutput)
    doLast {
        uboOutput.parentFile.mkdirs()

        fun download(urlStr: String): Boolean {
            println("Téléchargement: $urlStr")
            return try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "KiraCast-Build/1.0")
                    connectTimeout = 20_000
                    readTimeout = 60_000
                }
                conn.inputStream.use { input ->
                    uboOutput.outputStream().use { output -> input.copyTo(output) }
                }
                if (!uboOutput.exists() || uboOutput.length() < 10_000L) {
                    uboOutput.delete()
                    throw IllegalStateException("Fichier XPI suspect (taille insuffisante)")
                }
                println("uBlock Origin OK → ${uboOutput.absolutePath}")
                true
            } catch (e: Exception) {
                println("Échec: ${e.message}")
                false
            }
        }

        val githubUrl = "https://github.com/gorhill/uBlock/releases/latest/download/uBlock0_firefox.xpi"
        if (download(githubUrl)) return@doLast

        println("Fallback: AMO API…")
        try {
            val api = URL("https://addons.mozilla.org/api/v5/addons/addon/ublock-origin/")
            val apiConn = (api.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "KiraCast-Build/1.0")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 20_000
                readTimeout = 20_000
            }
            val json = apiConn.inputStream.bufferedReader().use { it.readText() }
            val xpiRegex = """"url"\s*:\s*"(https:[^"]+\.xpi)"""".toRegex()
            val match = xpiRegex.find(json)
                ?: throw IllegalStateException("Impossible d’extraire l’URL XPI depuis AMO API")
            val amoUrl = match.groupValues[1].replace("\\u0026", "&").replace("\\/", "/")

            if (download(amoUrl)) return@doLast
            throw GradleException("Téléchargement uBlock depuis AMO a échoué.")
        } catch (e: Exception) {
            throw GradleException("Échec récupération uBlock (GitHub + AMO). Détail: ${e.message}", e)
        }
    }
}

tasks.named("preBuild").configure { dependsOn("fetchUBlock") }

dependencies {
    // GeckoView
    implementation("org.mozilla.geckoview:geckoview:130.0.+")

    // Media3 / ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")

    // Cast Framework
    implementation("com.google.android.gms:play-services-cast-framework:21.4.0")

    // Réseau / JSON
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // ⬅️ AJOUT

    // Persistance
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Sécurité
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
