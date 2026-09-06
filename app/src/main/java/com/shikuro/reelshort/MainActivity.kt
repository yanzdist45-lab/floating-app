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
