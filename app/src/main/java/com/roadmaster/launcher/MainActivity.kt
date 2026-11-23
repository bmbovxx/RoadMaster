package com.roadmaster.launcher

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioManager
import android.os.Bundle
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.setBackgroundColor(0xFF0B0F14.toInt())
        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String, callback: GeolocationPermissions.Callback
            ) = callback.invoke(origin, true, false)
        }
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("file:///android_asset/index.html")
        setContentView(web)
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ), 1
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    // As a launcher, Back must not close the home screen.
    @Deprecated("launcher")
    override fun onBackPressed() {}

    inner class Bridge {
        private val audio get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        private fun go(i: Intent): Boolean = try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runOnUiThread { startActivity(i) }
            true
        } catch (e: Exception) { false }

        @JavascriptInterface
        fun launchPackage(pkg: String): Boolean {
            val i = packageManager.getLaunchIntentForPackage(pkg) ?: return false
            return go(i)
        }

        @JavascriptInterface
        fun openSettings(action: String): Boolean = go(Intent(action))

        @JavascriptInterface
        fun dial(): Boolean = go(Intent(Intent.ACTION_DIAL))

        @JavascriptInterface
        fun openMusic(): Boolean =
            go(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))

        @JavascriptInterface
        fun mediaKey(code: Int) {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }

        @JavascriptInterface
        fun setVolume(pct: Int) {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, pct * max / 100, 0)
        }

        @JavascriptInterface
        fun getVolume(): Int {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
        }

        @JavascriptInterface
        fun getApps(): String {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val arr = JSONArray()
            pm.queryIntentActivities(q, 0)
                .sortedBy { it.loadLabel(pm).toString().lowercase() }
                .forEach { r ->
                    if (r.activityInfo.packageName == packageName) return@forEach
                    val bmp = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
                    val d = r.loadIcon(pm)
                    d.setBounds(0, 0, 72, 72)
                    d.draw(Canvas(bmp))
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    arr.put(
                        JSONObject()
                            .put("n", r.loadLabel(pm).toString())
                            .put("p", r.activityInfo.packageName)
                            .put("i", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                    )
                }
            return arr.toString()
        }
    }
}
