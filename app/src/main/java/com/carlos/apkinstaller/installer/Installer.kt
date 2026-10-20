package com.carlos.apkinstaller.installer

import java.io.File

sealed interface InstallEvent {
    val sessionId: Int

    data class Success(override val sessionId: Int, val packageName: String?) : InstallEvent
    data class Failure(override val sessionId: Int, val status: Int, val message: String?) : InstallEvent
}

/** Installation backend. Only the rootless PackageInstaller one exists for now (Shizuku/root could be added). */
interface Installer {
    /** Writes [apks] into a new install session and commits it. Returns the session id; the outcome arrives via [InstallEvents]. */
    suspend fun install(packageName: String, apks: List<File>, onProgress: (Float) -> Unit): Int
}
