package com.memorycurator.app.data.local

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    var isDeepAnalysisEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEEP_ANALYSIS, false)
        set(value) = prefs.edit().putBoolean(KEY_DEEP_ANALYSIS, value).apply()

    var isPhysicalStorageEnabled: Boolean
        get() = prefs.getBoolean(KEY_PHYSICAL_STORAGE, false)
        set(value) = prefs.edit().putBoolean(KEY_PHYSICAL_STORAGE, value).apply()

    companion object {
        private const val KEY_DEEP_ANALYSIS = "deep_analysis_enabled"
        private const val KEY_PHYSICAL_STORAGE = "physical_storage_enabled"
    }
}
