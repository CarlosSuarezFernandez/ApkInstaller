package com.carlos.apkinstaller.installer

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Bridges results from [InstallResultReceiver] to the UI. */
class InstallEvents {
    private val _events = MutableSharedFlow<InstallEvent>(replay = 4, extraBufferCapacity = 16)
    val events: SharedFlow<InstallEvent> = _events

    fun emit(event: InstallEvent) {
        _events.tryEmit(event)
    }
}
