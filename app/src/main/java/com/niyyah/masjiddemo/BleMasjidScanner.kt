package com.niyyah.masjiddemo

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.UUID

class BleMasjidScanner(
    context: Context,
    private val onBeaconDetected: (rssi: Int) -> Unit,
    private val onBeaconLost: () -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        const val DEVICE_NAME = "NIYYAH-MASJID-TEST"
        val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-1234567890AB")
        private const val LOST_TIMEOUT_MS = 8_000L
    }
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    private var lastSeenAt = 0L
    private val lostRunnable = Runnable {
        if (scanning && System.currentTimeMillis() - lastSeenAt >= LOST_TIMEOUT_MS) onBeaconLost()
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (isTarget(result)) {
                lastSeenAt = System.currentTimeMillis()
                handler.removeCallbacks(lostRunnable)
                handler.postDelayed(lostRunnable, LOST_TIMEOUT_MS)
                onBeaconDetected(result.rssi)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            handler.removeCallbacks(lostRunnable)
            onError("BLE scan failed (error $errorCode).")
        }
    }
    fun hasScanPermission(): Boolean = if (android.os.Build.VERSION.SDK_INT >= 31) {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    fun hasConnectPermission(): Boolean = if (android.os.Build.VERSION.SDK_INT >= 31) {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else true
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
    fun start() {
        if (scanning) return
        if (!hasScanPermission()) { onError("Nearby-device/BLE scan permission is not granted."); return }
        if (!hasConnectPermission()) { onError("Nearby-device/Bluetooth connect permission is not granted."); return }
        if (!isBluetoothEnabled()) { onError("Bluetooth is turned off."); return }
        val bleScanner = scanner ?: run { onError("BLE scanner is unavailable on this device."); return }
        try {
            scanning = true
            lastSeenAt = 0L
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            bleScanner.startScan(null, settings, scanCallback)
        } catch (_: SecurityException) {
            scanning = false
            onError("Bluetooth permission was revoked.")
        }
    }
    fun stop() {
        if (!scanning) return
        try { scanner?.stopScan(scanCallback) } catch (_: SecurityException) {}
        scanning = false
        handler.removeCallbacks(lostRunnable)
    }
    private fun isTarget(result: ScanResult): Boolean {
        val record = result.scanRecord
        val hasService = record?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
        val name = record?.deviceName ?: runCatching { result.device.name }.getOrNull()
        return hasService || name == DEVICE_NAME
    }
}
