package com.phoenix.launcher.mode

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.phoenix.launcher.settings.LauncherSettings
import com.phoenix.launcher.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelTest {

    /** In-memory stand-in for DataStore; outlives view models like the real file does. */
    private class FakeRepository(initial: LauncherSettings = LauncherSettings()) : SettingsRepository {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<LauncherSettings> = state
        override suspend fun update(transform: (LauncherSettings) -> LauncherSettings) = state.update(transform)
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun freshProcess_startsInPersistedDefaultMode() = runTest {
        val vm = LauncherViewModel(FakeRepository(LauncherSettings(defaultMode = LauncherMode.RADIO)), SavedStateHandle())
        assertThat(vm.mode.value).isEqualTo(LauncherMode.RADIO)
    }

    @Test
    fun defaultIsAndroidAuto() = runTest {
        val vm = LauncherViewModel(FakeRepository(), SavedStateHandle())
        assertThat(vm.mode.value).isEqualTo(LauncherMode.AA)
    }

    @Test
    fun selectMode_changesCurrentButNotDefault() = runTest {
        val repo = FakeRepository()
        val vm = LauncherViewModel(repo, SavedStateHandle())
        vm.selectMode(LauncherMode.PHONE)
        assertThat(vm.mode.value).isEqualTo(LauncherMode.PHONE)
        assertThat(repo.settings.value.defaultMode).isEqualTo(LauncherMode.AA)
    }

    @Test
    fun setDefaultMode_survivesProcessDeath() = runTest {
        val repo = FakeRepository()
        LauncherViewModel(repo, SavedStateHandle()).setDefaultMode(LauncherMode.SETTINGS)

        val recreated = LauncherViewModel(repo, SavedStateHandle())
        assertThat(recreated.mode.value).isEqualTo(LauncherMode.SETTINGS)
        assertThat(recreated.settings.value?.defaultMode).isEqualTo(LauncherMode.SETTINGS)
    }

    @Test
    fun savedState_winsOverDefaultOnRecreate() = runTest {
        val handle = SavedStateHandle()
        LauncherViewModel(FakeRepository(), handle).selectMode(LauncherMode.RADIO)

        val recreated = LauncherViewModel(FakeRepository(), handle)
        assertThat(recreated.mode.value).isEqualTo(LauncherMode.RADIO)
    }

    @Test
    fun blankAaPackage_fallsBackToDefault() = runTest {
        val repo = FakeRepository()
        val vm = LauncherViewModel(repo, SavedStateHandle())
        vm.setAaReceiverPackage("  ")
        assertThat(repo.settings.value.aaReceiverPackage).isEqualTo(LauncherSettings.DEFAULT_AA_RECEIVER_PACKAGE)
        vm.setAaReceiverPackage(" org.example.aa ")
        assertThat(repo.settings.value.aaReceiverPackage).isEqualTo("org.example.aa")
    }
}
