package com.nmaclean.minimaltv

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings
import androidx.compose.runtime.Immutable

@Immutable
data class TvInputEntry(
    val id: String,
    val label: String,
    val typeLabel: String,
    val isPassthrough: Boolean,
    val packageName: String
)

object TvInputRepository {

    fun loadInputs(context: Context): List<TvInputEntry> {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        val rawList = runCatching { manager.tvInputList }.getOrNull().orEmpty()

        return rawList
            .filterNot { runCatching { it.isHidden(context) }.getOrDefault(false) }
            .map { info ->
                val rawLabel = runCatching { info.loadLabel(context)?.toString()?.trim() }.getOrNull().orEmpty()
                val customLabel = runCatching { info.loadCustomLabel(context)?.toString()?.trim() }.getOrNull().orEmpty()
                val typeLabel = typeDisplayName(info.type)
                val label = when {
                    customLabel.isNotEmpty() && rawLabel.isNotEmpty() && !customLabel.equals(rawLabel, ignoreCase = true) ->
                        "$customLabel ($rawLabel)"
                    customLabel.isNotEmpty() -> customLabel
                    rawLabel.isNotEmpty() -> rawLabel
                    else -> typeLabel
                }
                TvInputEntry(
                    id = info.id,
                    label = label,
                    typeLabel = typeLabel,
                    isPassthrough = info.isPassthroughInput,
                    packageName = info.serviceInfo?.packageName.orEmpty()
                )
            }
            .sortedWith(compareBy({ !it.isPassthrough }, { it.label.lowercase() }))
    }

    fun launchInput(context: Context, entry: TvInputEntry): Boolean {
        val uri = if (entry.isPassthrough) {
            TvContract.buildChannelUriForPassthroughInput(entry.id)
        } else {
            TvContract.buildChannelsUriForInput(entry.id)
        }
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(viewIntent) }.isSuccess) {
            return true
        }
        if (entry.packageName.isNotEmpty()) {
            val pmIntent = context.packageManager.getLeanbackLaunchIntentForPackage(entry.packageName)
                ?: context.packageManager.getLaunchIntentForPackage(entry.packageName)
            if (pmIntent != null && runCatching { context.startActivity(pmIntent) }.isSuccess) {
                return true
            }
        }
        return openSystemInputs(context)
    }

    fun openSystemInputs(context: Context): Boolean {
        val tvInputsIntent = Intent("com.android.tv.action.VIEW_INPUTS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(tvInputsIntent) }.isSuccess) {
            return true
        }
        val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(settingsIntent) }.isSuccess
    }

    fun typeDisplayName(type: Int): String = when (type) {
        TvInputInfo.TYPE_HDMI -> "HDMI"
        TvInputInfo.TYPE_TUNER -> "Tuner"
        TvInputInfo.TYPE_COMPONENT -> "Component"
        TvInputInfo.TYPE_COMPOSITE -> "Composite"
        TvInputInfo.TYPE_DISPLAY_PORT -> "DisplayPort"
        TvInputInfo.TYPE_VGA -> "VGA"
        TvInputInfo.TYPE_DVI -> "DVI"
        TvInputInfo.TYPE_SCART -> "SCART"
        TvInputInfo.TYPE_SVIDEO -> "S-Video"
        else -> "Input"
    }
}
