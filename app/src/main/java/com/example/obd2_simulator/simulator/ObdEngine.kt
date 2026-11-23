package com.example.obd2_simulator.simulator

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Motor de simulación: acerca las lecturas actuales a las del escenario con rampas
 * realistas (la temperatura sube rápido y baja lento) y agrega un poco de ruido.
 * No sabe nada de Bluetooth ni de pantallas.
 */
object ObdEngine {
    private const val RPM_RATE = 500.0      // por segundo
    private const val ECT_UP = 2.0
    private const val ECT_DOWN = 0.4
    private const val VOLT_RATE = 0.4
    private const val LOAD_RATE = 20.0
    private const val MAF_RATE = 8.0
    private const val MAP_RATE = 20.0
    private const val STFT_RATE = 5.0
    private const val LTFT_RATE = 3.0

    /**
     * @param dtSec segundos desde el paso anterior
     * @param tSec segundos desde que arrancó la simulación (para el sensor de oxígeno oscilante)
     */
    fun step(
        current: LiveData,
        scenario: Scenario,
        dtSec: Double,
        tSec: Double,
        random: Random = Random.Default,
    ): LiveData {
        val t = scenario.targets()
        val dt = dtSec.coerceIn(0.0, 2.0)
        val running = t.rpm > 0.0

        fun noise(amplitude: Double): Double =
            if (amplitude <= 0.0) 0.0 else random.nextDouble(-amplitude, amplitude)

        val rpmNoise = when {
            scenario == Scenario.MISFIRE -> 160.0
            running -> 12.0
            else -> 0.0
        }
        val upstream = when {
            scenario == Scenario.LEAN -> 0.18 + noise(0.04)
            !running -> 0.1
            else -> 0.45 + 0.35 * sin(tSec * 2.0 * PI / 1.6)   // sensor de banda estrecha: oscila
        }

        return LiveData(
            rpm = approach(current.rpm, t.rpm, RPM_RATE * dt) + noise(rpmNoise),
            ect = approach(current.ect, t.ect, ECT_UP * dt, ECT_DOWN * dt) + noise(0.15),
            volt = approach(current.volt, t.volt, VOLT_RATE * dt) + noise(0.04),
            load = approach(current.load, t.load, LOAD_RATE * dt) + noise(if (running) 0.8 else 0.0),
            maf = approach(current.maf, t.maf, MAF_RATE * dt) + noise(if (running) 0.15 else 0.0),
            map = approach(current.map, t.map, MAP_RATE * dt) + noise(if (running) 0.8 else 0.0),
            stft = approach(current.stft, t.stft, STFT_RATE * dt) + noise(if (running) 0.8 else 0.0),
            ltft = approach(current.ltft, t.ltft, LTFT_RATE * dt) + noise(if (running) 0.1 else 0.0),
            o2b1s1 = upstream,
            o2b1s2 = if (running) t.o2b1s2 + noise(0.02) else t.o2b1s2,
        ).clamped()
    }

    /** Mueve "cur" hacia "target" sin pasarse; sube a "up" por paso y baja a "down" por paso. */
    private fun approach(cur: Double, target: Double, up: Double, down: Double = up): Double = when {
        abs(target - cur) <= (if (target > cur) up else down) -> target
        target > cur -> cur + up
        else -> cur - down
    }
}
