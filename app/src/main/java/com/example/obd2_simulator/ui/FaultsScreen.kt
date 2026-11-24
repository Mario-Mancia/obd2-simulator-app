package com.example.obd2_simulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.obd2_simulator.simulator.DtcCatalog
import com.example.obd2_simulator.ui.components.ErrorText
import com.example.obd2_simulator.ui.components.HintText
import com.example.obd2_simulator.ui.components.IconBadge
import com.example.obd2_simulator.ui.components.InfoNote
import com.example.obd2_simulator.ui.components.ScreenColumn
import com.example.obd2_simulator.ui.components.SectionCard
import com.example.obd2_simulator.ui.components.StatTile
import com.example.obd2_simulator.ui.components.StatusPill
import com.example.obd2_simulator.ui.components.statusColors
import java.util.Locale

/** Luz check engine, códigos de falla y freeze frame. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FaultsScreen(vm: SimulatorViewModel) {
    val sim by vm.sim.collectAsStateWithLifecycle()
    val dtc = sim.dtc
    var code by rememberSaveable { mutableStateOf("") }
    var asPending by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ScreenColumn {
        InfoNote(
            "Un código es una falla que la computadora del auto detectó. Los almacenados son fallas confirmadas; " +
                "los pendientes aún se están vigilando y no encienden la luz."
        )

        SectionCard("Luz check engine (MIL)", icon = Icons.Filled.ReportProblem) {
            val colors = MaterialTheme.colorScheme
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (dtc.mil) {
                    StatusPill("ENCENDIDA", colors.errorContainer, colors.onErrorContainer, icon = Icons.Filled.Warning)
                    Text("${dtc.milKm} km con la luz encendida")
                } else {
                    StatusPill("Apagada", colors.surfaceVariant, colors.onSurfaceVariant, icon = Icons.Filled.Check)
                    Text("Sin fallas almacenadas", color = colors.onSurfaceVariant)
                }
            }
            HintText("La luz se enciende sola al agregar un código almacenado y se apaga al quitar el último.")
        }

        SectionCard("Agregar código", icon = Icons.Filled.Add) {
            val normalized = DtcCatalog.normalize(code)
            val invalid = code.length == 5 && !DtcCatalog.isValid(normalized)
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(5); error = null },
                label = { Text("Código (ej. P0171)") },
                singleLine = true,
                isError = error != null || invalid,
                supportingText = {
                    Text(
                        when {
                            invalid -> "Formato: una letra P, C, B o U, un dígito 0 a 3 y tres caracteres hexadecimales."
                            DtcCatalog.isValid(normalized) -> DtcCatalog.describe(normalized)
                            else -> "${code.length}/5"
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            HintText("Atajos con códigos comunes:")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DtcCatalog.common.forEach { c ->
                    AssistChip(onClick = { code = c; error = null }, label = { Text(c) })
                }
            }
            val kinds = listOf(false to "Almacenado", true to "Pendiente")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { index, (pending, label) ->
                    SegmentedButton(
                        selected = asPending == pending,
                        onClick = { asPending = pending },
                        shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                        label = { Text(label) },
                    )
                }
            }
            Button(onClick = {
                error = vm.addDtc(code, asPending)
                if (error == null) code = ""
            }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Agregar y enviar")
            }
            error?.let { ErrorText(it) }
            HintText("Un código almacenado también congela las lecturas (freeze frame) y la placa guarda un evento.")
        }

        CodeList("Códigos almacenados", Icons.Filled.Error, dtc.stored, pending = false, onRemove = vm::removeDtc)
        CodeList("Códigos pendientes", Icons.Filled.Schedule, dtc.pending, pending = true, onRemove = vm::removeDtc)

        sim.freeze?.let { f ->
            SectionCard("Freeze frame", icon = Icons.Filled.AcUnit) {
                HintText("Foto de las lecturas en el momento de la falla ${f.dtc}.")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("RPM", f.rpm.toInt().toString(), Modifier.weight(1f))
                    StatTile("ECT", "${fmt(f.ect)} °C", Modifier.weight(1f))
                    StatTile("LTFT", "${fmt(f.ltft)} %", Modifier.weight(1f))
                }
                OutlinedButton(onClick = { vm.resendFreezeFrame() }) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reenviar")
                }
                HintText("Cada envío crea un evento nuevo en la placa. Úsalo solo para probar el almacenamiento.")
            }
        }

        OutlinedButton(
            onClick = { vm.clearDtcs() },
            enabled = dtc.stored.isNotEmpty() || dtc.pending.isNotEmpty(),
        ) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Borrar todos los códigos")
        }
    }
}

@Composable
private fun CodeList(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    codes: List<String>,
    pending: Boolean,
    onRemove: (String) -> Unit,
) {
    SectionCard(title, icon = icon) {
        if (codes.isEmpty()) HintText(if (pending) "Ninguno pendiente." else "Ninguno almacenado.")
        codes.forEachIndexed { index, c ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val critical = !pending && DtcCatalog.isCriticalOnDevice(c)
            val colors = MaterialTheme.colorScheme
            val status = statusColors()
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    if (critical) IconBadge(Icons.Filled.Error, container = colors.errorContainer, content = colors.onErrorContainer)
                    else if (pending) IconBadge(Icons.Filled.Schedule, container = status.warnContainer, content = status.onWarnContainer)
                    else IconBadge(Icons.Filled.Warning)
                },
                headlineContent = { Text(c, style = MaterialTheme.typography.titleSmall) },
                supportingContent = {
                    Text(DtcCatalog.describe(c) + if (critical) " · la placa lo trata como crítico" else "")
                },
                trailingContent = {
                    IconButton(onClick = { onRemove(c) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Quitar $c")
                    }
                },
            )
        }
    }
}

private fun fmt(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)
