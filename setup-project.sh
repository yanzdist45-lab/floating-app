#!/data/data/com.termux/files/usr/bin/bash

set -e

echo "=========================================="
echo " ReelShort Floating Overlay Project"
echo "=========================================="

mkdir -p .github/workflows
mkdir -p app/src/main/java/com/shikuro/reelshort
mkdir -p app/src/main/res/values

# Layout lama sudah tidak dipakai.
rm -rf app/src/main/res/layout


# ============================================================
# settings.gradle.kts
# ============================================================

cat > settings.gradle.kts <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ReelShortFloating"

include(":app")
EOF


# ============================================================
# root build.gradle.kts
# ============================================================

cat > build.gradle.kts <<'EOF'
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
EOF


# ============================================================
# gradle.properties
# ============================================================

cat > gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
EOF


# ============================================================
# app/build.gradle.kts
# ============================================================

cat > app/build.gradle.kts <<'EOF'
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

        versionCode = 2
        versionName = "2.0"
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
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
}
EOF


# ============================================================
# AndroidManifest.xml
# ============================================================

cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>

<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <uses-permission
        android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <uses-permission
        android:name="android.permission.FOREGROUND_SERVICE" />

    <uses-permission
        android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

    <application
        android:allowBackup="true"
        android:label="ReelShort Floating"
        android:theme="@style/AppTheme">

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
EOF


# ============================================================
# styles.xml
# ============================================================

cat > app/src/main/res/values/styles.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>

<resources>

    <style
        name="AppTheme"
        parent="android:style/Theme.Material.NoActionBar">

        <item name="android:fontFamily">
            sans
        </item>

        <item name="android:windowBackground">
            #0A0A0A
        </item>

        <item name="android:statusBarColor">
            #0A0A0A
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
EOF


# ============================================================
# MainActivity.kt
# Search -> pilih drama -> jalankan floating service
# ============================================================

