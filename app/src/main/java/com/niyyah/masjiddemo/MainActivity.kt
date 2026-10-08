package com.niyyah.masjiddemo

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

import android.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("NiyyahMasjidDemo", "onCreate: started")
        super.onCreate(savedInstanceState)
        Log.d("NiyyahMasjidDemo", "onCreate: calling setContent")
        setContent { 
            Log.d("NiyyahMasjidDemo", "setContent: inside root composable")
            NiyyahMasjidDemo() 
        }
    }

    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun hasDndAccess(): Boolean = notificationManager().isNotificationPolicyAccessGranted

    private fun isDndCurrentlyOn(): Boolean {
        val filter = notificationManager().currentInterruptionFilter
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

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
        Log.d("NiyyahMasjidDemo", "NiyyahMasjidDemo: executing")
        var inside by remember { mutableStateOf(false) }
        var hasAccess by remember { mutableStateOf(hasDndAccess()) }
        var dndOn by remember { mutableStateOf(if (hasAccess) isDndCurrentlyOn() else false) }
        var previousDndFilter by remember { mutableStateOf(NotificationManager.INTERRUPTION_FILTER_ALL) }
        var message by remember { mutableStateOf("Waiting for the ESP32 beacon…") }

        // Auto-refresh permission when returning from settings
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasAccess = hasDndAccess()
                    dndOn = if (hasAccess) isDndCurrentlyOn() else false
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                            Text(if (inside) "Masjid detected" else "Outside masjid", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(message)
                            HorizontalDivider()
                            StatusRow("Beacon", if (inside) "DETECTED" else "NOT DETECTED")
                            StatusRow("Zone", if (inside) "INSIDE" else "OUTSIDE")
                            StatusRow("Do Not Disturb", if (dndOn) "ON" else "OFF")
                        }
                    }

                    if (!hasAccess) {
                        Button(onClick = { openDndSettings() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Grant DND access")
                        }
                        Text("Android requires your permission before an app can control Do Not Disturb.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedButton(onClick = {
                            hasAccess = hasDndAccess()
                            dndOn = if (hasAccess) isDndCurrentlyOn() else false
                        }, modifier = Modifier.fillMaxWidth()) { Text("Refresh DND permission") }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (hasDndAccess()) {
                                    if (!inside) {
                                        previousDndFilter = notificationManager().currentInterruptionFilter
                                    }
                                    inside = true
                                    val success = setDnd(true)
                                    dndOn = if (success) true else isDndCurrentlyOn()
                                    message = if (success) "Demo beacon detected. DND enabled." else "DND permission is not active."
                                } else {
                                    message = "Grant DND access first."
                                }
                            }, modifier = Modifier.weight(1f)
                        ) { Text("Simulate Enter") }
                        OutlinedButton(
                            onClick = {
                                if (hasDndAccess()) {
                                    if (inside) {
                                        setDndFilter(previousDndFilter)
                                        inside = false
                                    }
                                    dndOn = isDndCurrentlyOn()
                                    message = "Demo beacon lost. Previous DND state restored."
                                } else {
                                    message = "Grant DND access first."
                                }
                            }, modifier = Modifier.weight(1f)
                        ) { Text("Simulate Leave") }
                    }

                    Text("Hardware mode will replace these two buttons with BLE beacon detection.", style = MaterialTheme.typography.bodySmall)
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
