package com.example.obd2_simulator.simulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Comprueba que los mensajes del simulador tengan la forma exacta que espera el firmware
 * (decisión técnica v1) y que cada escenario se comporte como se describe.
 * Son pruebas de JVM puro: no necesitan teléfono ni Bluetooth.
 */
class ProtocolCompatTest {

    // ------------------------------------------------------------ formato

    @Test
    fun infoMatchesSpecExample() {
        assertEquals(
            """{"t":"info","vin":"1HGCM82633A004352","yr":2003,"proto":"ISO9141-2"}""",
            ObdMessages.info(VehicleInfo.REFERENCE),
        )
    }

    @Test
    fun dtcMatchesSpecExample() {
        val d = DtcState(mil = true, milKm = 42, stored = listOf("P0171"), pending = listOf("P0300"))
        assertEquals(
            """{"t":"dtc","mil":true,"mil_km":42,"stored":["P0171"],"pend":["P0300"]}""",
            ObdMessages.dtc(d),
        )
    }

    @Test
    fun dtcWithoutFaultsStillSendsBothLists() {
        assertEquals("""{"t":"dtc","mil":false,"stored":[],"pend":[]}""", ObdMessages.dtc(DtcState()))
    }

    @Test
    fun freezeFrameMatchesSpecExample() {
        assertEquals(
            """{"t":"ff","dtc":"P0171","rpm":2450,"ect":88.0,"ltft":18.2}""",
            ObdMessages.freeze(FreezeFrame("P0171", 2450.0, 88.0, 18.2)),
        )
    }

    @Test
    fun monitorsUseTheSixShortKeys() {
        val r = Readiness().with(Monitor.EVAP, MonitorStatus.INCOMPLETE).with(Monitor.CAT, MonitorStatus.UNSUPPORTED)
        assertEquals(
            """{"t":"mon","misfire":"ok","fuel":"ok","comp":"ok","cat":"na","evap":"inc","o2":"ok"}""",
            ObdMessages.monitors(r),
        )
    }

    @Test
    fun liveUsesDotAsDecimalSeparatorAndKeepsKeyOrder() {
        val line = ObdMessages.live(LiveData(rpm = 820.0, ect = 89.04, volt = 14.1, ltft = -2.5))
        assertTrue(line, line.startsWith("""{"t":"live","rpm":820,"ect":89.0,"volt":14.10,"load":0.0,"""))
        assertTrue(line, line.contains(""""ltft":-2.5"""))
        assertFalse(line, line.contains(","+" "))
    }

    @Test
    fun negativeZeroIsNeverPrinted() {
        assertFalse(ObdMessages.live(LiveData(stft = -0.01)).contains("-0.0"))
    }

    @Test
    fun everyScenarioProducesLinesWithinTheFirmwareLimit() {
        for (s in Scenario.entries) {
            var live = Scenario.IDLE.targets()
            var dtc = DtcState()
            s.dtcCode?.let { dtc = dtc.add(it, asPending = false) }
            val lines = mutableListOf(
                ObdMessages.info(VehicleInfo.REFERENCE),
                ObdMessages.monitors(Readiness()),
                ObdMessages.dtc(dtc),
            )
            repeat(120) { i ->
                live = ObdEngine.step(live, s, 1.0, i.toDouble(), Random(i))
                lines += ObdMessages.live(live)
            }
            lines.forEach {
                assertTrue("${s.name}: ${it.length} caracteres", it.length <= ObdMessages.MAX_LINE)
                assertFalse("salto de línea dentro del JSON", it.contains('\n'))
            }
        }
    }

    // ------------------------------------------------------------- límites

    @Test
    fun longCodeListsAreCappedAtEight() {
        val many = (0 until 12).map { "P01%02d".format(it) }
        val line = ObdMessages.dtc(DtcState(stored = many))
        assertEquals(8, Regex("P01\\d\\d").findAll(line).count())
    }

    @Test
    fun invalidCodesAreNeverSent() {
        val line = ObdMessages.dtc(DtcState(stored = listOf("P0171", "xx", "P01\"1")))
        assertTrue(line, line.contains(""""stored":["P0171"]"""))
    }

    @Test
    fun vinValidationFollowsIsoRules() {
        assertTrue(VehicleInfo.isValidVin("1HGCM82633A004352"))
        assertFalse(VehicleInfo.isValidVin("1HGCM82633A00435"))      // 16
        assertFalse(VehicleInfo.isValidVin("1HGCM82633A00435I"))     // contiene I
        assertFalse(VehicleInfo.isValidVin("1hgcm82633a004352"))     // minúsculas
    }

