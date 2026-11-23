package com.mcbane23.parentalcontrol.update

import org.json.JSONObject

/**
 * The small JSON file published next to the APKs in the `parental-control` release of
 * mcbane23/apps. It has the same shape as the manifests of the other apps kept there.
 */
data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apk: String,
    val sha256: String,
    val notes: String,
) {
    fun isNewerThan(installedVersionCode: Long): Boolean = versionCode > installedVersionCode

    companion object {
        private val APK_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.apk")
        private val SHA256 = Regex("[0-9a-f]{64}")

        /** Throws [IllegalArgumentException] when the text is not a usable manifest. */
        fun parse(text: String): UpdateManifest {
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw IllegalArgumentException("The update information is not valid JSON", e)
            }
            val versionCode = json.optLong("versionCode", -1)
            val versionName = json.optString("versionName")
            val apk = json.optString("apk")
            val sha256 = json.optString("sha256").lowercase()
            require(versionCode > 0) { "The update information has no version code" }
            require(versionName.isNotBlank()) { "The update information has no version name" }
            // The name is joined to the download address, so it must be a plain file name.
            require(APK_NAME.matches(apk)) { "The update information has a bad APK name" }
            require(SHA256.matches(sha256)) { "The update information has a bad checksum" }
            val notes = json.optJSONObject("notes")?.optString("en").orEmpty().trim()
            return UpdateManifest(versionCode, versionName, apk, sha256, notes)
        }
    }
}
