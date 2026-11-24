package com.example.obd2_simulator.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.obd2_simulator.hardware.BlePermissions
import com.example.obd2_simulator.hardware.BleState
import com.example.obd2_simulator.hardware.BleStatus
import com.example.obd2_simulator.ui.components.ErrorText
import com.example.obd2_simulator.ui.components.HintText
import com.example.obd2_simulator.ui.components.IconBadge
import com.example.obd2_simulator.ui.components.InfoNote
import com.example.obd2_simulator.ui.components.ScreenColumn
import com.example.obd2_simulator.ui.components.SectionCard
import com.example.obd2_simulator.ui.components.StatusPill

/** Buscar la placa, conectarse y ver el estado del enlace. */
@Composable
fun ConnectionScreen(vm: SimulatorViewModel) {
    val context = LocalContext.current
    val ble by vm.ble.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<String?>(null) }
    var scanRequest by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) scanRequest++
        else notice = "Sin el permiso de Bluetooth no se puede buscar la placa."
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (BlePermissions.isBluetoothOn(context)) scanRequest++
        else notice = "Bluetooth sigue apagado."
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (BlePermissions.isLocationOn(context)) scanRequest++
        else notice = "La ubicación sigue apagada; en Android 11 es necesaria para buscar dispositivos."
    }

    // Revisa en orden: permisos → Bluetooth → ubicación (solo Android 11) → buscar.
    LaunchedEffect(scanRequest) {
        if (scanRequest == 0) return@LaunchedEffect
        when {
            !BlePermissions.hasAll(context) -> permissionLauncher.launch(BlePermissions.required())
            !BlePermissions.isBluetoothOn(context) ->
                bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            !BlePermissions.isLocationOn(context) -> {
                notice = "En Android 11 hace falta activar la ubicación para buscar dispositivos Bluetooth."
                locationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            else -> {
                notice = null
                vm.startScan()
            }
        }
    }

    ScreenColumn {
        StatusHero(ble, onDisconnect = { vm.disconnect() })

        (ble.error ?: notice)?.let { ErrorText(it) }

        InfoNote(
            "Esta app hace de vehículo: genera datos OBD2 y se los manda por Bluetooth LE a la placa ESP32. " +
                "Primero conéctala aquí; después usa las otras pestañas."
        )

        SectionCard("Buscar placa", icon = Icons.Filled.Search) {
            if (ble.status == BleStatus.SCANNING) {
                OutlinedButton(onClick = { vm.stopScan() }) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Detener búsqueda")
                }
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Button(onClick = { scanRequest++ }, enabled = ble.status == BleStatus.IDLE) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Buscar OBD2-Monitor")
                }
            }
            if (ble.devices.isEmpty()) {
                HintText(
                    if (ble.status == BleStatus.SCANNING) "Buscando dispositivos que ofrezcan el servicio del monitor…"
                    else "Aún no hay resultados. Enciende la placa y toca Buscar."
                )
            } else {
                HintText("Toca un dispositivo para conectarte.")
            }
            ble.devices.forEachIndexed { index, d ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { IconBadge(Icons.Filled.Bluetooth) },
                    headlineContent = { Text(d.name) },
                    supportingContent = { Text(d.address) },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.SignalCellularAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = signalAlpha(d.rssi)),
                            )
                            Text("${d.rssi} dBm", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.clickable { vm.connect(d) },
                )
            }
        }

        SectionCard("Si la placa no aparece", icon = Icons.Filled.Info) {
            Tip("Revisa que la placa esté encendida y que no esté mostrando el portal de configuración WiFi: mientras el portal está abierto, el Bluetooth se pausa.")
            Tip("Solo se puede tener un teléfono conectado a la vez; cierra otras apps que usen la placa.")
            Tip("Se buscan dispositivos con el servicio 6E400001-B5A0-F393-E0A9-E50E24DCCA9E.")
            Tip("Android pide permiso de Bluetooth (dispositivos cercanos) la primera vez; en Android 11 también la ubicación.")
        }
    }
}

/** Tarjeta grande con el estado del enlace y el botón para desconectar o cancelar. */
@Composable
private fun StatusHero(ble: BleState, onDisconnect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container = when (ble.status) {
        BleStatus.READY -> colors.primaryContainer
        BleStatus.CONNECTING, BleStatus.SCANNING -> colors.tertiaryContainer
        BleStatus.IDLE -> colors.surfaceContainer
    }
    val onContainer = when (ble.status) {
        BleStatus.READY -> colors.onPrimaryContainer
        BleStatus.CONNECTING, BleStatus.SCANNING -> colors.onTertiaryContainer
        BleStatus.IDLE -> colors.onSurface
    }
    val icon = when (ble.status) {
        BleStatus.READY -> Icons.Filled.BluetoothConnected
        BleStatus.CONNECTING, BleStatus.SCANNING -> Icons.Filled.BluetoothSearching
        BleStatus.IDLE -> Icons.Filled.BluetoothDisabled
    }
    val title = when (ble.status) {
        BleStatus.READY -> "Conectado a ${ble.connected?.name ?: "la placa"}"
        BleStatus.CONNECTING -> "Conectando a ${ble.connected?.name ?: "la placa"}…"
        BleStatus.SCANNING -> "Buscando la placa…"
        BleStatus.IDLE -> "Sin conexión con la placa"
    }
    val subtitle = when (ble.status) {
        BleStatus.READY -> "Los datos del simulador ya llegan a la placa."
        BleStatus.CONNECTING -> "Negociando el enlace y el tamaño de los mensajes."
        BleStatus.SCANNING -> "Enciende la placa y mantenla cerca del teléfono."
        BleStatus.IDLE -> "Busca la placa abajo para empezar."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconBadge(icon, container = onContainer.copy(alpha = 0.12f), content = onContainer, size = 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (ble.status == BleStatus.CONNECTING) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (ble.status == BleStatus.READY) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("MTU ${ble.mtu}", onContainer.copy(alpha = 0.12f), onContainer, icon = Icons.Filled.Check)
                    StatusPill("${ble.mtu - 3} bytes por escritura", onContainer.copy(alpha = 0.12f), onContainer)
                }
                Text(
                    ble.connected?.address.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = onContainer.copy(alpha = 0.8f),
                )
            }
            if (ble.status == BleStatus.READY || ble.status == BleStatus.CONNECTING) {
                OutlinedButton(onClick = onDisconnect) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (ble.status == BleStatus.READY) "Desconectar" else "Cancelar")
                }
            }
        }
    }
}

@Composable
private fun Tip(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp),
        )
        HintText(text)
    }
}

/** Más intensa cuanto más cerca está la placa. */
private fun signalAlpha(rssi: Int): Float = when {
    rssi >= -60 -> 1f
    rssi >= -75 -> 0.65f
    else -> 0.35f
}
