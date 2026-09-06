package com.shikuro.reelshort

import android.app.Activity
import android.app.Dialog
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : Activity() {

    private val api =
        "https://reelshort.vercel.app"


    /*
     * DATA
     */

    data class Book(
        val id: String,
        val title: String,
        val chapters: Int,
        val theme: String
    )


    data class Episode(
        val index: Int,
        val chapterId: String,
        val url: String,
        val duration: Int
    )


    private val episodes =
        mutableListOf<Episode>()


    private var currentEpisodePosition =
        0


    private var currentBookTitle =
        ""


    /*
     * UI
     */

    private lateinit var root:
            FrameLayout


    private var player:
            ExoPlayer? = null


    private var playerView:
            PlayerView? = null


    private var episodeButton:
            Button? = null


    private var titleText:
            TextView? = null


    private var miniButton:
            Button? = null


    private var touchStartY =
        0f


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)


        root =
            FrameLayout(this)


        root.setBackgroundColor(
            Color.BLACK
        )


        setContentView(root)


        showSearchScreen()
    }


    /*
     * =========================================================
     * SEARCH
     * =========================================================
     */

    private fun showSearchScreen() {

        releasePlayer()


        root.removeAllViews()


        val main =
            LinearLayout(this)


        main.orientation =
            LinearLayout.VERTICAL


        main.setPadding(
            dp(14),
            dp(18),
            dp(14),
            dp(14)
        )


        main.setBackgroundColor(
            Color.rgb(
                10,
                10,
                10
            )
        )


        root.addView(
            main,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )


        /*
         * TITLE
         */

        val logo =
            TextView(this)


        logo.text =
            "ReelShort"


        logo.textSize =
            24f


        logo.setTextColor(
            Color.WHITE
        )


        logo.setTypeface(
            null,
            Typeface.BOLD
        )


        logo.setPadding(
            4,
            6,
            4,
            dp(14)
        )


        main.addView(
            logo
        )


        /*
         * SEARCH BAR
         */

        val searchRow =
            LinearLayout(this)


        searchRow.orientation =
            LinearLayout.HORIZONTAL


        val input =
            EditText(this)


        input.hint =
            "Cari drama..."


        input.setHintTextColor(
            Color.GRAY
        )


        input.setTextColor(
            Color.WHITE
        )


        input.setSingleLine()


        input.textSize =
            16f


        input.setBackgroundColor(
            Color.rgb(
                30,
                30,
                30
            )
        )


        input.setPadding(
            dp(14),
            0,
            dp(14),
            0
        )


        searchRow.addView(
            input,
            LinearLayout.LayoutParams(
                0,
                dp(50),
                1f
            )
        )


        val searchButton =
            Button(this)


        searchButton.text =
            "Cari"


        val searchButtonParams =
            LinearLayout.LayoutParams(
                dp(85),
                dp(50)
            )


        searchButtonParams.marginStart =
            dp(8)


        searchRow.addView(
            searchButton,
            searchButtonParams
        )


        main.addView(
            searchRow
        )


        /*
         * STATUS
         */

        val status =
            TextView(this)


        status.text =
            "Cari drama yang mau ditonton."


        status.setTextColor(
            Color.GRAY
        )


        status.setPadding(
            4,
            dp(14),
            4,
            dp(12)
        )


        main.addView(
            status
        )


        /*
         * RESULTS
         */

        val scroll =
            ScrollView(this)


        val results =
            LinearLayout(this)


        results.orientation =
            LinearLayout.VERTICAL


        scroll.addView(
            results
        )


        main.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )


        fun runSearch() {

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


            searchDrama(
                keyword,
                status,
                results
            )
        }


        searchButton.setOnClickListener {
            runSearch()
        }


        input.setOnEditorActionListener {
                _,
                _,
                _ ->

            runSearch()

            true
        }
    }


    /*
     * =========================================================
     * SEARCH API
     * =========================================================
     */

    private fun searchDrama(
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


                    val themeText =
                        if (
                            themes != null &&
                            themes.length() > 0
                        ) {

                            buildString {

                                for (
                                    t in 0 until themes.length()
                                ) {

                                    if (
                                        t > 0
                                    ) {
                                        append(
                                            ", "
                                        )
                                    }


                                    append(
                                        themes.optString(
                                            t
                                        )
                                    )
                                }
                            }

                        } else {

                            ""

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
                                themeText
                        )

                    )
                }


                runOnUiThread {

                    status.text =
                        "${books.size} hasil ditemukan"


                    books.forEach {

                        addBookView(
                            it,
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


    /*
     * =========================================================
     * BOOK CARD
     * =========================================================
     */

    private fun addBookView(
        book: Book,
        container: LinearLayout
    ) {

        val card =
            LinearLayout(this)


        card.orientation =
            LinearLayout.VERTICAL


        card.setPadding(
            dp(16),
            dp(15),
            dp(16),
            dp(15)
        )


        card.setBackgroundColor(
            Color.rgb(
                28,
                28,
                28
            )
        )


        val title =
            TextView(this)


        title.text =
            book.title


        title.textSize =
            16f


        title.setTextColor(
            Color.WHITE
        )


        title.setTypeface(
            null,
            Typeface.BOLD
        )


        card.addView(
            title
        )


        val meta =
            TextView(this)


        meta.text =
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


        meta.textSize =
            13f


        meta.setTextColor(
            Color.GRAY
        )


        meta.setPadding(
            0,
            dp(6),
            0,
            0
        )


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

            loadEpisodes(
                book
            )

        }
    }


    /*
     * =========================================================
     * LOAD ALL EPISODES
     * =========================================================
     */

    private fun loadEpisodes(
        book: Book
    ) {

        Toast.makeText(
            this,
            "Memuat episode...",
            Toast.LENGTH_SHORT
        ).show()


        Thread {

            try {

                val response =
                    httpGet(
                        "$api/api/stream/all-episode?lang=in&bookId=${book.id}"
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


                    var videoUrl =
                        item.optString(
                            "sourceVideoUrl"
                        )


                    /*
                     * fallback sourceUrl
                     */

                    if (
                        videoUrl.isEmpty()
                    ) {

                        val streams =
                            item.optJSONArray(
                                "streams"
                            )


                        if (
                            streams != null &&
                            streams.length() > 0
                        ) {

                            videoUrl =
                                streams
                                    .getJSONObject(0)
                                    .optString(
                                        "sourceUrl"
                                    )

                        }

                    }


                    /*
                     * fallback proxy
                     */

                    if (
                        videoUrl.isEmpty()
                    ) {

                        videoUrl =
                            item.optString(
                                "videoUrl"
                            )

                    }


                    if (
                        videoUrl.startsWith(
                            "http://"
                        )
                    ) {

                        videoUrl =
                            "https://" +
                            videoUrl.removePrefix(
                                "http://"
                            )

                    }


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
                                    videoUrl,

                                duration =
                                    item.optInt(
                                        "duration"
                                    )
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


                currentBookTitle =
                    json.optString(
                        "title",
                        book.title
                    )


                runOnUiThread {

                    episodes.clear()

                    episodes.addAll(
                        loaded
                    )


                    showPlayerScreen(
                        0
                    )

                }

            } catch (
                e: Exception
            ) {

                e.printStackTrace()


                runOnUiThread {

                    Toast.makeText(
                        this,
                        "Gagal: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()

                }

            }

        }.start()
    }


    /*
     * =========================================================
     * PLAYER SCREEN
     * =========================================================
     */

    private fun showPlayerScreen(
        startPosition: Int
    ) {

        root.removeAllViews()


        val playerRoot =
            FrameLayout(this)


        playerRoot.setBackgroundColor(
            Color.BLACK
        )


        root.addView(
            playerRoot,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )


        /*
         * EXOPLAYER
         */

        player =
            ExoPlayer
                .Builder(this)
                .build()


        playerView =
            PlayerView(this)


        playerView!!.player =
            player


        playerView!!.useController =
            true


        playerRoot.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )


        /*
         * TOP BAR
         */

        val top =
            LinearLayout(this)


        top.orientation =
            LinearLayout.HORIZONTAL


        top.gravity =
            Gravity.CENTER_VERTICAL


        top.setPadding(
            dp(10),
            dp(25),
            dp(10),
            dp(10)
        )


        top.setBackgroundColor(
            Color.argb(
                150,
                0,
                0,
                0
            )
        )


        val back =
            Button(this)


        back.text =
            "←"


        top.addView(
            back,
            LinearLayout.LayoutParams(
                dp(55),
                dp(48)
            )
        )


        titleText =
            TextView(this)


        titleText!!.text =
            currentBookTitle


        titleText!!.textSize =
            14f


        titleText!!.setTextColor(
            Color.WHITE
        )


        titleText!!.gravity =
            Gravity.CENTER


        titleText!!.maxLines =
            2


        top.addView(
            titleText,
            LinearLayout.LayoutParams(
                0,
                dp(55),
                1f
            )
        )


        miniButton =
            Button(this)


        miniButton!!.text =
            "MINI"


        top.addView(
            miniButton,
            LinearLayout.LayoutParams(
                dp(80),
                dp(48)
            )
        )


        playerRoot.addView(
            top,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )


        /*
         * BOTTOM
         */

        val bottom =
            LinearLayout(this)


        bottom.orientation =
            LinearLayout.HORIZONTAL


        bottom.gravity =
            Gravity.CENTER


        bottom.setPadding(
            dp(10),
            dp(8),
            dp(10),
            dp(20)
        )


        bottom.setBackgroundColor(
            Color.argb(
                180,
                0,
                0,
                0
            )
        )


        val previous =
            Button(this)


        previous.text =
            "‹"


        bottom.addView(
            previous,
            LinearLayout.LayoutParams(
                dp(70),
                dp(52)
            )
        )


        episodeButton =
            Button(this)


        bottom.addView(
            episodeButton,
            LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
            )
        )


        val next =
            Button(this)


        next.text =
            "›"


        bottom.addView(
            next,
            LinearLayout.LayoutParams(
                dp(70),
                dp(52)
            )
        )


        playerRoot.addView(
            bottom,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )


        /*
         * BUTTONS
         */

        back.setOnClickListener {

            showSearchScreen()

        }


        miniButton!!.setOnClickListener {

            enterPip()

        }


        previous.setOnClickListener {

            previousEpisode()

        }


        next.setOnClickListener {

            nextEpisode()

        }


        episodeButton!!.setOnClickListener {

            showEpisodePicker()

        }


        /*
         * SWIPE SHORTS
         */

        playerView!!.setOnTouchListener {
                _,
                event ->

            when (
                event.action
            ) {

                MotionEvent.ACTION_DOWN -> {

                    touchStartY =
                        event.rawY

                }


                MotionEvent.ACTION_UP -> {

                    val difference =
                        event.rawY -
                        touchStartY


                    if (
                        difference < -150
                    ) {

                        nextEpisode()

                        return@setOnTouchListener true

                    }


                    if (
                        difference > 150
                    ) {

                        previousEpisode()

                        return@setOnTouchListener true

                    }

                }

            }


            false
        }


        /*
         * AUTO NEXT
         */

        player!!.addListener(

            object : Player.Listener {

                override fun onPlaybackStateChanged(
                    state: Int
                ) {

                    if (
                        state ==
                        Player.STATE_ENDED
                    ) {

                        nextEpisode()

                    }

                }

            }

        )


        playEpisode(
            startPosition
        )
    }


    /*
     * =========================================================
     * PLAY
     * =========================================================
     */

    private fun playEpisode(
        position: Int
    ) {

        if (
            position < 0 ||
            position >= episodes.size
        ) {
            return
        }


        currentEpisodePosition =
            position


        val episode =
            episodes[
                position
            ]


        episodeButton?.text =
            "EP.${episode.index} / EP.${episodes.size}"


        titleText?.text =
            "$currentBookTitle\nEpisode ${episode.index}"


        val mediaItem =
            MediaItem.fromUri(
                episode.url
            )


        player?.apply {

            setMediaItem(
                mediaItem
            )

            prepare()

            playWhenReady =
                true

        }

    }


    private fun nextEpisode() {

        val next =
            currentEpisodePosition + 1


        if (
            next >= episodes.size
        ) {

            Toast.makeText(
                this,
                "Episode terakhir",
                Toast.LENGTH_SHORT
            ).show()


            return

        }


        playEpisode(
            next
        )
    }


    private fun previousEpisode() {

        val previous =
            currentEpisodePosition - 1


        if (
            previous < 0
        ) {
            return
        }


        playEpisode(
            previous
        )
    }


    /*
     * =========================================================
     * EPISODE PICKER
     * =========================================================
     */

    private fun showEpisodePicker() {

        val dialog =
            Dialog(this)


        val outer =
            LinearLayout(this)


        outer.orientation =
            LinearLayout.VERTICAL


        outer.setPadding(
            dp(14),
            dp(14),
            dp(14),
            dp(20)
        )


        outer.setBackgroundColor(
            Color.rgb(
                24,
                24,
                24
            )
        )


        val title =
            TextView(this)


        title.text =
            "Pilih Episode"


        title.textSize =
            21f


        title.setTypeface(
            null,
            Typeface.BOLD
        )


        title.setTextColor(
            Color.WHITE
        )


        title.setPadding(
            dp(6),
            dp(5),
            dp(6),
            dp(15)
        )


        outer.addView(
            title
        )


        val scroll =
            ScrollView(this)


        val grid =
            GridLayout(this)


        grid.columnCount =
            4


        grid.setPadding(
            0,
            0,
            0,
            dp(20)
        )


        episodes.forEachIndexed {
                position,
                episode ->


            val button =
                Button(this)


            button.text =
                "EP ${episode.index}"


            val params =
                GridLayout.LayoutParams()


            params.width =
                0


            params.height =
                dp(55)


            params.columnSpec =
                GridLayout.spec(
                    GridLayout.UNDEFINED,
                    1f
                )


            params.setMargins(
                dp(3),
                dp(3),
                dp(3),
                dp(3)
            )


            button.layoutParams =
                params


            if (
                position ==
                currentEpisodePosition
            ) {

                button.alpha =
                    1f

            } else {

                button.alpha =
                    0.75f

            }


            button.setOnClickListener {

                dialog.dismiss()

                playEpisode(
                    position
                )

            }


            grid.addView(
                button
            )

        }


        scroll.addView(
            grid
        )


        outer.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )


        dialog.setContentView(
            outer
        )


        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.7).toInt()
        )


        dialog.show()


        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.7).toInt()
        )
    }


    /*
     * =========================================================
     * PICTURE IN PICTURE
     * =========================================================
     */

    private fun enterPip() {

        if (
            episodes.isEmpty()
        ) {
            return
        }


        try {

            val params =
                PictureInPictureParams
                    .Builder()

                    .setAspectRatio(
                        Rational(
                            16,
                            9
                        )
                    )

                    .build()


            enterPictureInPictureMode(
                params
            )

        } catch (
            e: Exception
        ) {

            Toast.makeText(
                this,
                "PiP gagal: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()

        }

    }


    override fun onUserLeaveHint() {

        super.onUserLeaveHint()


        if (
            player?.isPlaying ==
            true
        ) {

            enterPip()

        }

    }


    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {

        super.onPictureInPictureModeChanged(
            isInPictureInPictureMode,
            newConfig
        )


        playerView?.useController =
            !isInPictureInPictureMode


        titleText?.visibility =
            if (
                isInPictureInPictureMode
            )
                View.GONE
            else
                View.VISIBLE


        miniButton?.visibility =
            if (
                isInPictureInPictureMode
            )
                View.GONE
            else
                View.VISIBLE

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
            25000


        connection.setRequestProperty(
            "User-Agent",
            "ReelShortFloating/1.0 Android"
        )


        connection.setRequestProperty(
            "Accept",
            "application/json"
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
     * UTILS
     * =========================================================
     */

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
            resources.displayMetrics.density
        ).toInt()

    }


    private fun releasePlayer() {

        playerView?.player =
            null


        playerView =
            null


        player?.release()


        player =
            null
    }


    @Deprecated(
        "Deprecated in Java"
    )
    override fun onBackPressed() {

        if (
            player != null
        ) {

            showSearchScreen()

        } else {

            super.onBackPressed()

        }

    }


    override fun onDestroy() {

        releasePlayer()

        super.onDestroy()
    }
}
