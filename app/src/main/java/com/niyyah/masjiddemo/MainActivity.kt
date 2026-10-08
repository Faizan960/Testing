package com.niyyah.masjiddemo

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NiyyahMasjidDemo() }
    }

    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun hasDndAccess(): Boolean = notificationManager().isNotificationPolicyAccessGranted
    private fun isDndCurrentlyOn(): Boolean =
        notificationManager().currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

    private fun openDndSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    private fun setDnd(enabled: Boolean): Boolean {
        val nm = notificationManager()
        if (!nm.isNotificationPolicyAccessGranted) return false
        nm.setInterruptionFilter(
            if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return true
    }

    private fun setDndFilter(filter: Int): Boolean {
        val nm = notificationManager()
        if (!nm.isNotificationPolicyAccessGranted) return false
        nm.setInterruptionFilter(filter)
        return true
    }

    @Composable
    private fun NiyyahMasjidDemo() {
        var inside by remember { mutableStateOf(false) }
        var beaconDetected by remember { mutableStateOf(false) }
        var beaconRssi by remember { mutableStateOf<Int?>(null) }
        var hasDndAccess by remember { mutableStateOf(hasDndAccess()) }
        var dndOn by remember { mutableStateOf(if (hasDndAccess) isDndCurrentlyOn() else false) }
        var previousDndFilter by remember { mutableStateOf(NotificationManager.INTERRUPTION_FILTER_ALL) }
        var message by remember { mutableStateOf("Waiting for the ESP32 beacon…") }
        var bluetoothEnabled by remember { mutableStateOf(false) }

        val scanner = remember {
            BleMasjidScanner(
                context = this@MainActivity,
                onBeaconDetected = { rssi ->
                    beaconRssi = rssi
                    beaconDetected = true
                    if (!inside) {
                        previousDndFilter = notificationManager().currentInterruptionFilter
                        inside = true
                        if (hasDndAccess()) {
                            val success = setDnd(true)
                            dndOn = if (success) true else isDndCurrentlyOn()
                            message = if (success) "Masjid beacon detected. Masjid Mode enabled."
                            else "Beacon detected, but DND access is unavailable."
                        } else {
                            dndOn = isDndCurrentlyOn()
                            message = "Beacon detected. Grant DND access to enable Masjid Mode."
                        }
                    } else {
                        message = "Masjid beacon detected. RSSI " + rssi + " dBm."
                    }
                },
                onBeaconLost = {
                    beaconDetected = false
                    beaconRssi = null
                    if (inside && hasDndAccess()) setDndFilter(previousDndFilter)
                    inside = false
                    dndOn = if (hasDndAccess()) isDndCurrentlyOn() else false
                    message = "Masjid beacon lost. Previous DND state restored."
                },
                onError = { error -> message = error }
            )
        }

        fun refreshBluetoothState() { bluetoothEnabled = scanner.isBluetoothEnabled() }
        fun refreshDndState() {
            hasDndAccess = hasDndAccess()
            dndOn = if (hasDndAccess) isDndCurrentlyOn() else false
        }

        val blePermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            if (results.values.all { it }) {
                message = "Nearby-device access granted. Starting beacon scan…"
                refreshBluetoothState()
                scanner.start()
            } else {
                message = "Nearby-device permission is required to detect the masjid beacon."
            }
        }

        fun requestBlePermissions() {
            val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            blePermissionLauncher.launch(permissions)
        }

        fun hasBlePermissions(): Boolean = scanner.hasScanPermission() && scanner.hasConnectPermission()

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, scanner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        refreshDndState()
                        refreshBluetoothState()
                        if (hasBlePermissions() && bluetoothEnabled) scanner.start()
                    }
                    Lifecycle.Event.ON_PAUSE -> scanner.stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                scanner.stop()
            }
        }

        MaterialTheme(colorScheme = lightColorScheme()) {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
                ) {
                    Text("NIYYAH", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("MASJID MODE DEMO", style = MaterialTheme.typography.labelLarge)

                    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                if (inside) "Masjid detected" else "Outside masjid",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(message)
                            HorizontalDivider()
                            StatusRow("Beacon", if (beaconDetected) "DETECTED" else "NOT DETECTED")
                            StatusRow("RSSI", beaconRssi?.let { String.format(Locale.US, "%d dBm", it) } ?: "—")
                            StatusRow("Zone", if (inside) "INSIDE" else "OUTSIDE")
                            StatusRow("Bluetooth", if (bluetoothEnabled) "ON" else "OFF")
                            StatusRow("Do Not Disturb", if (dndOn) "ON" else "OFF")
                        }
                    }

                    if (!scanner.hasScanPermission() || !scanner.hasConnectPermission()) {
                        Button(onClick = { requestBlePermissions() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Grant Nearby Device Access")
                        }
                    } else if (!bluetoothEnabled) {
                        Button(
                            onClick = {
                                try { startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                                catch (_: Exception) { message = "Please enable Bluetooth from system settings." }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Enable Bluetooth") }
                    } else {
                        OutlinedButton(
                            onClick = {
                                refreshBluetoothState()
                                refreshDndState()
                                scanner.stop()
                                scanner.start()
                                message = "Scanning for NIYYAH-MASJID-TEST…"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Restart Beacon Scan") }
                    }

                    if (!hasDndAccess) {
                        Button(onClick = { openDndSettings() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Grant DND access")
                        }
                        Text(
                            "Android requires your permission before an app can control Do Not Disturb.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (hasDndAccess()) {
                                    if (!inside) previousDndFilter = notificationManager().currentInterruptionFilter
                                    inside = true
                                    beaconDetected = true
                                    message = "Simulated beacon detected."
                                    val success = setDnd(true)
                                    dndOn = if (success) true else isDndCurrentlyOn()
                                } else {
                                    message = "Grant DND access first."
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Simulate Enter") }

                        OutlinedButton(
                            onClick = {
                                if (inside) {
                                    if (hasDndAccess()) setDndFilter(previousDndFilter)
                                    inside = false
                                }
                                beaconDetected = false
                                beaconRssi = null
                                dndOn = if (hasDndAccess()) isDndCurrentlyOn() else false
                                message = "Simulated beacon lost. Previous DND state restored."
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Simulate Leave") }
                    }

                    Text(
                        "Hardware mode scans for the ESP32 BLE beacon. The buttons remain for fallback testing.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    @Composable
    private fun StatusRow(label: String, value: String) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}
