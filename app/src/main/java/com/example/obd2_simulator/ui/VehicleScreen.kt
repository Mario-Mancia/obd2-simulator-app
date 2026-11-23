package com.example.obd2_simulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.obd2_simulator.simulator.Monitor
import com.example.obd2_simulator.simulator.MonitorStatus
import com.example.obd2_simulator.simulator.VehicleInfo
import com.example.obd2_simulator.ui.components.ErrorText
import com.example.obd2_simulator.ui.components.HintText
import com.example.obd2_simulator.ui.components.ScreenColumn
import com.example.obd2_simulator.ui.components.SectionCard

/** Identificación del vehículo y estado de los monitores de emisiones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleScreen(vm: SimulatorViewModel) {
    val sim by vm.sim.collectAsStateWithLifecycle()
    val ble by vm.ble.collectAsStateWithLifecycle()
    var vin by rememberSaveable { mutableStateOf(sim.vehicle.vin) }
    var year by rememberSaveable { mutableStateOf(sim.vehicle.year.toString()) }
    var protocol by rememberSaveable { mutableStateOf(sim.vehicle.protocol) }
    var error by remember { mutableStateOf<String?>(null) }
    var applied by remember { mutableStateOf(false) }

    LaunchedEffect(vin, year, protocol) { applied = false }

    ScreenColumn {
        SectionCard("Vehículo") {
            OutlinedTextField(
                value = vin,
                onValueChange = { vin = it.uppercase().take(17); error = null },
                label = { Text("VIN (17 caracteres)") },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                supportingText = { Text("${vin.length}/17") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = year,
                onValueChange = { year = it.filter(Char::isDigit).take(4); error = null },
                label = { Text("Año") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Protocolo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleInfo.PROTOCOLS.forEach { p ->
                    FilterChip(selected = protocol == p, onClick = { protocol = p }, label = { Text(p) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    error = vm.setVehicle(vin, year.toIntOrNull(), protocol)
                    applied = error == null
                }) { Text("Aplicar y enviar") }
                OutlinedButton(onClick = {
                    val ref = VehicleInfo.REFERENCE
                    vin = ref.vin; year = ref.year.toString(); protocol = ref.protocol; error = null
                }) { Text("Valores de ejemplo") }
            }
            error?.let { ErrorText(it) }
            if (applied) HintText("Listo. ${if (ble.isReady) "Enviado a la placa." else "Se enviará al conectarte."}")
        }

        SectionCard("Monitores de emisiones") {
            Monitor.entries.forEach { m ->
                Text(m.label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonitorStatus.entries.forEach { st ->
                        FilterChip(
                            selected = sim.readiness[m] == st,
                            onClick = { vm.setMonitor(m, st) },
                            label = { Text(st.label) },
                        )
                    }
                }
            }
            HintText("Son informativos: la placa los guarda pero no generan alertas.")
        }
    }
}
