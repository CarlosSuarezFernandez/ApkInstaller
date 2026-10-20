package com.carlos.apkinstaller.parser

import com.carlos.apkinstaller.model.SplitType

object SplitClassifier {
    private val abis = setOf("armeabi", "armeabi_v7a", "arm64_v8a", "x86", "x86_64", "mips", "mips64", "riscv64")

    val densities = linkedMapOf(
        "ldpi" to 120, "mdpi" to 160, "tvdpi" to 213, "hdpi" to 240,
        "xhdpi" to 320, "xxhdpi" to 480, "xxxhdpi" to 640,
    )
    private val otherDensities = setOf("nodpi", "anydpi")
    private val sdkSuffix = Regex("_v\\d+$")
    private val localeRegex = Regex("^([a-z]{2,3}([_-][a-z0-9]{2,8})*|b\\+[a-z]{2,3}(\\+[a-z0-9]+)*)$", RegexOption.IGNORE_CASE)

    fun classify(splitName: String?, isFeatureSplit: Boolean = false): Pair<SplitType, String?> {
        if (splitName == null) return SplitType.BASE to null
        val marker = splitName.lastIndexOf("config.")
        if (marker < 0 || isFeatureSplit) return SplitType.FEATURE to null
        // Drop an SDK version qualifier such as `_v4` (added by aapt2 for some configs).
        val qualifier = splitName.substring(marker + "config.".length).replace(sdkSuffix, "")
        val normalized = qualifier.lowercase()
        return when {
            normalized in abis -> SplitType.ABI to normalized
            normalized in densities || normalized in otherDensities -> SplitType.DENSITY to normalized
            localeRegex.matches(qualifier) -> SplitType.LANGUAGE to qualifier
            else -> SplitType.OTHER to qualifier
        }
    }

    /** Language part of a locale qualifier: `es_ES` -> `es`, `b+sr+Latn` -> `sr`. */
    fun languageOf(qualifier: String): String =
        qualifier.removePrefix("b+").split('_', '-', '+').first().lowercase()
}
