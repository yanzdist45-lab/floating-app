package com.shikuro.reelshort

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var topControls: LinearLayout
    private lateinit var bottomControls: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var episodeText: TextView
    private lateinit var loadingText: TextView
    private lateinit var centerPlay: TextView

    private val episodes = mutableListOf<Episode>()
    private var currentPosition = 0
    private var bookId = ""
    private var bookTitle = ""

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartedAt = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { setControlsVisible(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
            bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE).orEmpty()

            if (bookId.isBlank()) {
                finish()
                return
            }

            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK

            // Init player BEFORE building UI. This avoids lateinit ordering crashes.
            player = ExoPlayer.Builder(this).build()
            buildUi()
            installPlayerListener()
            loadEpisodes()

        } catch (error: Throwable) {
            error.printStackTrace()
            Toast.makeText(
                this,
                "Shorts gagal dibuka: ${error.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        setContentView(root)

        playerView = PlayerView(this).apply {
            player = this@ShortsActivity.player
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
            setBackgroundColor(Color.argb(55, 0, 0, 0))
        }
        root.addView(
            loadingText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        centerPlay = TextView(this).apply {
            text = "▶"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            visibility = View.GONE
            background = rounded(Color.argb(190, 20, 23, 30), 999f)
            setOnClickListener {
                togglePlayPause()
                showControlsTemporarily()
            }
        }
        root.addView(
            centerPlay,
            FrameLayout.LayoutParams(dp(68), dp(68), Gravity.CENTER)
        )

        topControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(8))
            setBackgroundColor(Color.argb(90, 0, 0, 0))
        }

        val back = TextView(this).apply {
            text = "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.argb(120, 24, 27, 34), 999f)
            setOnClickListener { finish() }
        }
        topControls.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        titleText = TextView(this).apply {
            text = bookTitle
            textSize = 14f
            maxLines = 1
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        episodeText = TextView(this).apply {
            text = "Episode"
            textSize = 11f
            setTextColor(Color.argb(190, 255, 255, 255))
        }

        titleBox.addView(titleText)
        titleBox.addView(episodeText)
        topControls.addView(
            titleBox,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        root.addView(
            topControls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        bottomControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(18))
            setBackgroundColor(Color.argb(100, 0, 0, 0))
        }

        val previous = compactButton("‹") {
            previousEpisode()
            showControlsTemporarily()
        }

        val picker = compactButton("Episode") {
            showEpisodePicker()
            showControlsTemporarily()
        }.apply { tag = "episode_picker" }

        val next = compactButton("›") {
            nextEpisode()
            showControlsTemporarily()
        }

        bottomControls.addView(previous, LinearLayout.LayoutParams(dp(48), dp(44)))
        bottomControls.addView(picker, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            setMargins(dp(8), 0, dp(8), 0)
        })
        bottomControls.addView(next, LinearLayout.LayoutParams(dp(48), dp(44)))

        root.addView(
            bottomControls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        playerView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
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
                        elapsed < 900 && vertical && dy < -dp(72) -> nextEpisode()
                        elapsed < 900 && vertical && dy > dp(72) -> previousEpisode()
                        abs(dx) < dp(18) && abs(dy) < dp(18) -> togglePlayPause()
                    }

                    showControlsTemporarily()
                    true
                }

                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun installPlayerListener() {
        player.addListener(
            object : Player.Listener {
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
                    centerPlay.visibility = if (isPlaying) View.GONE else View.VISIBLE
                    if (isPlaying) showControlsTemporarily() else setControlsVisible(true)
                }

                override fun onPlayerError(error: PlaybackException) {
                    loadingText.visibility = View.VISIBLE
                    loadingText.text = "Video gagal diputar"
                }
            }
        )
    }

    private fun loadEpisodes() {
        Thread {
            try {
                val body = httpGet(
                    "$API/api/stream/all-episode?lang=in&bookId=$bookId"
                )
                val json = JSONObject(body)
                if (!json.optBoolean("ok")) throw Exception("API episode gagal")

                val array = json.getJSONArray("episodes")
                val loaded = mutableListOf<Episode>()

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val url = selectVideoUrl(item)
                    if (url.isNotBlank()) {
                        loaded.add(Episode(item.optInt("index", i + 1), url))
                    }
                }

                if (loaded.isEmpty()) throw Exception("Tidak ada stream")
                val realTitle = json.optString("title", bookTitle)

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
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
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    loadingText.visibility = View.VISIBLE
                    loadingText.text = "Gagal memuat episode
${error.message.orEmpty()}"
                }
            }
        }.start()
    }

    private fun playEpisode(position: Int) {
        if (position !in episodes.indices || isFinishing || isDestroyed) return

        currentPosition = position
        val episode = episodes[position]
        episodeText.text = "Episode ${episode.index} / ${episodes.size}"

        bottomControls.findViewWithTag<TextView>("episode_picker")?.text =
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
    }

    private fun nextEpisode() {
        if (episodes.isEmpty()) return
        val next = currentPosition + 1
        if (next <= episodes.lastIndex) playEpisode(next)
    }

    private fun previousEpisode() {
        if (episodes.isEmpty()) return
        val previous = currentPosition - 1
        if (previous >= 0) playEpisode(previous)
    }

    private fun togglePlayPause() {
        if (!::player.isInitialized) return
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
        if (!::topControls.isInitialized || !::bottomControls.isInitialized) return
        val alpha = if (visible) 1f else 0f
        topControls.animate().alpha(alpha).setDuration(160).start()
        bottomControls.animate().alpha(alpha).setDuration(160).start()
        topControls.isClickable = visible
        bottomControls.isClickable = visible
    }

    private fun showEpisodePicker() {
        if (episodes.isEmpty() || isFinishing || isDestroyed) return

        val dialog = Dialog(this)
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(16))
            background = rounded(Color.rgb(18, 21, 28), 24f)
        }

        val header = TextView(this).apply {
            text = "Pilih Episode"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(6), dp(4), dp(6), dp(12))
        }
        outer.addView(header)

        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply { columnCount = 4 }

        episodes.forEachIndexed { position, episode ->
            val button = compactButton("EP ${episode.index}") {
                dialog.dismiss()
                playEpisode(position)
                showControlsTemporarily()
            }.apply { alpha = if (position == currentPosition) 1f else 0.72f }

            grid.addView(button, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(50)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }

        scroll.addView(grid)
        outer.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        dialog.setContentView(outer)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.72f).toInt()
            )
        }
    }

    private fun compactButton(label: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.WHITE)
            background = rounded(Color.argb(205, 25, 29, 37), 16f)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { action() }
        }
    }

    private fun selectVideoUrl(item: JSONObject): String {
        var result = item.optString("sourceVideoUrl")

        if (result.isBlank()) {
            val streams = item.optJSONArray("streams")
            if (streams != null && streams.length() > 0) {
                result = streams.optJSONObject(0)?.optString("sourceUrl").orEmpty()
            }
        }

        if (result.isBlank()) result = item.optString("videoUrl")
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
        connection.setRequestProperty("User-Agent", "ReelShortFloating/4.2 Android")

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw Exception("HTTP $code")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), Color.argb(25, 255, 255, 255))
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) player.pause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::playerView.isInitialized) playerView.player = null
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
