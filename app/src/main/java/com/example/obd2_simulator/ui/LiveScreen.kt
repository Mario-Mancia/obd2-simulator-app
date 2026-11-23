package com.example.obd2_simulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.obd2_simulator.simulator.LiveField
import com.example.obd2_simulator.simulator.Scenario
import com.example.obd2_simulator.ui.components.HintText
import com.example.obd2_simulator.ui.components.ScreenColumn
import com.example.obd2_simulator.ui.components.SectionCard
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Escenarios, lecturas en vivo y control del envío periódico. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(vm: SimulatorViewModel) {
    val sim by vm.sim.collectAsStateWithLifecycle()
    val ble by vm.ble.collectAsStateWithLifecycle()

    ScreenColumn {
        SectionCard("Escenario") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Scenario.entries.forEach { s ->
                    FilterChip(
                        selected = sim.scenario == s && !sim.manual,
                        onClick = { vm.selectScenario(s) },
                        label = { Text(s.title) },
                    )
                }
            }
            HintText(sim.scenario.description)
            if (sim.manual) {
                Text(
                    "Modo manual: las lecturas ya no se mueven solas.",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { vm.resumeScenario() }) { Text("Volver al escenario") }
            }
        }

        SectionCard("Envío a la placa") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Enviar lecturas automáticamente")
                Switch(checked = sim.autoSend, onCheckedChange = vm::setAutoSend)
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
            Button(onClick = { vm.sendNow(MsgKind.LIVE) }, enabled = ble.isReady) { Text("Enviar lectura ahora") }
            if (!ble.isReady) HintText("Conéctate a la placa en la pestaña Conexión para enviar datos.")
        }

        SectionCard("Lecturas en tiempo real") {
            LiveField.entries.forEach { field ->
                LiveRow(field, field.get(sim.live)) { vm.setManualValue(field, it) }
            }
            HintText("Arrastra un control para fijar el valor a mano.")
        }
    }
}

@Composable
private fun LiveRow(field: LiveField, value: Double, onChange: (Double) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(field.label, style = MaterialTheme.typography.bodyMedium)
        Text(
            formatValue(value, field.decimals) + " " + field.unit,
            style = MaterialTheme.typography.titleSmall,
        )
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toDouble()) },
        valueRange = field.range.start.toFloat()..field.range.endInclusive.toFloat(),
    )
}

private fun formatValue(v: Double, decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", v)
