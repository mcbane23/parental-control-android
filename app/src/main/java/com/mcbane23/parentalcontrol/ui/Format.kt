package com.mcbane23.parentalcontrol.ui

/** "1 h 20 min", "45 min" or "Less than a minute". */
fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "Less than a minute"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

fun formatMinutes(minutes: Int): String = formatDuration(minutes * 60_000L)
