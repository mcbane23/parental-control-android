package com.mcbane23.parentalcontrol

import com.mcbane23.parentalcontrol.data.PairingCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PairingCodesTest {
    @Test
    fun generatedCodesAreWellFormed() {
        val random = Random(42)
        repeat(200) {
            assertTrue(PairingCodes.isWellFormed(PairingCodes.generate(random)))
        }
    }

    @Test
    fun normalizeIgnoresCaseSpacesAndDashes() {
        assertEquals("ABC234", PairingCodes.normalize(" abc-2 34 "))
    }

    @Test
    fun rejectsWrongLengthAndAmbiguousCharacters() {
        assertFalse(PairingCodes.isWellFormed("ABC23"))
        assertFalse(PairingCodes.isWellFormed("ABC10O"))
    }
}
