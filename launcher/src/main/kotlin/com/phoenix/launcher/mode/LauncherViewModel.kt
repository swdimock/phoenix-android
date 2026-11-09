package com.phoenix.launcher.mode

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.phoenix.launcher.settings.AaTransport
import com.phoenix.launcher.settings.LauncherSettings
import com.phoenix.launcher.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the current [LauncherMode] and exposes settings edits.
 *
 * The current mode lives in [SavedStateHandle] (survives activity recreation); a fresh process starts in the
 * persisted [LauncherSettings.defaultMode]. [mode] is null until that default has been read, so the UI never
 * flashes the wrong pane.
 */
class LauncherViewModel(
    private val repository: SettingsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    val settings: StateFlow<LauncherSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val mode: StateFlow<LauncherMode?> = savedState.getStateFlow<String?>(KEY_MODE, null)
        .map(LauncherMode::parse)
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherMode.parse(savedState[KEY_MODE]))

    init {
        if (savedState.get<String>(KEY_MODE) == null) {
            viewModelScope.launch {
                val default = repository.settings.first().defaultMode
                if (savedState.get<String>(KEY_MODE) == null) savedState[KEY_MODE] = default.name
            }
        }
    }

    fun selectMode(mode: LauncherMode) {
        savedState[KEY_MODE] = mode.name
    }

    fun setDefaultMode(mode: LauncherMode) = edit { it.copy(defaultMode = mode) }
    fun setVehicleProfile(id: String) = edit { it.copy(vehicleProfileId = id) }
    fun setAudioReadyTimeout(ms: Long) = edit { it.copy(audioReadyTimeoutMs = ms) }
    fun setAaReceiverPackage(pkg: String) = edit {
        it.copy(aaReceiverPackage = pkg.trim().ifEmpty { LauncherSettings.DEFAULT_AA_RECEIVER_PACKAGE })
    }

    fun setAaTransport(transport: AaTransport) = edit { it.copy(aaTransport = transport) }
    fun setAaTcpEndpoint(host: String, port: Int) = edit {
        it.copy(
            aaTcpHost = host.trim().ifEmpty { LauncherSettings.DEFAULT_AA_TCP_HOST },
            aaTcpPort = port.takeIf { p -> p in 1..65535 } ?: LauncherSettings.DEFAULT_AA_TCP_PORT,
        )
    }

    private fun edit(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    companion object {
        const val KEY_MODE = "mode"

        fun factory(repository: SettingsRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { LauncherViewModel(repository, createSavedStateHandle()) }
        }
    }
}
