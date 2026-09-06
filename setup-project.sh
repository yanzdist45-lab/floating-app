#!/data/data/com.termux/files/usr/bin/bash

set -e

echo "=========================================="
echo " ReelShort Dual Mode Project"
echo "=========================================="

mkdir -p .github/workflows
mkdir -p app/src/main/java/com/shikuro/reelshort
mkdir -p app/src/main/res/values
mkdir -p app/src/main/assets

rm -rf app/src/main/res/layout

cat > settings.gradle.kts <<'__SETTINGS__'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(
        RepositoriesMode.FAIL_ON_PROJECT_REPOS
    )

    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ReelShortFloating"

include(":app")
__SETTINGS__

cat > build.gradle.kts <<'__ROOT_GRADLE__'
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
__ROOT_GRADLE__

cat > gradle.properties <<'__PROPS__'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
__PROPS__

cat > app/build.gradle.kts <<'__APP_GRADLE__'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shikuro.reelshort"

    compileSdk = 35

    defaultConfig {
        applicationId = "com.shikuro.reelshort"

        minSdk = 26
        targetSdk = 35

        versionCode = 4
        versionName = "4.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")

    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
}
__APP_GRADLE__

cat > app/src/main/AndroidManifest.xml <<'__MANIFEST__'
<?xml version="1.0" encoding="utf-8"?>

<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission
        android:name="android.permission.INTERNET" />

    <uses-permission
        android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <uses-permission
        android:name="android.permission.FOREGROUND_SERVICE" />

    <uses-permission
        android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

    <application
        android:allowBackup="true"
        android:label="ReelShort Floating"
        android:theme="@style/AppTheme"
        android:usesCleartextTraffic="false">

        <activity
            android:name=".ShortsActivity"
            android:exported="false" />

        <activity
            android:name=".MainActivity"
            android:exported="true">

            <intent-filter>

                <action
                    android:name="android.intent.action.MAIN" />

                <category
                    android:name="android.intent.category.LAUNCHER" />

            </intent-filter>

        </activity>

        <service
            android:name=".FloatingPlayerService"
            android:exported="false"
            android:stopWithTask="false"
            android:foregroundServiceType="mediaPlayback" />

    </application>

</manifest>
__MANIFEST__

cat > app/src/main/res/values/styles.xml <<'__STYLES__'
<?xml version="1.0" encoding="utf-8"?>

<resources>

    <style
        name="AppTheme"
        parent="android:style/Theme.Material.NoActionBar">

        <item name="android:fontFamily">
            sans
        </item>

        <item name="android:windowBackground">
            #000000
        </item>

        <item name="android:statusBarColor">
            #000000
        </item>

        <item name="android:navigationBarColor">
            #000000
        </item>

        <item name="android:windowLightStatusBar">
            false
        </item>

        <item name="android:colorAccent">
            #FFFFFF
        </item>

    </style>

</resources>
__STYLES__

cat > app/src/main/java/com/shikuro/reelshort/MainActivity.kt <<'__MAIN_KT__'
package com.shikuro.reelshort

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {

    companion object {
        private const val PENDING_BOOK_ID = "pending_book_id"
        private const val PENDING_BOOK_TITLE = "pending_book_title"
    }

    private lateinit var webView: WebView

    private val prefs by lazy {
        getSharedPreferences("main", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = false
            allowFileAccess = false
        }

        val assetLoader =
            WebViewAssetLoader.Builder()
                .addPathHandler(
                    "/assets/",
                    WebViewAssetLoader.AssetsPathHandler(this)
                )
                .build()

        webView.webViewClient =
            object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest
                ) =
                    assetLoader.shouldInterceptRequest(
                        request.url
                    )
            }

        webView.addJavascriptInterface(
            AndroidBridge(),
            "AndroidApp"
        )

        setContentView(webView)

        webView.loadUrl(
            "https://appassets.androidplatform.net/assets/index.html"
        )
    }

    override fun onResume() {
        super.onResume()

        val bookId =
            prefs.getString(
                PENDING_BOOK_ID,
                null
            )

        val title =
            prefs.getString(
                PENDING_BOOK_TITLE,
                ""
            )
                .orEmpty()

        if (
            bookId != null &&
            Settings.canDrawOverlays(this)
        ) {
            prefs.edit()
                .remove(PENDING_BOOK_ID)
                .remove(PENDING_BOOK_TITLE)
                .apply()

            startFloatingService(
                bookId,
                title
            )
        }
    }

    inner class AndroidBridge {

        @JavascriptInterface
        fun searchBooks(
            keyword: String
        ): String {
            return try {
                val encoded =
                    URLEncoder.encode(
                        keyword,
                        "UTF-8"
                    )

                httpGet(
                    "https://reelshort.vercel.app/search?lang=in&keyword=$encoded"
                )
            } catch (
                error: Exception
            ) {
                JSONObject()
                    .put("ok", false)
                    .put(
                        "message",
                        error.message ?: "Search gagal"
                    )
                    .put(
                        "items",
                        org.json.JSONArray()
                    )
                    .toString()
            }
        }

        @JavascriptInterface
        fun startShorts(
            bookId: String,
            title: String
        ) {
            runOnUiThread {
                if (bookId.isBlank()) return@runOnUiThread

                startActivity(
                    Intent(
                        this@MainActivity,
                        ShortsActivity::class.java
                    ).apply {
                        putExtra(
                            ShortsActivity.EXTRA_BOOK_ID,
                            bookId
                        )

                        putExtra(
                            ShortsActivity.EXTRA_BOOK_TITLE,
                            title
                        )
                    }
                )
            }
        }

        @JavascriptInterface
        fun startFloating(
            bookId: String,
            title: String
        ) {
            runOnUiThread {
                if (bookId.isBlank()) return@runOnUiThread

                if (
                    Settings.canDrawOverlays(
                        this@MainActivity
                    )
                ) {
                    startFloatingService(
                        bookId,
                        title
                    )
                } else {
                    prefs.edit()
                        .putString(
                            PENDING_BOOK_ID,
                            bookId
                        )
                        .putString(
                            PENDING_BOOK_TITLE,
                            title
                        )
                        .apply()

                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse(
                                "package:$packageName"
                            )
                        )
                    )
                }
            }
        }
    }

    private fun startFloatingService(
        bookId: String,
        title: String
    ) {
        val intent =
            Intent(
                this,
                FloatingPlayerService::class.java
            ).apply {
                putExtra(
                    FloatingPlayerService.EXTRA_BOOK_ID,
                    bookId
                )

                putExtra(
                    FloatingPlayerService.EXTRA_BOOK_TITLE,
                    title
                )
            }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        /*
         * Balikin user ke game/app sebelumnya.
         */
        moveTaskToBack(true)
    }

    private fun httpGet(
        address: String
    ): String {

        val connection =
            URL(address)
                .openConnection()
                as HttpURLConnection

        connection.requestMethod =
            "GET"

        connection.connectTimeout =
            15000

        connection.readTimeout =
            25000

        connection.setRequestProperty(
            "Accept",
            "application/json"
        )

        connection.setRequestProperty(
            "User-Agent",
            "ReelShortFloating/4.0 Android"
        )

        try {
            val code =
                connection.responseCode

            val stream =
                if (
                    code in 200..299
                ) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val body =
                stream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    ?: ""

            if (
                code !in 200..299
            ) {
                throw Exception(
                    "HTTP $code"
                )
            }

            return body

        } finally {
            connection.disconnect()
        }
    }


    override fun onDestroy() {
        webView.removeJavascriptInterface(
            "AndroidApp"
        )
        webView.destroy()
        super.onDestroy()
    }
}
__MAIN_KT__

