package com.carlos.apkinstaller.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlos.apkinstaller.R
import com.carlos.apkinstaller.data.LoadedApp
import com.carlos.apkinstaller.model.ApkSplit
import com.carlos.apkinstaller.model.SplitType
import com.carlos.apkinstaller.ui.theme.ThemeMode
import com.carlos.apkinstaller.ui.theme.ThemeSettings
import com.carlos.apkinstaller.ui.theme.supportsDynamicColor
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    themeSettings: ThemeSettings,
    onThemeChange: (ThemeSettings) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = state !is UiState.Idle && state !is UiState.Installing) { viewModel.reset() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.menu))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_theme)) },
                                onClick = { menuOpen = false; showSettings = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                UiState.Idle -> HomeContent(onPicked = viewModel::load)
                UiState.Loading -> CenteredProgress(stringResource(R.string.loading))
                is UiState.Ready -> DetailsContent(
                    state = s,
                    onToggle = viewModel::toggleSplit,
                    onInstall = viewModel::install,
                    onCancel = viewModel::reset,
                )
                is UiState.Installing -> InstallingContent(s)
                is UiState.Installed -> ResultContent(s.loaded, null, onDone = viewModel::reset)
                is UiState.Failed -> ResultContent(s.loaded, s.error, onDone = viewModel::reset)
            }
        }
    }

    if (showSettings) {
        ThemeDialog(themeSettings, onThemeChange, onDismiss = { showSettings = false })
    }
}

@Composable
private fun HomeContent(onPicked: (List<android.net.Uri>) -> Unit) {
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(listOf(uri))
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onPicked(uris)
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(144.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = { pickOne.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pick_file))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { pickMany.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pick_multiple))
        }
    }
}

@Composable
private fun CenteredProgress(text: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text)
    }
}

@Composable
private fun AppHeader(loaded: LoadedApp) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        val bitmap = remember(loaded) { loaded.icon?.toBitmap(192, 192)?.asImageBitmap() }
        if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(loaded.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(loaded.app.packageName, style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.version_format, loaded.app.versionName ?: "?", loaded.app.versionCode ?: 0L),
                style = MaterialTheme.typography.bodySmall,
            )
            if (loaded.installedVersionCode != null) {
                Text(
                    stringResource(
                        R.string.installed_version_format,
                        loaded.installedVersionName ?: "?",
                        loaded.installedVersionCode,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    val total = loaded.app.splits.sumOf { it.sizeBytes }
    Text(
        stringResource(R.string.splits_summary, loaded.app.splits.size, Formatter.formatShortFileSize(context, total)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DetailsContent(
    state: UiState.Ready,
    onToggle: (String) -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var askPermission by remember { mutableStateOf(false) }
    val groups = state.loaded.app.splits.groupBy { it.type }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            item { AppHeader(state.loaded) }
            if (state.hasIncompatibleAbi) {
                item { WarningCard(stringResource(R.string.warning_incompatible_abi)) }
            }
            if (state.loaded.installedVersionCode != null &&
                (state.loaded.app.versionCode ?: 0) < state.loaded.installedVersionCode
            ) {
                item { WarningCard(stringResource(R.string.warning_downgrade)) }
            }
            SplitType.entries.forEach { type ->
                val splits = groups[type].orEmpty()
                if (splits.isNotEmpty() && state.loaded.app.splits.size > 1) {
                    item {
                        Text(
                            stringResource(type.titleRes()),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(splits, key = { it.id }) { split ->
                        SplitRow(split, checked = split.id in state.selectedIds, onToggle = { onToggle(split.id) })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
            Button(
                onClick = {
                    if (context.packageManager.canRequestPackageInstalls()) onInstall() else askPermission = true
                },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.install)) }
        }
    }

    if (askPermission) {
        AlertDialog(
            onDismissRequest = { askPermission = false },
            title = { Text(stringResource(R.string.permission_title)) },
            text = { Text(stringResource(R.string.permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    askPermission = false
                    openUnknownSourcesSettings(context)
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { askPermission = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SplitRow(split: ApkSplit, checked: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !split.isRequired, onClick = onToggle).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked || split.isRequired, onCheckedChange = { onToggle() }, enabled = !split.isRequired)
        Column(Modifier.weight(1f)) {
            Text(splitTitle(split), style = MaterialTheme.typography.bodyLarge)
            val subtitle = listOfNotNull(
                split.configForSplit?.let { stringResource(R.string.for_feature, it) },
                split.splitName ?: stringResource(R.string.split_base_name),
            ).joinToString(" · ")
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Formatter.formatShortFileSize(context, split.sizeBytes), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun splitTitle(split: ApkSplit): String = when (split.type) {
    SplitType.BASE -> stringResource(R.string.split_base)
    SplitType.LANGUAGE -> {
        val locale = Locale.forLanguageTag(split.qualifier.orEmpty().removePrefix("b+").replace('_', '-').replace('+', '-'))
        locale.getDisplayName(LocalConfiguration.current.locales[0]).replaceFirstChar { it.titlecase() }.ifBlank { split.qualifier.orEmpty() }
    }
    SplitType.ABI, SplitType.DENSITY, SplitType.OTHER -> split.qualifier.orEmpty()
    SplitType.FEATURE -> split.splitName.orEmpty()
}

private fun SplitType.titleRes(): Int = when (this) {
    SplitType.BASE -> R.string.group_base
    SplitType.FEATURE -> R.string.group_feature
    SplitType.ABI -> R.string.group_abi
    SplitType.DENSITY -> R.string.group_density
    SplitType.LANGUAGE -> R.string.group_language
    SplitType.OTHER -> R.string.group_other
}

@Composable
private fun WarningCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun InstallingContent(state: UiState.Installing) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        AppHeader(state.loaded)
        Spacer(Modifier.height(32.dp))
        if (state.waitingForUser) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.waiting_confirmation))
        } else {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.installing_progress, (state.progress * 100).toInt()))
        }
    }
}

@Composable
private fun ResultContent(loaded: LoadedApp?, error: ErrorMessage?, onDone: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        if (loaded != null) {
            AppHeader(loaded)
            Spacer(Modifier.height(24.dp))
        }
        if (error == null) {
            Text(stringResource(R.string.install_success), style = MaterialTheme.typography.titleMedium)
        } else {
            Text(stringResource(R.string.error_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(error.resId))
            if (!error.detail.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.done)) }
            val launch = loaded?.takeIf { error == null }
                ?.let { context.packageManager.getLaunchIntentForPackage(it.app.packageName) }
            if (launch != null) {
                Button(onClick = { context.startActivity(launch) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.open_app))
                }
            }
        }
    }
}

@Composable
private fun ThemeDialog(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onChange(settings.copy(mode = mode)) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.mode == mode, onClick = { onChange(settings.copy(mode = mode)) })
                        Text(stringResource(mode.labelRes()))
                    }
                }
                if (supportsDynamicColor) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.theme_dynamic), modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = { onChange(settings.copy(dynamicColor = it)) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun openUnknownSourcesSettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }
}
