package com.nomlab.bleed

import android.Manifest
import android.app.ActivityManager
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.bluetooth.le.AdvertiseSettings
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
    private var txPowerLevel by mutableStateOf(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM) // デフォルト: MEDIUM
    private var autoStartEnabled by mutableStateOf(false)

    // TX Power設定のデータクラス
    data class TxPowerOption(
        val level: Int,
        val displayName: String,
        val dbmValue: Int
    )

    // TX Powerの選択肢
    private val txPowerOptions = listOf(
        TxPowerOption(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW, "Ultra Low (-21dBm)", -21),
        TxPowerOption(AdvertiseSettings.ADVERTISE_TX_POWER_LOW, "Low (-12dBm)", -12),
        TxPowerOption(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM, "Medium (-7dBm)", -7),
        TxPowerOption(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH, "High (1dBm)", 1)
    )

    // サービス停止を受信するBroadcastReceiver
    private val serviceStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BeaconTransmitterService.BROADCAST_SERVICE_STOPPED) {
                isServiceRunning = false
                Toast.makeText(this@MainActivity, "サービスが停止されました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // BroadcastReceiverの登録を解除
        try {
            unregisterReceiver(serviceStoppedReceiver)
        } catch (e: IllegalArgumentException) {
            // レシーバーが既に登録解除されている場合
        }
    }

    // 権限リクエスト用のランチャー
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "iBeacon送信には位置情報とBluetooth権限が必要です", Toast.LENGTH_LONG).show()
        } else {
            startBeaconService()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSettings()
        isServiceRunning = isServiceActuallyRunning()

        // BroadcastReceiverを登録
        val filter = IntentFilter(BeaconTransmitterService.BROADCAST_SERVICE_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceStoppedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serviceStoppedReceiver, filter)
        }

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bleed",
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
                        textStyle = MaterialTheme.typography.bodyMedium,
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

                    // TX Power選択（プルダウン）
                    var expanded by remember { mutableStateOf(false) }
                    val selectedOption = txPowerOptions.find { it.level == txPowerLevel }
                        ?: txPowerOptions[2] // デフォルトでMedium

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded && !isServiceRunning }
                    ) {
                        OutlinedTextField(
                            value = selectedOption.displayName,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("TX Power") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            enabled = !isServiceRunning,
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            txPowerOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        txPowerLevel = option.level
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 自動起動設定
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自動起動",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "デバイス起動時にサービスを自動的に開始します",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoStartEnabled,
                            onCheckedChange = {
                                autoStartEnabled = it
                                saveAutoStartSetting(it)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 送信開始/停止ボタン
            Button(
                onClick = {
                    if (isServiceRunning) {
                        stopBeaconService()
                    } else {
                        if (isSettingsValid() && isBluetoothEnabled()) {
                            saveSettings()
                            if (missingPermissions().isNotEmpty()) {
                                requestPermissionLauncher.launch(missingPermissions())
                            } else {
                                startBeaconService()
                            }
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

    private fun isSettingsValid(): Boolean {
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

        return true
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("beacon_settings", Context.MODE_PRIVATE)
        uuid = prefs.getString("uuid", "12345678-1234-5678-9012-123456789abc") ?: "12345678-1234-5678-9012-123456789abc"
        major = prefs.getString("major", "1") ?: "1"
        minor = prefs.getString("minor", "1") ?: "1"
        txPowerLevel = prefs.getInt("tx_power_level", AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)

        val statePrefs = getSharedPreferences("beacon_service_state", Context.MODE_PRIVATE)
        autoStartEnabled = statePrefs.getBoolean("auto_start_enabled", false)
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("beacon_settings", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString("uuid", uuid)
            putString("major", major)
            putString("minor", minor)
            putInt("tx_power_level", txPowerLevel)
            apply()
        }
    }

    private fun saveAutoStartSetting(enabled: Boolean) {
        val prefs = getSharedPreferences("beacon_service_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_start_enabled", enabled).apply()
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "このデバイスはBluetoothをサポートしていません", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetoothが無効になっています", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Android 13以降では通知権限が必要
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        return permissions.toTypedArray()
    }

    private fun missingPermissions(): Array<String> {
        return requiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    private fun startBeaconService() {
        val serviceIntent = Intent(this, BeaconTransmitterService::class.java)
        startForegroundService(serviceIntent)
        isServiceRunning = true
    }

    private fun stopBeaconService() {
        val serviceIntent = Intent(this, BeaconTransmitterService::class.java)
        stopService(serviceIntent)
        isServiceRunning = false
    }

    /**
     * サービスが実際に動作しているかを確認する
     */
    @Suppress("DEPRECATION")
    private fun isServiceActuallyRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        return try {
            // 実行中のサービス一覧を取得
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)

            // BeaconTransmitterServiceが実行中かチェック
            runningServices.any { serviceInfo ->
                serviceInfo.service.className == BeaconTransmitterService::class.java.name
            }
        } catch (e: Exception) {
            // API 30以降では制限があるため、SharedPreferencesで状態を管理する代替案
            val prefs = getSharedPreferences("beacon_service_state", Context.MODE_PRIVATE)
            prefs.getBoolean("is_running", false)
        }
    }
}
