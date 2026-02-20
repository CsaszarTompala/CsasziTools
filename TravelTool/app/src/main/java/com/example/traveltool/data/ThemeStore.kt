package com.example.traveltool.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the user's chosen theme. Uses plain SharedPreferences (no encryption needed).
 */
object ThemeStore {
    private const val PREFS = "theme_prefs"
    private const val KEY_THEME = "selected_theme"

    enum class ThemeChoice { DRACULA, DARK, BRIGHT }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getTheme(context: Context): ThemeChoice {
        val saved = prefs(context).getString(KEY_THEME, ThemeChoice.DRACULA.name)
        return try { ThemeChoice.valueOf(saved!!) } catch (_: Exception) { ThemeChoice.DRACULA }
    }

    fun setTheme(context: Context, theme: ThemeChoice) {
        prefs(context).edit().putString(KEY_THEME, theme.name).apply()
    }
}
