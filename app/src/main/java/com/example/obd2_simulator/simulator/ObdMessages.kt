package com.example.obd2_simulator.simulator

import java.util.Locale

/**
 * Codifica los 5 mensajes que espera el firmware del ESP32 (decisión técnica v1):
 * una línea de JSON compacto por mensaje. El salto de línea final lo agrega quien envía.
 * El firmware acepta hasta 256 caracteres por línea.
 */
object ObdMessages {
    const val MAX_LINE = 255

    /** {"t":"info","vin":"...","yr":2003,"proto":"ISO9141-2"} */
    fun info(v: VehicleInfo): String =
        Line("info").str("vin", v.vin).int("yr", v.year).str("proto", v.protocol).build()

    /** {"t":"dtc","mil":true,"mil_km":42,"stored":["P0171"],"pend":["P0300"]} — siempre incluye ambas listas. */
    fun dtc(d: DtcState): String {
        val line = Line("dtc").bool("mil", d.mil)
        if (d.mil) line.int("mil_km", d.milKm)
        return line.list("stored", d.stored.usable()).list("pend", d.pending.usable()).build()
    }

    /** {"t":"live","rpm":820,"ect":89.0,...} */
    fun live(l: LiveData): String = Line("live")
        .num("rpm", l.rpm, 0)
        .num("ect", l.ect, 1)
        .num("volt", l.volt, 2)
        .num("load", l.load, 1)
        .num("maf", l.maf, 2)
        .num("map", l.map, 0)
        .num("stft", l.stft, 1)
        .num("ltft", l.ltft, 1)
        .num("o2b1s1", l.o2b1s1, 2)
        .num("o2b1s2", l.o2b1s2, 2)
        .build()

    /** {"t":"ff","dtc":"P0171","rpm":2450,"ect":88.0,"ltft":18.2} */
    fun freeze(f: FreezeFrame): String =
        Line("ff").str("dtc", f.dtc).num("rpm", f.rpm, 0).num("ect", f.ect, 1).num("ltft", f.ltft, 1).build()

    /** {"t":"mon","misfire":"ok","fuel":"ok","comp":"ok","cat":"ok","evap":"inc","o2":"ok"} */
    fun monitors(r: Readiness): String {
        val line = Line("mon")
        Monitor.entries.forEach { line.str(it.wire, r[it].wire) }
        return line.build()
    }

    private fun List<String>.usable(): List<String> =
        filter(DtcCatalog::isValid).take(DtcCatalog.MAX_CODES)

    /** Armador mínimo de JSON de un solo nivel: orden fijo, sin espacios, decimales con punto. */
    private class Line(type: String) {
        private val sb = StringBuilder("{\"t\":\"").append(type).append('"')

        private fun key(k: String) { sb.append(",\"").append(k).append("\":") }

        fun str(k: String, v: String): Line { key(k); sb.append('"').append(escape(v)).append('"'); return this }
        fun int(k: String, v: Int): Line { key(k); sb.append(v); return this }
        fun bool(k: String, v: Boolean): Line { key(k); sb.append(v); return this }
        fun num(k: String, v: Double, decimals: Int): Line { key(k); sb.append(format(v, decimals)); return this }
        fun list(k: String, v: List<String>): Line {
            key(k)
            sb.append('[').append(v.joinToString(",") { "\"" + escape(it) + "\"" }).append(']')
            return this
        }

        fun build(): String {
            val s = sb.toString() + "}"
            check(s.length <= MAX_LINE) { "Mensaje demasiado largo: ${s.length} caracteres" }
            return s
        }
    }
}

private fun format(v: Double, decimals: Int): String {
    val x = if (v.isFinite()) v else 0.0
    val s = String.format(Locale.US, "%." + decimals + "f", x)
    // Evita "-0.0": se ve raro aunque sea JSON válido.
    return if (s.startsWith("-") && s.none { it in '1'..'9' }) s.substring(1) else s
}

private fun escape(s: String): String {
    val out = StringBuilder()
    for (c in s) {
        when {
            c == '"' || c == '\\' -> out.append('\\').append(c)
            c.code < 32 -> Unit
            else -> out.append(c)
        }
    }
    return out.toString()
}
