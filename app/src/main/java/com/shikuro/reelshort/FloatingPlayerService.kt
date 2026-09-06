package com.shikuro.reelshort

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
        val chapterId: String,
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


    private var titleText:
            TextView? = null


    private var episodeButton:
            Button? = null


    private var loadingText:
            TextView? = null


    private var episodePanel:
            LinearLayout? = null


    private var episodeGrid:
            GridLayout? = null


    private val episodes =
        mutableListOf<Episode>()


    private var currentEpisodePosition =
        0


    private var currentBookId =
        ""


    private var currentBookTitle =
        ""


    private var loadingBook =
        false


    override fun onCreate() {
        super.onCreate()

        wm =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager


        startForegroundNotification()


        player =
            ExoPlayer
                .Builder(this)
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


        /*
         * Tidak ada book baru?
         * Restore floating yang sudah ada.
         */

        if (
            bookId.isEmpty()
        ) {

            restoreFromBubble()

            return START_STICKY
        }


        /*
         * Pastikan UI overlay sudah dibuat.
         */

        if (
            expandedRoot == null
        ) {

            createExpandedWindow()
            createBubbleWindow()
        }


        restoreFromBubble()


        /*
         * Kalau drama berbeda, load ulang episode.
         */

        if (
            bookId != currentBookId ||
            episodes.isEmpty()
        ) {

            currentBookId =
                bookId


            currentBookTitle =
                title


            loadBookEpisodes(
                bookId,
                title
            )

        } else {

            updateHeader()
        }


        return START_STICKY
    }


    /*
     * =========================================================
     * FOREGROUND NOTIFICATION
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

                description =
                    "Menjaga floating video tetap aktif"

                setSound(
                    null,
                    null
                )
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
            Notification
                .Builder(
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

                .setOngoing(
                    true
                )

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
            if (
                episode != null
            ) {

                "EP.${episode.index} / EP.${episodes.size}"

            } else {

                "Floating player aktif"
            }


        val notification =
            Notification
                .Builder(
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

                .setContentText(
                    text
                )

                .setOngoing(
                    true
                )

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
     * EXPANDED WINDOW
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
         * Landscape:
         * sekitar 38% lebar layar seperti screenshot lu.
         *
         * Portrait:
         * sekitar 88%.
         */

        val initialWidth =
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


        val initialHeight =
            (
                initialWidth *
                0.72f
            ).toInt()


        expandedParams =
            WindowManager.LayoutParams(

                initialWidth,

                initialHeight,

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
                    dp(25)


                y =
                    dp(90)
            }


        val root =
            FrameLayout(this)


        /*
         * CARD
         */

        val card =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                background =
                    rounded(
                        Color.argb(
                            245,
                            15,
                            15,
                            15
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
         * HEADER
         */

        val header =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(10),
                    dp(5),
                    dp(6),
                    dp(5)
                )
            }


        val dragTitle =
            TextView(this).apply {

                text =
                    "ReelShort"

                textSize =
                    13f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(7),
                    0,
                    dp(8),
                    0
                )
            }


        titleText =
            dragTitle


        header.addView(
            dragTitle,
            LinearLayout.LayoutParams(
                0,
                dp(40),
                1f
            )
        )


        val minimize =
            TextView(this).apply {

                text =
                    "—"

                textSize =
                    22f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setOnClickListener {

                    minimizeToBubble()
                }
            }


        header.addView(
            minimize,
            LinearLayout.LayoutParams(
                dp(44),
                dp(40)
            )
        )


        val close =
            TextView(this).apply {

                text =
                    "×"

                textSize =
                    23f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setOnClickListener {

                    stopSelf()
                }
            }


        header.addView(
            close,
            LinearLayout.LayoutParams(
                dp(44),
                dp(40)
            )
        )


        card.addView(
            header
        )


        enableWindowDrag(
            dragTitle
        )


        /*
         * VIDEO AREA
         */

        val playerContainer =
            FrameLayout(this).apply {

                setBackgroundColor(
                    Color.BLACK
                )
            }


        playerView =
            PlayerView(this).apply {

                player =
                    this@FloatingPlayerService.player

                useController =
                    false

                setBackgroundColor(
                    Color.BLACK
                )


                /*
                 * Tap video = play / pause.
                 */

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


        /*
         * Loading label
         */

        loadingText =
            TextView(this).apply {

                text =
                    "Memuat..."

                textSize =
                    13f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setBackgroundColor(
                    Color.argb(
                        80,
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


        card.addView(

            playerContainer,

            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )


        /*
         * CONTROLS
         */

        val controls =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(6),
                    dp(4),
                    dp(6),
                    dp(4)
                )
            }


        val previous =
            Button(this).apply {

                text =
                    "‹"

                textSize =
                    21f

                setOnClickListener {

                    previousEpisode()
                }
            }


        controls.addView(

            previous,

            LinearLayout.LayoutParams(
                dp(55),
                dp(44)
            )
        )


        episodeButton =
            Button(this).apply {

                text =
                    "EP.- / EP.-"

                textSize =
                    12f

                setOnClickListener {

                    toggleEpisodePanel()
                }
            }


        controls.addView(

            episodeButton,

            LinearLayout.LayoutParams(
                0,
                dp(44),
                1f
            )
        )


        val next =
            Button(this).apply {

                text =
                    "›"

                textSize =
                    21f

                setOnClickListener {

                    nextEpisode()
                }
            }


        controls.addView(

            next,

            LinearLayout.LayoutParams(
                dp(55),
                dp(44)
            )
        )


        card.addView(
            controls
        )


        /*
         * RESIZE HANDLE
         */

        val resize =
            TextView(this).apply {

                text =
                    "◢"

                textSize =
                    17f

                gravity =
                    Gravity.END or
                    Gravity.CENTER_VERTICAL

                setTextColor(
                    Color.GRAY
                )

                setPadding(
                    0,
                    0,
                    dp(9),
                    0
                )
            }


        card.addView(

            resize,

            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(20)
            )
        )


        enableResize(
            resize
        )


        /*
         * EPISODE PANEL
         */

        createEpisodePanel(
            root
        )


        expandedRoot =
            root


        wm.addView(
            root,
            expandedParams
        )
    }


    /*
     * =========================================================
     * EPISODE PANEL
     * =========================================================
     */

    private fun createEpisodePanel(
        parent: FrameLayout
    ) {

        val panel =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                visibility =
                    View.GONE

                setPadding(
                    dp(10),
                    dp(8),
                    dp(10),
                    dp(10)
                )

                background =
                    rounded(
                        Color.argb(
                            250,
                            24,
                            24,
                            24
                        ),
                        14f
                    )
            }


        val top =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }


        val title =
            TextView(this).apply {

                text =
                    "Pilih Episode"

                textSize =
                    15f

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                setTextColor(
                    Color.WHITE
                )
            }


        top.addView(

            title,

            LinearLayout.LayoutParams(
                0,
                dp(42),
                1f
            )
        )


        val close =
            TextView(this).apply {

                text =
                    "×"

                textSize =
                    21f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setOnClickListener {

                    hideEpisodePanel()
                }
            }


        top.addView(

            close,

            LinearLayout.LayoutParams(
                dp(44),
                dp(42)
            )
        )


        panel.addView(
            top
        )


        val scroll =
            ScrollView(this)


        val grid =
            GridLayout(this).apply {

                columnCount =
                    4

                setPadding(
                    0,
                    0,
                    0,
                    dp(10)
                )
            }


        episodeGrid =
            grid


        scroll.addView(
            grid
        )


        panel.addView(

            scroll,

            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )


        parent.addView(

            panel,

            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {

                setMargins(
                    dp(12),
                    dp(50),
                    dp(12),
                    dp(12)
                )
            }
        )


        episodePanel =
            panel
    }


    private fun rebuildEpisodeGrid() {

        val grid =
            episodeGrid
                ?: return


        grid.removeAllViews()


        episodes.forEachIndexed {
                position,
                episode ->


            val button =
                Button(this).apply {

                    text =
                        "${episode.index}"

                    textSize =
                        11f


                    alpha =
                        if (
                            position ==
                            currentEpisodePosition
                        ) {

                            1f

                        } else {

                            0.72f
                        }


                    setOnClickListener {

                        playEpisode(
                            position
                        )

                        hideEpisodePanel()
                    }
                }


            val params =
                GridLayout.LayoutParams()


            params.width =
                0


            params.height =
                dp(46)


            params.columnSpec =
                GridLayout.spec(
                    GridLayout.UNDEFINED,
                    1f
                )


            params.setMargins(
                dp(2),
                dp(2),
                dp(2),
                dp(2)
            )


            grid.addView(
                button,
                params
            )
        }
    }


    private fun toggleEpisodePanel() {

        val panel =
            episodePanel
                ?: return


        if (
            panel.visibility ==
            View.VISIBLE
        ) {

            hideEpisodePanel()

        } else {

            rebuildEpisodeGrid()

            panel.visibility =
                View.VISIBLE
        }
    }


    private fun hideEpisodePanel() {

        episodePanel?.visibility =
            View.GONE
    }


    /*
     * =========================================================
     * BUBBLE
     * =========================================================
     */

    private fun createBubbleWindow() {

        bubbleParams =
            WindowManager.LayoutParams(

                dp(58),

                dp(58),

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
                    21f

                gravity =
                    Gravity.CENTER

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                setTextColor(
                    Color.WHITE
                )

                background =
                    GradientDrawable().apply {

                        shape =
                            GradientDrawable.OVAL

                        setColor(
                            Color.argb(
                                245,
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

        hideEpisodePanel()


        expandedRoot?.visibility =
            View.GONE


        bubbleRoot?.visibility =
            View.VISIBLE
    }


    private fun restoreFromBubble() {

        if (
            expandedRoot == null ||
            bubbleRoot == null
        ) {
            return
        }


        bubbleRoot?.visibility =
            View.GONE


        expandedRoot?.visibility =
            View.VISIBLE
    }


    /*
     * =========================================================
     * LOAD EPISODES
     * =========================================================
     */

    private fun loadBookEpisodes(
        bookId: String,
        fallbackTitle: String
    ) {

        if (
            loadingBook
        ) {
            return
        }


        loadingBook =
            true


        episodes.clear()


        loadingText?.apply {

            visibility =
                View.VISIBLE

            text =
                "Memuat episode..."
        }


        episodeButton?.text =
            "MEMUAT..."


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


                val loaded =
                    mutableListOf<Episode>()


                val array =
                    json.getJSONArray(
                        "episodes"
                    )


                for (
                    i in 0 until array.length()
                ) {

                    val item =
                        array.getJSONObject(
                            i
                        )


                    val videoUrl =
                        selectVideoUrl(
                            item
                        )


                    if (
                        videoUrl.isNotEmpty()
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
                                    videoUrl
                            )
                        )
                    }
                }


                if (
                    loaded.isEmpty()
                ) {

                    throw Exception(
                        "Tidak ada stream video"
                    )
                }


                val realTitle =
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
                        realTitle


                    loadingBook =
                        false


                    val preferences =
                        getSharedPreferences(
                            "player",
                            MODE_PRIVATE
                        )


                    val saved =
                        preferences.getInt(
                            "episode_$bookId",
                            0
                        )


                    currentEpisodePosition =
                        saved.coerceIn(
                            0,
                            episodes.lastIndex
                        )


                    rebuildEpisodeGrid()


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
                            "Gagal: ${e.message}"
                    }


                    episodeButton?.text =
                        "GAGAL"
                }
            }

        }.start()
    }


    /*
     * =========================================================
     * SELECT VIDEO URL
     * =========================================================
     */

    private fun selectVideoUrl(
        item: JSONObject
    ): String {

        /*
         * Native Android tidak punya masalah CORS,
         * jadi source HLS langsung adalah pilihan pertama.
         */

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


        /*
         * Fallback proxy.
         */

        if (
            result.isEmpty()
        ) {

            result =
                item.optString(
                    "videoUrl"
                )
        }


        /*
         * Hindari cleartext.
         */

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


    /*
     * =========================================================
     * PLAYBACK
     * =========================================================
     */

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
                "Memuat EP.${episode.index}..."
        }


        titleText?.text =
            currentBookTitle


        episodeButton?.text =
            "EP.${episode.index} / EP.${episodes.size}"


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


        rebuildEpisodeGrid()


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


    private fun previousEpisode() {

        if (
            episodes.isEmpty()
        ) {
            return
        }


        val previous =
            currentEpisodePosition - 1


        if (
            previous >= 0
        ) {

            playEpisode(
                previous
            )
        }
    }


    private fun togglePlayPause() {

        val p =
            player
                ?: return


        if (
            p.isPlaying
        ) {

            p.pause()

        } else {

            p.play()
        }
    }


    /*
     * =========================================================
     * PLAYER EVENTS
     * =========================================================
     */

    private fun installPlayerEvents() {

        /*
         * Listener utama dipasang di onCreate.
         *
         * Loading state ditangani terpisah karena
         * PlayerView bisa dibuat setelah player.
         */
    }


    /*
     * =========================================================
     * DRAG
     * =========================================================
     */

    private fun enableWindowDrag(
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
                                )
                                    .coerceAtLeast(
                                        0
                                    )


                            val maxY =
                                (
                                    metrics.heightPixels -
                                    params.height
                                )
                                    .coerceAtLeast(
                                        0
                                    )


                            params.x =
                                (
                                    startX +
                                    (
                                        event.rawX -
                                        touchX
                                    ).toInt()
                                )
                                    .coerceIn(
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
                                )
                                    .coerceIn(
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


                            val deltaX =
                                (
                                    event.rawX -
                                    touchX
                                ).toInt()


                            val deltaY =
                                (
                                    event.rawY -
                                    touchY
                                ).toInt()


                            val minWidth =
                                dp(250)


                            val minHeight =
                                dp(190)


                            val maxWidth =
                                (
                                    metrics.widthPixels -
                                    params.x
                                )
                                    .coerceAtLeast(
                                        minWidth
                                    )


                            val maxHeight =
                                (
                                    metrics.heightPixels -
                                    params.y
                                )
                                    .coerceAtLeast(
                                        minHeight
                                    )


                            params.width =
                                (
                                    startWidth +
                                    deltaX
                                )
                                    .coerceIn(
                                        minWidth,
                                        maxWidth
                                    )


                            params.height =
                                (
                                    startHeight +
                                    deltaY
                                )
                                    .coerceIn(
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
     * BUBBLE DRAG + TAP
     * =========================================================
     */

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
                                abs(dx) > dp(5) ||
                                abs(dy) > dp(5)
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
                                )
                                    .coerceAtLeast(
                                        0
                                    )


                            val maxY =
                                (
                                    metrics.heightPixels -
                                    params.height
                                )
                                    .coerceAtLeast(
                                        0
                                    )


                            params.x =
                                (
                                    startX +
                                    dx.toInt()
                                )
                                    .coerceIn(
                                        0,
                                        maxX
                                    )


                            params.y =
                                (
                                    startY +
                                    dy.toInt()
                                )
                                    .coerceIn(
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

                            if (
                                !moved
                            ) {

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
            "ReelShortFloating/2.0 Android"
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
     * HELPERS
     * =========================================================
     */

    private fun updateHeader() {

        val episode =
            episodes.getOrNull(
                currentEpisodePosition
            )


        titleText?.text =
            currentBookTitle


        episodeButton?.text =
            if (
                episode != null
            ) {

                "EP.${episode.index} / EP.${episodes.size}"

            } else {

                "EP.- / EP.-"
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

                wm.removeView(
                    it
                )
            }

        } catch (
            _: Exception
        ) {
        }


        try {

            bubbleRoot?.let {

                wm.removeView(
                    it
                )
            }

        } catch (
            _: Exception
        ) {
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
