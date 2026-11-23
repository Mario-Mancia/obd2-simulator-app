package com.example.obd2_simulator.simulator

/**
 * Situaciones de prueba. Cada una define los valores hacia los que "viaja" el motor
 * y, si aplica, un código de falla que se agrega al elegirla.
 * Los umbrales mencionados son los del firmware (Config.h) y pueden cambiar.
 */
enum class Scenario(val title: String, val description: String, val dtcCode: String? = null) {
    IDLE("Ralentí normal", "Motor caliente en ralentí. No debe generar alertas."),
    CRUISE("Crucero en carretera", "2200 rpm, todo normal. No debe generar alertas."),
    OVERHEAT("Sobrecalentamiento", "La temperatura sube hasta 118 °C: aviso desde 105 °C y alerta crítica desde 110 °C."),
    LOW_VOLTAGE("Falla de carga", "El voltaje baja a 11.4 V con el motor encendido: alerta crítica bajo 12 V."),
    HIGH_VOLTAGE("Sobrecarga eléctrica", "El voltaje sube a 16.4 V con el motor encendido: alerta crítica sobre 16 V."),
    LEAN(
        "Mezcla pobre",
        "El ajuste largo (LTFT) sube a +27 %: alerta tras 5 lecturas seguidas. Agrega el código P0171.",
        dtcCode = "P0171",
    ),
    MISFIRE(
        "Fallo de encendido",
        "Ralentí inestable. Agrega P0300 y enciende la luz check engine: alerta crítica.",
        dtcCode = "P0300",
    ),
    ENGINE_OFF("Motor apagado", "Llave en contacto: 0 rpm y ~12.6 V. El voltaje bajo no debe alertar.");

    /** Valores hacia los que tiende el motor en este escenario. */
    fun targets(): LiveData = when (this) {
        IDLE -> LiveData(800.0, 90.0, 14.1, 22.0, 3.5, 35.0, 0.5, 1.5, 0.45, 0.65)
        CRUISE -> LiveData(2200.0, 92.0, 14.2, 45.0, 14.0, 55.0, 1.0, 1.5, 0.45, 0.65)
        OVERHEAT -> LiveData(900.0, 118.0, 13.9, 30.0, 4.0, 38.0, 0.5, 1.5, 0.45, 0.65)
        LOW_VOLTAGE -> LiveData(850.0, 90.0, 11.4, 25.0, 3.6, 36.0, 0.5, 1.5, 0.45, 0.65)
        HIGH_VOLTAGE -> LiveData(850.0, 90.0, 16.4, 22.0, 3.5, 35.0, 0.5, 1.5, 0.45, 0.65)
        LEAN -> LiveData(850.0, 90.0, 14.1, 24.0, 3.2, 38.0, 12.0, 27.0, 0.18, 0.65)
        MISFIRE -> LiveData(750.0, 90.0, 14.0, 28.0, 3.3, 42.0, 6.0, 8.0, 0.45, 0.65)
        ENGINE_OFF -> LiveData(0.0, 30.0, 12.6, 0.0, 0.0, 100.0, 0.0, 0.0, 0.1, 0.4)
    }
}
