package com.carlos.apkinstaller.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SessionInstaller(private val context: Context) : Installer {

    override suspend fun install(packageName: String, apks: List<File>, onProgress: (Float) -> Unit): Int =
        withContext(Dispatchers.IO) {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(packageName)
                setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
                }
            }
            val sessionId = packageInstaller.createSession(params)
            try {
                packageInstaller.openSession(sessionId).use { session ->
                    val total = apks.sumOf { it.length() }.coerceAtLeast(1)
                    var written = 0L
                    val buffer = ByteArray(1 shl 16)
                    apks.forEachIndexed { index, apk ->
                        session.openWrite("split_$index.apk", 0, apk.length()).use { out ->
                            apk.inputStream().use { input ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    out.write(buffer, 0, read)
                                    written += read
                                    onProgress(written.toFloat() / total)
                                }
                            }
                            session.fsync(out)
                        }
                    }
                    session.commit(statusReceiver(sessionId).intentSender)
                }
            } catch (e: Exception) {
                runCatching { packageInstaller.abandonSession(sessionId) }
                throw e
            }
            sessionId
        }

    private fun statusReceiver(sessionId: Int): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }
}