cat > app/src/main/java/com/shikuro/reelshort/ShortsActivity.kt <<'__SHORTS_KT__'
package com.shikuro.reelshort

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ShortsActivity : Activity() {

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_BOOK_TITLE = "book_title"

        private const val API =
            "https://reelshort.vercel.app"
    }

    data class Episode(
        val index: Int,
        val url: String
    )

    private lateinit var root: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer

    private lateinit var topControls: LinearLayout
    private lateinit var bottomControls: LinearLayout

    private lateinit var titleText: TextView
    private lateinit var episodeText: TextView
    private lateinit var loadingText: TextView

    private val episodes =
        mutableListOf<Episode>()

    private var currentPosition = 0

    private var bookId = ""
    private var bookTitle = ""

    private var touchStartY = 0f
    private var touchStartX = 0f
    private var touchStartedAt = 0L

    private val handler =
        Handler(Looper.getMainLooper())

    private val hideControlsRunnable =
        Runnable {
            setControlsVisible(false)
        }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        bookId =
            intent.getStringExtra(
                EXTRA_BOOK_ID
            )
                .orEmpty()

        bookTitle =
            intent.getStringExtra(
                EXTRA_BOOK_TITLE
            )
                .orEmpty()

        if (bookId.isBlank()) {
            finish()
            return
        }

        window.statusBarColor =
            Color.BLACK

        window.navigationBarColor =
            Color.BLACK

        buildUi()

        player =
            ExoPlayer.Builder(this)
                .build()

        playerView.player =
            player

        player.addListener(
            object : Player.Listener {

                override fun onPlaybackStateChanged(
                    state: Int
                ) {
                    when (state) {

                        Player.STATE_BUFFERING -> {
                            loadingText.visibility =
                                View.VISIBLE

                            loadingText.text =
                                "Memuat..."
                        }

                        Player.STATE_READY -> {
                            loadingText.visibility =
                                View.GONE
                        }

                        Player.STATE_ENDED -> {
                            nextEpisode()
                        }
                    }
                }

                override fun onPlayerError(
                    error: PlaybackException
                ) {
                    loadingText.visibility =
                        View.VISIBLE

                    loadingText.text =
                        "Video gagal diputar"
                }
            }
        )

        loadEpisodes()
    }


    private fun buildUi() {

        root =
            FrameLayout(this).apply {
                setBackgroundColor(
                    Color.BLACK
                )
            }

        setContentView(root)


        playerView =
            PlayerView(this).apply {
                useController = false

                resizeMode =
                    AspectRatioFrameLayout.RESIZE_MODE_FIT

                setBackgroundColor(
                    Color.BLACK
                )
            }

        root.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )


        loadingText =
            TextView(this).apply {
                text =
                    "Memuat episode..."

                gravity =
                    Gravity.CENTER

                textSize =
                    14f

                setTextColor(
                    Color.argb(235, 255, 255, 255)
                )

                setBackgroundColor(
                    Color.argb(
                        70,
                        0,
                        0,
                        0
                    )
                )
            }

        root.addView(
            loadingText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )


        topControls =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(12),
                    dp(10),
                    dp(12),
                    dp(10)
                )

                setBackgroundColor(
                    Color.argb(
                        130,
                        0,
                        0,
                        0
                    )
                )
            }

        val back =
            Button(this).apply {
                text = "←"

                setOnClickListener {
                    finish()
                }
            }

        topControls.addView(
            back,
            LinearLayout.LayoutParams(
                dp(52),
                dp(46)
            )
        )

        val titleBox =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(10),
                    0,
                    0,
                    0
                )
            }

        titleText =
            TextView(this).apply {
                text =
                    bookTitle

                textSize =
                    14f

                maxLines =
                    1

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    null,
                    Typeface.BOLD
                )
            }

        episodeText =
            TextView(this).apply {
                text =
                    "Episode"

                textSize =
                    12f

                setTextColor(
                    Color.LTGRAY
                )
            }

        titleBox.addView(
            titleText
        )

        titleBox.addView(
            episodeText
        )

        topControls.addView(
            titleBox,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(
            topControls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )


        bottomControls =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(16),
                    dp(10),
                    dp(16),
                    dp(18)
                )

                setBackgroundColor(
                    Color.argb(
                        145,
                        0,
                        0,
                        0
                    )
                )
            }

        val previous =
            Button(this).apply {
                text = "‹"

                setOnClickListener {
                    previousEpisode()
                    showControlsTemporarily()
                }
            }

        val episodePicker =
            Button(this).apply {
                text =
                    "Episode"

                setOnClickListener {
                    showEpisodePicker()
                    showControlsTemporarily()
                }
            }

        val next =
            Button(this).apply {
                text = "›"

                setOnClickListener {
                    nextEpisode()
                    showControlsTemporarily()
                }
            }

        bottomControls.addView(
            previous,
            LinearLayout.LayoutParams(
                dp(58),
                dp(48)
            )
        )

        bottomControls.addView(
            episodePicker,
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            )
        )

        bottomControls.addView(
            next,
            LinearLayout.LayoutParams(
                dp(58),
                dp(48)
            )
        )

        episodePicker.tag =
            "episode_picker"

        root.addView(
            bottomControls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )


        playerView.setOnTouchListener {
                _,
                event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    touchStartX =
                        event.rawX

                    touchStartY =
                        event.rawY

                    touchStartedAt =
                        System.currentTimeMillis()

                    true
                }

                MotionEvent.ACTION_UP -> {

                    val dx =
                        event.rawX -
                        touchStartX

                    val dy =
                        event.rawY -
                        touchStartY

                    val elapsed =
                        System.currentTimeMillis() -
                        touchStartedAt

                    val vertical =
                        kotlin.math.abs(dy) >
                        kotlin.math.abs(dx)

                    if (
                        elapsed < 900 &&
                        vertical &&
                        dy < -dp(70)
                    ) {
                        nextEpisode()
                        showControlsTemporarily()
                        true
                    } else if (
                        elapsed < 900 &&
                        vertical &&
                        dy > dp(70)
                    ) {
                        previousEpisode()
                        showControlsTemporarily()
                        true
                    } else {
                        togglePlayPause()
                        showControlsTemporarily()
                        true
                    }
                }

                else -> true
            }
        }
    }


    private fun loadEpisodes() {

        Thread {

            try {

                val body =
                    httpGet(
                        "$API/api/stream/all-episode?lang=in&bookId=$bookId"
                    )

                val json =
                    JSONObject(body)

                if (
                    !json.optBoolean("ok")
                ) {
                    throw Exception(
                        "API episode gagal"
                    )
                }

                val array =
                    json.getJSONArray(
                        "episodes"
                    )

                val loaded =
                    mutableListOf<Episode>()

                for (
                    i in 0 until array.length()
                ) {

                    val item =
                        array.getJSONObject(i)

                    val url =
                        selectVideoUrl(item)

                    if (url.isNotBlank()) {
                        loaded.add(
                            Episode(
                                index =
                                    item.optInt(
                                        "index",
                                        i + 1
                                    ),
                                url = url
                            )
                        )
                    }
                }

                if (loaded.isEmpty()) {
                    throw Exception(
                        "Tidak ada stream"
                    )
                }

                val realTitle =
                    json.optString(
                        "title",
                        bookTitle
                    )

                runOnUiThread {

                    episodes.clear()

                    episodes.addAll(
                        loaded
                    )

                    bookTitle =
                        realTitle

                    titleText.text =
                        realTitle

                    val saved =
                        getSharedPreferences(
                            "player",
                            MODE_PRIVATE
                        )
                            .getInt(
                                "episode_$bookId",
                                0
                            )

                    currentPosition =
                        saved.coerceIn(
                            0,
                            episodes.lastIndex
                        )

                    playEpisode(
                        currentPosition
                    )

                    showControlsTemporarily()
                }

            } catch (
                error: Exception
            ) {

                error.printStackTrace()

                runOnUiThread {
                    loadingText.visibility =
                        View.VISIBLE

                    loadingText.text =
                        "Gagal memuat episode"

                    Toast.makeText(
                        this,
                        error.message ?: "Gagal",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        }.start()
    }


    private fun playEpisode(
        position: Int
    ) {

        if (
            position !in episodes.indices
        ) return

        currentPosition =
            position

        val episode =
            episodes[position]

        episodeText.text =
            "Episode ${episode.index} / ${episodes.size}"

        val picker =
            bottomControls.findViewWithTag<Button>(
                "episode_picker"
            )

        picker?.text =
            "EP.${episode.index} / EP.${episodes.size}"

        loadingText.visibility =
            View.VISIBLE

        loadingText.text =
            "Memuat..."

        player.setMediaItem(
            MediaItem.fromUri(
                episode.url
            )
        )

        player.prepare()

        player.playWhenReady =
            true

        getSharedPreferences(
            "player",
            MODE_PRIVATE
        )
            .edit()
            .putInt(
                "episode_$bookId",
                position
            )
            .apply()
    }


    private fun nextEpisode() {

        val next =
            currentPosition + 1

        if (
            next <= episodes.lastIndex
        ) {
            playEpisode(next)
        }
    }


    private fun previousEpisode() {

        val previous =
            currentPosition - 1

        if (previous >= 0) {
            playEpisode(previous)
        }
    }


    private fun togglePlayPause() {

        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }


    private fun showControlsTemporarily() {

        handler.removeCallbacks(
            hideControlsRunnable
        )

        setControlsVisible(true)

        handler.postDelayed(
            hideControlsRunnable,
            2600
        )
    }


    private fun setControlsVisible(
        visible: Boolean
    ) {

        val value =
            if (visible) {
                View.VISIBLE
            } else {
                View.GONE
            }

        topControls.visibility =
            value

        bottomControls.visibility =
            value
    }


    private fun showEpisodePicker() {

        if (episodes.isEmpty()) return

        val dialog =
            Dialog(this)

        val outer =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(12),
                    dp(12),
                    dp(12),
                    dp(16)
                )

                setBackgroundColor(
                    Color.rgb(
                        24,
                        24,
                        24
                    )
                )
            }

        val header =
            TextView(this).apply {

                text =
                    "Pilih Episode"

                textSize =
                    20f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                setPadding(
                    dp(5),
                    dp(4),
                    dp(5),
                    dp(12)
                )
            }

        outer.addView(header)

        val scroll =
            ScrollView(this)

        val grid =
            GridLayout(this).apply {
                columnCount = 4
            }

        episodes.forEachIndexed {
                position,
                episode ->

            val button =
                Button(this).apply {

                    text =
                        "EP ${episode.index}"

                    alpha =
                        if (
                            position ==
                            currentPosition
                        ) 1f else 0.72f

                    setOnClickListener {
                        dialog.dismiss()
                        playEpisode(position)
                        showControlsTemporarily()
                    }
                }

            val params =
                GridLayout.LayoutParams().apply {

                    width = 0
                    height = dp(52)

                    columnSpec =
                        GridLayout.spec(
                            GridLayout.UNDEFINED,
                            1f
                        )

                    setMargins(
                        dp(2),
                        dp(2),
                        dp(2),
                        dp(2)
                    )
                }

            grid.addView(
                button,
                params
            )
        }

        scroll.addView(grid)

        outer.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        dialog.setContentView(outer)
        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (
                resources.displayMetrics.heightPixels *
                0.72f
            ).toInt()
        )
    }


    private fun selectVideoUrl(
        item: JSONObject
    ): String {

        var result =
            item.optString(
                "sourceVideoUrl"
            )

        if (result.isBlank()) {

            val streams =
                item.optJSONArray(
                    "streams"
                )

            if (
                streams != null &&
                streams.length() > 0
            ) {
                result =
                    streams.optJSONObject(0)
                        ?.optString(
                            "sourceUrl"
                        )
                        .orEmpty()
            }
        }

        if (result.isBlank()) {
            result =
                item.optString(
                    "videoUrl"
                )
        }

        if (
            result.startsWith(
                "http://"
            )
        ) {
            result =
                "https://" +
                result.removePrefix(
                    "http://"
                )
        }

        return result
    }


    private fun httpGet(
        address: String
    ): String {

        val connection =
            URL(address)
                .openConnection()
                as HttpURLConnection

        connection.requestMethod =
            "GET"

        connection.connectTimeout =
            15000

        connection.readTimeout =
            30000

        connection.setRequestProperty(
            "Accept",
            "application/json"
        )

        connection.setRequestProperty(
            "User-Agent",
            "ReelShortFloating/4.0 Android"
        )

        try {

            val code =
                connection.responseCode

            val stream =
                if (
                    code in 200..299
                ) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val body =
                stream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    ?: ""

            if (
                code !in 200..299
            ) {
                throw Exception(
                    "HTTP $code"
                )
            }

            return body

        } finally {
            connection.disconnect()
        }
    }


    private fun dp(
        value: Int
    ): Int =
        (
            value *
            resources.displayMetrics.density
        ).toInt()


    override fun onDestroy() {

        handler.removeCallbacksAndMessages(
            null
        )

        playerView.player =
            null

        player.release()

        super.onDestroy()
    }
}
__SHORTS_KT__

