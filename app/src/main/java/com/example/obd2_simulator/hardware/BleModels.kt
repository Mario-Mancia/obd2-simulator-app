package com.example.obd2_simulator.hardware

import java.util.UUID

/** Constantes del servicio BLE del firmware (src/config/Config.h). No cambiarlas solo de un lado. */
object BleUuids {
    val SERVICE: UUID = UUID.fromString("6E400001-B5A0-F393-E0A9-E50E24DCCA9E")
    /** El simulador escribe aquí; el ESP32 la recibe. */
    val RX: UUID = UUID.fromString("6E400002-B5A0-F393-E0A9-E50E24DCCA9E")
    const val DEVICE_NAME = "OBD2-Monitor"

    /** El ESP32 usa MTU 247; el central debe pedir el mismo valor. */
    const val REQUESTED_MTU = 247
    /** Mínimo del protocolo ATT; se usa si no se logra negociar. */
    const val DEFAULT_MTU = 23
    /** Cada escritura lleva 3 bytes de cabecera ATT. */
    const val ATT_OVERHEAD = 3
}

enum class BleStatus { IDLE, SCANNING, CONNECTING, READY }

/** Dispositivo encontrado en el escaneo. */
data class BleDevice(val name: String, val address: String, val rssi: Int)

/** Estado de la conexión, lo que la interfaz necesita para dibujarse. */
data class BleState(
    val status: BleStatus = BleStatus.IDLE,
    val devices: List<BleDevice> = emptyList(),
    val connected: BleDevice? = null,
    val mtu: Int = BleUuids.DEFAULT_MTU,
    /** Mensaje de error para mostrar; null si no hay. */
    val error: String? = null,
) {
    val isReady: Boolean get() = status == BleStatus.READY
}
