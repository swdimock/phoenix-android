package com.phoenix.fytipc

/**
 * Debug surface published only by the `mock-syu-ms` emulator stand-in (never by the real vendor
 * service). Subscribe with `toolkit.module(MockSyuDebug.MODULE).updates(AUDIO_PATH_READY, AUDIBLE)`;
 * values are `int[]{0|1}`. On real hardware these codes simply never update.
 */
object MockSyuDebug {
    const val MODULE = Modules.CUSTOMER

    /** 1 once the simulated ACM/CAN box readiness signal has been delivered after boot. */
    const val AUDIO_PATH_READY = 9001

    /** 1 once the simulated head-unit audio path is actually audible (source select received). */
    const val AUDIBLE = 9002

    /** Broadcast by the mock when its simulated ignition goes off / on (debug only). */
    const val ACTION_ACC_OFF = "com.phoenix.mock.ACC_OFF"
    const val ACTION_ACC_ON = "com.phoenix.mock.ACC_ON"
}
