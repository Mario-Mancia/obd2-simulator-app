package com.example.obd2_simulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.obd2_simulator.ui.components.HintText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Todo lo que se envió a la placa (más reciente arriba). */
@Composable
fun LogScreen(vm: SimulatorViewModel) {
    val log by vm.log.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var showLive by rememberSaveable { mutableStateOf(false) }
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val visible = remember(log, showLive) {
        log.asReversed().filter { showLive || it.kind != MsgKind.LIVE }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enviados: ${stats.sent} · Fallidos: ${stats.failed}", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { vm.clearLog() }) { Text("Limpiar") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = showLive, onCheckedChange = { showLive = it })
                Text("Mostrar lecturas live")
            }
        }
        if (visible.isEmpty()) {
            HintText("Todavía no hay mensajes.", Modifier.padding(horizontal = 16.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(visible, key = { it.id }) { e ->
                val colors = MaterialTheme.colorScheme
                val color = when (e.level) {
                    LogLevel.SENT -> colors.onSurface
                    LogLevel.FAILED -> colors.error
                    LogLevel.NOTE -> colors.onSurfaceVariant
                }
                val tag = when (e.level) {
                    LogLevel.SENT -> "→"
                    LogLevel.FAILED -> "✗"
                    LogLevel.NOTE -> "•"
                }
                Text(
                    text = "${clock.format(Date(e.timeMs))} $tag ${e.text}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (e.kind != null) FontFamily.Monospace else FontFamily.Default,
                    color = color,
                )
            }
        }
    }
}
