package com.duende.safegrab

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Preferencias NO sensibles (alias, tema, colores, bloqueo). Nunca contraseñas ni cookies. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var alias by mutableStateOf(sp.getString("alias", "") ?: "")
        private set
    var themeMode by mutableStateOf(sp.getInt("theme", 0))   // 0 automático, 1 claro, 2 oscuro
        private set
    var bgColor by mutableStateOf(sp.getInt("c_bg", 0))      // 0 = color por defecto
        private set
    var barColor by mutableStateOf(sp.getInt("c_bar", 0))
        private set
    var btnColor by mutableStateOf(sp.getInt("c_btn", 0))
        private set
    var lockEnabled by mutableStateOf(sp.getBoolean("lock", false))
        private set
    var customBgPath by mutableStateOf(sp.getString("bg_path", null))
        private set

    fun updateAlias(v: String) {
        val clean = v.trim().take(30)
        alias = clean
        sp.edit().putString("alias", clean).apply()
    }

    fun setTheme(v: Int) {
        themeMode = v
        sp.edit().putInt("theme", v).apply()
    }

    fun setColors(bg: Int, bar: Int, btn: Int) {
        bgColor = bg
        barColor = bar
        btnColor = btn
        sp.edit().putInt("c_bg", bg).putInt("c_bar", bar).putInt("c_btn", btn).apply()
    }

    fun resetColors() = setColors(0, 0, 0)

    fun setLock(v: Boolean) {
        lockEnabled = v
        sp.edit().putBoolean("lock", v).apply()
    }

    fun setCustomBg(path: String?) {
        customBgPath = path
        sp.edit().putString("bg_path", path).apply()
    }
}
