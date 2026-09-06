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
