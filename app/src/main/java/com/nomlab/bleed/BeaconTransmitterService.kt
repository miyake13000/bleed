package com.nomlab.bleed

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.BeaconTransmitter
import android.bluetooth.le.AdvertiseSettings;
import java.util.*

class BeaconTransmitterService : Service() {
    companion object {
        private const val TAG = "BeaconTransmitterService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "beacon_service_channel"
    }

    private var beaconTransmitter: BeaconTransmitter? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // フォアグラウンド通知を開始
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // iBeacon送信を開始
        startBeaconTransmission()

        return START_STICKY // サービスが終了してもシステムが再起動する
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopBeaconTransmission()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "iBeacon送信サービス",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "iBeaconパケットを送信しています"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("iBeacon送信中")
        .setContentText("バックグラウンドでiBeaconを送信しています")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun startBeaconTransmission() {
        try {
            // Bluetoothアダプターの確認
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Log.e(TAG, "Bluetooth is not available or not enabled")
                return
            }

            // SharedPreferencesから設定を読み込み
            val prefs = getSharedPreferences("beacon_settings", Context.MODE_PRIVATE)
            val uuidString = prefs.getString("uuid", "12345678-1234-5678-9012-123456789abc")!!
            val major = prefs.getString("major", "1")!!.toInt()
            val minor = prefs.getString("minor", "1")!!.toInt()
            val txPower = prefs.getString("tx_power", "-59")!!.toInt()

            Log.d(TAG, "Starting beacon with UUID: $uuidString, Major: $major, Minor: $minor, TX Power: $txPower")

            // iBeaconの設定
            val beacon = Beacon.Builder()
                .setId1(uuidString) // UUID
                .setId2(major.toString()) // Major
                .setId3(minor.toString()) // Minor
                .setManufacturer(0x004c) // Apple Inc.
                .setTxPower(txPower)
                .setDataFields(listOf(0L))
                .build()

            // BeaconParserの設定（iBeacon形式）
            val beaconParser = BeaconParser()
                .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")

            // BeaconTransmitterの初期化
            beaconTransmitter = BeaconTransmitter(applicationContext, beaconParser)

            // 送信間隔の設定（デフォルト: 100ms間隔）
            beaconTransmitter?.advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
            beaconTransmitter?.advertiseTxPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM

            // 送信開始
            try {
                beaconTransmitter?.startAdvertising(beacon)
                Log.i(TAG, "iBeacon transmission started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start iBeacon transmission")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting beacon transmission", e)
        }
    }

    private fun stopBeaconTransmission() {
        beaconTransmitter?.stopAdvertising()
        Log.i(TAG, "iBeacon transmission stopped")
    }
}