cat > app/src/main/java/com/shikuro/reelshort/FloatingPlayerService.kt <<'__FLOAT_KT__'
package com.shikuro.reelshort

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class FloatingPlayerService : Service() {

    companion object {

        const val EXTRA_BOOK_ID =
            "book_id"

        const val EXTRA_BOOK_TITLE =
            "book_title"

        private const val API =
            "https://reelshort.vercel.app"

        private const val CHANNEL_ID =
            "reelshort_floating"

        private const val NOTIFICATION_ID =
            6969
    }


    data class Episode(
        val index: Int,
        val url: String
    )


    private lateinit var wm:
            WindowManager

    private var expandedRoot:
            FrameLayout? = null

    private var bubbleRoot:
            FrameLayout? = null

    private var expandedParams:
            WindowManager.LayoutParams? = null

    private var bubbleParams:
            WindowManager.LayoutParams? = null

    private var player:
            ExoPlayer? = null

    private var playerView:
            PlayerView? = null

    private var loadingText:
            TextView? = null

    private var menuPanel:
            LinearLayout? = null

    private var dotsView:
            TextView? = null

    private var resizeZone:
            View? = null

    private var fullscreenItem:
            TextView? = null

    private val episodes =
        mutableListOf<Episode>()

    private var currentPosition =
        0

    private var currentBookId =
        ""

    private var currentBookTitle =
        ""

    private var loadingBook =
        false

    private var fullscreen =
        false

    private var restoreX =
        0

    private var restoreY =
        0

    private var restoreWidth =
        0

    private var restoreHeight =
        0

    private val handler by lazy {
        android.os.Handler(mainLooper)
    }

    private val autoHideDots =
        Runnable {
            if (
                menuPanel?.visibility !=
                View.VISIBLE
            ) {
                dotsView?.alpha =
                    0f
            }
        }


    override fun onCreate() {
        super.onCreate()

        wm =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        startForegroundNotification()

        player =
            ExoPlayer.Builder(this)
                .build()

        player?.addListener(
            object : Player.Listener {

                override fun onPlaybackStateChanged(
                    state: Int
                ) {
                    when (state) {

                        Player.STATE_BUFFERING -> {

                            loadingText?.visibility =
                                View.VISIBLE

                            loadingText?.text =
                                "Memuat..."
                        }

                        Player.STATE_READY -> {

                            loadingText?.visibility =
                                View.GONE
                        }

                        Player.STATE_ENDED -> {

                            nextEpisode()
                        }
                    }
                }

                override fun onPlayerError(
                    error: PlaybackException
                ) {

                    loadingText?.visibility =
                        View.VISIBLE

                    loadingText?.text =
                        "Video gagal diputar"
                }
            }
        )
    }


    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (
            !Settings.canDrawOverlays(
                this
            )
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        val bookId =
            intent
                ?.getStringExtra(
                    EXTRA_BOOK_ID
                )
                .orEmpty()

        val title =
            intent
                ?.getStringExtra(
                    EXTRA_BOOK_TITLE
                )
                .orEmpty()

        if (bookId.isBlank()) {
            restoreFromBubble()
            return START_STICKY
        }

        if (expandedRoot == null) {
            createExpandedWindow()
            createBubbleWindow()
        }

        restoreFromBubble()

        if (
            currentBookId != bookId ||
            episodes.isEmpty()
        ) {
            currentBookId =
                bookId

            currentBookTitle =
                title

            loadEpisodes(
                bookId,
                title
            )
        } else {
            showDotsTemporarily()
        }

        return START_STICKY
    }


    /*
     * =========================================================
     * NOTIFICATION
     * =========================================================
     */

    private fun startForegroundNotification() {

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "ReelShort Floating",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
            }

        manager.createNotificationChannel(
            channel
        )

        val openIntent =
            Intent(
                this,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

        val notification =
            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_media_play
                )
                .setContentTitle(
                    "ReelShort Floating"
                )
                .setContentText(
                    "Floating player aktif"
                )
                .setContentIntent(
                    pendingIntent
                )
                .setOngoing(true)
                .build()

        startForeground(
            NOTIFICATION_ID,
            notification
        )
    }


    private fun updateNotification() {

        val episode =
            episodes.getOrNull(
                currentPosition
            )

        val text =
            if (episode != null) {
                "EP.${episode.index} / EP.${episodes.size}"
            } else {
                "Floating player aktif"
            }

        val notification =
            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_media_play
                )
                .setContentTitle(
                    currentBookTitle.ifBlank {
                        "ReelShort Floating"
                    }
                )
                .setContentText(text)
                .setOngoing(true)
                .build()

        (
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
        ).notify(
            NOTIFICATION_ID,
            notification
        )
    }


    /*
     * =========================================================
     * MAIN FLOATING WINDOW
     * =========================================================
     */

    private fun createExpandedWindow() {

        val metrics =
            resources.displayMetrics

        val screenWidth =
            metrics.widthPixels

        val screenHeight =
            metrics.heightPixels

        val width =
            if (
                screenWidth >
                screenHeight
            ) {
                (
                    screenWidth *
                    0.38f
                ).toInt()
            } else {
                (
                    screenWidth *
                    0.88f
                ).toInt()
            }

        /*
         * Cuma bar ⋯ tipis + video.
         * Tidak ada title, EP bar, atau resize icon.
         */
        val height =
            (
                width *
                9f /
                16f
            ).toInt() +
            dp(16)

        expandedParams =
            WindowManager.LayoutParams(
                width,
                height,

                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,

                PixelFormat.TRANSLUCENT
            ).apply {

                gravity =
                    Gravity.TOP or
                    Gravity.START

                x =
                    dp(20)

                y =
                    dp(80)
            }


        val root =
            FrameLayout(this)

        expandedRoot =
            root


        val card =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                background =
                    rounded(
                        Color.rgb(
                            10,
                            10,
                            10
                        ),
                        18f
                    )
            }

        root.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )


        /*
         * Tiga titik ini sekaligus:
         * - drag handle
         * - tombol popup menu
         * - auto-hide control
         */
        val dots =
            TextView(this).apply {

                text =
                    "⋯"

                textSize =
                    20f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                alpha =
                    1f
            }

        dotsView =
            dots

        card.addView(
            dots,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(16)
            )
        )

        enableDotsDragAndMenu(
            dots
        )


        val playerContainer =
            FrameLayout(this).apply {
                setBackgroundColor(
                    Color.BLACK
                )
            }

        card.addView(
            playerContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )


        playerView =
            PlayerView(this).apply {

                player =
                    this@FloatingPlayerService.player

                useController =
                    false

                resizeMode =
                    AspectRatioFrameLayout.RESIZE_MODE_FIT

                setBackgroundColor(
                    Color.BLACK
                )

                setOnClickListener {

                    togglePlayPause()

                    showDotsTemporarily()
                }
            }

        playerContainer.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )


        loadingText =
            TextView(this).apply {

                text =
                    "Memuat..."

                textSize =
                    12f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setBackgroundColor(
                    Color.argb(
                        70,
                        0,
                        0,
                        0
                    )
                )
            }

        playerContainer.addView(
            loadingText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )


        /*
         * Resize tetap ada tapi 100% invisible.
         * Drag pojok kanan bawah.
         */
        val resize =
            View(this).apply {
                setBackgroundColor(
                    Color.TRANSPARENT
                )
            }

        resizeZone =
            resize

        root.addView(
            resize,
            FrameLayout.LayoutParams(
                dp(34),
                dp(34),
                Gravity.BOTTOM or
                    Gravity.END
            )
        )

        enableResize(
            resize
        )


        createMenu(
            root
        )


        wm.addView(
            root,
            expandedParams
        )

        showDotsTemporarily()
    }


    /*
     * =========================================================
     * ⋯ MENU + AUTO HIDE
     * =========================================================
     */

    private fun showDotsTemporarily() {

        handler.removeCallbacks(
            autoHideDots
        )

        dotsView?.alpha =
            1f

        handler.postDelayed(
            autoHideDots,
            2200
        )
    }


    private fun enableDotsDragAndMenu(
        target: TextView
    ) {

        target.setOnTouchListener(
            object : View.OnTouchListener {

                var startX =
                    0

                var startY =
                    0

                var touchX =
                    0f

                var touchY =
                    0f

                var moved =
                    false


                override fun onTouch(
                    view: View?,
                    event: MotionEvent
                ): Boolean {

                    val params =
                        expandedParams
                            ?: return false

                    when (
                        event.action
                    ) {

                        MotionEvent.ACTION_DOWN -> {

                            handler.removeCallbacks(
                                autoHideDots
                            )

                            target.alpha =
                                1f

                            startX =
                                params.x

                            startY =
                                params.y

                            touchX =
                                event.rawX

                            touchY =
                                event.rawY

                            moved =
                                false

                            return true
                        }


                        MotionEvent.ACTION_MOVE -> {

                            if (fullscreen) {
                                return true
                            }

                            val dx =
                                event.rawX -
                                touchX

                            val dy =
                                event.rawY -
                                touchY

                            if (
                                abs(dx) >
                                dp(4) ||
                                abs(dy) >
                                dp(4)
                            ) {
                                moved =
                                    true
                            }

                            val metrics =
                                resources.displayMetrics

                            val maxX =
                                (
                                    metrics.widthPixels -
                                    params.width
                                ).coerceAtLeast(0)

                            val maxY =
                                (
                                    metrics.heightPixels -
                                    params.height
                                ).coerceAtLeast(0)

                            params.x =
                                (
                                    startX +
                                    dx.toInt()
                                ).coerceIn(
                                    0,
                                    maxX
                                )

                            params.y =
                                (
                                    startY +
                                    dy.toInt()
                                ).coerceIn(
                                    0,
                                    maxY
                                )

                            expandedRoot?.let {

                                wm.updateViewLayout(
                                    it,
                                    params
                                )
                            }

                            return true
                        }


                        MotionEvent.ACTION_UP -> {

                            if (!moved) {

                                if (
                                    target.alpha <
                                    0.5f
                                ) {
                                    showDotsTemporarily()
                                } else {
                                    toggleMenu()
                                }
                            } else {
                                showDotsTemporarily()
                            }

                            return true
                        }
                    }

                    return false
                }
            }
        )
    }


    private fun createMenu(
        parent: FrameLayout
    ) {

        val menu =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                visibility =
                    View.GONE

                elevation =
                    dp(12).toFloat()

                background =
                    rounded(
                        Color.rgb(
                            38,
                            38,
                            38
                        ),
                        14f
                    )

                setPadding(
                    dp(5),
                    dp(5),
                    dp(5),
                    dp(5)
                )
            }


        val full =
            menuItem(
                "⛶    Layar penuh"
            ) {
                toggleFullscreen()
                hideMenu()
            }

        fullscreenItem =
            full


        val minimize =
            menuItem(
                "—    Minimalkan"
            ) {

                hideMenu()

                minimizeToBubble()
            }


        val close =
            menuItem(
                "×    Tutup"
            ) {
                stopSelf()
            }


        menu.addView(full)
        menu.addView(minimize)
        menu.addView(close)


        parent.addView(
            menu,
            FrameLayout.LayoutParams(
                dp(205),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or
                    Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin =
                    dp(18)
            }
        )

        menuPanel =
            menu
    }


    private fun menuItem(
        textValue: String,
        action: () -> Unit
    ): TextView {

        return TextView(this).apply {

            text =
                textValue

            textSize =
                14f

            gravity =
                Gravity.CENTER_VERTICAL

            setTextColor(
                Color.WHITE
            )

            setPadding(
                dp(15),
                0,
                dp(15),
                0
            )

            setOnClickListener {
                action()
            }

            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(44)
                )
        }
    }


    private fun toggleMenu() {

        val menu =
            menuPanel
                ?: return

        if (
            menu.visibility ==
            View.VISIBLE
        ) {
            hideMenu()
        } else {
            handler.removeCallbacks(
                autoHideDots
            )

            dotsView?.alpha =
                1f

            menu.visibility =
                View.VISIBLE
        }
    }


    private fun hideMenu() {

        menuPanel?.visibility =
            View.GONE

        showDotsTemporarily()
    }


    /*
     * =========================================================
     * FULLSCREEN
     * =========================================================
     */

    private fun toggleFullscreen() {

        val params =
            expandedParams
                ?: return

        val root =
            expandedRoot
                ?: return

        val metrics =
            resources.displayMetrics


        if (!fullscreen) {

            restoreX =
                params.x

            restoreY =
                params.y

            restoreWidth =
                params.width

            restoreHeight =
                params.height

            params.x =
                0

            params.y =
                0

            params.width =
                metrics.widthPixels

            params.height =
                metrics.heightPixels

            fullscreen =
                true

            fullscreenItem?.text =
                "↙    Kembalikan ukuran"

            resizeZone?.visibility =
                View.GONE

        } else {

            params.x =
                restoreX

            params.y =
                restoreY

            params.width =
                restoreWidth

            params.height =
                restoreHeight

            fullscreen =
                false

            fullscreenItem?.text =
                "⛶    Layar penuh"

            resizeZone?.visibility =
                View.VISIBLE
        }

        wm.updateViewLayout(
            root,
            params
        )

        showDotsTemporarily()
    }


    /*
     * =========================================================
     * BUBBLE
     * =========================================================
     */

    private fun createBubbleWindow() {

        bubbleParams =
            WindowManager.LayoutParams(
                dp(56),
                dp(56),

                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,

                PixelFormat.TRANSLUCENT
            ).apply {

                gravity =
                    Gravity.TOP or
                    Gravity.START

                x =
                    dp(20)

                y =
                    dp(120)
            }


        val root =
            FrameLayout(this)


        val icon =
            TextView(this).apply {

                text =
                    "R"

                textSize =
                    20f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                background =
                    GradientDrawable().apply {

                        shape =
                            GradientDrawable.OVAL

                        setColor(
                            Color.rgb(
                                24,
                                24,
                                24
                            )
                        )

                        setStroke(
                            dp(1),
                            Color.rgb(
                                80,
                                80,
                                80
                            )
                        )
                    }
            }


        root.addView(
            icon,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )


        root.visibility =
            View.GONE

        enableBubbleDrag(
            root
        )

        bubbleRoot =
            root

        wm.addView(
            root,
            bubbleParams
        )
    }


    private fun minimizeToBubble() {

        handler.removeCallbacks(
            autoHideDots
        )

        expandedRoot?.visibility =
            View.GONE

        bubbleRoot?.visibility =
            View.VISIBLE
    }


    private fun restoreFromBubble() {

        bubbleRoot?.visibility =
            View.GONE

        expandedRoot?.visibility =
            View.VISIBLE

        showDotsTemporarily()
    }


    private fun enableBubbleDrag(
        target: View
    ) {

        target.setOnTouchListener(
            object : View.OnTouchListener {

                var startX =
                    0

                var startY =
                    0

                var touchX =
                    0f

                var touchY =
                    0f

                var moved =
                    false


                override fun onTouch(
                    view: View?,
                    event: MotionEvent
                ): Boolean {

                    val params =
                        bubbleParams
                            ?: return false

                    when (
                        event.action
                    ) {

                        MotionEvent.ACTION_DOWN -> {

                            startX =
                                params.x

                            startY =
                                params.y

                            touchX =
                                event.rawX

                            touchY =
                                event.rawY

                            moved =
                                false

                            return true
                        }


                        MotionEvent.ACTION_MOVE -> {

                            val dx =
                                event.rawX -
                                touchX

                            val dy =
                                event.rawY -
                                touchY

                            if (
                                abs(dx) >
                                dp(4) ||
                                abs(dy) >
                                dp(4)
                            ) {
                                moved =
                                    true
                            }

                            val metrics =
                                resources.displayMetrics

                            val maxX =
                                (
                                    metrics.widthPixels -
                                    params.width
                                ).coerceAtLeast(0)

                            val maxY =
                                (
                                    metrics.heightPixels -
                                    params.height
                                ).coerceAtLeast(0)

                            params.x =
                                (
                                    startX +
                                    dx.toInt()
                                ).coerceIn(
                                    0,
                                    maxX
                                )

                            params.y =
                                (
                                    startY +
                                    dy.toInt()
                                ).coerceIn(
                                    0,
                                    maxY
                                )

                            bubbleRoot?.let {
                                wm.updateViewLayout(
                                    it,
                                    params
                                )
                            }

                            return true
                        }


                        MotionEvent.ACTION_UP -> {

                            if (!moved) {
                                restoreFromBubble()
                            }

                            return true
                        }
                    }

                    return false
                }
            }
        )
    }


    /*
     * =========================================================
     * INVISIBLE RESIZE ZONE
     * =========================================================
     */

    private fun enableResize(
        target: View
    ) {

        target.setOnTouchListener(
            object : View.OnTouchListener {

                var startWidth =
                    0

                var startHeight =
                    0

                var touchX =
                    0f

                var touchY =
                    0f


                override fun onTouch(
                    view: View?,
                    event: MotionEvent
                ): Boolean {

                    if (fullscreen) {
                        return false
                    }

                    val params =
                        expandedParams
                            ?: return false

                    when (
                        event.action
                    ) {

                        MotionEvent.ACTION_DOWN -> {

                            startWidth =
                                params.width

                            startHeight =
                                params.height

                            touchX =
                                event.rawX

                            touchY =
                                event.rawY

                            return true
                        }


                        MotionEvent.ACTION_MOVE -> {

                            val metrics =
                                resources.displayMetrics

                            val dx =
                                (
                                    event.rawX -
                                    touchX
                                ).toInt()

                            val dy =
                                (
                                    event.rawY -
                                    touchY
                                ).toInt()

                            val minWidth =
                                dp(220)

                            val minHeight =
                                dp(155)

                            val maxWidth =
                                (
                                    metrics.widthPixels -
                                    params.x
                                ).coerceAtLeast(
                                    minWidth
                                )

                            val maxHeight =
                                (
                                    metrics.heightPixels -
                                    params.y
                                ).coerceAtLeast(
                                    minHeight
                                )

                            params.width =
                                (
                                    startWidth +
                                    dx
                                ).coerceIn(
                                    minWidth,
                                    maxWidth
                                )

                            params.height =
                                (
                                    startHeight +
                                    dy
                                ).coerceIn(
                                    minHeight,
                                    maxHeight
                                )

                            expandedRoot?.let {

                                wm.updateViewLayout(
                                    it,
                                    params
                                )
                            }

                            return true
                        }
                    }

                    return false
                }
            }
        )
    }


    /*
     * =========================================================
     * EPISODE API + PLAYBACK
     * =========================================================
     */

    private fun loadEpisodes(
        bookId: String,
        fallbackTitle: String
    ) {

        if (loadingBook) return

        loadingBook =
            true

        episodes.clear()

        loadingText?.visibility =
            View.VISIBLE

        loadingText?.text =
            "Memuat..."


        Thread {

            try {

                val body =
                    httpGet(
                        "$API/api/stream/all-episode?lang=in&bookId=$bookId"
                    )

                val json =
                    JSONObject(body)

                if (
                    !json.optBoolean("ok")
                ) {
                    throw Exception(
                        "API episode gagal"
                    )
                }

                val array =
                    json.getJSONArray(
                        "episodes"
                    )

                val loaded =
                    mutableListOf<Episode>()

                for (
                    i in 0 until array.length()
                ) {

                    val item =
                        array.getJSONObject(i)

                    val url =
                        selectVideoUrl(item)

                    if (url.isNotBlank()) {
                        loaded.add(
                            Episode(
                                index =
                                    item.optInt(
                                        "index",
                                        i + 1
                                    ),
                                url = url
                            )
                        )
                    }
                }

                if (loaded.isEmpty()) {
                    throw Exception(
                        "Tidak ada video"
                    )
                }

                val title =
                    json.optString(
                        "title",
                        fallbackTitle
                    )

                runOnMain {

                    episodes.clear()

                    episodes.addAll(
                        loaded
                    )

                    currentBookTitle =
                        title

                    loadingBook =
                        false

                    val saved =
                        getSharedPreferences(
                            "player",
                            MODE_PRIVATE
                        )
                            .getInt(
                                "episode_$bookId",
                                0
                            )

                    currentPosition =
                        saved.coerceIn(
                            0,
                            episodes.lastIndex
                        )

                    playEpisode(
                        currentPosition
                    )
                }

            } catch (
                error: Exception
            ) {

                error.printStackTrace()

                runOnMain {

                    loadingBook =
                        false

                    loadingText?.visibility =
                        View.VISIBLE

                    loadingText?.text =
                        "Gagal memuat video"
                }
            }

        }.start()
    }


    private fun playEpisode(
        position: Int
    ) {

        if (
            position !in episodes.indices
        ) return

        currentPosition =
            position

        val episode =
            episodes[position]

        loadingText?.visibility =
            View.VISIBLE

        loadingText?.text =
            "Memuat..."

        player?.setMediaItem(
            MediaItem.fromUri(
                episode.url
            )
        )

        player?.prepare()

        player?.playWhenReady =
            true

        getSharedPreferences(
            "player",
            MODE_PRIVATE
        )
            .edit()
            .putInt(
                "episode_$currentBookId",
                position
            )
            .apply()

        updateNotification()
    }


    private fun nextEpisode() {

        val next =
            currentPosition + 1

        if (
            next <= episodes.lastIndex
        ) {
            playEpisode(next)
        }
    }


    private fun togglePlayPause() {

        val currentPlayer =
            player
                ?: return

        if (currentPlayer.isPlaying) {
            currentPlayer.pause()
        } else {
            currentPlayer.play()
        }
    }


    private fun selectVideoUrl(
        item: JSONObject
    ): String {

        var result =
            item.optString(
                "sourceVideoUrl"
            )

        if (result.isBlank()) {

            val streams =
                item.optJSONArray(
                    "streams"
                )

            if (
                streams != null &&
                streams.length() > 0
            ) {
                result =
                    streams.optJSONObject(0)
                        ?.optString(
                            "sourceUrl"
                        )
                        .orEmpty()
            }
        }

        if (result.isBlank()) {
            result =
                item.optString(
                    "videoUrl"
                )
        }

        if (
            result.startsWith(
                "http://"
            )
        ) {
            result =
                "https://" +
                result.removePrefix(
                    "http://"
                )
        }

        return result
    }


    private fun httpGet(
        address: String
    ): String {

        val connection =
            URL(address)
                .openConnection()
                as HttpURLConnection

        connection.requestMethod =
            "GET"

        connection.connectTimeout =
            15000

        connection.readTimeout =
            30000

        connection.setRequestProperty(
            "Accept",
            "application/json"
        )

        connection.setRequestProperty(
            "User-Agent",
            "ReelShortFloating/4.0 Android"
        )

        try {

            val code =
                connection.responseCode

            val stream =
                if (
                    code in 200..299
                ) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val body =
                stream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    ?: ""

            if (
                code !in 200..299
            ) {
                throw Exception(
                    "HTTP $code"
                )
            }

            return body

        } finally {
            connection.disconnect()
        }
    }


    private fun rounded(
        color: Int,
        radiusDp: Float
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius =
                dp(radiusDp)
                    .toFloat()
        }
    }


    private fun dp(
        value: Int
    ): Int =
        (
            value *
            resources.displayMetrics.density
        ).toInt()


    private fun dp(
        value: Float
    ): Int =
        (
            value *
            resources.displayMetrics.density
        ).toInt()


    private fun runOnMain(
        action: () -> Unit
    ) {

        handler.post(action)
    }


    override fun onDestroy() {

        handler.removeCallbacksAndMessages(
            null
        )

        playerView?.player =
            null

        player?.release()

        player =
            null

        try {
            expandedRoot?.let {
                wm.removeView(it)
            }
        } catch (_: Exception) {
        }

        try {
            bubbleRoot?.let {
                wm.removeView(it)
            }
        } catch (_: Exception) {
        }

        expandedRoot =
            null

        bubbleRoot =
            null

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        super.onDestroy()
    }


    override fun onBind(
        intent: Intent?
    ): IBinder? =
        null
}
__FLOAT_KT__

