package com.example.floatinggoogle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var panel: LinearLayout
    private lateinit var bubble: TextView
    private lateinit var web: WebView
    private lateinit var panelLp: WindowManager.LayoutParams
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private var panelShown = false
    private var bubbleShown = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopSelf()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        buildPanel()
        buildBubble()
        showPanel()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("float", "Floating Google", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, FloatingService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "float")
            .setContentTitle("Floating Google aktif")
            .setContentText("Ketuk untuk menutup")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(stop)
            .build()
        startForeground(1, n)
    }

    private fun overlayParams(w: Int, h: Int, focusable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
    }

    private fun button(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 18f
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { onClick() }
    }

    private fun buildPanel() {
        panelLp = overlayParams(dp(320), dp(460), true).apply { x = dp(20); y = dp(100) }

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }

        // Bar atas: geser untuk memindahkan jendela
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF1A73E8.toInt())
            setPadding(dp(10), 0, 0, 0)
        }
        bar.addView(TextView(this).apply {
            text = "Google"
            setTextColor(Color.WHITE)
            textSize = 14f
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        bar.addView(button("◀") { if (web.canGoBack()) web.goBack() }, LinearLayout.LayoutParams(-2, dp(40)))
        bar.addView(button("—") { showBubble() }, LinearLayout.LayoutParams(-2, dp(40)))
        bar.addView(button("✕") { stopSelf() }, LinearLayout.LayoutParams(-2, dp(40)))
        bar.setOnTouchListener(dragListener({ panelLp }, { panel }))
        panel.addView(bar)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl("https://www.google.com")
        }
        panel.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))

        // Pegangan ubah ukuran (pojok kanan bawah)
        val grip = TextView(this).apply {
            text = "◢"
            setTextColor(0xFF5F6368.toInt())
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(6), 0)
            setBackgroundColor(0xFFF1F3F4.toInt())
        }
        grip.setOnTouchListener(resizeListener())
        panel.addView(grip, LinearLayout.LayoutParams(-1, dp(24)))
    }

    private fun buildBubble() {
        bubbleLp = overlayParams(dp(56), dp(56), false).apply { x = dp(10); y = dp(200) }
        bubble = TextView(this).apply {
            text = "G"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF1A73E8.toInt())
            }
            elevation = dp(6).toFloat()
        }
        bubble.setOnTouchListener(dragListener({ bubbleLp }, { bubble }) { showPanel() })
    }

    private fun showPanel() {
        if (bubbleShown) { wm.removeView(bubble); bubbleShown = false }
        if (!panelShown) { wm.addView(panel, panelLp); panelShown = true }
    }

    private fun showBubble() {
        if (panelShown) { wm.removeView(panel); panelShown = false }
        if (!bubbleShown) { wm.addView(bubble, bubbleLp); bubbleShown = true }
    }

    private fun dragListener(
        lp: () -> WindowManager.LayoutParams,
        view: () -> View,
        onTap: (() -> Unit)? = null
    ): View.OnTouchListener {
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        return View.OnTouchListener { _, e ->
            val p = lp()
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    p.x = sx + dx; p.y = sy + dy
                    wm.updateViewLayout(view(), p)
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap?.invoke()
            }
            true
        }
    }

    private fun resizeListener(): View.OnTouchListener {
        var sw = 0; var sh = 0; var tx = 0f; var ty = 0f
        return View.OnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sw = panelLp.width; sh = panelLp.height; tx = e.rawX; ty = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    panelLp.width = (sw + (e.rawX - tx)).toInt().coerceAtLeast(dp(200))
                    panelLp.height = (sh + (e.rawY - ty)).toInt().coerceAtLeast(dp(240))
                    wm.updateViewLayout(panel, panelLp)
                }
            }
            true
        }
    }

    override fun onDestroy() {
        if (panelShown) wm.removeView(panel)
        if (bubbleShown) wm.removeView(bubble)
        web.destroy()
        super.onDestroy()
    }
}
