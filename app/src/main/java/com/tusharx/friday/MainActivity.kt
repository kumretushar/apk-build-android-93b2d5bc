package com.tusharx.friday

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val vm: FridayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FridayTheme {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
                LaunchedEffect(Unit) {
                    launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS))
                }
                var settings by remember { mutableStateOf(false) }
                BackHandler(enabled = settings) { settings = false }
                if (settings) SettingsScreen(vm) { settings = false }
                else MainScreen(vm) { settings = true }
            }
        }
    }

    override fun onStart() { super.onStart(); vm.foreground = true }
    override fun onStop() { super.onStop(); vm.foreground = false; vm.pauseVoice() }
}
