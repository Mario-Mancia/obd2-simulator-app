package com.example.obd2_simulator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.example.obd2_simulator.ui.components.IconBadge
import com.example.obd2_simulator.ui.components.InfoNote
import com.example.obd2_simulator.ui.components.StatTile
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatTile("Enviados", stats.sent.toString(), Modifier.weight(1f), icon = Icons.Filled.CheckCircle)
                StatTile("Fallidos", stats.failed.toString(), Modifier.weight(1f), icon = Icons.Filled.Error)
                OutlinedButton(onClick = { vm.clearLog() }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Limpiar")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Mostrar lecturas live", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(checked = showLive, onCheckedChange = { showLive = it })
            }
            InfoNote(
                "Aquí queda cada mensaje JSON enviado a la placa. Las lecturas live se ocultan porque salen cada segundo; " +
                    "actívalas si quieres ver el contenido exacto."
            )
        }
        if (visible.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconBadge(Icons.Filled.Inbox, size = 56.dp)
                Text("Todavía no hay mensajes", style = MaterialTheme.typography.titleMedium)
                HintText("Conéctate a la placa y empieza a enviar datos para verlos aquí.")
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = { it.id }) { e -> LogRow(e, clock.format(Date(e.timeMs))) }
        }
    }
}

@Composable
private fun LogRow(e: LogEntry, time: String) {
    val colors = MaterialTheme.colorScheme
    val container = if (e.level == LogLevel.FAILED) colors.errorContainer else colors.surfaceContainer
    val onContainer = if (e.level == LogLevel.FAILED) colors.onErrorContainer else colors.onSurface
    val icon = when (e.level) {
        LogLevel.SENT -> Icons.AutoMirrored.Filled.Send
        LogLevel.FAILED -> Icons.Filled.Error
        LogLevel.NOTE -> Icons.Filled.Info
    }
    val levelLabel = when (e.level) {
        LogLevel.SENT -> "Enviado"
        LogLevel.FAILED -> "Falló"
        LogLevel.NOTE -> "Nota"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val badgeBg = if (e.level == LogLevel.FAILED) colors.error else colors.primaryContainer
            val badgeFg = if (e.level == LogLevel.FAILED) colors.onError else colors.onPrimaryContainer
            IconBadge(icon, container = badgeBg, content = badgeFg, size = 32.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "$time · $levelLabel · ${kindLabel(e.kind).replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (e.level == LogLevel.FAILED) onContainer else colors.onSurfaceVariant,
                )
                Text(
                    text = e.text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (e.kind != null) FontFamily.Monospace else FontFamily.Default,
                    color = onContainer,
                )
            }
        }
    }
}
