package com.mcbane23.parentalcontrol.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The PIN a parent types on the child phone to open the screens that could remove or
 * switch off the app. Only a salted hash is stored with the child's rules.
 */
object ParentPin {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8
    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256

    fun isValid(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt.toByteArray(Charsets.UTF_8), ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.toHex()
    }

    fun matches(pin: String, salt: String, hash: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), hash.toByteArray())

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

/** Things a child phone tells the parents about, besides usage and location. */
enum class AlertType {
    /** Apps were locked because mobile data or flight mode cut the phone off, away from Wi-Fi. */
    DATA_OFF,
    DATA_ON,
    /** Uninstall protection (device admin) was switched off. */
    ADMIN_OFF,
    /** The accessibility service was switched off, so nothing is enforced any more. */
    GUARD_OFF,
    /** Someone opened a protected settings screen and was asked for the PIN. */
    SETTINGS_BLOCKED,
    /** Several wrong PINs in a row. */
    PIN_WRONG,
    /** The right PIN was entered; protected settings were open for a few minutes. */
    PIN_USED,
}

data class Alert(val type: AlertType, val timeMillis: Long)

/** The child phone keeps its latest alerts in one short list. */
object AlertLog {
    const val MAX = 30

    fun add(list: List<Alert>, alert: Alert): List<Alert> = (list + alert).takeLast(MAX)

    fun encode(list: List<Alert>): String = list.joinToString(";") { "${it.type.name}|${it.timeMillis}" }

    fun decode(text: String): List<Alert> = text.split(';').mapNotNull { entry ->
        val parts = entry.split('|')
        val type = AlertType.entries.firstOrNull { it.name == parts.getOrNull(0) }
        val time = parts.getOrNull(1)?.toLongOrNull()
        if (type != null && time != null) Alert(type, time) else null
    }
}

/** How the child phone is connected right now. */
data class ConnectionState(
    /** False on a tablet that cannot use a mobile network at all. */
    val telephony: Boolean,
    val simPresent: Boolean,
    val wifi: Boolean,
    /** The "Mobile data" switch. */
    val mobileDataOn: Boolean,
    val airplaneMode: Boolean,
) {
    /** Whether the phone can reach its family when it leaves Wi-Fi. */
    val mobileDataUsable: Boolean
        get() = !telephony || !simPresent || (mobileDataOn && !airplaneMode)
}

object DataLock {
    /**
     * Apps are locked when the child has cut the phone off: mobile data is switched off or
     * flight mode is on. Never on Wi-Fi, never on a device without a SIM, and not when the
     * phone simply has no signal.
     */
    fun shouldLock(required: Boolean, state: ConnectionState): Boolean =
        required && !state.wifi && !state.mobileDataUsable
}

object SettingsGuard {
    /** A screen showing this many other apps by name is a list of apps, not a screen about ours. */
    const val LIST_THRESHOLD = 3

    /**
     * Decides from the text on a system settings screen whether it is about this app: its
     * app info, its uninstall dialog, its device admin or accessibility page. It does not
     * depend on the phone's language, only on app names.
     */
    fun isProtectedPage(texts: Collection<String>, ownLabel: String, otherLabels: Set<String>): Boolean {
        if (texts.none { it.contains(ownLabel) }) return false
        val others = texts.filterTo(HashSet()) { it in otherLabels }
        return others.size < LIST_THRESHOLD
    }
}
