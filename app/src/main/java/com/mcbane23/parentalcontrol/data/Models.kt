package com.mcbane23.parentalcontrol.data

enum class Role(val id: String) {
    PARENT("parent"),
    CHILD("child");

    companion object {
        fun fromId(id: String?): Role? = entries.firstOrNull { it.id == id }
    }
}

/** What this signed-in account is: which family it belongs to and in which role. */
data class UserProfile(
    val uid: String,
    val familyId: String,
    val role: Role,
)

data class Family(
    val id: String,
    val name: String,
    val ownerUid: String,
)

data class Member(
    val uid: String,
    val name: String,
    val role: Role,
    /** The main parent who created the family. */
    val owner: Boolean,
    /** Parents with full control can add devices and, later, change rules. */
    val fullControl: Boolean,
)

data class PairingCode(
    val code: String,
    val role: Role,
    val expiresAtMillis: Long,
)
