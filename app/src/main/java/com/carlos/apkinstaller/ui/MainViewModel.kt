package com.carlos.apkinstaller.ui

import android.content.pm.PackageInstaller
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlos.apkinstaller.R
import com.carlos.apkinstaller.data.ApkRepository
import com.carlos.apkinstaller.data.LoadedApp
import com.carlos.apkinstaller.installer.InstallEvent
import com.carlos.apkinstaller.installer.InstallEvents
import com.carlos.apkinstaller.installer.Installer
import com.carlos.apkinstaller.model.ApkLoadException
import com.carlos.apkinstaller.model.LoadError
import com.carlos.apkinstaller.parser.SplitSelector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ErrorMessage(@StringRes val resId: Int, val detail: String? = null)

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Ready(
        val loaded: LoadedApp,
        val selectedIds: Set<String>,
        val hasIncompatibleAbi: Boolean,
    ) : UiState

    data class Installing(val loaded: LoadedApp, val progress: Float, val waitingForUser: Boolean) : UiState
    data class Installed(val loaded: LoadedApp) : UiState
    data class Failed(val loaded: LoadedApp?, val error: ErrorMessage) : UiState
}

class MainViewModel(
    private val repository: ApkRepository,
    private val installer: Installer,
    installEvents: InstallEvents,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var currentSessionId: Int? = null
    // Results can arrive before install() learns its session id; keep them until matched.
    private val unmatchedEvents = mutableMapOf<Int, InstallEvent>()
    private var loadJob: Job? = null

    init {
        repository.clearWorkDir()
        viewModelScope.launch {
            installEvents.events.collect { event ->
                if (event.sessionId == currentSessionId) onInstallEvent(event) else unmatchedEvents[event.sessionId] = event
            }
        }
    }

    fun load(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (_state.value is UiState.Installing) return
        loadJob?.cancel()
        _state.value = UiState.Loading
        loadJob = viewModelScope.launch {
            _state.value = try {
                val loaded = repository.load(uris)
                val selection = SplitSelector.defaultSelection(loaded.app.splits, repository.deviceSpec())
                UiState.Ready(loaded, selection.selectedIds, selection.hasIncompatibleAbi)
            } catch (e: ApkLoadException) {
                UiState.Failed(null, ErrorMessage(e.error.messageRes(), e.message))
            } catch (e: Exception) {
                UiState.Failed(null, ErrorMessage(R.string.error_io, e.message))
            }
        }
    }

    fun toggleSplit(id: String) {
        _state.update { state ->
            if (state !is UiState.Ready) return@update state
            val split = state.loaded.app.splits.firstOrNull { it.id == id } ?: return@update state
            if (split.isRequired) return@update state
            state.copy(selectedIds = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id)
        }
    }

    fun install() {
        val ready = _state.value as? UiState.Ready ?: return
        val files = ready.loaded.app.splits.filter { it.id in ready.selectedIds }.map { it.file }
        _state.value = UiState.Installing(ready.loaded, 0f, waitingForUser = false)
        viewModelScope.launch {
            try {
                currentSessionId = null
                val sessionId = installer.install(ready.loaded.app.packageName, files) { progress ->
                    _state.update { (it as? UiState.Installing)?.copy(progress = progress) ?: it }
                }
                currentSessionId = sessionId
                _state.update { (it as? UiState.Installing)?.copy(progress = 1f, waitingForUser = true) ?: it }
                unmatchedEvents.remove(sessionId)?.let(::onInstallEvent)
            } catch (e: Exception) {
                _state.value = UiState.Failed(ready.loaded, ErrorMessage(R.string.install_failed_generic, e.message))
            }
        }
    }

    private fun onInstallEvent(event: InstallEvent) {
        val loaded = (_state.value as? UiState.Installing)?.loaded ?: return
        currentSessionId = null
        _state.value = when (event) {
            is InstallEvent.Success -> UiState.Installed(loaded)
            is InstallEvent.Failure -> UiState.Failed(loaded, ErrorMessage(installStatusMessage(event.status), event.message))
        }
        repository.clearWorkDir()
    }

    fun reset() {
        if (_state.value is UiState.Installing) return
        loadJob?.cancel()
        repository.clearWorkDir()
        _state.value = UiState.Idle
    }

    private fun LoadError.messageRes(): Int = when (this) {
        LoadError.NOT_AN_ARCHIVE -> R.string.error_not_archive
        LoadError.NO_APKS -> R.string.error_no_apks
        LoadError.UNREADABLE_MANIFEST -> R.string.error_unreadable_manifest
        LoadError.MIXED_PACKAGES -> R.string.error_mixed_packages
        LoadError.VERSION_MISMATCH -> R.string.error_version_mismatch
        LoadError.NO_BASE -> R.string.error_no_base
        LoadError.MULTIPLE_BASES -> R.string.error_multiple_bases
        LoadError.IO -> R.string.error_io
    }

    private fun installStatusMessage(status: Int): Int = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> R.string.install_failed_aborted
        PackageInstaller.STATUS_FAILURE_BLOCKED -> R.string.install_failed_blocked
        PackageInstaller.STATUS_FAILURE_CONFLICT -> R.string.install_failed_conflict
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> R.string.install_failed_incompatible
        PackageInstaller.STATUS_FAILURE_INVALID -> R.string.install_failed_invalid
        PackageInstaller.STATUS_FAILURE_STORAGE -> R.string.install_failed_storage
        else -> R.string.install_failed_generic
    }

    override fun onCleared() {
        if (_state.value !is UiState.Installing) repository.clearWorkDir()
    }
}