cat > app/src/main/assets/index.html <<'__INDEX_HTML__'
<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="UTF-8">
  <meta
    name="viewport"
    content="width=device-width, initial-scale=1, viewport-fit=cover"
  >

  <title>ReelShort Player</title>

  <link rel="stylesheet" href="style.css">

</head>

<body>

  <!-- =========================
       HOME / SEARCH
  ========================== -->

  <main id="homePage" class="home-page">

    <header class="home-header">
      <h1>ReelShort</h1>

      <div class="search-box">
        <input
          id="searchInput"
          type="search"
          placeholder="Cari drama..."
          autocomplete="off"
        >

        <button id="searchButton">
          Cari
        </button>
      </div>
    </header>

    <div class="home-content">

      <div id="searchStatus" class="search-status">
        Cari drama yang mau ditonton.
      </div>

      <div id="searchResults" class="movie-grid"></div>

    </div>

  </main>


  <!-- =========================
       SHORTS PLAYER
  ========================== -->

  <main id="playerPage" class="player-page hidden">

    <!-- TOP -->

    <div class="player-top">

      <button
        id="backButton"
        class="icon-button"
        aria-label="Kembali"
      >
        ←
      </button>

      <div class="top-title">

        <div id="playerTitle" class="player-title">
          ReelShort
        </div>

        <div id="playerEpisodeText" class="player-subtitle">
          Episode 1
        </div>

      </div>

    </div>


    <!-- VIDEO -->

    <section id="videoStage" class="video-stage">

      <video
        id="video"
        playsinline
        preload="metadata"
      ></video>


      <!-- LOADING -->

      <div id="loading" class="loading hidden">
        <div class="spinner"></div>
      </div>


      <!-- ERROR -->

      <div id="videoError" class="video-error hidden">
        Gagal memuat video
      </div>


      <!-- TAP PLAY -->

      <button
        id="centerPlay"
        class="center-play hidden"
        aria-label="Play"
      >
        ▶
      </button>

    </section>


    <!-- FULLSCREEN -->

    <button
      id="fullscreenButton"
      class="fullscreen-button"
    >
      <span>⛶</span>
      <span>Layar penuh</span>
    </button>


    <!-- PLAYER BOTTOM -->

    <div class="player-bottom">

      <!-- PROGRESS -->

      <div
        id="progressArea"
        class="progress-area"
      >

        <input
          id="progress"
          class="progress"
          type="range"
          min="0"
          max="1000"
          value="0"
        >

        <div class="time-row">

          <span id="currentTime">
            00:00
          </span>

          <span id="durationTime">
            00:00
          </span>

        </div>

      </div>


      <!-- CONTROLS -->

      <div class="mini-controls">

        <button
          id="previousButton"
          class="mini-button"
        >
          ‹
        </button>

        <button
          id="playPauseButton"
          class="play-button"
        >
          ▶
        </button>

        <button
          id="nextButton"
          class="mini-button"
        >
          ›
        </button>

      </div>


      <!-- EPISODE BAR -->

      <button
        id="episodeBar"
        class="episode-bar"
      >

        <div class="episode-left">

          <span class="episode-icon">
            ▰
          </span>

          <strong id="episodePosition">
            EP.1/EP.1
          </strong>

        </div>

        <span
          id="episodeArrow"
          class="episode-arrow"
        >
          ⌃
        </span>

      </button>

    </div>


    <!-- SWIPE INDICATOR -->

    <div id="swipeIndicator" class="swipe-indicator">
      Swipe ↑ episode berikutnya
    </div>

  </main>


  <!-- =========================
       EPISODE SHEET
  ========================== -->

  <div
    id="episodeOverlay"
    class="episode-overlay hidden"
  ></div>

  <section
    id="episodeSheet"
    class="episode-sheet"
  >

    <div class="sheet-handle"></div>

    <div class="sheet-header">

      <div>
        <h2>Episode</h2>

        <span id="sheetEpisodeCount">
          0 episode
        </span>
      </div>

      <button
        id="closeSheetButton"
        class="close-sheet"
      >
        ✕
      </button>

    </div>

    <div
      id="episodeList"
      class="episode-list"
    ></div>

  </section>


  <!-- =========================
       MODE PICKER
  ========================== -->

  <div
    id="modeOverlay"
    class="mode-overlay hidden"
  ></div>

  <section
    id="modeSheet"
    class="mode-sheet"
    aria-hidden="true"
  >
    <div class="mode-handle"></div>

    <div class="mode-header">
      <div>
        <h2>Pilih mode nonton</h2>
        <p id="modeBookTitle">Drama</p>
      </div>

      <button
        id="closeModeButton"
        class="mode-close"
        type="button"
        aria-label="Tutup"
      >
        ✕
      </button>
    </div>

    <button
      id="shortsModeButton"
      class="mode-option"
      type="button"
    >
      <span class="mode-icon">▯</span>
      <span>
        <strong>YouTube Shorts</strong>
        <small>Fullscreen, swipe atas/bawah buat pindah episode.</small>
      </span>
    </button>

    <button
      id="floatingModeButton"
      class="mode-option"
      type="button"
    >
      <span class="mode-icon">◫</span>
      <span>
        <strong>Floating window</strong>
        <small>Nonton sambil buka game, bisa drag, resize, dan minimize.</small>
      </span>
    </button>
  </section>

  <script src="script.js"></script>

