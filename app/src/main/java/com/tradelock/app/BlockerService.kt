package com.tradelock.app

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.app.Notification

/**
 * The enforcer. Runs whenever TradeLock's accessibility permission is on.
 *  - Kicks blocked apps to the home screen while locked.
 *  - Checks every 15 s, so an open MT5 is closed at midnight even if you never switch apps.
 *  - Sends the 11:45 PM UAE warning.
 *  - While locked, refuses Settings / uninstall screens that mention TradeLock, so the
 *    lock can't be switched off in a weak moment. The partner password lifts the lock.
 */
class BlockerService : AccessibilityService() {

    companion object {
        @Volatile var running = false
        private const val CHANNEL = "warnings"
        private const val TICK_MS = 15_000L
        const val APP_NAME = "TradeLock"

        private fun isTamperSurface(pkg: String) =
            pkg.contains("settings") || pkg.contains("packageinstaller") ||
                pkg.contains("permissioncontroller") || pkg == "com.android.vending" ||
                pkg.contains("securitycenter") || pkg.contains("devicecare")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastLocked: Boolean? = null
    private var lastKick = 0L
    private var lastContentCheck = 0L

    private val tick = object : Runnable {
        override fun run() {
            check(null)
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        running = true
        ensureChannel(this)
        handler.post(tick)
        BlockWidget.updateAll(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> check(pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Only care about content changes inside Settings-like screens, throttled.
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
        handler.removeCallbacks(tick)
        BlockWidget.updateAll(this)
        super.onDestroy()
    }

    private fun check(eventPkg: String?) {
        val prefs = Prefs(this)
        val state = LockPolicy.state(this)

        if (state.locked != lastLocked) {
            lastLocked = state.locked
            BlockWidget.updateAll(this)
        }
        maybeWarn(prefs, state)
        if (!state.locked) return

        val pkg = eventPkg ?: try {
            rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) { null } ?: return
        if (pkg == packageName) return

        if (pkg in prefs.blockedPackages) {
            kickOut()
        } else if (isTamperSurface(pkg) && screenMentionsUs()) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
            kickOut()
        }
    }

    private fun kickOut() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val t = SystemClock.elapsedRealtime()
        if (t - lastKick < 1500) return
        lastKick = t
        startActivity(
            Intent(this, BlockedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun screenMentionsUs(): Boolean = try {
        val root = rootInActiveWindow
        root != null && root.findAccessibilityNodeInfosByText(APP_NAME).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    private fun maybeWarn(prefs: Prefs, state: LockPolicy.State) {
        if (state.locked || !LockPolicy.inWarningWindow(state.now)) return
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

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lock warnings", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}
