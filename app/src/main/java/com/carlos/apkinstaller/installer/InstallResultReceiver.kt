package com.carlos.apkinstaller.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class InstallResultReceiver : BroadcastReceiver(), KoinComponent {
    private val events: InstallEvents by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    events.emit(InstallEvent.Failure(sessionId, PackageInstaller.STATUS_FAILURE, null))
                    return
                }
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot show install confirmation", e)
                    events.emit(InstallEvent.Failure(sessionId, PackageInstaller.STATUS_FAILURE, e.message))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> events.emit(
                InstallEvent.Success(sessionId, intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)),
            )
            else -> events.emit(
                InstallEvent.Failure(sessionId, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)),
            )
        }
    }

    companion object {
        const val ACTION = "com.carlos.apkinstaller.INSTALL_STATUS"
        private const val TAG = "InstallResultReceiver"
    }
}
