package com.phoenix.fytipc

/**
 * Module ids exposed by the SYU toolkit (`com.syu.ms` -> `IRemoteToolkit.getRemoteModule(id)`).
 * Source: `FinalMainServer` in the vendor APKs, verified on FYT UIS7862 (Android 10) and UIS7870 (Android 13).
 */
object Modules {
    const val MAIN = 0
    const val RADIO = 1
    const val BT = 2
    const val DVD = 3
    const val SOUND = 4
    const val IPOD = 5
    const val TV = 6
    const val CANBUS = 7
    const val TPMS = 8
    const val DVR = 9
    const val STEER = 10
    const val SUBSERVER = 11
    const val CUSTOMER = 12

    val ALL: IntArray = intArrayOf(MAIN, RADIO, BT, DVD, SOUND, IPOD, TV, CANBUS, TPMS, DVR, STEER, SUBSERVER, CUSTOMER)

    fun name(id: Int): String = when (id) {
        MAIN -> "MAIN"; RADIO -> "RADIO"; BT -> "BT"; DVD -> "DVD"; SOUND -> "SOUND"
        IPOD -> "IPOD"; TV -> "TV"; CANBUS -> "CANBUS"; TPMS -> "TPMS"; DVR -> "DVR"
        STEER -> "STEER"; SUBSERVER -> "SUBSERVER"; CUSTOMER -> "CUSTOMER"
        else -> "MODULE_$id"
    }
}

/**
 * Well-known CANBUS (module 7) codes. Partial; the full `FinalCanbus` table lives in `com.syu.canbus.apk`
 * and the climate (`U_AIR_*`) subset is defined per-vehicle in [VehicleProfile] implementations.
 */
object CanbusCodes {
    // Capability flags (1004..1018)
    const val U_EXIST_DOOR = 1004
    const val U_EXIST_AIR = 1005
    const val U_EXIST_PANORAMA = 1006
    const val U_EXIST_TEMP_OUT = 1007
    const val U_EXIST_CAR_RADIO = 1008 // NOTE: same number as C_CANBUS_FRAME_TO_MCU in the write namespace
    const val U_EXIST_AIR_CONTROL = 1009

    // Raw MCU frame bridge
    const val U_CANBUS_FRAME_TO_UI = 1019     // subscribe: raw MCU -> UI frames (~26 fps)
    const val C_CANBUS_FRAME_TO_MCU = 1008    // cmd: raw UI -> MCU frame
    const val C_CANBUS_FRAME_TO_MCU_0X10 = 1013

    // Vehicle data (read)
    const val U_CUR_SPEED = 1031
    const val U_ENGINE_SPEED = 1032
    const val U_GPS_ANGLE = 1033
}

/** Binder wire-level constants for the SYU IPC. Transaction ids are assigned by AIDL declaration order. */
object SyuBinder {
    const val SERVICE_ACTION = "com.syu.ms.toolkit"
    const val SERVICE_PACKAGE = "com.syu.ms"

    const val DESCRIPTOR_TOOLKIT = "com.syu.ipc.IRemoteToolkit"
    const val DESCRIPTOR_MODULE = "com.syu.ipc.IRemoteModule"
    const val DESCRIPTOR_CALLBACK = "com.syu.ipc.IModuleCallback"

    // IRemoteToolkit
    const val TX_TOOLKIT_GET_REMOTE_MODULE = 1

    // IRemoteModule
    const val TX_MODULE_CMD = 1
    const val TX_MODULE_GET = 2
    const val TX_MODULE_REGISTER = 3
    const val TX_MODULE_UNREGISTER = 4

    // IModuleCallback
    const val TX_CALLBACK_UPDATE = 1

    /** Flag value `com.syu.air` passes to `register`. */
    const val REGISTER_FLAG_DEFAULT = 1
}
