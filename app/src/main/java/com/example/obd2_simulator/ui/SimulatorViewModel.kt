package com.example.obd2_simulator.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.obd2_simulator.hardware.BleCentral
import com.example.obd2_simulator.hardware.BleDevice
import com.example.obd2_simulator.hardware.BleState
import com.example.obd2_simulator.simulator.DtcCatalog
import com.example.obd2_simulator.simulator.FreezeFrame
import com.example.obd2_simulator.simulator.LiveData
import com.example.obd2_simulator.simulator.LiveField
import com.example.obd2_simulator.simulator.Monitor
import com.example.obd2_simulator.simulator.MonitorStatus
import com.example.obd2_simulator.simulator.ObdEngine
import com.example.obd2_simulator.simulator.ObdMessages
import com.example.obd2_simulator.simulator.Scenario
import com.example.obd2_simulator.simulator.SimState
import com.example.obd2_simulator.simulator.VehicleInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Tipos de mensaje que se envían a la placa (el campo "t" del JSON). */
enum class MsgKind(val wire: String) { INFO("info"), DTC("dtc"), LIVE("live"), FF("ff"), MON("mon") }

enum class LogLevel { SENT, FAILED, NOTE }

data class LogEntry(
    val id: Long,
    val timeMs: Long,
    val level: LogLevel,
    val kind: MsgKind?,
    val text: String,
)

data class SendStats(val sent: Int = 0, val failed: Int = 0)

/**
 * Une el simulador (qué datos "produce" el vehículo) con el Bluetooth (cómo llegan al ESP32).
 * La interfaz solo lee los StateFlow y llama a las funciones públicas.
 */
class SimulatorViewModel(app: Application) : AndroidViewModel(app) {
    private val central = BleCentral(app)

    val ble: StateFlow<BleState> = central.state

    private val _sim = MutableStateFlow(SimState())
    val sim: StateFlow<SimState> = _sim.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    private val _stats = MutableStateFlow(SendStats())
    val stats: StateFlow<SendStats> = _stats.asStateFlow()

    private class Outgoing(val kind: MsgKind, val line: String)

    private val outbox = Channel<Outgoing>(OUTBOX_SIZE)
    private val liveInFlight = AtomicBoolean(false)
    private var nextLogId = 0L

    init {
        // Un solo "hilo" de envío: los mensajes salen en el orden en que se pidieron.
        viewModelScope.launch {
            for (m in outbox) deliver(m)
        }
        // Al conectarse, la placa recibe primero quién es el vehículo y cómo está.
        viewModelScope.launch {
            central.state.map { it.isReady }.distinctUntilChanged().collect { ready ->
                if (ready) {
                    note("Conectado. Enviando identificación, monitores y códigos.")
                    val s = _sim.value
                    enqueue(MsgKind.INFO, ObdMessages.info(s.vehicle))
                    enqueue(MsgKind.MON, ObdMessages.monitors(s.readiness))
                    enqueue(MsgKind.DTC, ObdMessages.dtc(s.dtc))
                } else {
                    while (outbox.tryReceive().isSuccess) { /* descartar lo pendiente */ }
                    liveInFlight.set(false)
                }
            }
        }
        viewModelScope.launch { tickLoop() }
    }

    // ------------------------------------------------------------- Bluetooth

    fun startScan() = central.startScan()
    fun stopScan() = central.stopScan()
    fun connect(device: BleDevice) = central.connect(device)
    fun disconnect() {
        central.disconnect()
        note("Desconectado.")
    }

    // ------------------------------------------------------------ simulación

    /** Cambia de escenario. Si el escenario trae un código de falla, se registra con su "freeze frame". */
    fun selectScenario(scenario: Scenario) {
        _sim.update { it.copy(scenario = scenario, manual = false) }
        note("Escenario: ${scenario.title}")
        scenario.dtcCode?.let { addDtc(it, pending = false, snapshot = scenario.targets()) }
    }

    /** Edita una lectura a mano; el motor se detiene hasta llamar a [resumeScenario]. */
    fun setManualValue(field: LiveField, value: Double) {
        _sim.update { it.copy(manual = true, live = field.set(it.live, value).clamped()) }
    }

    fun resumeScenario() {
        _sim.update { it.copy(manual = false) }
    }

    fun setAutoSend(enabled: Boolean) {
        _sim.update { it.copy(autoSend = enabled) }
    }

    fun setLiveInterval(ms: Long) {
        _sim.update { it.copy(liveIntervalMs = ms) }
    }

    // ---------------------------------------------------------------- fallas

    /**
     * Agrega un código. Si queda almacenado se enciende la luz MIL, se congela un "freeze frame"
     * con las lecturas de ese instante y se envían "dtc" + "ff" (el firmware guarda un evento por cada "ff").
     * @param snapshot lecturas a congelar; si es null se usan las actuales.
     * @return null si todo salió bien; si no, el motivo para mostrarlo en pantalla.
     */
    fun addDtc(rawCode: String, pending: Boolean, snapshot: LiveData? = null): String? {
        val code = DtcCatalog.normalize(rawCode)
        if (!DtcCatalog.isValid(code)) return "Código no válido. Ejemplo: P0171"
        val before = _sim.value.dtc
        val already = if (pending) code in before.pending || code in before.stored else code in before.stored
        if (already) return "El código $code ya está en la lista."
        val list = if (pending) before.pending else before.stored
        if (list.size >= DtcCatalog.MAX_CODES) return "La placa guarda hasta ${DtcCatalog.MAX_CODES} códigos por lista."

        var frame: FreezeFrame? = null
        _sim.update { s ->
            val next = s.dtc.add(code, pending)
            if (!pending) {
                val l = snapshot ?: s.live
                frame = FreezeFrame(code, l.rpm, l.ect, l.ltft)
            }
            s.copy(dtc = next, freeze = frame ?: s.freeze)
        }
        enqueue(MsgKind.DTC, ObdMessages.dtc(_sim.value.dtc))
        frame?.let { enqueue(MsgKind.FF, ObdMessages.freeze(it)) }
        return null
    }

