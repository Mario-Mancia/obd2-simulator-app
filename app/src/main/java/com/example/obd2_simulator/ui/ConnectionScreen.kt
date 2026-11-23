package com.example.obd2_simulator.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.obd2_simulator.hardware.BleStatus
import com.example.obd2_simulator.hardware.BlePermissions
import com.example.obd2_simulator.ui.components.ErrorText
import com.example.obd2_simulator.ui.components.HintText
import com.example.obd2_simulator.ui.components.ScreenColumn
import com.example.obd2_simulator.ui.components.SectionCard

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
        SectionCard("Estado") {
            val device = ble.connected
            when (ble.status) {
                BleStatus.READY -> {
                    Text("Conectado a ${device?.name ?: "la placa"}")
                    HintText("${device?.address ?: ""} · MTU ${ble.mtu} (hasta ${ble.mtu - 3} bytes por escritura)")
                    OutlinedButton(onClick = { vm.disconnect() }) { Text("Desconectar") }
                }
                BleStatus.CONNECTING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Conectando a ${device?.name ?: "la placa"}…")
                    }
                    OutlinedButton(onClick = { vm.disconnect() }) { Text("Cancelar") }
                }
                else -> Text("Sin conexión con la placa.")
            }
            (ble.error ?: notice)?.let { ErrorText(it) }
        }

        SectionCard("Buscar placa") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ble.status == BleStatus.SCANNING) {
                    OutlinedButton(onClick = { vm.stopScan() }) { Text("Detener búsqueda") }
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Button(
                        onClick = { scanRequest++ },
                        enabled = ble.status == BleStatus.IDLE,
                    ) { Text("Buscar OBD2-Monitor") }
                }
            }
            if (ble.devices.isEmpty()) {
                HintText(
                    if (ble.status == BleStatus.SCANNING) "Buscando dispositivos que ofrezcan el servicio del monitor…"
                    else "Aún no hay resultados."
                )
            }
            ble.devices.forEachIndexed { index, d ->
                if (index > 0) HorizontalDivider()
                ListItem(
                    headlineContent = { Text(d.name) },
                    supportingContent = { Text(d.address) },
                    trailingContent = { Text("${d.rssi} dBm") },
                    modifier = Modifier.clickable { vm.connect(d) },
                )
            }
        }

        SectionCard("Si la placa no aparece") {
            HintText("• Revisa que la placa esté encendida y que no esté mostrando el portal de configuración WiFi: mientras el portal está abierto, el Bluetooth se pausa.")
            HintText("• Solo se puede tener un teléfono conectado a la vez; cierra otras apps que usen la placa.")
            HintText("• Se buscan dispositivos con el servicio 6E400001-B5A0-F393-E0A9-E50E24DCCA9E.")
        }
    }
}
