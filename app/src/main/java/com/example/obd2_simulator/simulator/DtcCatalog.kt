package com.example.obd2_simulator.simulator

/** Catálogo mínimo de códigos de falla: validación y descripciones (el vehículo solo entrega el código). */
object DtcCatalog {
    /** El firmware guarda hasta 8 códigos por lista. */
    const val MAX_CODES = 8

    private val CODE_REGEX = Regex("^[PCBU][0-3][0-9A-F]{3}$")
    private val MISFIRE_REGEX = Regex("^P030[0-8]$")
    private val CYLINDER_REGEX = Regex("^P030[1-8]$")

    val common: List<String> = listOf("P0300", "P0301", "P0171", "P0172", "P0420", "P0128", "P0113", "P0562")

    private val descriptions = mapOf(
        "P0300" to "Fallo de encendido aleatorio o múltiple",
        "P0171" to "Mezcla demasiado pobre (banco 1)",
        "P0172" to "Mezcla demasiado rica (banco 1)",
        "P0420" to "Eficiencia del catalizador baja (banco 1)",
        "P0128" to "Refrigerante por debajo de la temperatura del termostato",
        "P0113" to "Sensor de temperatura del aire de admisión: señal alta",
        "P0562" to "Voltaje del sistema bajo",
    )

    fun normalize(raw: String): String = raw.trim().uppercase()

    fun isValid(code: String): Boolean = CODE_REGEX.matches(code)

    fun describe(code: String): String {
        descriptions[code]?.let { return it }
        if (CYLINDER_REGEX.matches(code)) return "Fallo de encendido en el cilindro ${code.last()}"
        return when (code.firstOrNull()) {
            'P' -> "Motor / transmisión"
            'C' -> "Chasis"
            'B' -> "Carrocería"
            'U' -> "Red de comunicación"
            else -> "Código desconocido"
        }
    }

    /** El firmware trata un P0300–P0308 almacenado como alerta crítica. */
    fun isCriticalOnDevice(code: String): Boolean = MISFIRE_REGEX.matches(code)
}
