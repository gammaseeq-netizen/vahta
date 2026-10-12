package com.vakht.calendar

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView

class MainActivity : Activity() {

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBars(this, true)

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#0D111B"))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(this), "Android")
        web.loadUrl("file:///android_asset/index.html")
        setContentView(web)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    /** Мост JS -> Android: сохраняет график, обновляет виджет и цвет системных панелей. */
    class Bridge(private val act: Activity) {

        @JavascriptInterface
        fun saveSchedule(start: String, work: Int, rest: Int, workStartMin: Int, workEndMin: Int) {
            val ctx = act.applicationContext
            ctx.getSharedPreferences("vakht", Context.MODE_PRIVATE).edit()
                .putString("start", start)
                .putInt("work", work)
                .putInt("rest", rest)
                .putInt("ws", workStartMin)
                .putInt("we", workEndMin)
                .apply()
            VakhtWidget.refreshAll(ctx)
        }

        @JavascriptInterface
        fun setTheme(dark: Boolean) {
            act.runOnUiThread { applyBars(act, dark) }
        }
    }
}

@Suppress("DEPRECATION")
fun applyBars(act: Activity, dark: Boolean) {
    val c = Color.parseColor(if (dark) "#0D111B" else "#F3F6FC")
    act.window.statusBarColor = c
    act.window.navigationBarColor = c
    act.window.decorView.systemUiVisibility =
        if (dark) 0
        else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
}
