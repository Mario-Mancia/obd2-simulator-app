package com.example.obd2_simulator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.obd2_simulator.hardware.BleStatus
import com.example.obd2_simulator.ui.components.StatusPill

private enum class Tab(val title: String, val icon: ImageVector) {
    CONNECTION("Conexión", Icons.Filled.Search),
    SIMULATOR("Simulador", Icons.Filled.PlayArrow),
    FAULTS("Fallas", Icons.Filled.Warning),
    VEHICLE("Vehículo", Icons.Filled.Settings),
    LOG("Registro", Icons.Filled.Menu),
}

/** Raíz de la aplicación: barra superior, navegación inferior y la pantalla elegida. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorApp(vm: SimulatorViewModel = viewModel()) {
    val ble by vm.ble.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(0) }
    val tabs = Tab.entries

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("OBD2 Simulator") },
                actions = {
                    val colors = MaterialTheme.colorScheme
                    val end = Modifier.padding(end = 12.dp)
                    when (ble.status) {
                        BleStatus.READY -> StatusPill("Conectado", colors.primaryContainer, colors.onPrimaryContainer, end)
                        BleStatus.CONNECTING -> StatusPill("Conectando…", colors.tertiaryContainer, colors.onTertiaryContainer, end)
                        BleStatus.SCANNING -> StatusPill("Buscando…", colors.secondaryContainer, colors.onSecondaryContainer, end)
                        BleStatus.IDLE -> StatusPill("Sin conexión", colors.surfaceVariant, colors.onSurfaceVariant, end)
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { padding ->
        val body = Modifier.padding(padding)
        Box(body) {
            when (tabs[selected]) {
                Tab.CONNECTION -> ConnectionScreen(vm)
                Tab.SIMULATOR -> LiveScreen(vm)
                Tab.FAULTS -> FaultsScreen(vm)
                Tab.VEHICLE -> VehicleScreen(vm)
                Tab.LOG -> LogScreen(vm)
            }
        }
    }
}
