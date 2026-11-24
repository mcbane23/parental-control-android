package com.mcbane23.parentalcontrol.child

import android.os.SystemClock

/**
 * What the accessibility service and the PIN screen share while the app is running.
 * Times use the clock that counts from boot, so changing the phone's date does not help.
 */
object GuardState {
    const val UNLOCK_MS = 5 * 60_000L
    const val MAX_ATTEMPTS = 5
    const val LOCKOUT_MS = 60_000L

    @Volatile var pinSalt: String? = null
    @Volatile var pinHash: String? = null

    @Volatile private var unlockedUntil = 0L
    @Volatile private var lockedOutUntil = 0L
    private var wrongAttempts = 0

    /** Protected settings are only guarded once a parent has set a PIN. */
    val armed: Boolean get() = !pinSalt.isNullOrEmpty() && !pinHash.isNullOrEmpty()

    fun isUnlocked(): Boolean = SystemClock.elapsedRealtime() < unlockedUntil

    fun unlock() {
        wrongAttempts = 0
        unlockedUntil = SystemClock.elapsedRealtime() + UNLOCK_MS
    }

    /** Seconds until the PIN can be tried again, or 0. */
    fun lockoutSeconds(): Int {
        val left = lockedOutUntil - SystemClock.elapsedRealtime()
        return if (left <= 0) 0 else ((left + 999) / 1000).toInt()
    }

    /** Counts a wrong PIN. Returns true when this one started a lockout. */
    @Synchronized
    fun wrongAttempt(): Boolean {
        wrongAttempts++
        if (wrongAttempts < MAX_ATTEMPTS) return false
        wrongAttempts = 0
        lockedOutUntil = SystemClock.elapsedRealtime() + LOCKOUT_MS
        return true
    }
}