</body>
</html>
__INDEX_HTML__

cat > app/src/main/assets/style.css <<'__STYLE_CSS__'
* {
  box-sizing: border-box;
}

:root {
  --bg: #07090d;
  --bg-2: #0f131a;
  --panel: rgba(20, 24, 31, 0.82);
  --panel-2: rgba(28, 34, 44, 0.92);
  --line: rgba(255, 255, 255, 0.08);
  --text: #f5f7fb;
  --muted: #9ca7b7;
  --soft: #7c8798;
  --accent: #ffffff;
  --accent-2: #dfe7ff;
  --shadow: 0 16px 44px rgba(0, 0, 0, 0.34);
}

html,
body {
  margin: 0;
  min-height: 100%;
  background:
    radial-gradient(circle at top, rgba(58, 74, 112, 0.25), transparent 35%),
    linear-gradient(180deg, var(--bg-2), var(--bg));
  color: var(--text);
  font-family: Inter, Arial, Helvetica, sans-serif;
}

body {
  overscroll-behavior: none;
}

button,
input {
  font: inherit;
}

button {
  color: var(--text);
}

.hidden {
  display: none !important;
}

.home-page {
  min-height: 100dvh;
  background: transparent;
}

.home-header {
  position: sticky;
  top: 0;
  z-index: 20;
  padding: max(16px, env(safe-area-inset-top)) 16px 14px;
  background: rgba(8, 10, 14, 0.72);
  backdrop-filter: blur(18px) saturate(140%);
  border-bottom: 1px solid var(--line);
}

.home-header h1 {
  max-width: 1120px;
  margin: 0 auto 14px;
  font-size: 24px;
  letter-spacing: 0.2px;
}

.search-box {
  max-width: 1120px;
  margin: auto;
  display: flex;
  gap: 10px;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(14px);
}

.search-box input {
  flex: 1;
  min-width: 0;
  border: 0;
  border-radius: 14px;
  outline: none;
  padding: 14px 16px;
  background: transparent;
  color: var(--text);
}

.search-box input::placeholder {
  color: var(--soft);
}

