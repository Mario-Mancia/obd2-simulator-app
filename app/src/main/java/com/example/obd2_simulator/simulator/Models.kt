package com.example.obd2_simulator.simulator

/** Límites de cada lectura (los del estándar OBD2, redondeados). */
object LiveLimits {
    val RPM = 0.0..8000.0
    val ECT = -40.0..215.0
    val VOLT = 0.0..25.0
    val LOAD = 0.0..100.0
    val MAF = 0.0..655.0
    val MAP = 0.0..255.0
    val TRIM = -100.0..99.0
    val O2 = 0.0..1.275
}

/** Lecturas en tiempo real (mensaje "live"). Unidades fijas, ver decisión técnica v1. */
data class LiveData(
    val rpm: Double = 0.0,
    val ect: Double = 25.0,
    val volt: Double = 12.6,
    val load: Double = 0.0,
    val maf: Double = 0.0,
    val map: Double = 100.0,
    val stft: Double = 0.0,
    val ltft: Double = 0.0,
    val o2b1s1: Double = 0.45,
    val o2b1s2: Double = 0.45,
) {
    /** Devuelve una copia con cada valor dentro de su rango válido. */
    fun clamped() = LiveData(
        rpm = rpm.coerceIn(LiveLimits.RPM),
        ect = ect.coerceIn(LiveLimits.ECT),
        volt = volt.coerceIn(LiveLimits.VOLT),
        load = load.coerceIn(LiveLimits.LOAD),
        maf = maf.coerceIn(LiveLimits.MAF),
        map = map.coerceIn(LiveLimits.MAP),
        stft = stft.coerceIn(LiveLimits.TRIM),
        ltft = ltft.coerceIn(LiveLimits.TRIM),
        o2b1s1 = o2b1s1.coerceIn(LiveLimits.O2),
        o2b1s2 = o2b1s2.coerceIn(LiveLimits.O2),
    )
}

/** Campos de "live" con su etiqueta y rango, para dibujarlos y editarlos de forma genérica. */
enum class LiveField(
    val label: String,
    val unit: String,
    val decimals: Int,
    val range: ClosedFloatingPointRange<Double>,
) {
    RPM("RPM", "rpm", 0, LiveLimits.RPM),
    ECT("Temperatura del refrigerante (ECT)", "°C", 1, LiveLimits.ECT),
    VOLT("Voltaje", "V", 2, LiveLimits.VOLT),
    LOAD("Carga del motor", "%", 1, LiveLimits.LOAD),
    MAF("Flujo de aire (MAF)", "g/s", 2, LiveLimits.MAF),
    MAP("Presión de admisión (MAP)", "kPa", 0, LiveLimits.MAP),
    STFT("Ajuste de combustible corto (STFT)", "%", 1, LiveLimits.TRIM),
    LTFT("Ajuste de combustible largo (LTFT)", "%", 1, LiveLimits.TRIM),
    O2B1S1("Sensor O2 B1S1 (antes del catalizador)", "V", 2, LiveLimits.O2),
    O2B1S2("Sensor O2 B1S2 (después del catalizador)", "V", 2, LiveLimits.O2);

    fun get(d: LiveData): Double = when (this) {
        RPM -> d.rpm
        ECT -> d.ect
        VOLT -> d.volt
        LOAD -> d.load
        MAF -> d.maf
        MAP -> d.map
        STFT -> d.stft
        LTFT -> d.ltft
        O2B1S1 -> d.o2b1s1
        O2B1S2 -> d.o2b1s2
    }

    fun set(d: LiveData, v: Double): LiveData = when (this) {
        RPM -> d.copy(rpm = v)
        ECT -> d.copy(ect = v)
        VOLT -> d.copy(volt = v)
        LOAD -> d.copy(load = v)
        MAF -> d.copy(maf = v)
        MAP -> d.copy(map = v)
        STFT -> d.copy(stft = v)
        LTFT -> d.copy(ltft = v)
        O2B1S1 -> d.copy(o2b1s1 = v)
        O2B1S2 -> d.copy(o2b1s2 = v)
    }
}

