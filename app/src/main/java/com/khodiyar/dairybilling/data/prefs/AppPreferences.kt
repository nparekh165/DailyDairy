package com.khodiyar.dairybilling.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.khodiyar.dairybilling.security.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ui_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UiPrefs(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val lastBackupAt: Long = 0,
    val lastUsername: String = "",
    val lastProductId: Long = 0,
)

/**
 * Optional parts of the app the owner can switch off for a simpler app (Settings → App features).
 * All are on by default. Stored per dairy.
 */
data class Features(
    /** Sign in with username + PIN when the app opens, and auto-lock. Off: opens straight as the owner. */
    val requireSignIn: Boolean = true,
    /** Ask for the PIN before important actions (restore, reverse payment, finalize...). Off: a plain confirmation. */
    val pinForActions: Boolean = true,
    /** Draft/finalized bills with finalize, lock, reopen and revisions. Off: bills are always live. */
    val billingSteps: Boolean = true,
    /** Staff, Audit log and advanced settings. */
    val advanced: Boolean = true,
)

/**
 * DataStore holds only UI conveniences (theme, last backup time, remembered username), the
 * per-dairy feature switches and an encrypted session token. Business data lives exclusively in Room.
 */
class AppPreferences(private val context: Context, private val cipher: SecretCipher, dairyId: String = "main") {
    private val signInKey = booleanPreferencesKey("f_sign_in_$dairyId")
    private val pinActionsKey = booleanPreferencesKey("f_pin_actions_$dairyId")
    private val billingStepsKey = booleanPreferencesKey("f_billing_steps_$dairyId")
    private val advancedKey = booleanPreferencesKey("f_advanced_$dairyId")

    val features: Flow<Features> = context.dataStore.data.map { p ->
        Features(p[signInKey] ?: true, p[pinActionsKey] ?: true, p[billingStepsKey] ?: true, p[advancedKey] ?: true)
    }

    suspend fun setFeatures(f: Features) = context.dataStore.edit {
        it[signInKey] = f.requireSignIn
        it[pinActionsKey] = f.pinForActions
        it[billingStepsKey] = f.billingSteps
        it[advancedKey] = f.advanced
    }

    private val themeKey = intPreferencesKey("theme")
    private val lastBackupKey = longPreferencesKey("last_backup_at")
    private val lastUserKey = stringPreferencesKey("last_username")
    private val lastProductKey = longPreferencesKey("last_product")
    private val sessionKey = stringPreferencesKey("session_token")

    val prefs: Flow<UiPrefs> = context.dataStore.data.map { p ->
        UiPrefs(
            theme = ThemeMode.entries.getOrElse(p[themeKey] ?: 0) { ThemeMode.SYSTEM },
            lastBackupAt = p[lastBackupKey] ?: 0,
            lastUsername = p[lastUserKey] ?: "",
            lastProductId = p[lastProductKey] ?: 0,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.ordinal }
    suspend fun setLastBackup(at: Long) = context.dataStore.edit { it[lastBackupKey] = at }
    suspend fun setLastUsername(name: String) = context.dataStore.edit { it[lastUserKey] = name }
    suspend fun setLastProduct(id: Long) = context.dataStore.edit { it[lastProductKey] = id }

    /** Session survives process death only inside the auto-lock window, encrypted with a Keystore key. */
    suspend fun saveSession(userId: Long, lastActive: Long) {
        val token = runCatching { cipher.encrypt("$userId:$lastActive".toByteArray()) }.getOrNull() ?: return
        context.dataStore.edit { it[sessionKey] = token }
    }

    suspend fun readSession(): Pair<Long, Long>? {
        val token = context.dataStore.data.first()[sessionKey] ?: return null
        val plain = cipher.decrypt(token)?.toString(Charsets.UTF_8) ?: return null
        val parts = plain.split(':')
        if (parts.size != 2) return null
        return parts[0].toLongOrNull()?.let { id -> parts[1].toLongOrNull()?.let { id to it } }
    }

    suspend fun clearSession() = context.dataStore.edit { it.remove(sessionKey) }
}