    fun removeDtc(code: String) {
        _sim.update { it.copy(dtc = it.dtc.remove(code)) }
        enqueue(MsgKind.DTC, ObdMessages.dtc(_sim.value.dtc))
    }

    fun clearDtcs() {
        _sim.update { it.copy(dtc = it.dtc.copy(mil = false, milKm = 0, stored = emptyList(), pending = emptyList()), freeze = null) }
        enqueue(MsgKind.DTC, ObdMessages.dtc(_sim.value.dtc))
    }

    /** Reenvía el último freeze frame. Ojo: la placa guarda un evento nuevo cada vez. */
    fun resendFreezeFrame() {
        _sim.value.freeze?.let { enqueue(MsgKind.FF, ObdMessages.freeze(it)) }
    }

    // -------------------------------------------------------------- vehículo

    /** @return null si se aplicó; si no, el motivo. */
    fun setVehicle(vin: String, year: Int?, protocol: String): String? {
        val cleanVin = vin.trim().uppercase()
        if (!VehicleInfo.isValidVin(cleanVin)) return "El VIN debe tener 17 caracteres (sin I, O ni Q)."
        if (year == null || year !in VehicleInfo.YEARS) return "Año fuera de rango (${VehicleInfo.YEARS.first}–${VehicleInfo.YEARS.last})."
        if (protocol !in VehicleInfo.PROTOCOLS) return "Protocolo desconocido."
        val info = VehicleInfo(cleanVin, year, protocol)
        _sim.update { it.copy(vehicle = info) }
        enqueue(MsgKind.INFO, ObdMessages.info(info))
        return null
    }

    fun setMonitor(monitor: Monitor, status: MonitorStatus) {
        _sim.update { it.copy(readiness = it.readiness.with(monitor, status)) }
        enqueue(MsgKind.MON, ObdMessages.monitors(_sim.value.readiness))
    }

    // ------------------------------------------------------ envío manual

    fun sendNow(kind: MsgKind) {
        val s = _sim.value
        when (kind) {
            MsgKind.INFO -> enqueue(kind, ObdMessages.info(s.vehicle))
            MsgKind.DTC -> enqueue(kind, ObdMessages.dtc(s.dtc))
            MsgKind.LIVE -> enqueue(kind, ObdMessages.live(s.live))
            MsgKind.MON -> enqueue(kind, ObdMessages.monitors(s.readiness))
            MsgKind.FF -> resendFreezeFrame()
        }
    }

    fun clearLog() {
        _log.value = emptyList()
        _stats.value = SendStats()
    }

    // --------------------------------------------------------------- interno

    private suspend fun tickLoop() {
        val start = System.nanoTime()
        var last = start
        var sinceLiveMs = 0.0
        while (true) {
            delay(TICK_MS)
            val now = System.nanoTime()
            val dt = (now - last) / 1e9
            last = now
            val snapshot = _sim.value
            if (!snapshot.manual) {
                val t = (now - start) / 1e9
                _sim.update { if (it.manual) it else it.copy(live = ObdEngine.step(it.live, it.scenario, dt, t)) }
            }
            sinceLiveMs += dt * 1000.0
            if (snapshot.autoSend && sinceLiveMs >= snapshot.liveIntervalMs) {
                sinceLiveMs = 0.0
                if (central.state.value.isReady) enqueue(MsgKind.LIVE, ObdMessages.live(_sim.value.live))
            }
        }
    }

    private fun enqueue(kind: MsgKind, line: String) {
        if (!central.state.value.isReady) {
            if (kind != MsgKind.LIVE) addLog(LogLevel.NOTE, kind, "Sin conexión: no se envió $line")
            return
        }
        // Si la placa va lenta, no se acumulan lecturas "live": la siguiente reemplaza a la anterior.
        if (kind == MsgKind.LIVE && !liveInFlight.compareAndSet(false, true)) return
        if (!outbox.trySend(Outgoing(kind, line)).isSuccess) {
            if (kind == MsgKind.LIVE) liveInFlight.set(false)
            addLog(LogLevel.FAILED, kind, "Cola llena, se descartó: $line")
        }
    }

    private suspend fun deliver(m: Outgoing) {
        val ok = central.sendLine(m.line)
        if (m.kind == MsgKind.LIVE) liveInFlight.set(false)
        _stats.update { if (ok) it.copy(sent = it.sent + 1) else it.copy(failed = it.failed + 1) }
        addLog(if (ok) LogLevel.SENT else LogLevel.FAILED, m.kind, m.line)
    }

    private fun note(text: String) = addLog(LogLevel.NOTE, null, text)

    private fun addLog(level: LogLevel, kind: MsgKind?, text: String) {
        val entry = LogEntry(nextLogId++, System.currentTimeMillis(), level, kind, text)
        _log.update { (it + entry).takeLast(LOG_MAX) }
    }

    override fun onCleared() {
        central.release()
        outbox.close()
        super.onCleared()
    }

    private companion object {
        const val TICK_MS = 250L
        const val OUTBOX_SIZE = 32
        const val LOG_MAX = 300
    }
}
