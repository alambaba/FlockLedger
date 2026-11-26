package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "flock_ledger_prefs")

enum class UserRole {
    ADMIN,
    CARETAKER
}

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
    val adminPinHash: String = "",
    val adminPinSalt: String = "",
    val caretakerPinHash: String = "",
    val caretakerPinSalt: String = "",
    val lastSeenVersionCode: Long = 0L,
    val lastSeenVersionName: String = "",
    val updateSourceRepo: String = "",
    val monthlyFeedBudgetCap: Double = 0.0,
    val monthlyMedVetBudgetCap: Double = 0.0,
    val budgetAlertThresholdPercent: Int = 80,
) {
    val pinsConfigured: Boolean
        get() = adminPinHash.isNotEmpty() && caretakerPinHash.isNotEmpty()
}

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
        val ADMIN_PIN_HASH = stringPreferencesKey("admin_pin_hash")
        val ADMIN_PIN_SALT = stringPreferencesKey("admin_pin_salt")
        val CARETAKER_PIN_HASH = stringPreferencesKey("caretaker_pin_hash")
        val CARETAKER_PIN_SALT = stringPreferencesKey("caretaker_pin_salt")
        val LAST_SEEN_VERSION_CODE = longPreferencesKey("last_seen_version_code")
        val LAST_SEEN_VERSION_NAME = stringPreferencesKey("last_seen_version_name")
        val UPDATE_SOURCE_REPO = stringPreferencesKey("update_source_repo")
        val MONTHLY_FEED_BUDGET_CAP = doublePreferencesKey("monthly_feed_budget_cap")
        val MONTHLY_MED_VET_BUDGET_CAP = doublePreferencesKey("monthly_med_vet_budget_cap")
        val BUDGET_ALERT_THRESHOLD_PERCENT = intPreferencesKey("budget_alert_threshold_percent")
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
            adminPinHash = prefs[Keys.ADMIN_PIN_HASH] ?: "",
            adminPinSalt = prefs[Keys.ADMIN_PIN_SALT] ?: "",
            caretakerPinHash = prefs[Keys.CARETAKER_PIN_HASH] ?: "",
            caretakerPinSalt = prefs[Keys.CARETAKER_PIN_SALT] ?: "",
            lastSeenVersionCode = prefs[Keys.LAST_SEEN_VERSION_CODE] ?: 0L,
            lastSeenVersionName = prefs[Keys.LAST_SEEN_VERSION_NAME] ?: "",
            updateSourceRepo = prefs[Keys.UPDATE_SOURCE_REPO] ?: "",
            monthlyFeedBudgetCap = prefs[Keys.MONTHLY_FEED_BUDGET_CAP] ?: 0.0,
            monthlyMedVetBudgetCap = prefs[Keys.MONTHLY_MED_VET_BUDGET_CAP] ?: 0.0,
            budgetAlertThresholdPercent = prefs[Keys.BUDGET_ALERT_THRESHOLD_PERCENT] ?: 80,
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

    suspend fun savePins(adminPin: String, caretakerPin: String) {
        val adminSalt = generateSaltHex()
        val caretakerSalt = generateSaltHex()
        val adminHash = hashPin(adminPin.trim(), adminSalt)
        val caretakerHash = hashPin(caretakerPin.trim(), caretakerSalt)

        context.appDataStore.edit { prefs ->
            prefs[Keys.ADMIN_PIN_SALT] = adminSalt
            prefs[Keys.ADMIN_PIN_HASH] = adminHash
            prefs[Keys.CARETAKER_PIN_SALT] = caretakerSalt
            prefs[Keys.CARETAKER_PIN_HASH] = caretakerHash
        }
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

    suspend fun markVersionSeen(versionCode: Long, versionName: String) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.LAST_SEEN_VERSION_CODE] = versionCode
            prefs[Keys.LAST_SEEN_VERSION_NAME] = versionName
        }
    }

    suspend fun saveUpdateSource(repoOrUrl: String) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.UPDATE_SOURCE_REPO] = repoOrUrl.trim()
        }
    }

    suspend fun saveMonthlyBudgets(
        feedCap: Double,
        medVetCap: Double,
        alertThresholdPercent: Int = 80,
    ) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.MONTHLY_FEED_BUDGET_CAP] = feedCap.coerceAtLeast(0.0)
            prefs[Keys.MONTHLY_MED_VET_BUDGET_CAP] = medVetCap.coerceAtLeast(0.0)
            prefs[Keys.BUDGET_ALERT_THRESHOLD_PERCENT] = alertThresholdPercent.coerceIn(50, 99)
        }
    }

    companion object {
        private val random = SecureRandom()

        fun generateSaltHex(): String {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun hashPin(pin: String, saltHex: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = "$saltHex:$pin".toByteArray(Charsets.UTF_8)
            return digest.digest(input).joinToString("") { "%02x".format(it) }
        }

        fun resolveRoleForPin(pin: String, prefs: UserPreferences): UserRole? {
            val clean = pin.trim()
            if (clean.isEmpty() || !prefs.pinsConfigured) return null
            val adminCandidate = hashPin(clean, prefs.adminPinSalt)
            if (constantTimeEquals(adminCandidate, prefs.adminPinHash)) return UserRole.ADMIN
            val caretakerCandidate = hashPin(clean, prefs.caretakerPinSalt)
            if (constantTimeEquals(caretakerCandidate, prefs.caretakerPinHash)) return UserRole.CARETAKER
            return null
        }

        private fun constantTimeEquals(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            var diff = 0
            for (i in a.indices) {
                diff = diff or (a[i].code xor b[i].code)
            }
            return diff == 0
        }
    }
}
