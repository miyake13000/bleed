package com.nomlab.bleed

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(false)

    // 権限リクエスト用のランチャー
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "Permission result: $permissions")
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // すべての権限が許可された場合
            checkBluetoothAndStartService()
        } else {
            // 権限が拒否された場合
            Toast.makeText(this, "iBeacon送信には位置情報とBluetooth権限が必要です", Toast.LENGTH_LONG).show()
        }
    }

    // Bluetooth有効化リクエスト用のランチャー
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startBeaconService()
        } else {
            Toast.makeText(this, "Bluetoothを有効にしてください", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    @Composable
    fun MainScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "iBeacon送信アプリ",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = if (isServiceRunning) "送信中..." else "停止中",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isServiceRunning)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (isServiceRunning) {
                        stopBeaconService()
                    } else {
                        checkPermissionsAndStart()
                    }
                }
            ) {
                Text(if (isServiceRunning) "送信停止" else "送信開始")
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val requiredPermissions = mutableListOf<String>().apply {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            checkBluetoothAndStartService()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun checkBluetoothAndStartService() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "このデバイスはBluetoothをサポートしていません", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            // Bluetoothが無効な場合は有効化をリクエスト
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            startBeaconService()
        }
    }

    private fun startBeaconService() {
        val serviceIntent = Intent(this, BeaconTransmitterService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        isServiceRunning = true
        Toast.makeText(this, "iBeacon送信を開始しました", Toast.LENGTH_SHORT).show()
    }

    private fun stopBeaconService() {
        val serviceIntent = Intent(this, BeaconTransmitterService::class.java)
        stopService(serviceIntent)
        isServiceRunning = false
        Toast.makeText(this, "iBeacon送信を停止しました", Toast.LENGTH_SHORT).show()
    }
}