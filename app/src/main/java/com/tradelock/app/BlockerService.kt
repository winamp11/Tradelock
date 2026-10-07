package com.tradelock.app

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The enforcer. Runs whenever TradeLock's accessibility permission is on.
 *
 *  - The instant you tap a blocked app's icon, a full-screen "Trading is closed" cover
 *    appears on top, so the app never becomes visible. Behind the cover, the app is sent
 *    back to the home screen.
 *  - Also reacts to *any* activity from a blocked app (notifications, recents, links),
 *    and re-checks every 15 s so an open MT5 is covered at midnight.
 *  - Sends the 11:45 PM UAE warning.
 *  - While locked, refuses Settings / uninstall screens that mention TradeLock.
 */
class BlockerService : AccessibilityService() {

    companion object {
        @Volatile var running = false
        private const val CHANNEL = "warnings"
        private const val TICK_MS = 15_000L
        private const val COVER_AUTO_CLOSE_MS = 10_000L
        const val APP_NAME = "TradeLock"

        private fun isTamperSurface(pkg: String) =
            pkg.contains("settings") || pkg.contains("packageinstaller") ||
                pkg.contains("permissioncontroller") || pkg == "com.android.vending" ||
                pkg.contains("securitycenter") || pkg.contains("devicecare")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastLocked: Boolean? = null
    private var lastContentCheck = 0L
    private var lastHome = 0L
    private var lastBlockedCheck = 0L

    private var blocked: Set<String> = emptySet()
    private var blockedLabels: Set<String> = emptySet()

    private var cover: View? = null
    private var coverCountdown: TextView? = null
    private var coverUntil: TextView? = null
    private var coverLocal: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            refreshBlockedList()
            check(null)
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val coverTick = object : Runnable {
        override fun run() {
            if (cover == null) return
            val st = LockPolicy.state(this@BlockerService)
            if (!st.locked) { hideCover(); return }
            coverCountdown?.text = Fmt.countdown(st.unlockAt - st.now)
            coverUntil?.text = "Unlocks ${Fmt.uae(st.unlockAt)}"
            coverLocal?.text = Fmt.localIfDifferent(st.unlockAt) ?: ""
            handler.postDelayed(this, 1000)
        }
    }

    private val autoClose = Runnable { hideCover() }

    override fun onServiceConnected() {
        running = true
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Lock warnings", NotificationManager.IMPORTANCE_HIGH)
        )
        handler.post(tick)
        BlockWidget.updateAll(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return

        // 1. Any sign of life from a blocked app: cover it immediately.
        if (pkg in blocked) {
            // Live charts fire constant updates; only window changes are checked every time.
            val t = SystemClock.elapsedRealtime()
            if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || t - lastBlockedCheck > 500) {
                lastBlockedCheck = t
                check(pkg)
            }
            return
        }

        when (e.eventType) {
            // 2. Tapping a blocked app's icon in the launcher, before the app even opens.
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (blockedLabels.isEmpty()) return
                val clicked = (e.text.joinToString(" ") + " " + (e.contentDescription ?: ""))
                    .trim().lowercase()
                if (clicked.isNotEmpty() && blockedLabels.any { clicked == it || clicked.startsWith(it) } &&
                    LockPolicy.state(this).locked
                ) {
                    showCover()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> check(pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (!isTamperSurface(pkg)) return
                val t = SystemClock.elapsedRealtime()
                if (t - lastContentCheck < 700) return
                lastContentCheck = t
                check(pkg)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        hideCover()
        BlockWidget.updateAll(this)
        super.onDestroy()
    }

    private fun refreshBlockedList() {
        blocked = Prefs(this).blockedPackages
        blockedLabels = blocked.mapNotNull { pkg ->
            try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))
                    .toString().trim().lowercase()
            } catch (e: PackageManager.NameNotFoundException) { null }
        }.filter { it.length >= 2 }.toSet()
    }

    private fun check(eventPkg: String?) {
        val state = LockPolicy.state(this)

        if (state.locked != lastLocked) {
            lastLocked = state.locked
            refreshBlockedList()
            BlockWidget.updateAll(this)
        }
        maybeWarn(state)
        if (!state.locked) { hideCover(); return }

        val pkg = eventPkg ?: try {
            rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) { null } ?: return
        if (pkg == packageName) return

        if (pkg in blocked) {
            block()
        } else if (isTamperSurface(pkg) && screenMentionsUs()) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            block()
        }
    }

    /** Cover the screen right now, and send whatever is underneath to the home screen. */
    private fun block() {
        showCover()
        val t = SystemClock.elapsedRealtime()
        if (t - lastHome > 400) {
            lastHome = t
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    // ------------------------------------------------------------ the cover

    private fun showCover() {
        if (cover != null) {
            handler.removeCallbacks(autoClose)
            handler.postDelayed(autoClose, COVER_AUTO_CLOSE_MS)
            return
        }
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Ui.BG)
            setPadding(Ui.dp(ctx, 32), 0, Ui.dp(ctx, 32), 0)
            isClickable = true   // swallow touches so nothing underneath can be used
        }
        root.addView(Ui.text(ctx, "🔒", 64f, Ui.TEXT).apply { gravity = Gravity.CENTER })
        root.addView(Ui.text(ctx, "Trading is closed", 28f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER
        })
        root.addView(Ui.text(ctx, "You banked the profit. The market will still be there tomorrow.",
            16f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 28))
        })
        coverCountdown = Ui.text(ctx, "", 44f, Ui.RED, bold = true).apply { gravity = Gravity.CENTER }
        coverUntil = Ui.text(ctx, "", 16f, Ui.TEXT).apply { gravity = Gravity.CENTER }
        coverLocal = Ui.text(ctx, "", 14f, Ui.MUTED).apply { gravity = Gravity.CENTER }
        root.addView(coverCountdown)
        root.addView(coverUntil)
        root.addView(coverLocal)
        root.addView(Ui.button(ctx, "Close", Ui.CARD) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            hideCover()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = Ui.dp(ctx, 40)
        })

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )
        try {
            getSystemService(WindowManager::class.java).addView(root, lp)
            cover = root
            handler.post(coverTick)
            handler.postDelayed(autoClose, COVER_AUTO_CLOSE_MS)
        } catch (e: Exception) {
            // Fallback: full-screen activity.
            startActivity(Intent(this, BlockedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    private fun hideCover() {
        val v = cover ?: return
        cover = null
        handler.removeCallbacks(coverTick)
        handler.removeCallbacks(autoClose)
        try { getSystemService(WindowManager::class.java).removeView(v) } catch (e: Exception) {}
    }

    // ------------------------------------------------------------- helpers

    private fun screenMentionsUs(): Boolean = try {
        val root = rootInActiveWindow
        root != null && root.findAccessibilityNodeInfosByText(APP_NAME).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    private fun maybeWarn(state: LockPolicy.State) {
        if (state.locked || !LockPolicy.inWarningWindow(state.now)) return
        val prefs = Prefs(this)
        val key = LockPolicy.uaeDateKey(state.now)
        if (prefs.lastWarnDate == key) return
        prefs.lastWarnDate = key
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("MT5 locks in 15 minutes")
            .setContentText("Midnight UAE. Wrap up now and set your SL/TP.")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "MT5 locks at midnight UAE time until 10:00 AM. Close or protect open trades now. " +
                        "Positions stay open at your broker, and SL/TP orders still work."
                )
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(2345, n)
    }
}
