package com.example.obd2_simulator.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

/**
 * El teléfono como "central" BLE: busca el ESP32 ("OBD2-Monitor"), se conecta, pide MTU 247
 * y escribe líneas de JSON en la característica RX del firmware.
 *
 * Los permisos los pide la interfaz antes de llamar a [startScan] (ver [BlePermissions]);
 * por eso se suprime la advertencia de lint y se atrapa SecurityException por si el usuario
 * los revoca con la app abierta.
 */
@SuppressLint("MissingPermission")
class BleCentral(context: Context) {
    private val appContext = context.applicationContext
    private val adapter get() = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(BleState())
    val state: StateFlow<BleState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var mtu = BleUuids.DEFAULT_MTU
    private var scanning = false

    /** Cambia con cada conexión; los callbacks de una conexión anterior se ignoran. */
    @Volatile private var session = 0

    private val sendMutex = Mutex()
    @Volatile private var pendingWrite: CompletableDeferred<Boolean>? = null

    // ---------------------------------------------------------------- escaneo

    fun startScan() {
        if (scanning) return
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            fail("Bluetooth está apagado.")
            return
        }
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: SecurityException) {
            fail("Falta el permiso de Bluetooth.")
            return
        }
        scanning = true
        _state.update { it.copy(status = BleStatus.SCANNING, devices = emptyList(), error = null) }
        main.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
    }

    fun stopScan() {
        main.removeCallbacks(scanTimeout)
        if (!scanning) return
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        }
        _state.update { if (it.status == BleStatus.SCANNING) it.copy(status = BleStatus.IDLE) else it }
    }

    private val scanTimeout = Runnable { stopScan() }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: BleUuids.DEVICE_NAME
            val found = BleDevice(name, result.device.address, result.rssi)
            _state.update { s ->
                val others = s.devices.filter { it.address != found.address }
                s.copy(devices = (others + found).sortedByDescending { it.rssi })
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            main.removeCallbacks(scanTimeout)
            fail("No se pudo buscar dispositivos (código $errorCode).")
        }
    }

    // --------------------------------------------------------------- conexión

    fun connect(device: BleDevice) {
        stopScan()
        closeGatt()
        val mySession = ++session
        mtu = BleUuids.DEFAULT_MTU
        val remote = try {
            adapter?.getRemoteDevice(device.address)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (remote == null) {
            fail("Dirección Bluetooth no válida.")
            return
        }
        _state.update { it.copy(status = BleStatus.CONNECTING, connected = device, error = null) }
        gatt = try {
            remote.connectGatt(appContext, false, callbackFor(mySession), android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            null
        }
        if (gatt == null) {
            fail("No se pudo iniciar la conexión.")
            return
        }
        main.postDelayed({
            if (session == mySession && _state.value.status == BleStatus.CONNECTING) {
                closeGatt()
                fail("Tiempo agotado al conectar. ¿Está encendida la placa?")
            }
        }, CONNECT_TIMEOUT_MS)
    }

    fun disconnect() {
        session++
        closeGatt()
        _state.update { it.copy(status = BleStatus.IDLE, connected = null, mtu = BleUuids.DEFAULT_MTU) }
    }

    /** Libera todo; llamar al destruirse el ViewModel. */
    fun release() {
        stopScan()
        disconnect()
    }

    private fun callbackFor(mySession: Int) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (mySession != session) return
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    val asked = try { g.requestMtu(BleUuids.REQUESTED_MTU) } catch (_: SecurityException) { false }
                    if (!asked) discover(g)
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasReady = _state.value.status == BleStatus.READY
                    session++
                    closeGatt()
                    fail(if (wasReady) "Se perdió la conexión con la placa." else "No se pudo conectar (código $status).")
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            if (mySession != session) return
            mtu = if (status == BluetoothGatt.GATT_SUCCESS) newMtu else BleUuids.DEFAULT_MTU
            discover(g)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (mySession != session) return
            val characteristic = g.getService(BleUuids.SERVICE)?.getCharacteristic(BleUuids.RX)
            if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null) {
                session++
                closeGatt()
                fail("El dispositivo no tiene el servicio del monitor OBD2.")
                return
            }
            rx = characteristic
            _state.update { it.copy(status = BleStatus.READY, mtu = mtu, error = null) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (mySession != session) return
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    private fun discover(g: BluetoothGatt) {
        val started = try { g.discoverServices() } catch (_: SecurityException) { false }
        if (!started) {
            session++
            closeGatt()
            fail("No se pudo leer los servicios de la placa.")
        }
    }

    // ------------------------------------------------------------------ envío

    /**
     * Envía una línea de JSON al ESP32 (agrega el "\n" final) dividida en trozos que caben en el MTU.
     * Las llamadas se hacen en fila: una escritura espera a que termine la anterior.
     * @return true si todos los trozos fueron confirmados por la placa.
     */
    suspend fun sendLine(line: String): Boolean {
        return sendMutex.withLock {
            val g = gatt
            val c = rx
            if (g == null || c == null || !_state.value.isReady) return@withLock false
            val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
            val chunk = (mtu - BleUuids.ATT_OVERHEAD).coerceAtLeast(20)
            var offset = 0
            while (offset < bytes.size) {
                val part = bytes.copyOfRange(offset, min(offset + chunk, bytes.size))
                val done = CompletableDeferred<Boolean>()
                pendingWrite = done
                val started = writeChunk(g, c, part)
                val ok = started && (withTimeoutOrNull(WRITE_TIMEOUT_MS) { done.await() } ?: false)
                pendingWrite = null
                if (!ok) return@withLock false
                offset += part.size
            }
            true
        }
    }

    @Suppress("DEPRECATION")
    private fun writeChunk(g: BluetoothGatt, c: BluetoothGattCharacteristic, data: ByteArray): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            c.value = data
            g.writeCharacteristic(c)
        }
    } catch (_: SecurityException) {
        false
    }

    // ---------------------------------------------------------------- interno

    private fun closeGatt() {
        pendingWrite?.complete(false)
        pendingWrite = null
        val g = gatt
        gatt = null
        rx = null
        try {
            g?.disconnect()
            g?.close()
        } catch (_: SecurityException) {
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(status = BleStatus.IDLE, connected = null, mtu = BleUuids.DEFAULT_MTU, error = message) }
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 15_000L
        const val CONNECT_TIMEOUT_MS = 12_000L
        const val WRITE_TIMEOUT_MS = 3_000L
    }
}
