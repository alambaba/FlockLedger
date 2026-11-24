package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "flock_ledger_prefs")

data class UserPreferences(
    val language: AppLanguage = AppLanguage.SINDHI,
    val darkMode: Boolean = false,
    val preferCaretakerView: Boolean = false,
    val selectedFarmId: String = "",
    val googleSheetWebhookUrl: String = "",
    val siaEndpointUrl: String = "",
    val siaBucket: String = "flockledger",
    val siaAuthToken: String = "",
    val entriesSinceBackup: Int = 0,
    val lastBackupTimestampMillis: Long = 0L,
    val lastBackupSummary: String = "",
)

class AppPreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val PREFER_CARETAKER = booleanPreferencesKey("prefer_caretaker_view")
        val SELECTED_FARM_ID = stringPreferencesKey("selected_farm_id")
        val GOOGLE_SHEET_URL = stringPreferencesKey("google_sheet_webhook_url")
        val SIA_ENDPOINT_URL = stringPreferencesKey("sia_endpoint_url")
        val SIA_BUCKET = stringPreferencesKey("sia_bucket")
        val SIA_TOKEN = stringPreferencesKey("sia_auth_token")
        val ENTRIES_SINCE_BACKUP = intPreferencesKey("entries_since_backup")
        val LAST_BACKUP_MILLIS = longPreferencesKey("last_backup_millis")
        val LAST_BACKUP_SUMMARY = stringPreferencesKey("last_backup_summary")
    }

    val preferencesFlow: Flow<UserPreferences> = context.appDataStore.data.map { prefs ->
        val langCode = prefs[Keys.LANGUAGE] ?: AppLanguage.SINDHI.code
        UserPreferences(
            language = if (langCode == AppLanguage.ENGLISH.code) AppLanguage.ENGLISH else AppLanguage.SINDHI,
            darkMode = prefs[Keys.DARK_MODE] ?: false,
            preferCaretakerView = prefs[Keys.PREFER_CARETAKER] ?: false,
            selectedFarmId = prefs[Keys.SELECTED_FARM_ID] ?: "",
            googleSheetWebhookUrl = prefs[Keys.GOOGLE_SHEET_URL] ?: "",
            siaEndpointUrl = prefs[Keys.SIA_ENDPOINT_URL] ?: "",
            siaBucket = prefs[Keys.SIA_BUCKET] ?: "flockledger",
            siaAuthToken = prefs[Keys.SIA_TOKEN] ?: "",
            entriesSinceBackup = prefs[Keys.ENTRIES_SINCE_BACKUP] ?: 0,
            lastBackupTimestampMillis = prefs[Keys.LAST_BACKUP_MILLIS] ?: 0L,
            lastBackupSummary = prefs[Keys.LAST_BACKUP_SUMMARY] ?: "",
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.appDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setPreferCaretakerView(preferCaretaker: Boolean) {
        context.appDataStore.edit { it[Keys.PREFER_CARETAKER] = preferCaretaker }
    }

    suspend fun setSelectedFarmId(farmId: String) {
        context.appDataStore.edit { it[Keys.SELECTED_FARM_ID] = farmId }
    }

    suspend fun saveBackupEndpoints(
        googleSheetUrl: String,
        siaEndpointUrl: String,
        siaBucket: String,
        siaToken: String,
    ) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.GOOGLE_SHEET_URL] = googleSheetUrl.trim()
            prefs[Keys.SIA_ENDPOINT_URL] = siaEndpointUrl.trim()
            prefs[Keys.SIA_BUCKET] = siaBucket.trim().ifEmpty { "flockledger" }
            prefs[Keys.SIA_TOKEN] = siaToken.trim()
        }
    }

    /**
     * Increments entry counter and returns the new count.
     */
    suspend fun incrementEntryCounter(): Int {
        var newCount = 0
        context.appDataStore.edit { prefs ->
            val current = prefs[Keys.ENTRIES_SINCE_BACKUP] ?: 0
            newCount = current + 1
            prefs[Keys.ENTRIES_SINCE_BACKUP] = newCount
        }
        return newCount
    }

    suspend fun recordBackupResult(timestampMillis: Long, summary: String, resetCounter: Boolean) {
        context.appDataStore.edit { prefs ->
            if (resetCounter) {
                prefs[Keys.ENTRIES_SINCE_BACKUP] = 0
            }
            prefs[Keys.LAST_BACKUP_MILLIS] = timestampMillis
            prefs[Keys.LAST_BACKUP_SUMMARY] = summary
        }
    }
}