cat > app/src/main/java/com/shikuro/reelshort/MainActivity.kt <<'EOF'
package com.shikuro.reelshort

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : Activity() {

    private val api =
        "https://reelshort.vercel.app"

    private lateinit var root: LinearLayout

    private var screenCreated =
        false


    data class Book(
        val id: String,
        val title: String,
        val chapters: Int,
        val theme: String
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else {
            showSearchScreen()
        }
    }


    override fun onResume() {
        super.onResume()

        if (
            Settings.canDrawOverlays(this) &&
            !screenCreated
        ) {
            showSearchScreen()
        }
    }


    private fun requestOverlayPermission() {

        val layout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(25),
                    dp(25),
                    dp(25),
                    dp(25)
                )

                setBackgroundColor(
                    Color.rgb(
                        10,
                        10,
                        10
                    )
                )
            }


        val title =
            TextView(this).apply {

                text =
                    "Izin Floating Window"

                textSize =
                    24f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    null,
                    Typeface.BOLD
                )
            }


        val description =
            TextView(this).apply {

                text =
                    "ReelShort butuh izin tampil di atas aplikasi lain supaya player bisa nongol di atas game."

                textSize =
                    15f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    dp(15),
                    0,
                    dp(20)
                )
            }


        val button =
            Button(this).apply {

                text =
                    "IZINKAN FLOATING WINDOW"

                setOnClickListener {

                    val intent =
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse(
                                "package:$packageName"
                            )
                        )

                    startActivity(
                        intent
                    )
                }
            }


        layout.addView(
            title
        )

        layout.addView(
            description
        )

        layout.addView(
            button
        )


        setContentView(
            layout
        )
    }


    private fun showSearchScreen() {

        screenCreated =
            true


        root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(14),
                    dp(18),
                    dp(14),
                    dp(14)
                )

                setBackgroundColor(
                    Color.rgb(
                        10,
                        10,
                        10
                    )
                )
            }


        setContentView(
            root
        )


        val logo =
            TextView(this).apply {

                text =
                    "ReelShort Floating"

                textSize =
                    25f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                setPadding(
                    dp(4),
                    dp(4),
                    dp(4),
                    dp(16)
                )
            }


        root.addView(
            logo
        )


        val row =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }


        val input =
            EditText(this).apply {

                hint =
                    "Cari drama..."

                setSingleLine()

                imeOptions =
                    EditorInfo.IME_ACTION_SEARCH

                textSize =
                    16f

                setTextColor(
                    Color.WHITE
                )

                setHintTextColor(
                    Color.GRAY
                )

                setPadding(
                    dp(14),
                    0,
                    dp(14),
                    0
                )

                background =
                    rounded(
                        Color.rgb(
                            27,
                            27,
                            27
                        ),
                        12f
                    )
            }


        row.addView(
            input,
            LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
            )
        )


        val search =
            Button(this).apply {
                text = "Cari"
            }


        val searchParams =
            LinearLayout.LayoutParams(
                dp(85),
                dp(52)
            )


        searchParams.marginStart =
            dp(8)


        row.addView(
            search,
            searchParams
        )


        root.addView(
            row
        )


        val status =
            TextView(this).apply {

                text =
                    "Cari drama lalu tap hasilnya."

                textSize =
                    13f

                setTextColor(
                    Color.GRAY
                )

                setPadding(
                    dp(3),
                    dp(15),
                    dp(3),
                    dp(12)
                )
            }


        root.addView(
            status
        )


        val scroll =
            ScrollView(this)


        val results =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
            }


        scroll.addView(
            results
        )


        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )


        fun performSearch() {

            val keyword =
                input.text
                    .toString()
                    .trim()


            if (
                keyword.isEmpty()
            ) {
                return
            }


            status.text =
                "Mencari..."


            results.removeAllViews()


            searchBooks(
                keyword,
                status,
                results
            )
        }


        search.setOnClickListener {
            performSearch()
        }


        input.setOnEditorActionListener {
                _,
                action,
                _ ->

            if (
                action ==
                EditorInfo.IME_ACTION_SEARCH
            ) {

                performSearch()

                true

            } else {

                false

            }
        }
    }


    private fun searchBooks(
        keyword: String,
        status: TextView,
        results: LinearLayout
    ) {

        Thread {

            try {

                val encoded =
                    URLEncoder.encode(
                        keyword,
                        "UTF-8"
                    )


                val response =
                    httpGet(
                        "$api/search?lang=in&keyword=$encoded"
                    )


                val json =
                    JSONObject(
                        response
                    )


                if (
                    !json.optBoolean(
                        "ok"
                    )
                ) {

                    throw Exception(
                        "API search gagal"
                    )
                }


                val items =
                    json.getJSONArray(
                        "items"
                    )


                val books =
                    mutableListOf<Book>()


                for (
                    i in 0 until items.length()
                ) {

                    val item =
                        items.getJSONObject(
                            i
                        )


                    val themes =
                        item.optJSONArray(
                            "theme"
                        )


                    val theme =
                        buildString {

                            if (
                                themes != null
                            ) {

                                for (
                                    j in 0 until themes.length()
                                ) {

                                    if (
                                        j > 0
                                    ) {
                                        append(
                                            ", "
                                        )
                                    }


                                    append(
                                        themes.optString(
                                            j
                                        )
                                    )
                                }
                            }
                        }


                    books.add(

                        Book(

                            id =
                                item.optString(
                                    "book_id"
                                ),

                            title =
                                item.optString(
                                    "title"
                                ),

                            chapters =
                                item.optInt(
                                    "chapter_count"
                                ),

                            theme =
                                theme
                        )
                    )
                }


                runOnUiThread {

                    status.text =
                        "${books.size} hasil ditemukan"


                    books.forEach { book ->

                        addBookCard(
                            book,
                            results
                        )
                    }
                }

            } catch (
                e: Exception
            ) {

                e.printStackTrace()


                runOnUiThread {

                    status.text =
                        "Gagal: ${e.message}"
                }
            }

        }.start()
    }


    private fun addBookCard(
        book: Book,
        container: LinearLayout
    ) {

        val card =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(15),
                    dp(16),
                    dp(15)
                )

                background =
                    rounded(
                        Color.rgb(
                            27,
                            27,
                            27
                        ),
                        14f
                    )

                isClickable =
                    true

                isFocusable =
                    true
            }


        val title =
            TextView(this).apply {

                text =
                    book.title

                textSize =
                    16f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    null,
                    Typeface.BOLD
                )
            }


        card.addView(
            title
        )


        val meta =
            TextView(this).apply {

                text =
                    buildString {

                        append(
                            "${book.chapters} Episode"
                        )


                        if (
                            book.theme.isNotEmpty()
                        ) {

                            append(
                                "  •  ${book.theme}"
                            )
                        }
                    }

                textSize =
                    13f

                setTextColor(
                    Color.GRAY
                )

                setPadding(
                    0,
                    dp(7),
                    0,
                    0
                )
            }


        card.addView(
            meta
        )


        val params =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )


        params.bottomMargin =
            dp(10)


        container.addView(
            card,
            params
        )


        card.setOnClickListener {

            startFloatingPlayer(
                book
            )
        }
    }


    private fun startFloatingPlayer(
        book: Book
    ) {

        if (
            !Settings.canDrawOverlays(
                this
            )
        ) {

            requestOverlayPermission()

            return
        }


        val intent =
            Intent(
                this,
                FloatingPlayerService::class.java
            ).apply {

                putExtra(
                    FloatingPlayerService.EXTRA_BOOK_ID,
                    book.id
                )

                putExtra(
                    FloatingPlayerService.EXTRA_BOOK_TITLE,
                    book.title
                )
            }


        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            startForegroundService(
                intent
            )

        } else {

            startService(
                intent
            )
        }


        /*
         * Activity selesai.
         * Android balik ke app/game sebelumnya.
         */

        finish()
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
            "ReelShortFloating/2.0"
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

            setColor(
                color
            )

            cornerRadius =
                dp(radiusDp)
                    .toFloat()
        }
    }


    private fun dp(
        value: Int
    ): Int {

        return (
            value *
            resources.displayMetrics.density
        ).toInt()
    }


    private fun dp(
        value: Float
    ): Int {

        return (
            value *
            resources.displayMetrics.density
        ).toInt()
    }
}
EOF


