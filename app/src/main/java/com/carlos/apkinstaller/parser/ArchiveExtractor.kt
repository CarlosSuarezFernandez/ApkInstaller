package com.carlos.apkinstaller.parser

import com.carlos.apkinstaller.model.ApkLoadException
import com.carlos.apkinstaller.model.LoadError
import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Handles plain APKs and APK containers (.apks, .xapk, unencrypted .apkm). */
object ArchiveExtractor {

    /** True when [file] is itself an APK (a zip with a manifest at its root). */
    fun isApk(file: File): Boolean = openZip(file).use { it.getEntry("AndroidManifest.xml") != null }

    /**
     * Extracts the APKs to install from a container into [outDir]. Output names are
     * generated, never derived from entry names, so malicious paths can't escape [outDir].
     */
    fun extractApks(archive: File, outDir: File, prefix: String): List<File> = openZip(archive).use { zip ->
        val entries = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
            .associateBy { it.name }
        selectEntries(entries.keys.toList()).mapIndexed { index, name ->
            val out = File(outDir, "${prefix}_$index.apk")
            zip.getInputStream(entries.getValue(name)).use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            out
        }
    }

    /**
     * Picks which APK entries to install. bundletool `.apks` files contain `splits/`
     * (base, config and feature splits), `asset-slices/` (Play Asset Delivery packs)
     * and `standalones/` (pre-Lollipop variants that must not be mixed with splits).
     */
    fun selectEntries(names: List<String>): List<String> {
        val splits = names.filter { it.startsWith("splits/") || it.startsWith("asset-slices/") }
        if (splits.isNotEmpty()) return splits.sorted()
        val regular = names.filterNot { it.startsWith("standalones/") }
        return regular.ifEmpty { names.take(1) }.sorted()
    }

    private fun openZip(file: File): ZipFile = try {
        ZipFile(file)
    } catch (e: ZipException) {
        throw ApkLoadException(LoadError.NOT_AN_ARCHIVE, "${file.name} is not a zip file", e)
    }
}
