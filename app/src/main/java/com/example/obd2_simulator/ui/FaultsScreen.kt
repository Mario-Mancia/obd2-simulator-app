package com.example.obd2_simulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.obd2_simulator.simulator.DtcCatalog
import com.example.obd2_simulator.ui.components.ErrorText
import com.example.obd2_simulator.ui.components.HintText
import com.example.obd2_simulator.ui.components.ScreenColumn
import com.example.obd2_simulator.ui.components.SectionCard
import com.example.obd2_simulator.ui.components.StatusPill
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
        SectionCard("Luz check engine (MIL)") {
            val colors = MaterialTheme.colorScheme
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (dtc.mil) {
                    StatusPill("ENCENDIDA", colors.errorContainer, colors.onErrorContainer)
                    Text("${dtc.milKm} km con la luz encendida")
                } else {
                    StatusPill("Apagada", colors.surfaceVariant, colors.onSurfaceVariant)
                }
            }
            HintText("La luz se enciende sola al agregar un código almacenado y se apaga al quitar el último.")
        }

        SectionCard("Agregar código") {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(5); error = null },
                label = { Text("Código (ej. P0171)") },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
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
            }) { Text("Agregar y enviar") }
            error?.let { ErrorText(it) }
            HintText("Un código almacenado también congela las lecturas (freeze frame) y la placa guarda un evento.")
        }

        CodeList("Códigos almacenados", dtc.stored, vm::removeDtc)
        CodeList("Códigos pendientes", dtc.pending, vm::removeDtc)

        sim.freeze?.let { f ->
            SectionCard("Freeze frame") {
                Text("${f.dtc} · ${f.rpm.toInt()} rpm · ${fmt(f.ect)} °C · LTFT ${fmt(f.ltft)} %")
                OutlinedButton(onClick = { vm.resendFreezeFrame() }) { Text("Reenviar") }
                HintText("Cada envío crea un evento nuevo en la placa. Úsalo solo para probar el almacenamiento.")
            }
        }

        OutlinedButton(
            onClick = { vm.clearDtcs() },
            enabled = dtc.stored.isNotEmpty() || dtc.pending.isNotEmpty(),
        ) { Text("Borrar todos los códigos") }
    }
}

@Composable
private fun CodeList(title: String, codes: List<String>, onRemove: (String) -> Unit) {
    SectionCard(title) {
        if (codes.isEmpty()) HintText("Ninguno.")
        codes.forEachIndexed { index, c ->
            if (index > 0) HorizontalDivider()
            ListItem(
                headlineContent = { Text(c) },
                supportingContent = {
                    val critical = DtcCatalog.isCriticalOnDevice(c)
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
