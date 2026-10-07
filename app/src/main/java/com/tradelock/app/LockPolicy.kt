package com.tradelock.app

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * All lock rules live here. Every calculation uses UAE time (Asia/Dubai, UTC+4,
 * no daylight saving), never the phone's own timezone, so travelling changes nothing.
 *
 *  - Trading window: 10:00 AM to midnight UAE.
 *  - Midnight curfew: 00:00 to 10:00 UAE, blocked apps are always locked.
 *  - "Block now": locks until the next 10:00 AM UAE.
 *  - Password unlock: lifts the current lock until the moment it would have ended.
 */
object LockPolicy {
    val UAE: ZoneId = ZoneId.of("Asia/Dubai")
    const val UNLOCK_HOUR = 10
    const val WARN_HOUR = 23
    const val WARN_MINUTE = 45

    data class State(
        val now: Long,
        val locked: Boolean,
        val unlockAt: Long,      // epoch millis (UTC); 0 when not locked
        val reason: Reason,
    )

    enum class Reason { NONE, MANUAL, CURFEW }

    fun uae(utcMillis: Long): ZonedDateTime = Instant.ofEpochMilli(utcMillis).atZone(UAE)

    /** The next 10:00 AM UAE strictly after [nowUtc]. */
    fun nextUnlock(nowUtc: Long): Long {
        val now = uae(nowUtc)
        var target = now.toLocalDate().atTime(UNLOCK_HOUR, 0).atZone(UAE)
        if (!now.isBefore(target)) target = target.plusDays(1)
        return target.toInstant().toEpochMilli()
    }

    /** 00:00 to 09:59 UAE. */
    fun inCurfew(nowUtc: Long): Boolean = uae(nowUtc).hour < UNLOCK_HOUR

    /** 23:45 to 23:59 UAE. */
    fun inWarningWindow(nowUtc: Long): Boolean {
        val t = uae(nowUtc)
        return t.hour == WARN_HOUR && t.minute >= WARN_MINUTE
    }

    fun uaeDateKey(nowUtc: Long): String = uae(nowUtc).toLocalDate().toString()

    /** Pure rule evaluation, kept free of Android so it can be unit tested. */
    fun evaluate(now: Long, manualUnlockAt: Long, overrideUntil: Long): State {
        if (now < overrideUntil) return State(now, false, 0, Reason.NONE)
        if (now < manualUnlockAt) return State(now, true, manualUnlockAt, Reason.MANUAL)
        if (inCurfew(now)) return State(now, true, nextUnlock(now), Reason.CURFEW)
        return State(now, false, 0, Reason.NONE)
    }

    fun state(ctx: Context): State {
        val p = Prefs(ctx)
        return evaluate(TrustedClock.now(ctx), p.manualUnlockAt, p.overrideUntil)
    }

    /** The widget / big red button. */
    fun blockNow(ctx: Context): State {
        val p = Prefs(ctx)
        val now = TrustedClock.now(ctx)
        p.manualUnlockAt = nextUnlock(now)
        p.overrideUntil = 0
        p.lockCount = p.lockCount + 1
        return state(ctx)
    }

    /** Called only after the partner's password has been verified. */
    fun passwordUnlock(ctx: Context) {
        val s = state(ctx)
        val p = Prefs(ctx)
        p.overrideUntil = if (s.locked) s.unlockAt else 0
        p.manualUnlockAt = 0
    }
}
