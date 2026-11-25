package com.mcbane23.parentalcontrol

import com.mcbane23.parentalcontrol.data.AppRule
import com.mcbane23.parentalcontrol.data.Demo
import com.mcbane23.parentalcontrol.data.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoTest {
    private val now = 1_800_000_000_000L
    private val children = Demo.children(now, "2027-01-15")

    @Test
    fun familyHasOneOwnerAndDistinctMembers() {
        val members = Demo.parents + children.map { it.member }
        assertEquals(members.size, members.map { it.uid }.toSet().size)
        assertEquals(listOf(Demo.PARENT_UID), members.filter { it.owner }.map { it.uid })
        assertEquals(Demo.family.ownerUid, Demo.PARENT_UID)
        assertTrue(children.all { it.member.role == Role.CHILD })
    }

    @Test
    fun usageAddsUpAndRulesPointAtListedApps() {
        for (child in children) {
            val packages = child.apps.map { it.packageName }
            assertEquals(packages.size, packages.toSet().size)
            assertEquals(child.apps.sumOf { it.usedTodayMs }, child.status.usedTodayMs)
            assertTrue(packages.containsAll(child.rules.apps.keys))
        }
    }

    @Test
    fun nothingIsDatedInTheFuture() {
        for (child in children) {
            assertTrue(child.status.lastSeenMillis <= now)
            assertTrue(child.location.timeMillis <= now)
            assertTrue(child.alerts.all { it.timeMillis <= now })
            assertEquals(child.alerts.sortedByDescending { it.timeMillis }, child.alerts)
        }
    }

    @Test
    fun demoShowsBothAHealthyPhoneAndOneWithAProblem() {
        assertTrue(children.any { it.status.mobileData && it.rules.pinSet })
        assertTrue(children.any { !it.status.mobileData && !it.rules.pinSet })
    }

    @Test
    fun changesKeepTheOtherRules() {
        val rules = children.first().rules
        val changed = Demo.withAppRule(rules, "com.whatsapp", AppRule(blocked = true))
        assertTrue(changed.apps.getValue("com.whatsapp").blocked)
        assertEquals(rules.apps.size + 1, changed.apps.size)
        assertEquals(rules.dailyLimitMinutes, changed.dailyLimitMinutes)

        assertFalse(Demo.withPin(rules, set = false).pinSet)
        assertTrue(Demo.withPin(Demo.withPin(rules, set = false), set = true).pinSet)
    }

    @Test
    fun refreshMovesThePinAndTheClock() {
        val before = children.first().location
        val after = Demo.refreshed(before, now + 1000)
        assertEquals(now + 1000, after.timeMillis)
        assertTrue(after.latitude != before.latitude || after.longitude != before.longitude)
    }
}
