package com.mcbane23.parentalcontrol.data

import kotlin.random.Random

object PairingCodes {
    /** Letters and digits that are hard to confuse when read aloud or typed (no 0/O, 1/I/L). */
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6
    const val VALID_MINUTES = 15L

    fun generate(random: Random = Random.Default): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** Cleans up what a person typed: trims, uppercases and drops spaces and dashes. */
    fun normalize(input: String): String =
        input.uppercase().filter { it in ALPHABET }

    fun isWellFormed(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }
}
