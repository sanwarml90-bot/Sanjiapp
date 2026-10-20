package com.sanji.bomberpro.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore("sanji_theme")

class ThemeManager(private val context: Context) {

    private val KEY_THEME = stringPreferencesKey("current_theme")

    val defaultTheme: AppTheme = AppTheme.NEON_CIPHER

    val currentTheme: Flow<AppTheme> = context.themeDataStore.data.map { prefs ->
        val savedId = prefs[KEY_THEME] ?: defaultTheme.id
        AppTheme.entries.firstOrNull { it.id == savedId } ?: defaultTheme
    }

    suspend fun setTheme(theme: AppTheme) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_THEME] = theme.id
        }
    }

    val allThemes: List<AppTheme> = AppTheme.entries.toList()
}