    @Test
    fun protocolFitsInFirmwareField() {
        // El firmware guarda "proto" en 14 bytes (13 caracteres + fin de texto).
        VehicleInfo.PROTOCOLS.forEach { assertTrue(it, it.length <= 13) }
    }

    // ------------------------------------------------------------ DtcState

    @Test
    fun storedCodeTurnsMilOnAndRemovingTheLastTurnsItOff() {
        var d = DtcState().add("p0171", asPending = false)
        assertTrue(d.mil)
        assertEquals(listOf("P0171"), d.stored)
        d = d.remove("P0171")
        assertFalse(d.mil)
        assertEquals(0, d.milKm)
    }

    @Test
    fun pendingCodeDoesNotTurnMilOn() {
        val d = DtcState().add("P0300", asPending = true)
        assertFalse(d.mil)
        assertEquals(listOf("P0300"), d.pending)
    }

    @Test
    fun duplicatesAndInvalidCodesAreIgnored() {
        val d = DtcState().add("P0171", false)
        assertEquals(d, d.add("P0171", false))
        assertEquals(d, d.add("hola", false))
        assertEquals(d, d.add("P4171", false))
    }

    @Test
    fun storedCodePromotesItFromPending() {
        val d = DtcState().add("P0300", true).add("P0300", false)
        assertEquals(listOf("P0300"), d.stored)
        assertTrue(d.pending.isEmpty())
    }

    // ----------------------------------------------------------- escenarios

    private fun run(s: Scenario, seconds: Int, from: LiveData = Scenario.IDLE.targets()): LiveData {
        var live = from
        repeat(seconds) { i -> live = ObdEngine.step(live, s, 1.0, i.toDouble(), Random(7 + i)) }
        return live
    }

    @Test
    fun overheatCrossesTheCriticalThreshold() {
        val live = run(Scenario.OVERHEAT, 60)
        assertTrue("ect=${live.ect}", live.ect > 110.0)
    }

    @Test
    fun lowVoltageDropsBelowTwelveWhileRunning() {
        val live = run(Scenario.LOW_VOLTAGE, 30)
        assertTrue(live.rpm > 400.0)
        assertTrue("volt=${live.volt}", live.volt < 12.0)
    }

    @Test
    fun highVoltageGoesAboveSixteen() {
        assertTrue(run(Scenario.HIGH_VOLTAGE, 30).volt > 16.0)
    }

    @Test
    fun leanDrivesLtftPastTheCriticalLimit() {
        assertTrue(run(Scenario.LEAN, 30).ltft >= 25.0)
    }

    @Test
    fun normalScenariosStayInsideAllThresholds() {
        for (s in listOf(Scenario.IDLE, Scenario.CRUISE)) {
            var live = Scenario.IDLE.targets()
            repeat(300) { i ->
                live = ObdEngine.step(live, s, 1.0, i.toDouble(), Random(i))
                assertTrue("${s.name} ect=${live.ect}", live.ect < 105.0)
                assertTrue("${s.name} volt=${live.volt}", live.volt in 13.0..15.0)
                assertTrue("${s.name} ltft=${live.ltft}", kotlin.math.abs(live.ltft) < 15.0)
            }
        }
    }

    @Test
    fun engineOffStopsTheEngineAndKeepsBatteryVoltage() {
        val live = run(Scenario.ENGINE_OFF, 60, from = Scenario.CRUISE.targets())
        assertTrue("rpm=${live.rpm}", live.rpm < 1.0)
        assertTrue(live.volt in 12.0..13.0)
    }

    @Test
    fun engineNeverLeavesTheValidRanges() {
        var live = Scenario.IDLE.targets()
        for (s in Scenario.entries) {
            repeat(200) { i ->
                live = ObdEngine.step(live, s, 2.0, i.toDouble(), Random(i))
                assertEquals(live, live.clamped())
            }
        }
    }

    @Test
    fun misfireScenarioAddsACriticalCodeForTheDevice() {
        val code = Scenario.MISFIRE.dtcCode!!
        assertTrue(DtcCatalog.isCriticalOnDevice(code))
        assertNull(Scenario.IDLE.dtcCode)
    }

    @Test
    fun manualEditsAreClampedToTheFieldRange() {
        val l = LiveField.ECT.set(LiveData(), 999.0).clamped()
        assertEquals(215.0, l.ect, 0.0)
    }
}
