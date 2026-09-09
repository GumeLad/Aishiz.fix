package com.example.aishiz

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoScroll: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCROLL, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SCROLL, value).apply()

    var streamIntervalMs: Long
        get() = prefs.getLong(KEY_STREAM_INTERVAL, 50L).coerceIn(33L, 120L)
        set(value) = prefs.edit()
            .putLong(KEY_STREAM_INTERVAL, value.coerceIn(33L, 120L))
            .apply()

    var keepScreenAwake: Boolean
        get() = prefs.getBoolean(KEY_KEEP_AWAKE, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_AWAKE, value).apply()

    companion object {
        private const val PREFS_NAME = "aishiz_ui_settings"
        private const val KEY_AUTO_SCROLL = "auto_scroll"
        private const val KEY_STREAM_INTERVAL = "stream_interval"
        private const val KEY_KEEP_AWAKE = "keep_awake"
    }
}
