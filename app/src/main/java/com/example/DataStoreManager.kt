package com.example

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bepresent_preferences")

class DataStoreManager(private val context: Context) {

    companion object {
        val POINTS_BALANCE = intPreferencesKey("points_balance")
        val STREAK_DAYS = intPreferencesKey("streak_days")
        val FOCUS_SCORE = intPreferencesKey("focus_score")
        val SESSIONS_TODAY_COUNT = intPreferencesKey("sessions_today_count")
        val SCREEN_USED_MINUTES = intPreferencesKey("screen_used_minutes")
        val IS_ZEN_MASTER_UNLOCKED = booleanPreferencesKey("is_zen_master_unlocked")
        val IS_GOLDEN_THEME_UNLOCKED = booleanPreferencesKey("is_golden_theme_unlocked")
        val IS_CELESTIAL_GLOW_UNLOCKED = booleanPreferencesKey("is_celestial_glow_unlocked")
        val LAST_SESSION_DATE = stringPreferencesKey("last_session_date")
        val LAST_OPEN_DATE = stringPreferencesKey("last_open_date")
    }

    val pointsFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences -> preferences[POINTS_BALANCE] ?: 1240 }

    val streakFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences -> preferences[STREAK_DAYS] ?: 8 }

    val focusScoreFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences -> preferences[FOCUS_SCORE] ?: 73 }

    val sessionsTodayFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences -> preferences[SESSIONS_TODAY_COUNT] ?: 3 }

    val screenUsedMinutesFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences -> preferences[SCREEN_USED_MINUTES] ?: 107 }

    val isZenMasterUnlockedFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences -> preferences[IS_ZEN_MASTER_UNLOCKED] ?: false }

    val isGoldenThemeUnlockedFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences -> preferences[IS_GOLDEN_THEME_UNLOCKED] ?: false }

    val isCelestialGlowUnlockedFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences -> preferences[IS_CELESTIAL_GLOW_UNLOCKED] ?: false }

    val lastSessionDateFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences -> preferences[LAST_SESSION_DATE] }

    val lastOpenDateFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences -> preferences[LAST_OPEN_DATE] }

    suspend fun savePoints(points: Int) {
        context.dataStore.edit { preferences -> preferences[POINTS_BALANCE] = points }
    }

    suspend fun saveStreak(streak: Int) {
        context.dataStore.edit { preferences -> preferences[STREAK_DAYS] = streak }
    }

    suspend fun saveFocusScore(score: Int) {
        context.dataStore.edit { preferences -> preferences[FOCUS_SCORE] = score }
    }

    suspend fun saveSessionsToday(count: Int) {
        context.dataStore.edit { preferences -> preferences[SESSIONS_TODAY_COUNT] = count }
    }

    suspend fun saveScreenUsedMinutes(minutes: Int) {
        context.dataStore.edit { preferences -> preferences[SCREEN_USED_MINUTES] = minutes }
    }

    suspend fun saveZenMasterUnlocked(unlocked: Boolean) {
        context.dataStore.edit { preferences -> preferences[IS_ZEN_MASTER_UNLOCKED] = unlocked }
    }

    suspend fun saveGoldenThemeUnlocked(unlocked: Boolean) {
        context.dataStore.edit { preferences -> preferences[IS_GOLDEN_THEME_UNLOCKED] = unlocked }
    }

    suspend fun saveCelestialGlowUnlocked(unlocked: Boolean) {
        context.dataStore.edit { preferences -> preferences[IS_CELESTIAL_GLOW_UNLOCKED] = unlocked }
    }

    suspend fun saveLastSessionDate(dateStr: String) {
        context.dataStore.edit { preferences -> preferences[LAST_SESSION_DATE] = dateStr }
    }

    suspend fun saveLastOpenDate(dateStr: String) {
        context.dataStore.edit { preferences -> preferences[LAST_OPEN_DATE] = dateStr }
    }
}
