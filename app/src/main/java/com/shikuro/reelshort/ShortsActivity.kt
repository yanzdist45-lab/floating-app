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
