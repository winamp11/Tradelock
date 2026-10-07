package com.tradelock.app

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings

/**
 * A clock that ignores changes to the phone's date/time settings.
 *
 * 1. On Android 13+ it uses the network-synced clock, which the user can't edit.
 * 2. Otherwise it takes one wall-clock reading per boot and then counts forward
 *    using the time-since-boot counter, which can't be changed from Settings.
 * 3. Time never moves backwards compared with the last time it was seen.
 */
object TrustedClock {

    fun now(ctx: Context): Long {
        val p = Prefs(ctx)
        val elapsed = SystemClock.elapsedRealtime()
        val boot = bootCount(ctx)

        var t: Long? = networkTime()
        if (t == null) {
            t = if (p.anchorBoot == boot && p.anchorTrusted > 0 && elapsed >= p.anchorElapsed) {
                p.anchorTrusted + (elapsed - p.anchorElapsed)
            } else {
                val fresh = maxOf(System.currentTimeMillis(), p.highWater)
                p.setAnchor(fresh, elapsed, boot)
                fresh
            }
        }
        val result = maxOf(t, p.highWater)
        // Only persist the high-water mark occasionally to avoid constant disk writes.
        if (result - p.highWater > 60_000) p.highWater = result
        return result
    }

    private fun networkTime(): Long? {
        if (Build.VERSION.SDK_INT < 33) return null
        return try {
            SystemClock.currentNetworkTimeClock().millis()
        } catch (e: Exception) {
            null
        }
    }

    private fun bootCount(ctx: Context): Int =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)
}
