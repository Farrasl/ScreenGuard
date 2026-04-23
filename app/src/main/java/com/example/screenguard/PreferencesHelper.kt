package com.example.screenguard

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferencesHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ScreenGuardPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        const val LAST_SCREENSHOT_FILENAME = "last_shoulder_surfing_proof.jpg"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
        private const val KEY_DETECTION_LOG = "detection_log_v2"

        // Konstanta baru untuk Mode Pencegahan
        private const val KEY_PREVENTION_MODE = "prevention_mode"
        const val MODE_LOCK = "mode_lock"
        const val MODE_NOTIFICATION = "mode_notification"
    }

    fun setProtectionEnabled(isEnabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, isEnabled).apply()
    }

    fun isProtectionEnabled(): Boolean = prefs.getBoolean(KEY_PROTECTION_ENABLED, false)

    // Fungsi baru untuk mengatur mode
    fun setPreventionMode(mode: String) {
        prefs.edit().putString(KEY_PREVENTION_MODE, mode).apply()
    }

    // Fungsi baru untuk mengambil mode (Default: Lock)
    fun getPreventionMode(): String {
        return prefs.getString(KEY_PREVENTION_MODE, MODE_LOCK) ?: MODE_LOCK
    }

    fun addDetectionLog(logMap: Map<String, Any>) {
        val currentLogs = getDetectionLogsList()
        currentLogs.add(logMap)
        val json = gson.toJson(currentLogs)
        prefs.edit().putString(KEY_DETECTION_LOG, json).apply()
    }

    fun getDetectionLogsList(): MutableList<Map<String, Any>> {
        val json = prefs.getString(KEY_DETECTION_LOG, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<Map<String, Any>>>() {}.type
        return gson.fromJson(json, type)
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_DETECTION_LOG).apply()
    }

    fun removeLogAt(index: Int) {
        val currentLogs = getDetectionLogsList()
        if (index in 0 until currentLogs.size) {
            currentLogs.removeAt(index)
            val json = gson.toJson(currentLogs)
            prefs.edit().putString(KEY_DETECTION_LOG, json).apply()
        }
    }
}