# ============================================================
# FloatingPlayerService.kt
# ============================================================

cat > app/src/main/java/com/shikuro/reelshort/FloatingPlayerService.kt <<'EOF'
package com.shikuro.reelshort

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class FloatingPlayerService : Service() {

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_BOOK_TITLE = "book_title"

        private const val API =
            "https://reelshort.vercel.app"

        private const val CHANNEL_ID =
            "reelshort_floating"

        private const val NOTIFICATION_ID =
            6969
    }

    data class Episode(
        val index: Int,
        val chapterId: String,
        val url: String
    )

    private lateinit var wm: WindowManager

    private var expandedRoot: FrameLayout? = null
    private var bubbleRoot: FrameLayout? = null
    private var cardView: LinearLayout? = null

    private var expandedParams:
        WindowManager.LayoutParams? = null

    private var bubbleParams:
        WindowManager.LayoutParams? = null

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    private var loadingText: TextView? = null
    private var menuPanel: LinearLayout? = null
    private var fullscreenText: TextView? = null
    private var resizeHandle: View? = null

    private val episodes =
        mutableListOf<Episode>()

    private var currentEpisodePosition = 0

    private var currentBookId = ""
    private var currentBookTitle = ""

    private var loadingBook = false

    private var fullscreen = false

    private var restoreX = 0
    private var restoreY = 0
    private var restoreWidth = 0
    private var restoreHeight = 0


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
                            loadingText?.apply {
                                visibility = View.VISIBLE
                                text = "Memuat..."
                            }
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
                    loadingText?.apply {
                        visibility = View.VISIBLE
                        text = "Video gagal diputar"
                    }
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
            !Settings.canDrawOverlays(this)
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

        if (bookId.isEmpty()) {

            if (expandedRoot != null) {
                restoreFromBubble()
            }

            return START_STICKY
        }

        if (expandedRoot == null) {
            createExpandedWindow()
            createBubbleWindow()
        }

        restoreFromBubble()

        if (
            bookId != currentBookId ||
            episodes.isEmpty()
        ) {
            currentBookId = bookId
            currentBookTitle = title

            loadBookEpisodes(
                bookId,
                title
            )
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
                currentEpisodePosition
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
                    currentBookTitle.ifEmpty {
                        "ReelShort Floating"
                    }
                )
                .setContentText(text)
                .setOngoing(true)
                .build()

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            notification
        )
    }


    /*
     * =========================================================
     * FLOATING WINDOW
     * =========================================================
     */

    private fun createExpandedWindow() {

        val metrics =
            resources.displayMetrics

        val screenWidth =
            metrics.widthPixels

        val screenHeight =
            metrics.heightPixels

        /*
         * Landscape sekitar 38% layar.
         *
         * Tinggi cuma video 16:9 +
         * chrome kecil sekitar 60dp.
         */

        val width =
            if (screenWidth > screenHeight) {
                (screenWidth * 0.38f).toInt()
            } else {
                (screenWidth * 0.88f).toInt()
            }

        val videoHeight =
            (width * 9f / 16f)
                .toInt()

        val height =
            videoHeight +
            dp(60)

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

                x = dp(20)
                y = dp(80)
            }


        val root =
            FrameLayout(this)

        expandedRoot =
            root


        /*
         * CARD
         */

        val card =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                background =
                    rounded(
                        Color.rgb(
                            12,
                            12,
                            12
                        ),
                        18f
                    )
            }

        cardView = card

        root.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )


        /*
         * ONE UI STYLE TOP HANDLE
         */

        val chrome =
            FrameLayout(this)

        card.addView(
            chrome,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
            )
        )


        /*
         * Area drag dibuat lebih besar
         * daripada garisnya supaya gampang disentuh.
         */

        val dragZone =
            FrameLayout(this)

        chrome.addView(
            dragZone,
            FrameLayout.LayoutParams(
                dp(130),
                dp(20),
                Gravity.TOP or
                    Gravity.CENTER_HORIZONTAL
            )
        )


        val handle =
            View(this).apply {

                background =
                    rounded(
                        Color.rgb(
                            130,
                            130,
                            130
                        ),
                        10f
                    )
            }

        dragZone.addView(
            handle,
            FrameLayout.LayoutParams(
                dp(62),
                dp(4),
                Gravity.CENTER
            )
        )

        enableWindowDrag(
            dragZone
        )


        /*
         * THREE DOTS
         */

        val dots =
            TextView(this).apply {

                text = "⋯"

                textSize = 25f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setOnClickListener {
                    toggleMenu()
                }
            }

        val dotsParams =
            FrameLayout.LayoutParams(
                dp(60),
                dp(30),
                Gravity.BOTTOM or
                    Gravity.CENTER_HORIZONTAL
            )

        chrome.addView(
            dots,
            dotsParams
        )


        /*
         * VIDEO
         */

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

                setBackgroundColor(
                    Color.BLACK
                )

                setOnClickListener {
                    togglePlayPause()
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

                gravity =
                    Gravity.CENTER

                textSize =
                    12f

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
         * RESIZE HANDLE
         */

        val resize =
            TextView(this).apply {

                text =
                    "◢"

                textSize =
                    15f

                gravity =
                    Gravity.END or
                    Gravity.CENTER_VERTICAL

                setTextColor(
                    Color.rgb(
                        110,
                        110,
                        110
                    )
                )

                setPadding(
                    0,
                    0,
                    dp(8),
                    0
                )
            }

        resizeHandle =
            resize

        card.addView(
            resize,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(18)
            )
        )

        enableResize(
            resize
        )


        /*
         * POPUP MENU
         */

        createMenu(
            root
        )


        wm.addView(
            root,
            expandedParams
        )
    }


    /*
     * =========================================================
     * THREE DOT MENU
     * =========================================================
     */

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
                        15f
                    )

                setPadding(
                    dp(6),
                    dp(6),
                    dp(6),
                    dp(6)
                )
            }


        val full =
            menuItem(
                "⛶    Layar penuh"
            ) {
                toggleFullscreen()
                hideMenu()
            }

        fullscreenText =
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


        menu.addView(
            full
        )

        menu.addView(
            minimize
        )

        menu.addView(
            close
        )


        val params =
            FrameLayout.LayoutParams(
                dp(210),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or
                    Gravity.CENTER_HORIZONTAL
            )

        params.topMargin =
            dp(38)


        parent.addView(
            menu,
            params
        )


        menuPanel =
            menu
    }


    private fun menuItem(
        label: String,
        action: () -> Unit
    ): TextView {

        return TextView(this).apply {

            text =
                label

            textSize =
                14f

            gravity =
                Gravity.CENTER_VERTICAL

            setTextColor(
                Color.WHITE
            )

            setPadding(
                dp(16),
                0,
                dp(16),
                0
            )

            background =
                selectableBackground()

            setOnClickListener {
                action()
            }

            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(48)
                )
        }
    }


    private fun toggleMenu() {

        val menu =
            menuPanel
                ?: return

        menu.visibility =
            if (
                menu.visibility ==
                View.VISIBLE
            ) {
                View.GONE
            } else {
                View.VISIBLE
            }
    }


    private fun hideMenu() {
        menuPanel?.visibility =
            View.GONE
    }


    /*
     * =========================================================
     * FULL SCREEN OVERLAY
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


            params.x = 0
            params.y = 0

            params.width =
                metrics.widthPixels

            params.height =
                metrics.heightPixels


            fullscreen =
                true


            fullscreenText?.text =
                "↙    Kembalikan ukuran"


            resizeHandle?.visibility =
                View.GONE


            cardView?.background =
                rounded(
                    Color.BLACK,
                    0f
                )

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


            fullscreenText?.text =
                "⛶    Layar penuh"


            resizeHandle?.visibility =
                View.VISIBLE


            cardView?.background =
                rounded(
                    Color.rgb(
                        12,
                        12,
                        12
                    ),
                    18f
                )
        }


        wm.updateViewLayout(
            root,
            params
        )
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

                text = "R"

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
                                25,
                                25,
                                25
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

        hideMenu()

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
    }


    /*
     * =========================================================
     * EPISODES
     * =========================================================
     */

    private fun loadBookEpisodes(
        bookId: String,
        fallbackTitle: String
    ) {

        if (loadingBook) {
            return
        }

        loadingBook =
            true

        episodes.clear()


        loadingText?.apply {
            visibility =
                View.VISIBLE

            text =
                "Memuat..."
        }


        Thread {

            try {

                val response =
                    httpGet(
                        "$API/api/stream/all-episode?lang=in&bookId=$bookId"
                    )


                val json =
                    JSONObject(
                        response
                    )


                if (
                    !json.optBoolean(
                        "ok"
                    )
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
                        array.getJSONObject(
                            i
                        )


                    val url =
                        selectVideoUrl(
                            item
                        )


                    if (
                        url.isNotEmpty()
                    ) {

                        loaded.add(
                            Episode(
                                index =
                                    item.optInt(
                                        "index",
                                        i + 1
                                    ),

                                chapterId =
                                    item.optString(
                                        "chapterId"
                                    ),

                                url =
                                    url
                            )
                        )
                    }
                }


                if (
                    loaded.isEmpty()
                ) {
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


                    val prefs =
                        getSharedPreferences(
                            "player",
                            MODE_PRIVATE
                        )


                    val saved =
                        prefs.getInt(
                            "episode_$bookId",
                            0
                        )


                    currentEpisodePosition =
                        saved.coerceIn(
                            0,
                            episodes.lastIndex
                        )


                    playEpisode(
                        currentEpisodePosition
                    )
                }


            } catch (
                e: Exception
            ) {

                e.printStackTrace()


                runOnMain {

                    loadingBook =
                        false


                    loadingText?.apply {

                        visibility =
                            View.VISIBLE

                        text =
                            "Gagal memuat video"
                    }
                }
            }

        }.start()
    }


    private fun selectVideoUrl(
        item: JSONObject
    ): String {

        var result =
            item.optString(
                "sourceVideoUrl"
            )


        if (
            result.isEmpty()
        ) {

            val streams =
                item.optJSONArray(
                    "streams"
                )


            if (
                streams != null &&
                streams.length() > 0
            ) {

                result =
                    streams
                        .optJSONObject(0)
                        ?.optString(
                            "sourceUrl"
                        )
                        .orEmpty()
            }
        }


        if (
            result.isEmpty()
        ) {
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


    private fun playEpisode(
        position: Int
    ) {

        if (
            position !in
            episodes.indices
        ) {
            return
        }


        currentEpisodePosition =
            position


        val episode =
            episodes[
                position
            ]


        loadingText?.apply {

            visibility =
                View.VISIBLE

            text =
                "Memuat..."
        }


        player?.apply {

            setMediaItem(
                MediaItem.fromUri(
                    episode.url
                )
            )

            prepare()

            playWhenReady =
                true
        }


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

        if (
            episodes.isEmpty()
        ) {
            return
        }


        val next =
            currentEpisodePosition + 1


        if (
            next <=
            episodes.lastIndex
        ) {
            playEpisode(
                next
            )
        }
    }


    private fun togglePlayPause() {

        val p =
            player
                ?: return


        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
    }


    /*
     * =========================================================
     * DRAG WINDOW
     * =========================================================
     */

    private fun enableWindowDrag(
        target: View
    ) {

        target.setOnTouchListener(
            object : View.OnTouchListener {

                var startX = 0
                var startY = 0

                var touchX = 0f
                var touchY = 0f


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

                            startX =
                                params.x

                            startY =
                                params.y

                            touchX =
                                event.rawX

                            touchY =
                                event.rawY

                            return true
                        }


                        MotionEvent.ACTION_MOVE -> {

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
                                    (
                                        event.rawX -
                                        touchX
                                    ).toInt()
                                ).coerceIn(
                                    0,
                                    maxX
                                )


                            params.y =
                                (
                                    startY +
                                    (
                                        event.rawY -
                                        touchY
                                    ).toInt()
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
                    }


                    return false
                }
            }
        )
    }


    /*
     * =========================================================
     * RESIZE
     * =========================================================
     */

    private fun enableResize(
        target: View
    ) {

        target.setOnTouchListener(
            object : View.OnTouchListener {

                var startWidth = 0
                var startHeight = 0

                var touchX = 0f
                var touchY = 0f


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
                                dp(230)


                            val minHeight =
                                dp(170)


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
     * BUBBLE DRAG
     * =========================================================
     */

    private fun enableBubbleDrag(
        target: View
    ) {

        target.setOnTouchListener(
            object : View.OnTouchListener {

                var startX = 0
                var startY = 0

                var touchX = 0f
                var touchY = 0f

                var moved = false


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
                                abs(dx) > dp(5) ||
                                abs(dy) > dp(5)
                            ) {
                                moved = true
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
     * HTTP
     * =========================================================
     */

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
            "ReelShortFloating/3.0 Android"
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


    /*
     * =========================================================
     * UI HELPERS
     * =========================================================
     */

    private fun rounded(
        color: Int,
        radiusDp: Float
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(
                color
            )

            cornerRadius =
                dp(radiusDp)
                    .toFloat()
        }
    }


    private fun selectableBackground():
        GradientDrawable {

        return GradientDrawable().apply {

            setColor(
                Color.TRANSPARENT
            )

            cornerRadius =
                dp(10)
                    .toFloat()
        }
    }


    private fun dp(
        value: Int
    ): Int {

        return (
            value *
            resources.displayMetrics.density
        ).toInt()
    }


    private fun dp(
        value: Float
    ): Int {

        return (
            value *
            resources.displayMetrics.density
        ).toInt()
    }


    private fun runOnMain(
        action: () -> Unit
    ) {

        android.os.Handler(
            mainLooper
        ).post(
            action
        )
    }


    /*
     * =========================================================
     * CLEANUP
     * =========================================================
     */

    override fun onDestroy() {

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
    ): IBinder? {
        return null
    }
}
EOF



# ============================================================
# .gitignore
# ============================================================

cat > .gitignore <<'EOF'
.gradle/
.idea/
local.properties
build/
app/build/
*.iml
.DS_Store
EOF


# ============================================================
# GitHub Actions
# ============================================================

cat > .github/workflows/build.yml <<'EOF'
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
          name: ReelShort-Floating-Overlay
          path: app/build/outputs/apk/debug/app-debug.apk
EOF


echo
echo "=========================================="
echo " OVERLAY PROJECT BERHASIL DIGENERATE"
echo "=========================================="
echo
echo "Mode:"
echo "  - NO PiP"
echo "  - resizable overlay"
echo "  - draggable window"
echo "  - minimize bubble"
echo "  - draggable bubble"
echo "  - auto next"
echo "  - episode picker"
echo "  - save episode terakhir"
echo
echo "Files:"
find . -type f \
  -not -path "./.git/*" \
  | sort
echo
