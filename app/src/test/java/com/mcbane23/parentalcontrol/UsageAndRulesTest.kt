package com.mcbane23.parentalcontrol

import com.mcbane23.parentalcontrol.child.UsageTracker
import com.mcbane23.parentalcontrol.child.UsageTracker.UsageEvent
import com.mcbane23.parentalcontrol.data.AppRule
import com.mcbane23.parentalcontrol.data.BlockReason
import com.mcbane23.parentalcontrol.data.ChildRules
import com.mcbane23.parentalcontrol.data.RuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageAndRulesTest {
    private val resumed = 1
    private val paused = 2
    private val screenOff = 16

    @Test
    fun sumsResumeToPausePerPackage() {
        val events = listOf(
            UsageEvent(resumed, "a", "Main", 1_000),
            UsageEvent(paused, "a", "Main", 4_000),
            UsageEvent(resumed, "b", "Main", 4_000),
            UsageEvent(paused, "b", "Main", 5_000),
            UsageEvent(resumed, "a", "Main", 5_000),
            UsageEvent(paused, "a", "Main", 7_000),
        )
        assertEquals(mapOf("a" to 5_000L, "b" to 1_000L), UsageTracker.sumForeground(events, now = 10_000))
    }

    @Test
    fun appStillOpenCountsUntilNow() {
        val events = listOf(UsageEvent(resumed, "a", "Main", 2_000))
        assertEquals(mapOf("a" to 8_000L), UsageTracker.sumForeground(events, now = 10_000))
    }

    @Test
    fun screenOffStopsTheClock() {
        val events = listOf(
            UsageEvent(resumed, "a", "Main", 1_000),
            UsageEvent(screenOff, "android", "", 3_000),
        )
        assertEquals(mapOf("a" to 2_000L), UsageTracker.sumForeground(events, now = 60_000))
    }

    @Test
    fun pauseWithoutResumeIsIgnored() {
        val events = listOf(UsageEvent(paused, "a", "Main", 3_000))
        assertEquals(emptyMap<String, Long>(), UsageTracker.sumForeground(events, now = 10_000))
    }

    @Test
    fun blockedAppIsBlockedEvenWithNoUsage() {
        val rules = ChildRules(apps = mapOf("a" to AppRule(blocked = true)))
        assertEquals(BlockReason.BLOCKED, RuleEngine.check("a", rules, emptyMap(), 0))
        assertNull(RuleEngine.check("b", rules, emptyMap(), 0))
    }

    @Test
    fun appLimitAppliesOnlyToThatApp() {
        val rules = ChildRules(apps = mapOf("a" to AppRule(limitMinutes = 10)))
        val usage = mapOf("a" to 10 * 60_000L, "b" to 50 * 60_000L)
        assertEquals(BlockReason.APP_LIMIT, RuleEngine.check("a", rules, usage, 60 * 60_000L))
        assertNull(RuleEngine.check("b", rules, usage, 60 * 60_000L))
        assertNull(RuleEngine.check("a", rules, mapOf("a" to 9 * 60_000L), 9 * 60_000L))
    }

    @Test
    fun dailyLimitBlocksEveryCountedApp() {
        val rules = ChildRules(dailyLimitMinutes = 60)
        assertEquals(BlockReason.DAILY_LIMIT, RuleEngine.check("a", rules, emptyMap(), 60 * 60_000L))
        assertNull(RuleEngine.check("a", rules, emptyMap(), 59 * 60_000L))
    }
}
