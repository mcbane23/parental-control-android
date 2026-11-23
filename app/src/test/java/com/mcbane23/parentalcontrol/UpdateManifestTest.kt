package com.mcbane23.parentalcontrol

import com.mcbane23.parentalcontrol.update.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    private val sha = "1b13eafcffa1361543b5c43a4f82fb17acc41cf2022aadd417f44b42bb39d6f3"

    private fun json(
        versionCode: String = "2",
        apk: String = "parental-control-0.1.1.apk",
        sha256: String = sha,
    ) = """
        {
          "versionCode": $versionCode,
          "versionName": "0.1.1",
          "apk": "$apk",
          "sha256": "$sha256",
          "commit": "abc1234",
          "notes": { "en": "First line\nSecond line\n" }
        }
    """.trimIndent()

    @Test
    fun parsesAPublishedManifest() {
        val manifest = UpdateManifest.parse(json())
        assertEquals(2L, manifest.versionCode)
        assertEquals("0.1.1", manifest.versionName)
        assertEquals("parental-control-0.1.1.apk", manifest.apk)
        assertEquals(sha, manifest.sha256)
        assertEquals("First line\nSecond line", manifest.notes)
    }

    @Test
    fun onlyAHigherVersionCodeIsNewer() {
        val manifest = UpdateManifest.parse(json())
        assertTrue(manifest.isNewerThan(1))
        assertFalse(manifest.isNewerThan(2))
        assertFalse(manifest.isNewerThan(3))
    }

    @Test
    fun acceptsAnUppercaseChecksumAndMissingNotes() {
        val text = json(sha256 = sha.uppercase()).replace(Regex(""",\s*"notes".*\}\s*\}""", RegexOption.DOT_MATCHES_ALL), "}")
        val manifest = UpdateManifest.parse(text)
        assertEquals(sha, manifest.sha256)
        assertEquals("", manifest.notes)
    }

    @Test
    fun rejectsBrokenManifests() {
        assertThrows(IllegalArgumentException::class.java) { UpdateManifest.parse("<html>Not Found</html>") }
        assertThrows(IllegalArgumentException::class.java) { UpdateManifest.parse(json(versionCode = "0")) }
        assertThrows(IllegalArgumentException::class.java) { UpdateManifest.parse(json(sha256 = "abc")) }
    }

    @Test
    fun rejectsApkNamesThatAreNotPlainFileNames() {
        for (name in listOf("../evil.apk", "https://example.com/a.apk", "a/b.apk", "app.zip", "")) {
            assertThrows(IllegalArgumentException::class.java) { UpdateManifest.parse(json(apk = name)) }
        }
    }
}