/** Luz "check engine" y códigos de falla (mensaje "dtc"). */
data class DtcState(
    val mil: Boolean = false,
    val milKm: Int = 0,
    val stored: List<String> = emptyList(),
    val pending: List<String> = emptyList(),
) {
    /** Agrega un código. Un almacenado enciende la luz MIL. Si no es válido, está repetido o no hay lugar, no cambia nada. */
    fun add(code: String, asPending: Boolean): DtcState {
        val c = DtcCatalog.normalize(code)
        if (!DtcCatalog.isValid(c) || c in stored) return this
        if (!asPending) {
            if (stored.size >= DtcCatalog.MAX_CODES) return this
            return copy(stored = stored + c, pending = pending - c, mil = true, milKm = if (mil) milKm else DEFAULT_MIL_KM)
        }
        if (c in pending || pending.size >= DtcCatalog.MAX_CODES) return this
        return copy(pending = pending + c)
    }

    companion object {
        /** Kilómetros recorridos con la luz MIL encendida al aparecer la primera falla. */
        const val DEFAULT_MIL_KM = 12
    }

    /** Quita un código. Si ya no quedan almacenados, apaga la luz MIL. */
    fun remove(code: String): DtcState {
        val next = copy(stored = stored - code, pending = pending - code)
        return if (next.stored.isEmpty()) next.copy(mil = false, milKm = 0) else next
    }
}

/** Datos congelados al registrarse una falla (mensaje "ff"). */
data class FreezeFrame(val dtc: String, val rpm: Double, val ect: Double, val ltft: Double)

/** Identificación del vehículo (mensaje "info"). */
data class VehicleInfo(val vin: String, val year: Int, val protocol: String) {
    val isValid: Boolean get() = isValidVin(vin) && year in YEARS && protocol in PROTOCOLS

    companion object {
        val REFERENCE = VehicleInfo("1HGCM82633A004352", 2003, "ISO9141-2")
        val PROTOCOLS = listOf("ISO9141-2", "ISO14230-4", "J1850-PWM", "J1850-VPW", "ISO15765-4")
        val YEARS = 1996..2026
        private val VIN_REGEX = Regex("^[A-HJ-NPR-Z0-9]{17}$")

        fun isValidVin(vin: String): Boolean = VIN_REGEX.matches(vin)
    }
}

/** Monitores de emisiones (mensaje "mon"). Solo informativos. */
enum class Monitor(val wire: String, val label: String) {
    MISFIRE("misfire", "Fallo de encendido"),
    FUEL("fuel", "Sistema de combustible"),
    COMP("comp", "Componentes integrales"),
    CAT("cat", "Catalizador"),
    EVAP("evap", "Vapores de combustible (EVAP)"),
    O2("o2", "Sensores de oxígeno"),
}

enum class MonitorStatus(val wire: String, val label: String) {
    READY("ok", "Listo"),
    INCOMPLETE("inc", "Incompleto"),
    UNSUPPORTED("na", "N/A"),
}

data class Readiness(
    val status: Map<Monitor, MonitorStatus> = Monitor.entries.associateWith { MonitorStatus.READY },
) {
    operator fun get(m: Monitor): MonitorStatus = status[m] ?: MonitorStatus.READY
    fun with(m: Monitor, s: MonitorStatus) = Readiness(status + (m to s))
}

/** Todo lo que el simulador "es" en un instante. */
data class SimState(
    val scenario: Scenario = Scenario.IDLE,
    val manual: Boolean = false,
    val live: LiveData = Scenario.IDLE.targets(),
    val dtc: DtcState = DtcState(),
    val freeze: FreezeFrame? = null,
    val vehicle: VehicleInfo = VehicleInfo.REFERENCE,
    val readiness: Readiness = Readiness(),
    val autoSend: Boolean = true,
    val liveIntervalMs: Long = 1000L,
)
