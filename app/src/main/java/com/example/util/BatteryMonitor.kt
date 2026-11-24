package com.example.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager

data class DeviceBatteryStatus(
    val levelPercent: Int,
    val isPowerSaveMode: Boolean,
    val isLowBattery: Boolean
)

/**
 * Reads device battery percentage and OS power-save status to help Battery Saver mode
 * throttle visualizer animations and pause background voice capture when battery is low.
 */
object BatteryMonitor {
    const val LOW_BATTERY_THRESHOLD_PERCENT = 20

    fun getBatteryStatus(context: Context): DeviceBatteryStatus {
        val appCtx = context.applicationContext
        val powerManager = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isOsPowerSave = runCatching { powerManager?.isPowerSaveMode ?: false }.getOrDefault(false)

        val batteryLevel = readBatteryPercent(appCtx)
        val low = batteryLevel <= LOW_BATTERY_THRESHOLD_PERCENT || isOsPowerSave
        return DeviceBatteryStatus(
            levelPercent = batteryLevel,
            isPowerSaveMode = isOsPowerSave,
            isLowBattery = low
        )
    }

    fun shouldPauseBackgroundListening(context: Context): Boolean {
        if (!AppPreferencesManager.batterySaverMode.value) return false
        return getBatteryStatus(context).isLowBattery
    }

    private fun readBatteryPercent(context: Context): Int {
        val intentPct = try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100) / scale else -1
        } catch (_: Exception) {
            -1
        }
        if (intentPct in 0..100) return intentPct

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val cap = runCatching { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1 }.getOrDefault(-1)
        return if (cap in 0..100) cap else 100
    }
}
