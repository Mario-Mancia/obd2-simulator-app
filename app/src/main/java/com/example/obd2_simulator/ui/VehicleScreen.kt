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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.obd2_simulator.simulator.Monitor
import com.example.obd2_simulator.simulator.MonitorStatus
import com.example.obd2_simulator.simulator.VehicleInfo
import com.example.obd2_simulator.ui.components.ErrorText
import com.example.obd2_simulator.ui.components.HintText
import com.example.obd2_simulator.ui.components.InfoNote
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
        InfoNote(
            "Estos datos identifican al vehículo simulado. La placa los recibe al conectarse y cada vez que pulsas Aplicar."
        )

        SectionCard("Vehículo", icon = Icons.Filled.DirectionsCar) {
            val vinOk = VehicleInfo.isValidVin(vin)
            OutlinedTextField(
                value = vin,
                onValueChange = { vin = it.uppercase().take(17); error = null },
                label = { Text("VIN (17 caracteres)") },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                leadingIcon = {
                    if (vinOk) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                },
                supportingText = {
                    Text(
                        if (vinOk) "${vin.length}/17 · VIN válido"
                        else "${vin.length}/17 · letras y números, sin I, O ni Q"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = year,
                onValueChange = { year = it.filter(Char::isDigit).take(4); error = null },
                label = { Text("Año") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Entre ${VehicleInfo.YEARS.first} y ${VehicleInfo.YEARS.last}") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Protocolo", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleInfo.PROTOCOLS.forEach { p ->
                    val selected = protocol == p
                    FilterChip(
                        selected = selected,
                        onClick = { protocol = p },
                        label = { Text(p) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                    )
                }
            }
            HintText("El protocolo es el \"idioma\" del bus del auto; los anteriores a 2010 suelen usar ISO9141-2 o J1850.")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    error = vm.setVehicle(vin, year.toIntOrNull(), protocol)
                    applied = error == null
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Aplicar y enviar")
                }
                OutlinedButton(onClick = {
                    val ref = VehicleInfo.REFERENCE
                    vin = ref.vin; year = ref.year.toString(); protocol = ref.protocol; error = null
                }) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ejemplo")
                }
            }
            error?.let { ErrorText(it) }
            if (applied) {
                InfoNote(
                    "Listo. ${if (ble.isReady) "Enviado a la placa." else "Se enviará al conectarte."}",
                    icon = Icons.Filled.CheckCircle,
                )
            }
        }

        SectionCard("Monitores de emisiones", icon = Icons.Filled.Tune) {
            HintText("Cada monitor es una autoprueba del auto: Listo = ya la pasó, Incompleto = aún no se ejecuta, N/A = el auto no la tiene.")
            Monitor.entries.forEach { m ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(m.label, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MonitorStatus.entries.forEach { st ->
                            val selected = sim.readiness[m] == st
                            FilterChip(
                                selected = selected,
                                onClick = { vm.setMonitor(m, st) },
                                label = { Text(st.label) },
                                leadingIcon = {
                                    Icon(statusIcon(st), contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                        }
                    }
                }
            }
            HintText("Son informativos: la placa los guarda pero no generan alertas.")
        }
    }
}

private fun statusIcon(st: MonitorStatus): ImageVector = when (st) {
    MonitorStatus.READY -> Icons.Filled.Check
    MonitorStatus.INCOMPLETE -> Icons.Filled.Schedule
    MonitorStatus.UNSUPPORTED -> Icons.Filled.DoNotDisturbOn
}
