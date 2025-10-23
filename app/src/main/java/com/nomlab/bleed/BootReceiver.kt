package com.nomlab.bleed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            // 自動起動が有効かを確認
            val prefs = context.getSharedPreferences("beacon_service_state", Context.MODE_PRIVATE)
            val autoStartEnabled = prefs.getBoolean("auto_start_enabled", false)
            if (autoStartEnabled) {
                Log.d(TAG, "Starting BeaconTransmitterService")

                val serviceIntent = Intent(context, BeaconTransmitterService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }

                    Log.d(TAG, "BeaconTransmitterService started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start service after boot", e)
                }
            } else {
                Log.d(TAG, "Auto-start not enabled")
            }
        }
    }
}
