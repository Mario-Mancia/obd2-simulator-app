package com.example.obd2_simulator.hardware

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Permisos y requisitos del sistema para escanear y conectarse por BLE. */
object BlePermissions {
    /** Android 12+ pide permisos de Bluetooth; en Android 11 (API 30) el escaneo exige ubicación. */
    fun required(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasAll(context: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothOn(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }

    /** Solo API 30: sin el servicio de ubicación activo el escaneo no devuelve resultados. */
    fun needsLocationService(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    fun isLocationOn(context: Context): Boolean {
        if (!needsLocationService()) return true
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return lm?.isLocationEnabled == true
    }
}
