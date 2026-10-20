package com.carlos.apkinstaller.model

import java.io.File

enum class SplitType { BASE, FEATURE, ABI, DENSITY, LANGUAGE, OTHER }

/** Attributes read from the root `<manifest>` element of an APK. */
data class ApkManifestInfo(
    val packageName: String,
    val splitName: String?,
    val versionCode: Long?,
    val versionName: String?,
    val configForSplit: String?,
    val isFeatureSplit: Boolean,
)

data class ApkSplit(
    val id: String,
    val file: File,
    val splitName: String?,
    val type: SplitType,
    /** ABI, density or locale qualifier for config splits, e.g. `arm64_v8a`, `xxhdpi`, `es`. */
    val qualifier: String?,
    /** Feature split this config split belongs to, or null for the base app. */
    val configForSplit: String?,
    val sizeBytes: Long,
) {
    val isRequired: Boolean get() = type == SplitType.BASE
}

data class AppPackage(
    val packageName: String,
    val versionCode: Long?,
    val versionName: String?,
    val splits: List<ApkSplit>,
) {
    val base: ApkSplit get() = splits.first { it.type == SplitType.BASE }
}

data class DeviceSpec(
    /** Supported ABIs in preference order, e.g. `arm64-v8a`. */
    val abis: List<String>,
    val densityDpi: Int,
    /** Device languages in preference order, e.g. `es`, `en`. */
    val languages: List<String>,
)

enum class LoadError {
    NOT_AN_ARCHIVE,
    NO_APKS,
    UNREADABLE_MANIFEST,
    MIXED_PACKAGES,
    VERSION_MISMATCH,
    NO_BASE,
    MULTIPLE_BASES,
    IO,
}

class ApkLoadException(val error: LoadError, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: error.name, cause)
