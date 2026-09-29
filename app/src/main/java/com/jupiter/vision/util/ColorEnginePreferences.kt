package com.jupiter.vision.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ColorEnginePreferences {
    private const val PREFS_NAME = "jupitervision_settings"
    private const val KEY_COLOR_ENGINE = "pref_color_engine_enabled"

    private val _colorEngineFlow = MutableStateFlow(true)
    val colorEngineFlow: StateFlow<Boolean> = _colorEngineFlow.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs?.getBoolean(KEY_COLOR_ENGINE, true) ?: true
            _colorEngineFlow.value = isEnabled
        }
    }

    fun isColorEngineEnabled(context: Context): Boolean {
        init(context)
        return _colorEngineFlow.value
    }

    fun setColorEngineEnabled(context: Context, enabled: Boolean) {
        init(context)
        prefs?.edit()?.putBoolean(KEY_COLOR_ENGINE, enabled)?.apply()
        _colorEngineFlow.value = enabled
    }

    fun toggleColorEngine(context: Context): Boolean {
        init(context)
        val newState = !_colorEngineFlow.value
        setColorEngineEnabled(context, newState)
        return newState
    }
}
