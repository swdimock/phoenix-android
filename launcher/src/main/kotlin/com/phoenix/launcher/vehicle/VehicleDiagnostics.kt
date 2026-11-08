package com.phoenix.launcher.vehicle

import com.phoenix.fytipc.FytToolkit
import com.phoenix.fytipc.MockSyuDebug
import com.phoenix.fytipc.ModuleUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Live diagnostics for the Settings screen: recent updates and the mock's audio debug codes ([MockSyuDebug]). */
class VehicleDiagnostics(scope: CoroutineScope, toolkit: FytToolkit, log: (String) -> Unit = {}) {

    private val _recent = MutableStateFlow<List<ModuleUpdate>>(emptyList())

    /** Newest first, at most [RECENT_LIMIT]. */
    val recentUpdates: StateFlow<List<ModuleUpdate>> = _recent.asStateFlow()

    private val _mockAudioPathReady = MutableStateFlow<Int?>(null)
    val mockAudioPathReady: StateFlow<Int?> = _mockAudioPathReady.asStateFlow()

    private val _mockAudible = MutableStateFlow<Int?>(null)
    val mockAudible: StateFlow<Int?> = _mockAudible.asStateFlow()

    init {
        scope.launch {
            toolkit.allUpdates
                .retryForever(RETRY_MS) { log("allUpdates failed: $it") }
                .collect { u -> _recent.update { (listOf(u) + it).take(RECENT_LIMIT) } }
        }
        scope.launch {
            toolkit.updatesOf(MockSyuDebug.MODULE, MockSyuDebug.AUDIO_PATH_READY, MockSyuDebug.AUDIBLE)
                .retryForever(RETRY_MS) { log("mock debug subscription failed: $it") }
                .collect { u ->
                    when (u.code) {
                        MockSyuDebug.AUDIO_PATH_READY -> _mockAudioPathReady.value = u.int0
                        MockSyuDebug.AUDIBLE -> _mockAudible.value = u.int0
                    }
                }
        }
    }

    companion object {
        const val RECENT_LIMIT = 20
        private const val RETRY_MS = 5_000L
    }
}
