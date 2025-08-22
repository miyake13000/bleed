package com.nomlab.bleed

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var isServiceRunning by mutableStateOf(false)

    // iBeacon設定の状態
    private var uuid by mutableStateOf("12345678-1234-5678-9012-123456789abc")
    private var major by mutableStateOf("1")
    private var minor by mutableStateOf("1")
    private var txPower by mutableStateOf("-59")

    // 権限リクエスト用のランチャー
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            checkBluetoothAndStartService()
        } else {
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

        // 保存された設定を読み込み
        loadSettings()

        // サービスの実際の動作状態を確認
        isServiceRunning = isServiceActuallyRunning()

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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "iBeacon送信アプリ",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 送信状態表示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isServiceRunning) "送信中..." else "停止中",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isServiceRunning)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 設定セクション
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "iBeacon設定",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // UUID入力
                    OutlinedTextField(
                        value = uuid,
                        onValueChange = { uuid = it },
                        label = { Text("UUID") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isServiceRunning,
                        placeholder = { Text("12345678-1234-5678-9012-123456789abc") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Major/Minorを横並びに
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = major,
                            onValueChange = { major = it },
                            label = { Text("Major") },
                            modifier = Modifier.weight(1f),
                            enabled = !isServiceRunning,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("1") }
                        )

                        OutlinedTextField(
                            value = minor,
                            onValueChange = { minor = it },
                            label = { Text("Minor") },
                            modifier = Modifier.weight(1f),
                            enabled = !isServiceRunning,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("1") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // TX Power入力
                    OutlinedTextField(
                        value = txPower,
                        onValueChange = { txPower = it },
                        label = { Text("TX Power (dBm)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isServiceRunning,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("-59") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 送信開始/停止ボタン
            Button(
                onClick = {
                    if (isServiceRunning) {
                        stopBeaconService()
                    } else {
                        if (validateSettings()) {
                            saveSettings()
                            checkPermissionsAndStart()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isServiceRunning) "送信停止" else "送信開始")
            }

            if (!isServiceRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "※ 送信中は設定変更できません",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    private fun validateSettings(): Boolean {
        // UUID形式の簡易チェック
        val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        if (!uuid.matches(uuidPattern)) {
            Toast.makeText(this, "UUIDの形式が正しくありません", Toast.LENGTH_SHORT).show()
            return false
        }

        // Major/Minorの数値チェック（0-65535）
        try {
            val majorInt = major.toInt()
            val minorInt = minor.toInt()
            if (majorInt < 0 || majorInt > 65535 || minorInt < 0 || minorInt > 65535) {
                Toast.makeText(this, "Major/Minorは0-65535の範囲で入力してください", Toast.LENGTH_SHORT).show()
                return false
            }
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Major/Minorは数値で入力してください", Toast.LENGTH_SHORT).show()
            return false
        }

        // TX Powerの数値チェック（-100 to 20程度）
        try {
            val txPowerInt = txPower.toInt()
            if (txPowerInt < -100 || txPowerInt > 20) {
                Toast.makeText(this, "TX Powerは-100から20の範囲で入力してください", Toast.LENGTH_SHORT).show()
                return false
            }
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "TX Powerは数値で入力してください", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("beacon_settings", Context.MODE_PRIVATE)
        uuid = prefs.getString("uuid", "12345678-1234-5678-9012-123456789abc") ?: "12345678-1234-5678-9012-123456789abc"
        major = prefs.getString("major", "1") ?: "1"
        minor = prefs.getString("minor", "1") ?: "1"
        txPower = prefs.getString("tx_power", "-59") ?: "-59"
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("beacon_settings", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString("uuid", uuid)
            putString("major", major)
            putString("minor", minor)
            putString("tx_power", txPower)
            apply()
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

        // サービス状態をSharedPreferencesに保存
        val prefs = getSharedPreferences("beacon_service_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_running", true).apply()

        Toast.makeText(this, "iBeacon送信を開始しました", Toast.LENGTH_SHORT).show()
    }

    private fun stopBeaconService() {
        val serviceIntent = Intent(this, BeaconTransmitterService::class.java)
        stopService(serviceIntent)
        isServiceRunning = false

        // サービス状態をSharedPreferencesに保存
        val prefs = getSharedPreferences("beacon_service_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_running", false).apply()

        Toast.makeText(this, "iBeacon送信を停止しました", Toast.LENGTH_SHORT).show()
    }

    // サービスが実際に動作しているかを確認する
    private fun isServiceActuallyRunning(): Boolean {
        val prefs = getSharedPreferences("beacon_service_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("is_running", false)
    }
}
