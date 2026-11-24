package com.example.obd2_simulator.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.obd2_simulator.hardware.BleStatus
import com.example.obd2_simulator.ui.components.StatusPill

private enum class NavTab(val title: String, val icon: ImageVector) {
    CONNECTION("Conexión", Icons.Filled.Bluetooth),
    SIMULATOR("Simulador", Icons.Filled.Speed),
    FAULTS("Fallas", Icons.Filled.Warning),
    VEHICLE("Vehículo", Icons.Filled.DirectionsCar),
    LOG("Registro", Icons.AutoMirrored.Filled.List),
}

/** Nombre entendible de cada tipo de mensaje que se manda a la placa. */
internal fun kindLabel(kind: MsgKind?): String = when (kind) {
    MsgKind.INFO -> "identificación del vehículo"
    MsgKind.DTC -> "códigos de falla"
    MsgKind.LIVE -> "lecturas en vivo"
    MsgKind.FF -> "freeze frame"
    MsgKind.MON -> "monitores de emisiones"
    null -> "mensaje"
}

/** Raíz de la aplicación: barra superior, avisos, navegación inferior y la pantalla elegida. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorApp(vm: SimulatorViewModel = viewModel()) {
    val ble by vm.ble.collectAsStateWithLifecycle()
    val sim by vm.sim.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val tabs = NavTab.entries
    val colors = MaterialTheme.colorScheme

    // Cada acción manual deja un aviso breve abajo; las lecturas automáticas no, para no molestar.
    LaunchedEffect(log.lastOrNull()?.id) {
        val entry = log.lastOrNull() ?: return@LaunchedEffect
        if (entry.kind == MsgKind.LIVE) return@LaunchedEffect
        val message = when {
            entry.level == LogLevel.NOTE && entry.kind != null -> "Sin conexión: no se envió ${kindLabel(entry.kind)}"
            entry.level == LogLevel.NOTE -> entry.text
            entry.level == LogLevel.SENT -> "Enviado a la placa: ${kindLabel(entry.kind)}"
            else -> "No se pudo enviar: ${kindLabel(entry.kind)}"
        }
        snackbar.showSnackbar(message)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(24.dp))
                        Text(
                            text = "  OBD2 Simulator",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = { LinkPill(ble.status) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.primary,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = {
                            when (tab) {
                                NavTab.FAULTS -> BadgedBox(badge = {
                                    if (sim.dtc.stored.isNotEmpty()) Badge { Text("${sim.dtc.stored.size}") }
                                }) { Icon(tab.icon, contentDescription = null) }
                                NavTab.CONNECTION -> BadgedBox(badge = {
                                    if (ble.isReady) Badge()
                                }) { Icon(tab.icon, contentDescription = null) }
                                else -> Icon(tab.icon, contentDescription = null)
                            }
                        },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            Crossfade(targetState = selected, label = "pantalla") { index ->
                when (tabs[index]) {
                    NavTab.CONNECTION -> ConnectionScreen(vm)
                    NavTab.SIMULATOR -> LiveScreen(vm)
                    NavTab.FAULTS -> FaultsScreen(vm)
                    NavTab.VEHICLE -> VehicleScreen(vm)
                    NavTab.LOG -> LogScreen(vm)
                }
            }
        }
    }
}

/** Estado del enlace Bluetooth, siempre visible arriba a la derecha. */
@Composable
private fun LinkPill(status: BleStatus) {
    val colors = MaterialTheme.colorScheme
    val icon = when (status) {
        BleStatus.READY -> Icons.Filled.BluetoothConnected
        BleStatus.CONNECTING, BleStatus.SCANNING -> Icons.Filled.BluetoothSearching
        BleStatus.IDLE -> Icons.Filled.BluetoothDisabled
    }
    val label = when (status) {
        BleStatus.READY -> "Conectado"
        BleStatus.CONNECTING -> "Conectando"
        BleStatus.SCANNING -> "Buscando"
        BleStatus.IDLE -> "Sin conexión"
    }
    val container = when (status) {
        BleStatus.READY -> colors.primaryContainer
        BleStatus.CONNECTING, BleStatus.SCANNING -> colors.tertiaryContainer
        BleStatus.IDLE -> colors.surfaceContainerHigh
    }
    val content = when (status) {
        BleStatus.READY -> colors.onPrimaryContainer
        BleStatus.CONNECTING, BleStatus.SCANNING -> colors.onTertiaryContainer
        BleStatus.IDLE -> colors.onSurfaceVariant
    }
    StatusPill(label, container, content, Modifier.padding(end = 12.dp), icon)
}
