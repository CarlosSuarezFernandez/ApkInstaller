package com.carlos.apkinstaller

import com.carlos.apkinstaller.model.ApkLoadException
import com.carlos.apkinstaller.model.ApkManifestInfo
import com.carlos.apkinstaller.model.DeviceSpec
import com.carlos.apkinstaller.model.LoadError
import com.carlos.apkinstaller.model.SplitType
import com.carlos.apkinstaller.parser.ArchiveExtractor
import com.carlos.apkinstaller.parser.ManifestReader
import com.carlos.apkinstaller.parser.PackageAssembler
import com.carlos.apkinstaller.parser.SplitClassifier
import com.carlos.apkinstaller.parser.SplitSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Fixtures are real APKs produced by `aapt2 link --split`. */
class ApkParsingTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val fixtureNames = listOf("base", "config.arm64_v8a", "config.es", "config.xxhdpi", "config.mdpi")

    private fun fixture(name: String): File =
        File(tmp.root, "$name.apk").also { out ->
            javaClass.getResourceAsStream("/fixtures/$name.apk")!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        }

    private fun zipOf(name: String, entries: Map<String, File>): File = File(tmp.root, name).also { zip ->
        ZipOutputStream(zip.outputStream()).use { out ->
            entries.forEach { (entryName, file) ->
                out.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }

    @Test
    fun `reads base and split manifests compiled by aapt2`() {
        val base = ManifestReader.read(fixture("base"))
        assertEquals("com.example.splitdemo", base.packageName)
        assertEquals(7L, base.versionCode)
        assertEquals("1.2.3", base.versionName)
        assertNull(base.splitName)

        assertEquals("config.xxhdpi_v4", ManifestReader.read(fixture("config.xxhdpi")).splitName)
        assertEquals("config.arm64_v8a", ManifestReader.read(fixture("config.arm64_v8a")).splitName)
    }

    @Test
    fun `bundletool apks only installs splits and ignores standalones`() {
        val apks = zipOf(
            "app.apks",
            mapOf(
                "splits/base-master.apk" to fixture("base"),
                "splits/base-es.apk" to fixture("config.es"),
                "standalones/standalone-arm64_v8a_xxhdpi.apk" to fixture("base"),
                "toc.pb" to fixture("base"),
            ),
        )
        assertFalse(ArchiveExtractor.isApk(apks))
        val extracted = ArchiveExtractor.extractApks(apks, tmp.newFolder("out"), "b")
        val splitNames = extracted.map { ManifestReader.read(it).splitName }
        assertEquals(listOf("config.es", null), splitNames)
        extracted.forEach { assertEquals(tmp.root.resolve("out"), it.parentFile) }
    }

    @Test
    fun `xapk extracts root apks and skips other entries`() {
        val xapk = zipOf(
            "app.xapk",
            mapOf(
                "com.example.splitdemo.apk" to fixture("base"),
                "config.xxhdpi.apk" to fixture("config.xxhdpi"),
                "manifest.json" to fixture("base"),
                "icon.png" to fixture("base"),
            ),
        )
        assertEquals(2, ArchiveExtractor.extractApks(xapk, tmp.newFolder("x"), "x").size)
    }

    @Test
    fun `non zip input is rejected`() {
        val text = tmp.newFile("notes.txt").apply { writeText("hello") }
        val e = assertThrows(ApkLoadException::class.java) { ArchiveExtractor.isApk(text) }
        assertEquals(LoadError.NOT_AN_ARCHIVE, e.error)
    }

    @Test
    fun `preselects splits matching the device`() {
        val app = PackageAssembler.assemble(fixtureNames.map { fixture(it) }.map { it to ManifestReader.read(it) })
        assertEquals(SplitType.BASE, app.splits.first().type)

        val selection = SplitSelector.defaultSelection(
            app.splits,
            DeviceSpec(abis = listOf("arm64-v8a", "armeabi-v7a"), densityDpi = 420, languages = listOf("es", "en")),
        )
        val selected = app.splits.filter { it.id in selection.selectedIds }.map { it.splitName }.toSet()
        assertEquals(setOf(null, "config.arm64_v8a", "config.xxhdpi_v4", "config.es"), selected)
        assertFalse(selection.hasIncompatibleAbi)

        val x86 = SplitSelector.defaultSelection(app.splits, DeviceSpec(listOf("x86_64"), 160, listOf("en")))
        val x86Selected = app.splits.filter { it.id in x86.selectedIds }.map { it.splitName }.toSet()
        assertEquals(setOf(null, "config.mdpi_v4"), x86Selected)
        assertTrue(x86.hasIncompatibleAbi)
    }

    @Test
    fun `rejects inconsistent apk sets`() {
        fun info(pkg: String, split: String?, version: Long = 1) = ApkManifestInfo(pkg, split, version, null, null, false)
        val f = tmp.newFile("a.apk")
        fun errorOf(vararg infos: ApkManifestInfo) =
            assertThrows(ApkLoadException::class.java) { PackageAssembler.assemble(infos.map { f to it }) }.error

        assertEquals(LoadError.MIXED_PACKAGES, errorOf(info("a", null), info("b", "config.es")))
        assertEquals(LoadError.VERSION_MISMATCH, errorOf(info("a", null, 1), info("a", "config.es", 2)))
        assertEquals(LoadError.NO_BASE, errorOf(info("a", "config.es")))
    }

    @Test
    fun `classifies split names`() {
        assertEquals(SplitType.ABI to "x86_64", SplitClassifier.classify("config.x86_64"))
        assertEquals(SplitType.DENSITY to "xxxhdpi", SplitClassifier.classify("split_config.xxxhdpi"))
        assertEquals(SplitType.DENSITY to "xxhdpi", SplitClassifier.classify("config.xxhdpi_v4"))
        assertEquals(SplitType.LANGUAGE to "pt_BR", SplitClassifier.classify("config.pt_BR"))
        assertEquals(SplitType.FEATURE to null, SplitClassifier.classify("camera_feature"))
        assertEquals(SplitType.ABI to "arm64_v8a", SplitClassifier.classify("camera_feature.config.arm64_v8a"))
        assertEquals("sr", SplitClassifier.languageOf("b+sr+Latn"))
    }
}
