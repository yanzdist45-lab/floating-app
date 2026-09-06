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
