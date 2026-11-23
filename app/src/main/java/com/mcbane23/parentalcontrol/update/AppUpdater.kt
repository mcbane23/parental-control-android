package com.mcbane23.parentalcontrol.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Checks the `parental-control` release of mcbane23/apps for a newer APK, downloads it and
 * hands it to the system installer.
 */
class AppUpdater(private val context: Context) {

    val installedVersionName: String
        get() = packageInfo().versionName ?: "?"

    val installedVersionCode: Long
        get() = packageInfo().longVersionCode

    private fun packageInfo() = context.packageManager.getPackageInfo(context.packageName, 0)

    suspend fun fetchManifest(): UpdateManifest = withContext(Dispatchers.IO) {
        val text = open("$BASE_URL/$MANIFEST_NAME").use { it.readBytes().decodeToString() }
        try {
            UpdateManifest.parse(text)
        } catch (e: IllegalArgumentException) {
            throw IOException(e.message, e)
        }
    }

    /** Downloads the APK named in [manifest] and checks it against the manifest's checksum. */
    suspend fun download(manifest: UpdateManifest, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates")
            dir.deleteRecursively()
            dir.mkdirs()
            val target = File(dir, "update.apk")
            val digest = MessageDigest.getInstance("SHA-256")

            val connection = connect("$BASE_URL/${manifest.apk}")
            try {
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != manifest.sha256) {
                target.delete()
                throw IOException("The downloaded file is damaged. Try again.")
            }
            target
        }

    /**
     * Gives the APK to the system installer. The result arrives in [UpdateInstallReceiver],
     * which opens the system's confirmation screen when Android asks for one.
     */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // An app updating itself may skip the confirmation after the first approval.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("update.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(UpdateInstallReceiver.ACTION)
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // The system adds the result to the intent, so it must be mutable.
                    flags = flags or PendingIntent.FLAG_MUTABLE
                }
                val callback = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(callback.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    private fun open(url: String): InputStream {
        val connection = connect(url)
        return object : java.io.FilterInputStream(connection.inputStream) {
            override fun close() {
                super.close()
                connection.disconnect()
            }
        }
    }

    private fun connect(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Cache-Control", "no-cache")
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IOException("The update server answered $code")
        }
        return connection
    }

    companion object {
        const val BASE_URL = "https://github.com/mcbane23/apps/releases/download/parental-control"
        const val MANIFEST_NAME = "parental-control.json"
    }
}
