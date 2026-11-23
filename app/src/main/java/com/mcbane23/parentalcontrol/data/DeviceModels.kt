package com.mcbane23.parentalcontrol.data

/** What a child phone last reported about itself. */
data class DeviceStatus(
    val lastSeenMillis: Long,
    /** Local date the usage numbers belong to, as yyyy-MM-dd. */
    val day: String,
    val usedTodayMs: Long,
    /** Whether the child phone has granted the two permissions the rules depend on. */
    val usageAccess: Boolean,
    val accessibility: Boolean,
    /** Location permission, including "Allow all the time". */
    val locationAccess: Boolean,
)

data class ChildLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timeMillis: Long,
    val batteryPercent: Int?,
)

data class AppUsage(
    val packageName: String,
    val label: String,
    val usedTodayMs: Long,
)

data class AppRule(
    val blocked: Boolean = false,
    val limitMinutes: Int? = null,
)

/** The rules a parent has set for one child phone. */
data class ChildRules(
    val dailyLimitMinutes: Int? = null,
    val apps: Map<String, AppRule> = emptyMap(),
)

enum class BlockReason { BLOCKED, APP_LIMIT, DAILY_LIMIT }

object RuleEngine {
    /**
     * Decides whether [packageName] may be used right now.
     *
     * @param usageMs time in the foreground today, per package
     * @param totalMs time today that counts towards the daily limit
     */
    fun check(
        packageName: String,
        rules: ChildRules,
        usageMs: Map<String, Long>,
        totalMs: Long,
    ): BlockReason? {
        val rule = rules.apps[packageName]
        if (rule?.blocked == true) return BlockReason.BLOCKED
        val appLimit = rule?.limitMinutes
        if (appLimit != null && (usageMs[packageName] ?: 0L) >= appLimit * 60_000L) {
            return BlockReason.APP_LIMIT
        }
        val daily = rules.dailyLimitMinutes
        if (daily != null && totalMs >= daily * 60_000L) return BlockReason.DAILY_LIMIT
        return null
    }
}
