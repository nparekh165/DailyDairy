package com.khodiyar.dairybilling.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.UserEntity
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.UnauthorizedException
import com.khodiyar.dairybilling.domain.time.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets with a non-exportable AES-GCM key held in the Android Keystore. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): String
    fun decrypt(token: String): ByteArray?
}

class KeystoreCipher(private val alias: String = "dairy_session_key") : SecretCipher {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.doFinal(plain)
        return Base64.getEncoder().encodeToString(cipher.iv) + ":" + Base64.getEncoder().encodeToString(out)
    }

    override fun decrypt(token: String): ByteArray? = try {
        val (iv, body) = token.split(':', limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.getDecoder().decode(iv)))
        cipher.doFinal(Base64.getDecoder().decode(body))
    } catch (e: Exception) {
        null
    }
}

/**
 * Holds the signed-in user in memory. Every repository asks [require] before a protected
 * operation, so permissions are enforced below the UI, not just by hiding buttons.
 * Inactivity beyond the auto-lock window signs the user out.
 */
class SessionManager(private val time: TimeProvider) {
    private val _user = MutableStateFlow<UserEntity?>(null)
    val user: StateFlow<UserEntity?> = _user.asStateFlow()

    private var lastActivity = 0L
    @Volatile var autoLockMinutes: Int = 15

    val current: UserEntity? get() = _user.value

    fun start(user: UserEntity) {
        lastActivity = time.nowMillis()
        _user.value = user
    }

    fun refresh(user: UserEntity) {
        if (_user.value?.id == user.id) _user.value = user
    }

    fun logout() {
        _user.value = null
    }

    fun touch() {
        lastActivity = time.nowMillis()
    }

    /** Called when the app returns to foreground. Returns true if the session expired. */
    fun expireIfIdle(): Boolean {
        val u = _user.value ?: return false
        if (autoLockMinutes <= 0) return false
        val idle = time.nowMillis() - lastActivity
        if (idle > autoLockMinutes * 60_000L) {
            logout()
            return u.id != 0L
        }
        return false
    }

    fun has(permission: Permission): Boolean = Access.has(_user.value, permission)

    fun requireUser(): UserEntity =
        _user.value ?: throw UnauthorizedException(UiText.res(R.string.err_not_signed_in))

    fun require(permission: Permission): UserEntity {
        val u = requireUser()
        if (!Access.has(u, permission)) throw UnauthorizedException(UiText.res(R.string.err_not_permitted))
        touch()
        return u
    }
}