.search-box button {
  border: 0;
  border-radius: 14px;
  padding: 0 18px;
  min-height: 46px;
  background: linear-gradient(180deg, #ffffff, #d8e2ff);
  color: #090b10;
  font-weight: 800;
  box-shadow: 0 6px 16px rgba(255, 255, 255, 0.14);
}

.home-content {
  max-width: 1120px;
  margin: auto;
  padding: 18px 16px 28px;
}

.search-status {
  min-height: 22px;
  margin-bottom: 16px;
  color: var(--muted);
  font-size: 14px;
}

.movie-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(154px, 1fr));
  gap: 16px;
}

.movie-card {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(255,255,255,0.05), rgba(255,255,255,0.025));
  cursor: pointer;
  box-shadow: var(--shadow);
}

.movie-cover {
  display: block;
  width: 100%;
  aspect-ratio: 2 / 3;
  object-fit: cover;
  background: #1a1f29;
}

.movie-info {
  padding: 12px 12px 14px;
}

.movie-title {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-weight: 800;
  font-size: 14px;
  line-height: 1.4;
}

.movie-meta {
  margin-top: 8px;
  color: var(--muted);
  font-size: 12px;
}

.player-page {
  position: fixed;
  inset: 0;
  z-index: 100;
  height: 100dvh;
  overflow: hidden;
  background: #000;
  display: flex;
  flex-direction: column;
  touch-action: pan-x;
}

.player-top {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 15;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: max(12px, env(safe-area-inset-top)) 14px 14px;
  background: linear-gradient(to bottom, rgba(3, 4, 6, 0.82), rgba(3, 4, 6, 0));
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.icon-button {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 999px;
  background: rgba(24, 27, 35, 0.68);
  font-size: 20px;
  backdrop-filter: blur(12px);
}

.top-title {
  min-width: 0;
}

.player-title {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 15px;
  font-weight: 800;
}

.player-subtitle {
  margin-top: 3px;
  color: rgba(255, 255, 255, 0.72);
  font-size: 12px;
}

.video-stage {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #000;
}

.video-stage video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #000;
}

.center-play {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 72px;
  height: 72px;
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 50%;
  background: rgba(14, 17, 24, 0.76);
  font-size: 28px;
  backdrop-filter: blur(10px);
  box-shadow: 0 10px 28px rgba(0,0,0,0.35);
}

.loading {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  pointer-events: none;
}

.spinner {
  width: 42px;
  height: 42px;
  border: 4px solid rgba(255, 255, 255, 0.18);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.75s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.video-error {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: min(86%, 360px);
  padding: 14px 16px;
  text-align: center;
  color: #e7ebf4;
  font-size: 14px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: rgba(20, 24, 31, 0.72);
  backdrop-filter: blur(12px);
}

.fullscreen-button {
  position: absolute;
  left: 50%;
  bottom: 31%;
  z-index: 20;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid rgba(255,255,255,0.09);
  border-radius: 999px;
  padding: 11px 16px;
  background: rgba(18, 22, 29, 0.74);
  font-weight: 800;
  backdrop-filter: blur(14px);
}

.player-bottom {
  position: absolute;
  z-index: 15;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 16px max(18px, calc(env(safe-area-inset-bottom) + 10px));
  background: linear-gradient(to top, rgba(2,3,5,0.96), rgba(2,3,5,0));
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.player-page.controls-hidden .player-top,
.player-page.controls-hidden .player-bottom,
.player-page.controls-hidden .fullscreen-button {
  opacity: 0;
  pointer-events: none;
}

.player-page.controls-hidden .player-top {
  transform: translateY(-8px);
}

.player-page.controls-hidden .player-bottom {
  transform: translateY(10px);
}

.progress-area {
  margin-bottom: 12px;
}

.progress {
  width: 100%;
  height: 4px;
  margin: 0;
  accent-color: white;
  cursor: pointer;
}

.time-row {
  display: flex;
  justify-content: space-between;
  margin-top: 5px;
  color: rgba(255,255,255,0.6);
  font-size: 11px;
}

.mini-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  margin: 4px 0 12px;
}

.mini-controls button {
  border: 1px solid rgba(255,255,255,0.09);
  background: rgba(20, 24, 31, 0.72);
  backdrop-filter: blur(12px);
}

.mini-button {
  width: 42px;
  height: 42px;
  border-radius: 999px;
  font-size: 24px;
}

.play-button {
  width: 52px;
  height: 52px;
  border-radius: 999px;
  font-size: 20px;
}

.episode-bar {
  width: 100%;
  min-height: 64px;
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 18px;
  padding: 0 18px;
  background: rgba(20, 24, 31, 0.84);
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  backdrop-filter: blur(14px);
  box-shadow: var(--shadow);
}

.episode-left {
  display: flex;
  align-items: center;
  gap: 11px;
}

.episode-icon {
  font-size: 18px;
}

#episodePosition {
  font-size: 15px;
}

.episode-arrow {
  color: rgba(255,255,255,0.56);
  font-size: 24px;
}

.swipe-indicator {
  position: absolute;
  top: 16%;
  left: 50%;
  z-index: 25;
  transform: translateX(-50%);
  padding: 9px 13px;
  border: 1px solid rgba(255,255,255,0.09);
  border-radius: 999px;
  background: rgba(20, 24, 31, 0.72);
  color: rgba(255,255,255,0.82);
  font-size: 11px;
  opacity: 0;
  transition: opacity 0.3s;
  pointer-events: none;
  backdrop-filter: blur(14px);
}

.swipe-indicator.show {
  opacity: 1;
}

.episode-overlay {
  position: fixed;
  inset: 0;
  z-index: 199;
  background: rgba(0, 0, 0, 0.58);
  backdrop-filter: blur(4px);
}

.episode-sheet {
  position: fixed;
  z-index: 200;
  left: 0;
  right: 0;
  bottom: 0;
  height: min(72dvh, 680px);
  transform: translateY(105%);
  transition: transform 0.25s ease;
  border-radius: 26px 26px 0 0;
  background: linear-gradient(180deg, rgba(16, 20, 27, 0.98), rgba(11, 14, 20, 0.98));
  padding: 8px 16px max(16px, env(safe-area-inset-bottom));
  border-top: 1px solid var(--line);
}

.episode-sheet.open {
  transform: translateY(0);
}

.sheet-handle {
  width: 46px;
  height: 5px;
  margin: 2px auto 16px;
  border-radius: 20px;
  background: rgba(255,255,255,0.24);
}

.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.sheet-header h2 {
  margin: 0;
  font-size: 21px;
}

.sheet-header span {
  display: block;
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}

.close-sheet {
  width: 40px;
  height: 40px;
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 999px;
  background: rgba(255,255,255,0.06);
}

.episode-list {
  height: calc(100% - 82px);
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-content: start;
  gap: 10px;
  padding-bottom: 28px;
}

.episode-item {
  height: 48px;
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 12px;
  background: rgba(255,255,255,0.05);
  color: #dbe2ef;
  font-size: 13px;
}

