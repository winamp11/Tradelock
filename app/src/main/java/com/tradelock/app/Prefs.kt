package com.tradelock.app

import android.content.Context

/** Everything TradeLock stores, in one small private file on the phone. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("tradelock", Context.MODE_PRIVATE)

    companion object {
        const val MT5 = "net.metaquotes.metatrader5"
        val DEFAULT_BLOCKED = setOf(MT5)
    }

    // ---- Blocked apps
    var blockedPackages: Set<String>
        get() = sp.getStringSet("blocked", null)?.toSet() ?: DEFAULT_BLOCKED
        set(v) { sp.edit().putStringSet("blocked", HashSet(v)).commit() }

    // ---- Lock state (epoch millis, UTC)
    var manualUnlockAt: Long
        get() = sp.getLong("manualUnlockAt", 0)
        set(v) { sp.edit().putLong("manualUnlockAt", v).commit() }

    var overrideUntil: Long
        get() = sp.getLong("overrideUntil", 0)
        set(v) { sp.edit().putLong("overrideUntil", v).commit() }

    var lockCount: Int
        get() = sp.getInt("lockCount", 0)
        set(v) { sp.edit().putInt("lockCount", v).apply() }

    var lastWarnDate: String
        get() = sp.getString("lastWarnDate", "") ?: ""
        set(v) { sp.edit().putString("lastWarnDate", v).apply() }

    // ---- Password (PBKDF2 hash + salt, never the password itself)
    var pwHash: String?
        get() = sp.getString("pwHash", null)
        set(v) { sp.edit().putString("pwHash", v).commit() }

    var pwSalt: String?
        get() = sp.getString("pwSalt", null)
        set(v) { sp.edit().putString("pwSalt", v).commit() }

    var failedAttempts: Int
        get() = sp.getInt("failedAttempts", 0)
        set(v) { sp.edit().putInt("failedAttempts", v).commit() }

    var attemptLockoutUntil: Long
        get() = sp.getLong("attemptLockoutUntil", 0)
        set(v) { sp.edit().putLong("attemptLockoutUntil", v).commit() }

    // ---- Trusted clock anchor
    val anchorTrusted: Long get() = sp.getLong("anchorTrusted", 0)
    val anchorElapsed: Long get() = sp.getLong("anchorElapsed", 0)
    val anchorBoot: Int get() = sp.getInt("anchorBoot", -2)

    fun setAnchor(trusted: Long, elapsed: Long, boot: Int) {
        sp.edit().putLong("anchorTrusted", trusted).putLong("anchorElapsed", elapsed)
            .putInt("anchorBoot", boot).commit()
    }

    var highWater: Long
        get() = sp.getLong("highWater", 0)
        set(v) { sp.edit().putLong("highWater", v).apply() }
}
