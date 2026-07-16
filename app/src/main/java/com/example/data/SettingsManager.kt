package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("geostamp_settings", Context.MODE_PRIVATE)

    companion object {
        const val KEY_THEME = "theme_mode" // "SYSTEM", "LIGHT", "DARK"
        const val KEY_GPS_ACCURACY = "gps_accuracy" // "HIGH", "BALANCED", "LOW"
        const val KEY_DEFAULT_TEMPLATE = "default_template_id"
        const val KEY_DEFAULT_CAMERA = "default_camera_type" // "BACK", "FRONT"
        const val KEY_AUTOSAVE = "autosave_photos"
        const val KEY_WEATHER_API_KEY = "weather_api_key"
        const val KEY_COMPANY_LOGO_PATH = "company_logo_path"
    }

    var themeMode: String
        get() = prefs.getString(KEY_THEME, "DARK") ?: "DARK" // Default to DARK mode for outdoor screen contrast!
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var gpsAccuracy: String
        get() = prefs.getString(KEY_GPS_ACCURACY, "HIGH") ?: "HIGH"
        set(value) = prefs.edit().putString(KEY_GPS_ACCURACY, value).apply()

    var defaultTemplateId: Int
        get() = prefs.getInt(KEY_DEFAULT_TEMPLATE, 3) // Default to Template 3 (Professional)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_TEMPLATE, value).apply()

    var defaultCamera: String
        get() = prefs.getString(KEY_DEFAULT_CAMERA, "BACK") ?: "BACK"
        set(value) = prefs.edit().putString(KEY_DEFAULT_CAMERA, value).apply()

    var autoSave: Boolean
        get() = prefs.getBoolean(KEY_AUTOSAVE, false) // Prompt before save by default allows editing first!
        set(value) = prefs.edit().putBoolean(KEY_AUTOSAVE, value).apply()

    var weatherApiKey: String
        get() = prefs.getString(KEY_WEATHER_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEATHER_API_KEY, value).apply()

    var companyLogoPath: String?
        get() = prefs.getString(KEY_COMPANY_LOGO_PATH, null)
        set(value) = prefs.edit().putString(KEY_COMPANY_LOGO_PATH, value).apply()
}
