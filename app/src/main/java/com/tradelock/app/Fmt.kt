package com.tradelock.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dayTime = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)

    /** "10:00 AM UAE (Thu 8 Oct)" */
    fun uae(utc: Long): String {
        val z = LockPolicy.uae(utc)
        return "${time.format(z)} UAE (${DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH).format(z)})"
    }

    fun uaeTimeOnly(utc: Long): String = time.format(LockPolicy.uae(utc))

    /** "= 7:00 AM your time" — only shown when travelling outside UAE time. */
    fun localIfDifferent(utc: Long): String? {
        val local = ZoneId.systemDefault()
        val inst = Instant.ofEpochMilli(utc)
        if (local.rules.getOffset(inst) == LockPolicy.UAE.rules.getOffset(inst)) return null
        return "= ${dayTime.format(inst.atZone(local))} your time"
    }

    /** "13h 22m" / "4m 10s" */
    fun countdown(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m ${s % 60}s"
    }
}
