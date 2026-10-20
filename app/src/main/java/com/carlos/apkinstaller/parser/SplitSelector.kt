package com.carlos.apkinstaller.parser

import com.carlos.apkinstaller.model.ApkSplit
import com.carlos.apkinstaller.model.DeviceSpec
import com.carlos.apkinstaller.model.SplitType

object SplitSelector {

    data class Selection(val selectedIds: Set<String>, val hasIncompatibleAbi: Boolean)

    /** Preselects the splits that best match the device, mimicking what Play Store would deliver. */
    fun defaultSelection(splits: List<ApkSplit>, device: DeviceSpec): Selection {
        val selected = mutableSetOf<String>()
        var incompatibleAbi = false
        val deviceAbis = device.abis.map { it.lowercase().replace('-', '_') }
        val deviceLanguages = device.languages.map { it.lowercase() }

        splits.groupBy { it.configForSplit }.values.forEach { group ->
            group.filter { it.type in setOf(SplitType.BASE, SplitType.FEATURE, SplitType.OTHER) }
                .mapTo(selected) { it.id }

            val abiSplits = group.filter { it.type == SplitType.ABI }
            if (abiSplits.isNotEmpty()) {
                val best = deviceAbis.firstNotNullOfOrNull { abi -> abiSplits.firstOrNull { it.qualifier == abi } }
                if (best != null) selected += best.id else incompatibleAbi = true
            }

            bestDensity(group.filter { it.type == SplitType.DENSITY }, device.densityDpi)?.let { selected += it.id }

            group.filter { it.type == SplitType.LANGUAGE }
                .filter { SplitClassifier.languageOf(it.qualifier.orEmpty()) in deviceLanguages }
                .mapTo(selected) { it.id }
        }
        return Selection(selected, incompatibleAbi)
    }

    private fun bestDensity(splits: List<ApkSplit>, deviceDpi: Int): ApkSplit? {
        val withDpi = splits.mapNotNull { split -> SplitClassifier.densities[split.qualifier]?.let { split to it } }
        if (withDpi.isEmpty()) return splits.firstOrNull()
        return withDpi.filter { it.second >= deviceDpi }.minByOrNull { it.second }?.first
            ?: withDpi.maxBy { it.second }.first
    }
}
