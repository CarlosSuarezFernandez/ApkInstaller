package com.carlos.apkinstaller.parser

import com.carlos.apkinstaller.model.ApkLoadException
import com.carlos.apkinstaller.model.ApkManifestInfo
import com.carlos.apkinstaller.model.LoadError
import java.io.File
import java.util.zip.ZipFile

object ManifestReader {
    fun read(apk: File): ApkManifestInfo {
        val attributes = try {
            ZipFile(apk).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml")
                    ?: throw ApkLoadException(LoadError.UNREADABLE_MANIFEST, "No manifest in ${apk.name}")
                BinaryXmlParser.readRootAttributes(zip.getInputStream(entry).use { it.readBytes() })
            }
        } catch (e: ApkLoadException) {
            throw e
        } catch (e: Exception) {
            throw ApkLoadException(LoadError.UNREADABLE_MANIFEST, "Cannot read ${apk.name}", e)
        }
        val packageName = attributes["package"]?.takeIf { it.isNotBlank() }
            ?: throw ApkLoadException(LoadError.UNREADABLE_MANIFEST, "No package name in ${apk.name}")
        return ApkManifestInfo(
            packageName = packageName,
            splitName = attributes["split"]?.takeIf { it.isNotBlank() },
            versionCode = attributes["versionCode"]?.toLongOrNull(),
            versionName = attributes["versionName"],
            configForSplit = attributes["configForSplit"]?.takeIf { it.isNotBlank() },
            isFeatureSplit = attributes["isFeatureSplit"] == "true",
        )
    }
}
