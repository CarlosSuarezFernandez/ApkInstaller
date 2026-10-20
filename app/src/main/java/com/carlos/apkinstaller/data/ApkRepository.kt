package com.carlos.apkinstaller.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import com.carlos.apkinstaller.model.ApkLoadException
import com.carlos.apkinstaller.model.AppPackage
import com.carlos.apkinstaller.model.DeviceSpec
import com.carlos.apkinstaller.model.LoadError
import com.carlos.apkinstaller.parser.ArchiveExtractor
import com.carlos.apkinstaller.parser.ManifestReader
import com.carlos.apkinstaller.parser.PackageAssembler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

data class LoadedApp(
    val app: AppPackage,
    val label: String,
    val icon: Drawable?,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
)

class ApkRepository(private val context: Context) {
    private val workRoot = File(context.cacheDir, "install_work")

    /** Copies the given documents into private cache, unpacks containers and parses every APK. */
    suspend fun load(uris: List<Uri>): LoadedApp = withContext(Dispatchers.IO) {
        clearWorkDir()
        val dir = File(workRoot, UUID.randomUUID().toString()).apply { mkdirs() }
        val apkFiles = mutableListOf<File>()
        try {
            uris.forEachIndexed { index, uri ->
                val input = File(dir, "input_$index.tmp")
                copyUri(uri, input)
                if (ArchiveExtractor.isApk(input)) {
                    val apk = File(dir, "apk_$index.apk")
                    input.renameTo(apk)
                    apkFiles += apk
                } else {
                    apkFiles += ArchiveExtractor.extractApks(input, dir, "bundle$index")
                    input.delete()
                }
            }
            if (apkFiles.isEmpty()) throw ApkLoadException(LoadError.NO_APKS)
            val app = PackageAssembler.assemble(apkFiles.map { it to ManifestReader.read(it) })
            describe(app)
        } catch (e: IOException) {
            throw ApkLoadException(LoadError.IO, e.message, e)
        }
    }

    fun clearWorkDir() {
        workRoot.deleteRecursively()
    }

    fun deviceSpec(): DeviceSpec {
        val locales = context.resources.configuration.locales
        return DeviceSpec(
            abis = Build.SUPPORTED_ABIS.toList(),
            densityDpi = context.resources.displayMetrics.densityDpi,
            languages = (0 until locales.size()).map { locales[it].language },
        )
    }

    private fun copyUri(uri: Uri, target: File) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open $uri")
        stream.use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    private fun describe(app: AppPackage): LoadedApp {
        val pm = context.packageManager
        val basePath = app.base.file.absolutePath
        val archiveInfo = pm.getPackageArchiveInfo(basePath, 0)
        val appInfo = archiveInfo?.applicationInfo?.apply {
            sourceDir = basePath
            publicSourceDir = basePath
        }
        val installed = try {
            pm.getPackageInfo(app.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        return LoadedApp(
            app = app,
            label = appInfo?.loadLabel(pm)?.toString() ?: app.packageName,
            icon = appInfo?.loadIcon(pm),
            installedVersionName = installed?.versionName,
            installedVersionCode = installed?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
            },
        )
    }
}
