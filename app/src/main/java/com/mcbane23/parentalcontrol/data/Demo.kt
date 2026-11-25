package com.mcbane23.parentalcontrol.data

/** Everything the demo shows about one made-up child phone. */
data class DemoChild(
    val member: Member,
    val status: DeviceStatus,
    val apps: List<AppUsage>,
    val rules: ChildRules,
    val location: ChildLocation,
    /** Newest first, like the alerts a real child phone reports. */
    val alerts: List<Alert>,
)

/**
 * A made-up family for looking around the parent screens without a paired child phone.
 * It lives in memory only: nothing here is read from or written to Firebase, and it is
 * built fresh every time the demo starts.
 */
object Demo {
    const val PARENT_UID = "demo-parent"
    private const val OTHER_PARENT_UID = "demo-parent-2"
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    val family = Family(id = "demo-family", name = "Demo family", ownerUid = PARENT_UID)

    val parents: List<Member> = listOf(
        Member(PARENT_UID, "You", Role.PARENT, owner = true, fullControl = true),
        Member(OTHER_PARENT_UID, "Alex", Role.PARENT, owner = false, fullControl = false),
    )

    /**
     * Two children: one phone where everything works, and one with a problem and no rules
     * yet, so both the calm and the warning states of the screens can be seen.
     *
     * @param day today's date as yyyy-MM-dd, as a child phone would report it
     */
    fun children(nowMillis: Long, day: String): List<DemoChild> {
        val miaApps = listOf(
            AppUsage("com.google.android.youtube", "YouTube", 58 * MINUTE),
            AppUsage("com.zhiliaoapp.musically", "TikTok", 45 * MINUTE),
            AppUsage("com.roblox.client", "Roblox", 32 * MINUTE),
            AppUsage("com.whatsapp", "WhatsApp", 12 * MINUTE),
            AppUsage("com.android.chrome", "Chrome", 8 * MINUTE),
            AppUsage("com.instagram.android", "Instagram", 0),
            AppUsage("com.duolingo", "Duolingo", 0),
        )
        val mia = DemoChild(
            member = Member("demo-child-1", "Mia", Role.CHILD, owner = false, fullControl = false),
            status = DeviceStatus(
                lastSeenMillis = nowMillis - 2 * MINUTE,
                day = day,
                usedTodayMs = miaApps.sumOf { it.usedTodayMs },
                usageAccess = true,
                accessibility = true,
                locationAccess = true,
                deviceAdmin = true,
                mobileData = true,
            ),
            apps = miaApps,
            rules = ChildRules(
                dailyLimitMinutes = 180,
                apps = mapOf(
                    "com.google.android.youtube" to AppRule(limitMinutes = 60),
                    "com.zhiliaoapp.musically" to AppRule(limitMinutes = 45),
                    "com.instagram.android" to AppRule(blocked = true),
                ),
                requireMobileData = true,
                pinSalt = "demo",
                pinHash = "demo",
            ),
            location = ChildLocation(
                latitude = 51.5079,
                longitude = -0.1657,
                accuracyMeters = 12f,
                timeMillis = nowMillis - 4 * MINUTE,
                batteryPercent = 76,
            ),
            alerts = listOf(
                Alert(AlertType.PIN_USED, nowMillis - 3 * HOUR),
                Alert(AlertType.SETTINGS_BLOCKED, nowMillis - 26 * HOUR),
            ),
        )

        val leoApps = listOf(
            AppUsage("com.supercell.brawlstars", "Brawl Stars", 41 * MINUTE),
            AppUsage("com.google.android.youtube", "YouTube", 27 * MINUTE),
            AppUsage("com.spotify.music", "Spotify", 15 * MINUTE),
            AppUsage("com.android.chrome", "Chrome", 3 * MINUTE),
            AppUsage("com.mojang.minecraftpe", "Minecraft", 0),
        )
        val leo = DemoChild(
            member = Member("demo-child-2", "Leo", Role.CHILD, owner = false, fullControl = false),
            status = DeviceStatus(
                lastSeenMillis = nowMillis - 47 * MINUTE,
                day = day,
                usedTodayMs = leoApps.sumOf { it.usedTodayMs },
                usageAccess = true,
                accessibility = true,
                locationAccess = true,
                deviceAdmin = true,
                mobileData = false,
            ),
            apps = leoApps,
            rules = ChildRules(),
            location = ChildLocation(
                latitude = 51.5010,
                longitude = -0.1246,
                accuracyMeters = 35f,
                timeMillis = nowMillis - 47 * MINUTE,
                batteryPercent = 23,
            ),
            alerts = listOf(
                Alert(AlertType.DATA_OFF, nowMillis - 47 * MINUTE),
                Alert(AlertType.PIN_WRONG, nowMillis - 5 * HOUR),
                Alert(AlertType.SETTINGS_BLOCKED, nowMillis - 5 * HOUR - 2 * MINUTE),
            ),
        )
        return listOf(mia, leo)
    }

    /** What a tap on "Refresh" does in the demo: the same place, a little off, seen just now. */
    fun refreshed(location: ChildLocation, nowMillis: Long): ChildLocation = location.copy(
        latitude = location.latitude + 0.0004,
        longitude = location.longitude + 0.0006,
        timeMillis = nowMillis,
    )

    fun withAppRule(rules: ChildRules, packageName: String, rule: AppRule): ChildRules =
        rules.copy(apps = rules.apps + (packageName to rule))

    /** The demo never keeps the PIN that was typed, only whether one counts as set. */
    fun withPin(rules: ChildRules, set: Boolean): ChildRules =
        rules.copy(pinSalt = if (set) "demo" else null, pinHash = if (set) "demo" else null)
}
