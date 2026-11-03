package com.equinoxtrack.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.equinoxtrack.app.domain.AccentPalette
import com.equinoxtrack.app.domain.AppSettings
import com.equinoxtrack.app.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "equinox_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val accent = stringPreferencesKey("accent")
        val currency = stringPreferencesKey("base_currency")
        val biometric = booleanPreferencesKey("biometric_lock")
        val reminders = booleanPreferencesKey("bill_reminders")
        val protectGoals = booleanPreferencesKey("protect_goals")
        val buffer = longPreferencesKey("buffer_minor")
        val startDay = intPreferencesKey("period_start_day")
        val hide = booleanPreferencesKey("hide_balances")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val ratesUpdated = longPreferencesKey("rates_updated_at")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            accent = p[Keys.accent]?.let { runCatching { AccentPalette.valueOf(it) }.getOrNull() } ?: AccentPalette.VIOLET,
            baseCurrency = p[Keys.currency] ?: "PHP",
            biometricLock = p[Keys.biometric] ?: false,
            billReminders = p[Keys.reminders] ?: true,
            protectGoals = p[Keys.protectGoals] ?: true,
            bufferMinor = p[Keys.buffer] ?: 0L,
            periodStartDay = p[Keys.startDay] ?: 1,
            hideBalances = p[Keys.hide] ?: false,
            onboardingDone = p[Keys.onboarding] ?: false,
        )
    }

    val ratesUpdatedAt: Flow<Long> = context.settingsStore.data.map { it[Keys.ratesUpdated] ?: 0L }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = context.settingsStore.edit { it[Keys.theme] = mode.name }
    suspend fun setAccent(accent: AccentPalette) = context.settingsStore.edit { it[Keys.accent] = accent.name }
    suspend fun setBaseCurrency(code: String) = context.settingsStore.edit {
        it[Keys.currency] = code
        it[Keys.buffer] = 0L // the cushion was expressed in the old currency
    }
    suspend fun setBiometricLock(enabled: Boolean) = context.settingsStore.edit { it[Keys.biometric] = enabled }
    suspend fun setBillReminders(enabled: Boolean) = context.settingsStore.edit { it[Keys.reminders] = enabled }
    suspend fun setProtectGoals(enabled: Boolean) = context.settingsStore.edit { it[Keys.protectGoals] = enabled }
    suspend fun setBuffer(minor: Long) = context.settingsStore.edit { it[Keys.buffer] = minor.coerceAtLeast(0) }
    suspend fun setPeriodStartDay(day: Int) = context.settingsStore.edit { it[Keys.startDay] = day.coerceIn(1, 28) }
    suspend fun setHideBalances(hide: Boolean) = context.settingsStore.edit { it[Keys.hide] = hide }
    suspend fun setOnboardingDone() = context.settingsStore.edit { it[Keys.onboarding] = true }
    suspend fun setRatesUpdated(at: Long) = context.settingsStore.edit { it[Keys.ratesUpdated] = at }

    /** Restores every preference from a backup map (unknown keys ignored). */
    suspend fun restore(values: Map<String, String>) = context.settingsStore.edit { p ->
        values["theme_mode"]?.let { p[Keys.theme] = it }
        values["accent"]?.let { p[Keys.accent] = it }
        values["base_currency"]?.let { p[Keys.currency] = it }
        values["bill_reminders"]?.toBooleanStrictOrNull()?.let { p[Keys.reminders] = it }
        values["protect_goals"]?.toBooleanStrictOrNull()?.let { p[Keys.protectGoals] = it }
        values["buffer_minor"]?.toLongOrNull()?.let { p[Keys.buffer] = it }
        values["period_start_day"]?.toIntOrNull()?.let { p[Keys.startDay] = it.coerceIn(1, 28) }
        p[Keys.onboarding] = true
    }

    fun toBackupMap(s: AppSettings): Map<String, String> = mapOf(
        "theme_mode" to s.themeMode.name,
        "accent" to s.accent.name,
        "base_currency" to s.baseCurrency,
        "bill_reminders" to s.billReminders.toString(),
        "protect_goals" to s.protectGoals.toString(),
        "buffer_minor" to s.bufferMinor.toString(),
        "period_start_day" to s.periodStartDay.toString(),
    )
}
