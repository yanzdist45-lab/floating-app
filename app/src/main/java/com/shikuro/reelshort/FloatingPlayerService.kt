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
