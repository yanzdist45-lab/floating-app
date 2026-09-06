package com.shikuro.reelshort

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
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
import kotlin.math.abs

class ShortsActivity : Activity() {

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_BOOK_TITLE = "book_title"
        private const val API = "https://reelshort.vercel.app"
    }

    data class Episode(
        val index: Int,
        val url: String
    )

    private lateinit var root: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer

    private lateinit var topChrome: FrameLayout
    private lateinit var bottomMeta: LinearLayout
    private lateinit var rightRail: LinearLayout
    private lateinit var centerPlay: TextView
    private lateinit var titleText: TextView
    private lateinit var episodeText: TextView
    private lateinit var loadingText: TextView
    private lateinit var progressTrack: FrameLayout
    private lateinit var progressFill: View

    private val episodes = mutableListOf<Episode>()

    private var currentPosition = 0
    private var bookId = ""
    private var bookTitle = ""

    private var touchStartY = 0f
    private var touchStartX = 0f
    private var touchStartedAt = 0L

    private val handler = Handler(Looper.getMainLooper())

    private val hideControlsRunnable = Runnable {
        if (::player.isInitialized && player.isPlaying) {
            setControlsVisible(false)
        }
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
        bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE).orEmpty()

        if (bookId.isBlank()) {
            finish()
            return
        }

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        hideSystemBars()

        buildUi()

        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        loadingText.visibility = View.VISIBLE
                        loadingText.text = "Memuat..."
                    }

                    Player.STATE_READY -> {
                        loadingText.visibility = View.GONE
                    }

                    Player.STATE_ENDED -> nextEpisode()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    centerPlay.animate()
                        .alpha(0f)
                        .setDuration(140)
                        .withEndAction {
                            centerPlay.visibility = View.GONE
                        }
                        .start()
                    showControlsTemporarily()
                } else {
                    handler.removeCallbacks(hideControlsRunnable)
                    setControlsVisible(true)
                    centerPlay.text = "▶"
                    centerPlay.alpha = 1f
                    centerPlay.visibility = View.VISIBLE
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                loadingText.visibility = View.VISIBLE
                loadingText.text = "Video gagal diputar"
            }
        })

        handler.post(progressRunnable)
        loadEpisodes()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(
                    WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars()
                )
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        setContentView(root)

        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(Color.BLACK)
        }

        root.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        loadingText = TextView(this).apply {
            text = "Memuat episode..."
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(50, 0, 0, 0))
        }

        root.addView(
            loadingText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        topChrome = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(190, 0, 0, 0),
                    Color.argb(0, 0, 0, 0)
                )
            )
        }

        root.addView(
            topChrome,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(92),
                Gravity.TOP
            )
        )

        val back = TextView(this).apply {
            text = "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = pill(Color.argb(120, 24, 24, 28), 999f, true)
            setOnClickListener { finish() }
        }

        topChrome.addView(
            back,
            FrameLayout.LayoutParams(
                dp(42),
                dp(42),
                Gravity.START or Gravity.TOP
            ).apply {
                leftMargin = dp(14)
                topMargin = dp(12)
            }
        )

        val shortsLabel = TextView(this).apply {
            text = "Shorts"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
        }

        topChrome.addView(
            shortsLabel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42),
                Gravity.START or Gravity.TOP
            ).apply {
                leftMargin = dp(66)
                topMargin = dp(12)
            }
        )

        bottomMeta = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(76), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(
                    Color.argb(205, 0, 0, 0),
                    Color.argb(0, 0, 0, 0)
                )
            )
        }

        titleText = TextView(this).apply {
            text = bookTitle
            textSize = 15f
            maxLines = 2
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }

        episodeText = TextView(this).apply {
            text = "Episode"
            textSize = 12f
            setTextColor(Color.argb(205, 255, 255, 255))
            setPadding(0, dp(5), 0, 0)
        }

        val swipeHint = TextView(this).apply {
            text = "Swipe ↑↓ untuk pindah episode"
            textSize = 11f
            setTextColor(Color.argb(150, 255, 255, 255))
            setPadding(0, dp(5), 0, 0)
        }

        bottomMeta.addView(titleText)
        bottomMeta.addView(episodeText)
        bottomMeta.addView(swipeHint)

        root.addView(
            bottomMeta,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(142),
                Gravity.BOTTOM
            )
        )

        rightRail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val episodesButton = railButton("EP") {
            showEpisodePicker()
            showControlsTemporarily()
        }

        val previousButton = railButton("↑") {
            previousEpisode()
            showControlsTemporarily()
        }

        val nextButton = railButton("↓") {
            nextEpisode()
            showControlsTemporarily()
        }

        rightRail.addView(episodesButton)
        rightRail.addView(spacer(dp(10)))
        rightRail.addView(previousButton)
        rightRail.addView(spacer(dp(10)))
        rightRail.addView(nextButton)

        root.addView(
            rightRail,
            FrameLayout.LayoutParams(
                dp(62),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.BOTTOM
            ).apply {
                rightMargin = dp(10)
                bottomMargin = dp(116)
            }
        )

        centerPlay = TextView(this).apply {
            text = "▶"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = pill(Color.argb(155, 18, 20, 26), 999f, true)
            visibility = View.GONE
        }

        root.addView(
            centerPlay,
            FrameLayout.LayoutParams(
                dp(74),
                dp(74),
                Gravity.CENTER
            )
        )

        progressTrack = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(80, 255, 255, 255))
        }

        progressFill = View(this).apply {
            setBackgroundColor(Color.WHITE)
        }

        progressTrack.addView(
            progressFill,
            FrameLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START
            )
        )

        root.addView(
            progressTrack,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3),
                Gravity.BOTTOM
            )
        )

        playerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    touchStartedAt = System.currentTimeMillis()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    val elapsed = System.currentTimeMillis() - touchStartedAt
                    val vertical = abs(dy) > abs(dx)

                    when {
                        elapsed < 850 && vertical && dy < -dp(68) -> {
                            nextEpisode()
                            showControlsTemporarily()
                        }

                        elapsed < 850 && vertical && dy > dp(68) -> {
                            previousEpisode()
                            showControlsTemporarily()
                        }

                        abs(dx) < dp(16) && abs(dy) < dp(16) -> {
                            togglePlayPause()
                            showControlsTemporarily()
                        }

                        else -> showControlsTemporarily()
                    }
                    true
                }

                else -> true
            }
        }
    }

    private fun railButton(label: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = if (label.length > 1) 13f else 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = pill(Color.argb(135, 20, 23, 30), 999f, true)
            elevation = dp(4).toFloat()
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
    }

    private fun spacer(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, height)
        }
    }

    private fun loadEpisodes() {
        Thread {
            try {
                val body = httpGet(
                    "$API/api/stream/all-episode?lang=in&bookId=$bookId"
                )
                val json = JSONObject(body)

                if (!json.optBoolean("ok")) {
                    throw Exception("API episode gagal")
                }

                val array = json.getJSONArray("episodes")
                val loaded = mutableListOf<Episode>()

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val url = selectVideoUrl(item)
                    if (url.isNotBlank()) {
                        loaded.add(
                            Episode(
                                index = item.optInt("index", i + 1),
                                url = url
                            )
                        )
                    }
                }

                if (loaded.isEmpty()) {
                    throw Exception("Tidak ada stream")
                }

                val realTitle = json.optString("title", bookTitle)

                runOnUiThread {
                    episodes.clear()
                    episodes.addAll(loaded)
                    bookTitle = realTitle
                    titleText.text = realTitle

                    val saved = getSharedPreferences("player", MODE_PRIVATE)
                        .getInt("episode_$bookId", 0)

                    currentPosition = saved.coerceIn(0, episodes.lastIndex)
                    playEpisode(currentPosition)
                    showControlsTemporarily()
                }
            } catch (error: Exception) {
                error.printStackTrace()
                runOnUiThread {
                    loadingText.visibility = View.VISIBLE
                    loadingText.text = "Gagal memuat episode"
                    Toast.makeText(
                        this,
                        error.message ?: "Gagal",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun playEpisode(position: Int) {
        if (position !in episodes.indices) return

        currentPosition = position
        val episode = episodes[position]

        episodeText.text =
            "EP.${episode.index} / EP.${episodes.size}"

        loadingText.visibility = View.VISIBLE
        loadingText.text = "Memuat..."

        player.setMediaItem(MediaItem.fromUri(episode.url))
        player.prepare()
        player.playWhenReady = true

        getSharedPreferences("player", MODE_PRIVATE)
            .edit()
            .putInt("episode_$bookId", position)
            .apply()

        showControlsTemporarily()
    }

    private fun nextEpisode() {
        val next = currentPosition + 1
        if (next <= episodes.lastIndex) playEpisode(next)
    }

    private fun previousEpisode() {
        val previous = currentPosition - 1
        if (previous >= 0) playEpisode(previous)
    }

    private fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    private fun showControlsTemporarily() {
        handler.removeCallbacks(hideControlsRunnable)
        setControlsVisible(true)
        if (::player.isInitialized && player.isPlaying) {
            handler.postDelayed(hideControlsRunnable, 2400)
        }
    }

    private fun setControlsVisible(visible: Boolean) {
        val target = if (visible) 1f else 0f
        val duration = if (visible) 150L else 240L

        listOf<View>(topChrome, bottomMeta, rightRail).forEach { view ->
            view.animate()
                .alpha(target)
                .setDuration(duration)
                .start()
            view.isClickable = visible
        }
    }

    private fun updateProgress() {
        if (!::player.isInitialized || !::progressTrack.isInitialized) return

        val duration = player.duration
        val position = player.currentPosition

        if (duration <= 0L || progressTrack.width <= 0) {
            progressFill.layoutParams = progressFill.layoutParams.apply {
                width = 0
            }
            return
        }

        val ratio = (position.toDouble() / duration.toDouble())
            .coerceIn(0.0, 1.0)

        progressFill.layoutParams = progressFill.layoutParams.apply {
            width = (progressTrack.width * ratio).toInt()
        }
        progressFill.requestLayout()
    }

    private fun showEpisodePicker() {
        if (episodes.isEmpty()) return

        val dialog = Dialog(this)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(20))
            background = pill(Color.rgb(18, 21, 28), 24f, true)
        }

        val handle = View(this).apply {
            background = pill(Color.argb(90, 255, 255, 255), 999f, false)
        }

        outer.addView(
            handle,
            LinearLayout.LayoutParams(dp(46), dp(5)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16)
            }
        )

        val header = TextView(this).apply {
            text = "Episode  •  ${episodes.size} tersedia"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), 0, dp(4), dp(12))
        }
        outer.addView(header)

        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply { columnCount = 4 }

        episodes.forEachIndexed { position, episode ->
            val button = TextView(this).apply {
                text = "${episode.index}"
                gravity = Gravity.CENTER
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(
                    if (position == currentPosition) Color.BLACK else Color.WHITE
                )
                background = if (position == currentPosition) {
                    pill(Color.WHITE, 12f, false)
                } else {
                    pill(Color.rgb(31, 35, 44), 12f, true)
                }
                setOnClickListener {
                    dialog.dismiss()
                    playEpisode(position)
                }
            }

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(48)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            grid.addView(button, params)
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
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.58f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.BOTTOM)
        }

        dialog.setOnDismissListener {
            hideSystemBars()
            showControlsTemporarily()
        }

        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.68f).toInt()
        )
    }

    private fun selectVideoUrl(item: JSONObject): String {
        var result = item.optString("sourceVideoUrl")

        if (result.isBlank()) {
            val streams = item.optJSONArray("streams")
            if (streams != null && streams.length() > 0) {
                result = streams.optJSONObject(0)
                    ?.optString("sourceUrl")
                    .orEmpty()
            }
        }

        if (result.isBlank()) {
            result = item.optString("videoUrl")
        }

        if (result.startsWith("http://")) {
            result = "https://" + result.removePrefix("http://")
        }

        return result
    }

    private fun httpGet(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty(
            "User-Agent",
            "ReelShortFloating/5.0 Android"
        )

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: ""

            if (code !in 200..299) {
                throw Exception("HTTP $code")
            }

            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun pill(
        color: Int,
        radiusDp: Float,
        stroke: Boolean
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke) {
                setStroke(
                    dp(1),
                    Color.argb(28, 255, 255, 255)
                )
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::playerView.isInitialized) playerView.player = null
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