.episode-item.active {
  background: linear-gradient(180deg, #ffffff, #dfe7ff);
  color: #090b10;
  font-weight: 800;
}

@media (min-width: 700px) {
  .player-page {
    left: 50%;
    width: min(100%, 500px);
    transform: translateX(-50%);
    border-left: 1px solid var(--line);
    border-right: 1px solid var(--line);
    box-shadow: var(--shadow);
  }

  .episode-sheet {
    left: 50%;
    width: min(100%, 500px);
    transform: translate(-50%, 105%);
  }

  .episode-sheet.open {
    transform: translate(-50%, 0);
  }
}

@media (max-width: 600px) {
  .movie-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
__STYLE_CSS__

cat > app/src/main/assets/script.js <<'__SCRIPT_JS__'
const API =
  "https://reelshort.vercel.app";


/* =========================
   STATE
========================= */

let currentBook = null;

let episodes = [];

let currentEpisodeIndex = 0;

let hls = null;

let currentStreamCandidates = [];

let streamCandidateIndex = 0;

let touchStartY = 0;

let touchStartX = 0;

let touchStartTime = 0;

let isSeeking = false;

let wheelLocked = false;

let selectedModeBook = null;


/* =========================
   ELEMENTS
========================= */

const homePage =
  document.getElementById("homePage");

const searchInput =
  document.getElementById("searchInput");

const searchButton =
  document.getElementById("searchButton");

const searchStatus =
  document.getElementById("searchStatus");

const searchResults =
  document.getElementById("searchResults");


const playerPage =
  document.getElementById("playerPage");

const backButton =
  document.getElementById("backButton");

const playerTitle =
  document.getElementById("playerTitle");

const playerEpisodeText =
  document.getElementById("playerEpisodeText");

const videoStage =
  document.getElementById("videoStage");

const video =
  document.getElementById("video");

const loading =
  document.getElementById("loading");

const videoError =
  document.getElementById("videoError");

const centerPlay =
  document.getElementById("centerPlay");

const fullscreenButton =
  document.getElementById("fullscreenButton");

const progress =
  document.getElementById("progress");

const currentTime =
  document.getElementById("currentTime");

const durationTime =
  document.getElementById("durationTime");

const previousButton =
  document.getElementById("previousButton");

const nextButton =
  document.getElementById("nextButton");

const playPauseButton =
  document.getElementById("playPauseButton");

const episodeBar =
  document.getElementById("episodeBar");

const episodePosition =
  document.getElementById("episodePosition");

const swipeIndicator =
  document.getElementById("swipeIndicator");


const episodeOverlay =
  document.getElementById("episodeOverlay");

const episodeSheet =
  document.getElementById("episodeSheet");

const closeSheetButton =
  document.getElementById("closeSheetButton");

const sheetEpisodeCount =
  document.getElementById("sheetEpisodeCount");

const episodeList =
  document.getElementById("episodeList");

const modeOverlay =
  document.getElementById("modeOverlay");

const modeSheet =
  document.getElementById("modeSheet");

const modeBookTitle =
  document.getElementById("modeBookTitle");

const closeModeButton =
  document.getElementById("closeModeButton");

const shortsModeButton =
  document.getElementById("shortsModeButton");

const floatingModeButton =
  document.getElementById("floatingModeButton");


/* =========================
   SEARCH
========================= */

async function searchDrama() {

  const keyword =
    searchInput.value.trim();

  if (!keyword) {
    return;
  }

  searchStatus.textContent =
    "Mencari...";

  searchResults.innerHTML = "";

  try {

    let data;

    if (
      window.AndroidApp &&
      typeof window.AndroidApp.searchBooks ===
        "function"
    ) {

      const raw =
        window.AndroidApp.searchBooks(
          keyword
        );

      data =
        JSON.parse(raw);

    } else {

      const url =
        `${API}/search` +
        `?lang=in` +
        `&keyword=${encodeURIComponent(keyword)}`;

      const response =
        await fetch(url);

      if (!response.ok) {
        throw new Error(
          `HTTP ${response.status}`
        );
      }

      data =
        await response.json();
    }

    if (
      !data.ok ||
      !Array.isArray(data.items)
    ) {
      throw new Error(
        "Format API search tidak valid"
      );
    }

    searchStatus.textContent =
      `${data.items.length} hasil ditemukan`;

    renderMovies(
      data.items
    );

  } catch (error) {

    console.error(error);

    searchStatus.textContent =
      `Gagal mencari: ${error.message}`;

  }

}


/* =========================
   MOVIE LIST
========================= */

function renderMovies(items) {

  searchResults.innerHTML = "";

  items.forEach(book => {

    const card =
      document.createElement("article");

    card.className =
      "movie-card";


    const themes =
      Array.isArray(book.theme)
        ? book.theme.join(" • ")
        : "";


    card.innerHTML = `
      <img
        class="movie-cover"
        src="${escapeAttr(book.pic || "")}"
        alt="${escapeAttr(book.title || "")}"
        loading="lazy"
      >

      <div class="movie-info">

        <div class="movie-title">
          ${escapeHtml(book.title || "Tanpa judul")}
        </div>

        <div class="movie-meta">
          ${book.chapter_count || 0} EP
          ${themes ? ` • ${escapeHtml(themes)}` : ""}
        </div>

      </div>
    `;


    card.addEventListener(
      "click",
      () => showModePicker(book)
    );


    searchResults.appendChild(
      card
    );

  });

}



/* =========================
   MODE PICKER
========================= */

function showModePicker(book) {

  selectedModeBook = book;

  modeBookTitle.textContent =
    book.title || "Drama";

  modeOverlay.classList.remove(
    "hidden"
  );

  modeSheet.setAttribute(
    "aria-hidden",
    "false"
  );

  requestAnimationFrame(
    () => {
      modeSheet.classList.add(
        "open"
      );
    }
  );
}


function closeModePicker() {

  modeSheet.classList.remove(
    "open"
  );

  modeSheet.setAttribute(
    "aria-hidden",
    "true"
  );

  setTimeout(
    () => {
      modeOverlay.classList.add(
        "hidden"
      );
    },
    240
  );
}


function startShortsMode() {

  const book =
    selectedModeBook;

  if (!book) {
    return;
  }

  closeModePicker();

  if (
    window.AndroidApp &&
    typeof window.AndroidApp.startShorts ===
      "function"
  ) {

    window.AndroidApp.startShorts(
      String(book.book_id || ""),
      String(book.title || "")
    );

    return;
  }

  // Browser fallback: pakai Shorts HTML lama.
  openBook(book);
}


function startFloatingMode() {

  const book =
    selectedModeBook;

  if (!book) {
    return;
  }

  closeModePicker();

  if (
    window.AndroidApp &&
    typeof window.AndroidApp.startFloating ===
      "function"
  ) {

    window.AndroidApp.startFloating(
      String(book.book_id || ""),
      String(book.title || "")
    );

    return;
  }

  searchStatus.textContent =
    "Floating window tersedia di APK Android.";
}


/* =========================
   OPEN BOOK
========================= */

async function openBook(book) {

  currentBook = book;

  searchStatus.textContent =
    "Memuat episode...";

  try {

    const url =
      `${API}/api/stream/all-episode` +
      `?lang=in` +
      `&bookId=${encodeURIComponent(book.book_id)}`;

    const response =
      await fetch(url);

    if (!response.ok) {
      throw new Error(
        `HTTP ${response.status}`
      );
    }

    const data =
      await response.json();

    if (
      !data.ok ||
      !Array.isArray(data.episodes)
    ) {
      throw new Error(
        "Daftar episode tidak tersedia"
      );
    }


    episodes =
      data.episodes;

    currentEpisodeIndex = 0;


    currentBook = {
      ...book,

      title:
        data.title ||
        book.title,

      pic:
        data.pic ||
        book.pic,

      desc:
        data.desc || "",

      totalChapters:
        data.totalChapters ||
        data.chapters ||
        episodes.length
    };


    renderEpisodeSheet();


    homePage.classList.add(
      "hidden"
    );

    playerPage.classList.remove(
      "hidden"
    );


    document.body.style.overflow =
      "hidden";


    await playEpisodeByIndex(
      0
    );


    showSwipeHint();

  } catch (error) {

    console.error(error);

    searchStatus.textContent =
      `Gagal mengambil episode: ${error.message}`;

  }

}


/* =========================
   PLAY EPISODE
========================= */

async function playEpisodeByIndex(index) {

  if (
    index < 0 ||
    index >= episodes.length
  ) {
    return;
  }


  currentEpisodeIndex =
    index;


  const episode =
    episodes[index];


  updatePlayerUI(
    episode
  );


  closeEpisodeSheet();


  showLoading(true);

  hideVideoError();


  /*
    API ngasih URL proxy pakai HTTP.
    Kita coba ubah ke HTTPS dulu.

    Kalau proxy gagal, fallback ke
    sourceVideoUrl langsung.
  */

  currentStreamCandidates =
    buildStreamCandidates(
      episode
    );


  streamCandidateIndex = 0;


  if (
    !currentStreamCandidates.length
  ) {

    showLoading(false);

    showVideoError(
      "URL video tidak ditemukan"
    );

    return;

  }


  playCurrentCandidate();

}


/* =========================
   BUILD STREAM LIST
========================= */

function buildStreamCandidates(
  episode
) {

  const urls = [];


  function pushUrl(url) {

    if (!url) {
      return;
    }

    const secure =
      String(url).replace(
        /^http:\/\//i,
        "https://"
      );

    if (
      !urls.includes(secure)
    ) {
      urls.push(secure);
    }

  }


  /*
    Proxy dulu.
    Kemungkinan lebih ramah browser.
  */

  pushUrl(
    episode.videoUrl
  );


  if (
    Array.isArray(
      episode.streams
    )
  ) {

    episode.streams.forEach(
      stream => {

        pushUrl(
          stream.url
        );

      }
    );

  }


  /*
    Direct source sebagai fallback.
  */

  pushUrl(
    episode.sourceVideoUrl
  );


  if (
    Array.isArray(
      episode.streams
    )
  ) {

    episode.streams.forEach(
      stream => {

        pushUrl(
          stream.sourceUrl
        );

      }
    );

  }


  return urls;

}


/* =========================
   PLAYER STREAM
========================= */

function playCurrentCandidate() {

  destroyHLS();


  video.pause();

  video.removeAttribute(
    "src"
  );

  video.load();


  const url =
    currentStreamCandidates[
      streamCandidateIndex
    ];


  if (!url) {

    showLoading(false);

    showVideoError(
      "Semua sumber video gagal diputar"
    );

    return;

  }


  console.log(
    "PLAY URL:",
    url
  );


  if (
    window.Hls &&
    Hls.isSupported()
  ) {

    hls =
      new Hls({

        enableWorker: true,

        lowLatencyMode: false,

        backBufferLength: 20

      });


    hls.loadSource(
      url
    );


    hls.attachMedia(
      video
    );


    hls.on(
      Hls.Events.MANIFEST_PARSED,
      () => {

        showLoading(false);

        tryPlayVideo();

      }
    );


    hls.on(
      Hls.Events.ERROR,
      (
        event,
        data
      ) => {

        console.error(
          "HLS ERROR:",
          data
        );


        if (
          data.fatal
        ) {

          tryNextStream();

        }

      }
    );


    return;

  }


  if (
    video.canPlayType(
      "application/vnd.apple.mpegurl"
    )
  ) {

    video.src = url;

    video.addEventListener(
      "loadedmetadata",
      () => {

        showLoading(false);

        tryPlayVideo();

      },
      {
        once: true
      }
    );

    return;

  }


  showLoading(false);

  showVideoError(
    "Browser ini tidak mendukung HLS"
  );

}


/* =========================
   FALLBACK STREAM
========================= */

function tryNextStream() {

  streamCandidateIndex++;


  if (
    streamCandidateIndex >=
    currentStreamCandidates.length
  ) {

    showLoading(false);

    showVideoError(
      "Video gagal diputar pada browser ini"
    );

    return;

  }


  console.log(
    "Fallback stream:",
    currentStreamCandidates[
      streamCandidateIndex
    ]
  );


  playCurrentCandidate();

}


/* =========================
   PLAY
========================= */

function tryPlayVideo() {

  video
    .play()
    .then(() => {

      updatePlayButton();

    })
    .catch(() => {

      centerPlay.classList.remove(
        "hidden"
      );

      updatePlayButton();

    });

}


function togglePlay() {

  if (
    video.paused
  ) {

    video.play()
      .catch(() => {});

  } else {

    video.pause();

  }

}


/* =========================
   UI
========================= */

function updatePlayerUI(
  episode
) {

  playerTitle.textContent =
    currentBook?.title ||
    "ReelShort";


  playerEpisodeText.textContent =
    `Episode ${episode.index}`;


  episodePosition.textContent =
    `EP.${episode.index}/EP.${episodes.length}`;


  updateActiveEpisodeButton();

}


/* =========================
   NEXT / PREVIOUS
========================= */

function nextEpisode() {

  const next =
    currentEpisodeIndex + 1;


  if (
    next >= episodes.length
  ) {
    return;
  }


  playEpisodeByIndex(
    next
  );

}


function previousEpisode() {

  const previous =
    currentEpisodeIndex - 1;


  if (
    previous < 0
  ) {
    return;
  }


  playEpisodeByIndex(
    previous
  );

}


/* =========================
   EPISODE SHEET
========================= */

function renderEpisodeSheet() {

  episodeList.innerHTML =
    "";


  sheetEpisodeCount.textContent =
    `${episodes.length} episode`;


  episodes.forEach(
    (
      episode,
      index
    ) => {

      const button =
        document.createElement(
          "button"
        );


      button.className =
        "episode-item";


      button.dataset.index =
        index;


      button.textContent =
        `EP ${episode.index}`;


      button.addEventListener(
        "click",
        () => {

          playEpisodeByIndex(
            index
          );

        }
      );


      episodeList.appendChild(
        button
      );

    }
  );


  updateActiveEpisodeButton();

}


function updateActiveEpisodeButton() {

  document
    .querySelectorAll(
      ".episode-item"
    )
    .forEach(button => {

      const index =
        Number(
          button.dataset.index
        );


      button.classList.toggle(
        "active",
        index ===
          currentEpisodeIndex
      );

    });

}


function openEpisodeSheet() {

  episodeOverlay.classList.remove(
    "hidden"
  );


  requestAnimationFrame(
    () => {

      episodeSheet.classList.add(
        "open"
      );

    }
  );


  setTimeout(
    () => {

      const active =
        episodeList.querySelector(
          ".episode-item.active"
        );


      active?.scrollIntoView({
        block: "center"
      });

    },
    250
  );

}


function closeEpisodeSheet() {

  episodeSheet.classList.remove(
    "open"
  );


  setTimeout(
    () => {

      episodeOverlay.classList.add(
        "hidden"
      );

    },
    250
  );

}


/* =========================
   PROGRESS
========================= */

function updateProgress() {

  if (
    isSeeking ||
    !Number.isFinite(
      video.duration
    )
  ) {
    return;
  }


  const ratio =
    video.duration
      ? video.currentTime /
        video.duration
      : 0;


  progress.value =
    Math.floor(
      ratio * 1000
    );


  currentTime.textContent =
    formatTime(
      video.currentTime
    );


  durationTime.textContent =
    formatTime(
      video.duration
    );

}


function seekVideo() {

  if (
    !Number.isFinite(
      video.duration
    )
  ) {
    return;
  }


  const ratio =
    Number(progress.value) /
    1000;


  video.currentTime =
    ratio *
    video.duration;

}


/* =========================
   FORMAT TIME
========================= */

function formatTime(seconds) {

  if (
    !Number.isFinite(seconds)
  ) {
    return "00:00";
  }


  const total =
    Math.floor(seconds);


  const minutes =
    Math.floor(
      total / 60
    );


  const secs =
    total % 60;


  return (
    String(minutes)
      .padStart(2, "0")
    +
    ":"
    +
    String(secs)
      .padStart(2, "0")
  );

}


/* =========================
   FULLSCREEN
========================= */

async function toggleFullscreen() {

  try {

    if (
      document.fullscreenElement
    ) {

      await document.exitFullscreen();

      return;

    }


    if (
      playerPage.requestFullscreen
    ) {

      await playerPage.requestFullscreen();

    } else if (
      video.webkitEnterFullscreen
    ) {

      video.webkitEnterFullscreen();

    }

  } catch (error) {

    console.error(
      "FULLSCREEN:",
      error
    );

  }

}


/* =========================
   SWIPE SHORTS
========================= */

function handleTouchStart(
  event
) {

  if (
    episodeSheet.classList.contains(
      "open"
    )
  ) {
    return;
  }


  const touch =
    event.changedTouches[0];


  touchStartY =
    touch.clientY;


  touchStartX =
    touch.clientX;


  touchStartTime =
    Date.now();

}


function handleTouchEnd(
  event
) {

  if (
    episodeSheet.classList.contains(
      "open"
    )
  ) {
    return;
  }


  const touch =
    event.changedTouches[0];


  const deltaY =
    touch.clientY -
    touchStartY;


  const deltaX =
    touch.clientX -
    touchStartX;


  const elapsed =
    Date.now() -
    touchStartTime;


  /*
    Jangan dianggap swipe kalau
    lebih dominan horizontal.
  */

  if (
    Math.abs(deltaX) >
    Math.abs(deltaY)
  ) {
    return;
  }


  if (
    elapsed > 800
  ) {
    return;
  }


  const threshold = 70;


  /*
    Swipe atas
  */

  if (
    deltaY <
    -threshold
  ) {

    nextEpisode();

    return;

  }


  /*
    Swipe bawah
  */

  if (
    deltaY >
    threshold
  ) {

    previousEpisode();

  }

}


/* =========================
   DESKTOP WHEEL
========================= */

function handleWheel(
  event
) {

  if (
    wheelLocked
  ) {
    return;
  }


  if (
    Math.abs(event.deltaY) <
    40
  ) {
    return;
  }


  wheelLocked = true;


  if (
    event.deltaY > 0
  ) {

    nextEpisode();

  } else {

    previousEpisode();

  }


  setTimeout(
    () => {

      wheelLocked = false;

    },
    700
  );

}


/* =========================
   PLAYER STATUS
========================= */

function showLoading(show) {

  loading.classList.toggle(
    "hidden",
    !show
  );

}


function showVideoError(
  message
) {

  videoError.textContent =
    message;


  videoError.classList.remove(
    "hidden"
  );

}


function hideVideoError() {

  videoError.classList.add(
    "hidden"
  );

}


function updatePlayButton() {

  const paused =
    video.paused;


  playPauseButton.textContent =
    paused
      ? "▶"
      : "❚❚";


  centerPlay.classList.toggle(
    "hidden",
    !paused
  );

}


/* =========================
   SWIPE HINT
========================= */

function showSwipeHint() {

  swipeIndicator.classList.add(
    "show"
  );


  setTimeout(
    () => {

      swipeIndicator.classList.remove(
        "show"
      );

    },
    2200
  );

}


/* =========================
   BACK HOME
========================= */

function backHome() {

  closeEpisodeSheet();


  destroyHLS();


  video.pause();

  video.removeAttribute(
    "src"
  );

  video.load();


  playerPage.classList.add(
    "hidden"
  );


  homePage.classList.remove(
    "hidden"
  );


  document.body.style.overflow =
    "";


  currentStreamCandidates = [];

}


/* =========================
   HLS DESTROY
========================= */

function destroyHLS() {

  if (hls) {

    hls.destroy();

    hls = null;

  }

}


/* =========================
   ESCAPE
========================= */

function escapeHtml(text) {

  const div =
    document.createElement(
      "div"
    );


  div.textContent =
    String(text);


  return div.innerHTML;

}


function escapeAttr(text) {

  return String(text)

    .replaceAll(
      "&",
      "&amp;"
    )

    .replaceAll(
      '"',
      "&quot;"
    )

    .replaceAll(
      "<",
      "&lt;"
    )

    .replaceAll(
      ">",
      "&gt;"
    );

}


/* =========================
   EVENTS
========================= */

closeModeButton.addEventListener(
  "click",
  closeModePicker
);


modeOverlay.addEventListener(
  "click",
  closeModePicker
);


shortsModeButton.addEventListener(
  "click",
  startShortsMode
);


floatingModeButton.addEventListener(
  "click",
  startFloatingMode
);


searchButton.addEventListener(
  "click",
  searchDrama
);


searchInput.addEventListener(
  "keydown",
  event => {

    if (
      event.key === "Enter"
    ) {

      searchDrama();

    }

  }
);


backButton.addEventListener(
  "click",
  backHome
);


episodeBar.addEventListener(
  "click",
  openEpisodeSheet
);


closeSheetButton.addEventListener(
  "click",
  closeEpisodeSheet
);


episodeOverlay.addEventListener(
  "click",
  closeEpisodeSheet
);


fullscreenButton.addEventListener(
  "click",
  toggleFullscreen
);


playPauseButton.addEventListener(
  "click",
  togglePlay
);


centerPlay.addEventListener(
  "click",
  togglePlay
);


previousButton.addEventListener(
  "click",
  previousEpisode
);


nextButton.addEventListener(
  "click",
  nextEpisode
);


videoStage.addEventListener(
  "click",
  event => {

    if (
      event.target === video ||
      event.target === videoStage
    ) {

      togglePlay();

    }

  }
);


video.addEventListener(
  "play",
  updatePlayButton
);


video.addEventListener(
  "pause",
  updatePlayButton
);


video.addEventListener(
  "timeupdate",
  updateProgress
);


video.addEventListener(
  "durationchange",
  updateProgress
);


video.addEventListener(
  "waiting",
  () => showLoading(true)
);


video.addEventListener(
  "playing",
  () => showLoading(false)
);


video.addEventListener(
  "ended",
  () => {

    nextEpisode();

  }
);


/* PROGRESS */

progress.addEventListener(
  "pointerdown",
  () => {

    isSeeking = true;

  }
);


progress.addEventListener(
  "input",
  () => {

    if (
      Number.isFinite(
        video.duration
      )
    ) {

      const preview =
        (
          Number(
            progress.value
          ) /
          1000
        ) *
        video.duration;


      currentTime.textContent =
        formatTime(
          preview
        );

    }

  }
);


progress.addEventListener(
  "change",
  () => {

    seekVideo();

    isSeeking = false;

  }
);


/* SWIPE */

playerPage.addEventListener(
  "touchstart",
  handleTouchStart,
  {
    passive: true
  }
);


playerPage.addEventListener(
  "touchend",
  handleTouchEnd,
  {
    passive: true
  }
);


/* MOUSE WHEEL */

playerPage.addEventListener(
  "wheel",
  handleWheel,
  {
    passive: true
  }
);


/* KEYBOARD */

document.addEventListener(
  "keydown",
  event => {

    if (
      playerPage.classList.contains(
        "hidden"
      )
    ) {
      return;
    }


    if (
      event.key ===
      "ArrowDown"
    ) {

      nextEpisode();

    }


    if (
      event.key ===
      "ArrowUp"
    ) {

      previousEpisode();

    }


    if (
      event.key === " "
    ) {

      event.preventDefault();

      togglePlay();

    }


    if (
      event.key ===
      "Escape"
    ) {

      closeEpisodeSheet();

    }

  }
);
__SCRIPT_JS__

cat > .gitignore <<'__GITIGNORE__'
.gradle/
.idea/
local.properties
build/
app/build/
*.iml
.DS_Store
__GITIGNORE__

cat > .github/workflows/build.yml <<'__WORKFLOW__'
name: Build Android APK

on:
  workflow_dispatch:

  push:
    branches:
      - main

jobs:

  build:

    runs-on: ubuntu-latest

    steps:

      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.9'

      - name: Build APK
        run: |
          gradle :app:assembleDebug --stacktrace

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: ReelShort-Dual-Mode
          path: app/build/outputs/apk/debug/app-debug.apk
__WORKFLOW__

echo
echo "=========================================="
echo " PROJECT BERHASIL DIGENERATE"
echo "=========================================="
echo
echo "Mode 1 : YouTube Shorts native"
echo "Mode 2 : Floating overlay"
echo
echo "Floating:"
echo "  - ⋯ auto-hide"
echo "  - drag dari area ⋯"
echo "  - popup fullscreen/minimize/tutup"
echo "  - invisible resize kanan bawah"
echo "  - minimize jadi bubble R"
echo "  - auto-next episode"
echo
echo "Home:"
echo "  - WebView pakai UI HTML/CSS lama"
echo "  - tap drama -> pilih mode"
echo
echo "Files:"
find . -type f -not -path "./.git/*" | sort
echo
