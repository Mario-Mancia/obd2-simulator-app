package com.example.obd2_simulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.obd2_simulator.simulator.LiveData
import com.example.obd2_simulator.simulator.LiveField
import com.example.obd2_simulator.simulator.Scenario
import com.example.obd2_simulator.ui.components.HintText
import com.example.obd2_simulator.ui.components.InfoNote
import com.example.obd2_simulator.ui.components.ScreenColumn
import com.example.obd2_simulator.ui.components.SectionCard
import com.example.obd2_simulator.ui.components.StatusPill
import com.example.obd2_simulator.ui.components.statusColors
import java.util.Locale

private const val RPM_RUNNING = 400.0

/** Escenarios, lecturas en tiempo real y control del envío periódico. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(vm: SimulatorViewModel) {
    val sim by vm.sim.collectAsStateWithLifecycle()
    val ble by vm.ble.collectAsStateWithLifecycle()

    ScreenColumn {
        InfoNote(
            "Elige un escenario para que el vehículo simulado se comporte de cierta forma, o mueve los controles a mano. " +
                "Las etiquetas Aviso y Crítico indican qué debería hacer la placa con ese valor."
        )

        SectionCard("Escenario", icon = Icons.Filled.Tune) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Scenario.entries.forEach { s ->
                    val selected = sim.scenario == s && !sim.manual
                    FilterChip(
                        selected = selected,
                        onClick = { vm.selectScenario(s) },
                        label = { Text(s.title) },
                        leadingIcon = {
                            Icon(
                                if (selected) Icons.Filled.Done else scenarioIcon(s),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
            HintText(sim.scenario.description)
            if (sim.manual) {
                InfoNote(
                    "Modo manual: las lecturas ya no se mueven solas.",
                    icon = Icons.Filled.TouchApp,
                )
                OutlinedButton(onClick = { vm.resumeScenario() }) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Volver al escenario")
                }
            }
        }

        SectionCard("Envío a la placa", icon = Icons.AutoMirrored.Filled.Send) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enviar lecturas automáticamente", style = MaterialTheme.typography.bodyLarge)
                    HintText(if (sim.autoSend) "Se manda un mensaje live cada intervalo." else "Pausado: solo se envía al tocar el botón.")
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = sim.autoSend, onCheckedChange = vm::setAutoSend)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Intervalo entre lecturas", style = MaterialTheme.typography.labelLarge)
            }
            val options = listOf(500L to "0.5 s", 1000L to "1 s", 2000L to "2 s")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (ms, label) ->
                    SegmentedButton(
                        selected = sim.liveIntervalMs == ms,
                        onClick = { vm.setLiveInterval(ms) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        enabled = sim.autoSend,
                        label = { Text(label) },
                    )
                }
            }
            Button(onClick = { vm.sendNow(MsgKind.LIVE) }, enabled = ble.isReady) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Enviar lectura ahora")
            }
            if (!ble.isReady) {
                InfoNote(
                    "Conéctate a la placa en la pestaña Conexión para enviar datos.",
                    icon = Icons.Filled.Warning,
                )
            }
        }

        SectionCard("Lecturas en tiempo real", icon = Icons.Filled.Speed) {
            LiveField.entries.forEach { field ->
                LiveRow(field, field.get(sim.live), sim.live) { vm.setManualValue(field, it) }
            }
            HintText("Arrastra un control para fijar el valor a mano.")
        }
    }
}

private enum class Zone { NORMAL, WARN, CRIT, NONE }

/** Reproduce los umbrales del firmware (Config.h) para avisar qué esperar de la placa. */
private fun zoneOf(field: LiveField, v: Double, live: LiveData): Zone = when (field) {
    LiveField.ECT -> when {
        v >= 110.0 -> Zone.CRIT
        v >= 105.0 -> Zone.WARN
        else -> Zone.NORMAL
    }
    LiveField.VOLT -> when {
        live.rpm <= RPM_RUNNING -> Zone.NONE
        v < 12.0 || v > 16.0 -> Zone.CRIT
        v < 13.0 || v > 15.0 -> Zone.WARN
        else -> Zone.NORMAL
    }
    LiveField.LTFT -> {
        val a = kotlin.math.abs(v)
        when {
            a >= 25.0 -> Zone.CRIT
            a >= 15.0 -> Zone.WARN
            else -> Zone.NORMAL
        }
    }
    else -> Zone.NONE
}

private fun fieldNote(field: LiveField): String? = when (field) {
    LiveField.ECT -> "Aviso desde 105 °C, crítico desde 110 °C."
    LiveField.VOLT -> "Solo se evalúa con el motor encendido (más de 400 rpm). Normal: 13–15 V."
    LiveField.LTFT -> "Aviso desde ±15 %, crítico desde ±25 %; debe sostenerse 5 lecturas seguidas."
    else -> null
}

private fun fieldIcon(field: LiveField): ImageVector = when (field) {
    LiveField.RPM -> Icons.Filled.Speed
    LiveField.ECT -> Icons.Filled.Thermostat
    LiveField.VOLT -> Icons.Filled.BatteryChargingFull
    LiveField.LOAD -> Icons.Filled.Settings
    LiveField.MAF -> Icons.Filled.AcUnit
    LiveField.MAP -> Icons.Filled.Speed
    LiveField.STFT -> Icons.Filled.LocalGasStation
    LiveField.LTFT -> Icons.Filled.LocalGasStation
    LiveField.O2B1S1 -> Icons.Filled.Bolt
    LiveField.O2B1S2 -> Icons.Filled.Bolt
}

private fun scenarioIcon(s: Scenario): ImageVector = when (s) {
    Scenario.IDLE -> Icons.Filled.DirectionsCar
    Scenario.CRUISE -> Icons.Filled.Speed
    Scenario.OVERHEAT -> Icons.Filled.Thermostat
    Scenario.LOW_VOLTAGE -> Icons.Filled.BatteryAlert
    Scenario.HIGH_VOLTAGE -> Icons.Filled.Bolt
    Scenario.LEAN -> Icons.Filled.LocalGasStation
    Scenario.MISFIRE -> Icons.Filled.Build
    Scenario.ENGINE_OFF -> Icons.Filled.PowerSettingsNew
}

@Composable
private fun ZonePill(zone: Zone) {
    val colors = MaterialTheme.colorScheme
    val status = statusColors()
    when (zone) {
        Zone.NORMAL -> StatusPill("Normal", colors.secondaryContainer, colors.onSecondaryContainer, icon = Icons.Filled.Check)
        Zone.WARN -> StatusPill("Aviso", status.warnContainer, status.onWarnContainer, icon = Icons.Filled.Warning)
        Zone.CRIT -> StatusPill("Crítico", colors.errorContainer, colors.onErrorContainer, icon = Icons.Filled.Error)
        Zone.NONE -> Unit
    }
}

@Composable
private fun LiveRow(field: LiveField, value: Double, live: LiveData, onChange: (Double) -> Unit) {
    val zone = zoneOf(field, value, live)
    val status = statusColors()
    val colors = MaterialTheme.colorScheme
    val accent: Color = when (zone) {
        Zone.CRIT -> colors.error
        Zone.WARN -> status.warn
        else -> colors.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(fieldIcon(field), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Text(field.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            ZonePill(zone)
            Text(
                formatValue(value, field.decimals) + " " + field.unit,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = field.range.start.toFloat()..field.range.endInclusive.toFloat(),
        )
        fieldNote(field)?.let { HintText(it) }
    }
}

private fun formatValue(v: Double, decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", v)
