package com.carlos.apkinstaller.parser

import com.carlos.apkinstaller.model.ApkLoadException
import com.carlos.apkinstaller.model.ApkManifestInfo
import com.carlos.apkinstaller.model.ApkSplit
import com.carlos.apkinstaller.model.AppPackage
import com.carlos.apkinstaller.model.LoadError
import com.carlos.apkinstaller.model.SplitType
import java.io.File

/** Validates a set of APKs and groups them into a single installable package. */
object PackageAssembler {
    fun assemble(apks: List<Pair<File, ApkManifestInfo>>): AppPackage {
        if (apks.isEmpty()) throw ApkLoadException(LoadError.NO_APKS)
        val packages = apks.map { it.second.packageName }.toSet()
        if (packages.size > 1) throw ApkLoadException(LoadError.MIXED_PACKAGES, packages.joinToString())
        val versions = apks.mapNotNull { it.second.versionCode }.toSet()
        if (versions.size > 1) throw ApkLoadException(LoadError.VERSION_MISMATCH, versions.joinToString())

        val unique = apks.distinctBy { it.second.splitName }
        val bases = unique.count { it.second.splitName == null }
        if (bases == 0) throw ApkLoadException(LoadError.NO_BASE)
        if (bases > 1) throw ApkLoadException(LoadError.MULTIPLE_BASES)

        val splits = unique.map { (file, info) ->
            val (type, qualifier) = SplitClassifier.classify(info.splitName, info.isFeatureSplit)
            ApkSplit(
                id = file.name,
                file = file,
                splitName = info.splitName,
                type = type,
                qualifier = qualifier,
                configForSplit = info.configForSplit,
                sizeBytes = file.length(),
            )
        }.sortedWith(compareBy({ it.type.ordinal }, { it.splitName.orEmpty() }))

        val base = unique.first { it.second.splitName == null }.second
        return AppPackage(
            packageName = base.packageName,
            versionCode = base.versionCode ?: versions.firstOrNull(),
            versionName = base.versionName,
            splits = splits,
        ).also { check(it.splits.first().type == SplitType.BASE) }
    }
}
