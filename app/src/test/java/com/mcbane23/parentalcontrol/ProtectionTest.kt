package com.mcbane23.parentalcontrol

import com.mcbane23.parentalcontrol.data.Alert
import com.mcbane23.parentalcontrol.data.AlertLog
import com.mcbane23.parentalcontrol.data.AlertType
import com.mcbane23.parentalcontrol.data.ConnectionState
import com.mcbane23.parentalcontrol.data.DataLock
import com.mcbane23.parentalcontrol.data.ParentPin
import com.mcbane23.parentalcontrol.data.SettingsGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionTest {
    private val phone = ConnectionState(
        telephony = true,
        simPresent = true,
        wifi = false,
        mobileDataOn = true,
        airplaneMode = false,
    )

    @Test
    fun dataOnNeverLocks() {
        assertFalse(DataLock.shouldLock(true, phone))
    }

    @Test
    fun dataOffAwayFromWifiLocks() {
        assertTrue(DataLock.shouldLock(true, phone.copy(mobileDataOn = false)))
        assertTrue(DataLock.shouldLock(true, phone.copy(airplaneMode = true)))
    }

    @Test
    fun wifiNeverLocks() {
        assertFalse(DataLock.shouldLock(true, phone.copy(wifi = true, mobileDataOn = false)))
        assertFalse(DataLock.shouldLock(true, phone.copy(wifi = true, airplaneMode = true)))
    }

    @Test
    fun devicesWithoutMobileNetworkNeverLock() {
        assertFalse(DataLock.shouldLock(true, phone.copy(telephony = false, mobileDataOn = false)))
        assertFalse(DataLock.shouldLock(true, phone.copy(simPresent = false, mobileDataOn = false)))
    }

    @Test
    fun ruleSwitchedOffNeverLocks() {
        assertFalse(DataLock.shouldLock(false, phone.copy(mobileDataOn = false)))
    }

    @Test
    fun pinHashMatchesOnlyTheSamePin() {
        val salt = ParentPin.newSalt()
        val hash = ParentPin.hash("1234", salt)
        assertTrue(ParentPin.matches("1234", salt, hash))
        assertFalse(ParentPin.matches("1235", salt, hash))
        assertNotEquals(hash, ParentPin.hash("1234", ParentPin.newSalt()))
    }

    @Test
    fun pinMustBeFourToEightDigits() {
        assertTrue(ParentPin.isValid("1234"))
        assertTrue(ParentPin.isValid("12345678"))
        assertFalse(ParentPin.isValid("123"))
        assertFalse(ParentPin.isValid("123456789"))
        assertFalse(ParentPin.isValid("12a4"))
    }

    @Test
    fun alertsSurviveEncodingAndKeepOnlyTheLatest() {
        var list = emptyList<Alert>()
        repeat(AlertLog.MAX + 5) { list = AlertLog.add(list, Alert(AlertType.DATA_OFF, it.toLong())) }
        assertEquals(AlertLog.MAX, list.size)
        assertEquals(5L, list.first().timeMillis)
        assertEquals(list, AlertLog.decode(AlertLog.encode(list)))
        assertEquals(emptyList<Alert>(), AlertLog.decode(""))
        assertEquals(listOf(Alert(AlertType.PIN_USED, 7)), AlertLog.decode("NOPE|1;PIN_USED|7;broken"))
    }

    private val others = setOf("Chrome", "YouTube", "Gallery", "Camera", "Clock")

    @Test
    fun screenAboutThisAppIsProtected() {
        val appInfo = listOf("App info", "Parental Control", "Uninstall", "Force stop", "Battery")
        assertTrue(SettingsGuard.isProtectedPage(appInfo, "Parental Control", others))
        val dialog = listOf("Uninstall Parental Control?", "Cancel", "OK")
        assertTrue(SettingsGuard.isProtectedPage(dialog, "Parental Control", others))
    }

    @Test
    fun listOfAppsIsNotProtected() {
        val list = listOf("Apps", "Camera", "Chrome", "Clock", "Parental Control", "YouTube")
        assertFalse(SettingsGuard.isProtectedPage(list, "Parental Control", others))
    }

    @Test
    fun otherSettingsScreensAreNotProtected() {
        val main = listOf("Settings", "Connections", "Digital Wellbeing and parental controls")
        assertFalse(SettingsGuard.isProtectedPage(main, "Parental Control", others))
        val otherApp = listOf("App info", "Chrome", "Uninstall", "Force stop")
        assertFalse(SettingsGuard.isProtectedPage(otherApp, "Parental Control", others))
    }
}
