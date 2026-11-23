package com.example.obd2_simulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.obd2_simulator.ui.SimulatorApp
import com.example.obd2_simulator.ui.theme.OBD2SimulatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OBD2SimulatorTheme {
                SimulatorApp()
            }
        }
    }
}
