package com.phoenix.fytipc.binder

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import com.phoenix.fytipc.ConnectionState
import com.phoenix.fytipc.ModuleUpdate
import com.phoenix.fytipc.Modules
import com.phoenix.fytipc.SyuBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
class BinderFytToolkitTest {

    private lateinit var app: Application
    private lateinit var fake: FakeSyuToolkit
    private val component = ComponentName(SyuBinder.SERVICE_PACKAGE, "app.ToolkitService")
    private val toolkits = mutableListOf<BinderFytToolkit>()

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        fake = FakeSyuToolkit()
        shadowOf(app).setComponentNameAndServiceForBindServiceForIntent(
            Intent(SyuBinder.SERVICE_ACTION).setPackage(SyuBinder.SERVICE_PACKAGE), component, fake,
        )
    }

    @After
    fun tearDown() = toolkits.forEach { it.close() }

    private fun toolkit(timeoutMs: Long = 2_000) =
        BinderFytToolkit(app, connectTimeoutMs = timeoutMs).also { toolkits += it }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun awaitUntil(what: String, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!cond()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            idleMain()
            Thread.sleep(5)
        }
    }

    @Test
    fun `binds and reports connected`() {
        val t = toolkit()
        assertThat(t.connectionState.value).isEqualTo(ConnectionState.CONNECTING)
        idleMain()
        assertThat(t.connectionState.value).isEqualTo(ConnectionState.CONNECTED)
        t.close()
        assertThat(t.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
        t.close() // idempotent
    }

    @Test
    fun `register replays cached value, delivers updates, unregisters on cancel`() = runBlocking {
        val canbus = fake.module(Modules.CANBUS)
        canbus.cache[27] = intArrayOf(70)
        val t = toolkit()
        idleMain()

        val got = CopyOnWriteArrayList<ModuleUpdate>()
        val all = CopyOnWriteArrayList<ModuleUpdate>()
        val allJob = launch(Dispatchers.IO) { t.allUpdates.collect { all += it } }
        val job = launch(Dispatchers.IO) { t.canbus.updates(27, 28).collect { got += it } }

        awaitUntil("replay") { got.size == 1 }
        assertThat(canbus.events).containsAtLeast("register 27 flag=1", "register 28 flag=1")
        assertThat(got[0]).isEqualTo(ModuleUpdate(Modules.CANBUS, 27, intArrayOf(70)))

        canbus.push(28, 66)
        canbus.push(21, 3) // not subscribed by this collector, and not registered at all
        awaitUntil("update") { got.size == 2 }
        assertThat(got[1].code).isEqualTo(28)
        assertThat(got[1].int0).isEqualTo(66)
        // allUpdates has no replay, so only the push after subscription is guaranteed.
        awaitUntil("allUpdates") { all.any { it.code == 28 } }

        // A second collector on an already-registered code gets the locally cached value.
        val second = CopyOnWriteArrayList<ModuleUpdate>()
        val job2 = launch(Dispatchers.IO) { t.canbus.updates(27).collect { second += it } }
        awaitUntil("local replay") { second.size == 1 }
        assertThat(canbus.events.count { it == "register 27 flag=1" }).isEqualTo(1)

        job.cancel()
        awaitUntil("unregister 28") { "unregister 28" in canbus.events }
        assertThat(canbus.events).doesNotContain("unregister 27") // still collected by job2
        job2.cancel()
        awaitUntil("unregister 27") { "unregister 27" in canbus.events }
        assertThat(canbus.callbackCount(27)).isEqualTo(0)
        allJob.cancel()
    }

    @Test
    fun `cmd parcel layout is token, code, then nullable arrays`() = runBlocking {
        val t = toolkit()
        // Issued before the connection exists: suspends until connected.
        val pending = async(Dispatchers.IO) { t.canbus.cmd(6, intArrayOf(1, 4)) }
        awaitUntil("connected") { t.connectionState.value == ConnectionState.CONNECTED }
        pending.await()
        t.module(Modules.SOUND).cmd(0, intArrayOf(-1), floatArrayOf(0.5f), arrayOf("a", "b"))
        t.canbus.cmd(1008)

        assertThat(fake.module(Modules.CANBUS).commands).containsExactly(
            FakeSyuModule.RawCall(6, listOf(1, 4), null, null),
            FakeSyuModule.RawCall(1008, null, null, null),
        ).inOrder()
        assertThat(fake.module(Modules.SOUND).commands)
            .containsExactly(FakeSyuModule.RawCall(0, listOf(-1), listOf(0.5f), listOf("a", "b")))
        assertThat(t.module(Modules.SOUND)).isSameInstanceAs(t.sound)
    }

    @Test
    fun `get reads ModuleObject best effort`() = runBlocking {
        fake.module(Modules.MAIN).cache[40] = intArrayOf(123)
        val t = toolkit()
        idleMain()
        assertThat(t.main.get(40)).isEqualTo(ModuleUpdate(Modules.MAIN, 40, intArrayOf(123)))
        assertThat(t.main.get(41)).isNull()
    }

    @Test
    fun `reconnect re-registers live subscriptions`() = runBlocking {
        val t = toolkit()
        idleMain()
        val got = CopyOnWriteArrayList<ModuleUpdate>()
        val job = launch(Dispatchers.IO) { t.canbus.updates(27).collect { got += it } }
        awaitUntil("register") { fake.module(Modules.CANBUS).callbackCount(27) == 1 }

        val conn = shadowOf(app).boundServiceConnections.last()
        conn.onServiceDisconnected(component)
        assertThat(t.connectionState.value).isEqualTo(ConnectionState.CONNECTING)

        val restarted = FakeSyuToolkit()
        restarted.module(Modules.CANBUS).cache[27] = intArrayOf(72)
        conn.onServiceConnected(component, restarted)
        assertThat(t.connectionState.value).isEqualTo(ConnectionState.CONNECTED)
        awaitUntil("replay after reconnect") { got.any { it.int0 == 72 } }
        assertThat(restarted.module(Modules.CANBUS).events).contains("register 27 flag=1")
        job.cancel()
    }

    @Test
    fun `unbindable service is unavailable and calls time out`() = runBlocking {
        shadowOf(app).declareActionUnbindable(SyuBinder.SERVICE_ACTION)
        val t = toolkit(timeoutMs = 200)
        assertThat(t.connectionState.value).isEqualTo(ConnectionState.UNAVAILABLE)
        assertThrows(FytTimeoutException::class.java) {
            runBlocking { t.canbus.cmd(6, intArrayOf(1, 1)) }
        }
        Unit
    }
}
