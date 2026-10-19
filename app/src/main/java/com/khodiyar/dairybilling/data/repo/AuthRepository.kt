package com.khodiyar.dairybilling.data.repo

import androidx.room.withTransaction
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.data.db.AppSettingsEntity
import com.khodiyar.dairybilling.data.db.ProductEntity
import com.khodiyar.dairybilling.data.db.UserEntity
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.model.ProductUnit
import com.khodiyar.dairybilling.domain.model.UserRole
import com.khodiyar.dairybilling.domain.time.TimeProvider
import com.khodiyar.dairybilling.security.PasswordHasher
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.security.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed interface LoginResult {
    data class Success(val user: UserEntity) : LoginResult
    data object InvalidCredentials : LoginResult
    data object Inactive : LoginResult
    data class LockedOut(val untilMillis: Long) : LoginResult
}

class AuthRepository(
    private val db: AppDatabase,
    private val session: SessionManager,
    private val audit: AuditLogger,
    private val time: TimeProvider,
    private val iterations: Int = PasswordHasher.DEFAULT_ITERATIONS,
) {
    private val users = db.users()

    suspend fun needsSetup(): Boolean = users.count() == 0

    fun observeUsers(): Flow<List<UserEntity>> = users.observeAll()

    private fun validateUsername(username: String): String {
        val u = username.trim().lowercase()
        if (!Regex("^[a-z0-9._-]{3,32}$").matches(u)) throw AppException(R.string.err_username_format)
        return u
    }

    private fun validateSecret(pin: String) {
        if (!PasswordHasher.isAcceptable(pin)) throw AppException(R.string.err_pin_weak)
    }

    /** First-run: creates business settings, the Admin account and starter products (no rates). */
    suspend fun completeSetup(
        dairyName: String,
        phone: String,
        address: String,
        adminName: String,
        username: String,
        pin: String,
        createStarterProducts: Boolean,
    ): UserEntity {
        if (dairyName.isBlank()) throw AppException(R.string.err_required_dairy_name)
        if (adminName.isBlank()) throw AppException(R.string.err_required_name)
        val uname = validateUsername(username)
        validateSecret(pin)
        val hashed = withContext(Dispatchers.Default) { PasswordHasher.hash(pin, iterations) }
        val now = time.nowMillis()
        val user = db.withTransaction {
            if (users.count() > 0) throw AppException(R.string.err_setup_done)
            db.settings().upsert(
                AppSettingsEntity(
                    dairyName = dairyName.trim(),
                    phone = phone.trim(),
                    address = address.trim(),
                    invoiceFooter = "Thank you for your business.",
                    paymentInstructions = "Please pay by the 10th of the month.",
                    updatedAt = now,
                )
            )
            val created = UserEntity(
                username = uname,
                displayName = adminName.trim(),
                role = UserRole.ADMIN,
                pinHash = hashed.hash,
                pinSalt = hashed.salt,
                hashIterations = hashed.iterations,
                createdAt = now,
                updatedAt = now,
            )
            val id = users.insert(created)
            if (createStarterProducts) {
                listOf(
                    Triple("Milk", ProductUnit.LITER, 500L),
                    Triple("Curd (Dahi)", ProductUnit.KILOGRAM, 250L),
                    Triple("Paneer", ProductUnit.KILOGRAM, 250L),
                    Triple("Ghee", ProductUnit.KILOGRAM, 500L),
                ).forEachIndexed { i, (name, unit, step) ->
                    db.products().insert(ProductEntity(name = name, unit = unit, stepMilli = step, sortOrder = i, createdAt = now, updatedAt = now))
                }
            }
            created.copy(id = id)
        }
        session.start(user)
        audit.log(AuditLogger.Actions.SETUP, "user", user.id, "Initial admin '${user.username}' created")
        return user
    }

    suspend fun login(username: String, pin: String): LoginResult {
        val now = time.nowMillis()
        val user = users.findByUsername(username.trim().lowercase())
        if (user == null) {
            // Spend comparable time so response timing does not reveal valid usernames.
            withContext(Dispatchers.Default) { PasswordHasher.hash(pin, iterations) }
            return LoginResult.InvalidCredentials
        }
        if (user.lockedUntil > now) return LoginResult.LockedOut(user.lockedUntil)
        val ok = withContext(Dispatchers.Default) {
            PasswordHasher.verify(pin, user.pinHash, user.pinSalt, user.hashIterations)
        }
        if (!ok) {
            val attempts = user.failedAttempts + 1
            val lockMillis = if (attempts >= MAX_ATTEMPTS) LOCK_BASE_MILLIS * (1L shl (attempts - MAX_ATTEMPTS).coerceAtMost(6)) else 0L
            users.update(user.copy(failedAttempts = attempts, lockedUntil = if (lockMillis > 0) now + lockMillis else 0, updatedAt = now))
            audit.log(AuditLogger.Actions.LOGIN_FAILED, "user", user.id, "Failed attempt $attempts")
            return if (lockMillis > 0) LoginResult.LockedOut(now + lockMillis) else LoginResult.InvalidCredentials
        }
        if (!user.active) return LoginResult.Inactive
        val updated = user.copy(failedAttempts = 0, lockedUntil = 0)
        if (user.failedAttempts != 0 || user.lockedUntil != 0L) users.update(updated)
        session.start(updated)
        audit.log(AuditLogger.Actions.LOGIN, "user", user.id, "Signed in")
        return LoginResult.Success(updated)
    }

    /**
     * The app has no sign-in screen: it always opens as the dairy's owner (the first active admin).
     * Permission checks in the repositories still run, against that owner.
     */
    suspend fun signInOwner(): Boolean {
        val owner = users.observeAll().first().firstOrNull { it.active && it.role == UserRole.ADMIN } ?: return false
        session.start(owner)
        return true
    }

    /** Resumes a session persisted inside the auto-lock window. */
    suspend fun resume(userId: Long): Boolean {
        val user = users.get(userId) ?: return false
        if (!user.active) return false
        session.start(user)
        return true
    }

    suspend fun logout() {
        if (session.current != null) audit.log(AuditLogger.Actions.LOGOUT, "user", session.current?.id, "Signed out")
        session.logout()
    }

    /** Re-authentication for destructive actions: verifies the signed-in user's own PIN. */
    suspend fun verifyCurrentUser(pin: String): Boolean {
        val u = session.current ?: return false
        val fresh = users.get(u.id) ?: return false
        return withContext(Dispatchers.Default) { PasswordHasher.verify(pin, fresh.pinHash, fresh.pinSalt, fresh.hashIterations) }
    }

    suspend fun createStaff(displayName: String, username: String, pin: String, permissions: Set<Permission>): Long {
        session.require(Permission.MANAGE_USERS)
        if (displayName.isBlank()) throw AppException(R.string.err_required_name)
        val uname = validateUsername(username)
        validateSecret(pin)
        if (users.findByUsername(uname) != null) throw AppException(R.string.err_username_taken)
        val hashed = withContext(Dispatchers.Default) { PasswordHasher.hash(pin, iterations) }
        val now = time.nowMillis()
        return db.withTransaction {
            val id = users.insert(
                UserEntity(
                    username = uname, displayName = displayName.trim(), role = UserRole.STAFF,
                    pinHash = hashed.hash, pinSalt = hashed.salt, hashIterations = hashed.iterations,
                    permissions = Permission.encode(permissions), createdAt = now, updatedAt = now,
                )
            )
            audit.log(AuditLogger.Actions.USER_CREATED, "user", id, "Staff '$uname' perms=${Permission.encode(permissions)}")
            id
        }
    }

    suspend fun updateStaff(userId: Long, displayName: String, permissions: Set<Permission>, active: Boolean) {
        val admin = session.require(Permission.MANAGE_USERS)
        val user = users.get(userId) ?: throw AppException(R.string.err_not_found)
        if (displayName.isBlank()) throw AppException(R.string.err_required_name)
        if (user.id == admin.id && !active) throw AppException(R.string.err_cannot_deactivate_self)
        if (user.role == UserRole.ADMIN && !active && users.activeAdminCount() <= 1) throw AppException(R.string.err_last_admin)
        val updated = user.copy(
            displayName = displayName.trim(),
            permissions = if (user.role == UserRole.STAFF) Permission.encode(permissions) else "",
            active = active,
            updatedAt = time.nowMillis(),
        )
        db.withTransaction {
            users.update(updated)
            audit.log(AuditLogger.Actions.USER_UPDATED, "user", userId, "name=${updated.displayName} active=$active perms=${updated.permissions}")
        }
        session.refresh(updated)
    }

    suspend fun resetPin(userId: Long, newPin: String) {
        session.require(Permission.MANAGE_USERS)
        validateSecret(newPin)
        val user = users.get(userId) ?: throw AppException(R.string.err_not_found)
        val hashed = withContext(Dispatchers.Default) { PasswordHasher.hash(newPin, iterations) }
        db.withTransaction {
            users.update(user.copy(pinHash = hashed.hash, pinSalt = hashed.salt, hashIterations = hashed.iterations, failedAttempts = 0, lockedUntil = 0, updatedAt = time.nowMillis()))
            audit.log(AuditLogger.Actions.PIN_RESET, "user", userId, "PIN reset for '${user.username}'")
        }
    }

    suspend fun changeOwnPin(currentPin: String, newPin: String) {
        val me = session.requireUser()
        if (!verifyCurrentUser(currentPin)) throw AppException(R.string.err_wrong_pin)
        validateSecret(newPin)
        val hashed = withContext(Dispatchers.Default) { PasswordHasher.hash(newPin, iterations) }
        val user = users.get(me.id) ?: throw AppException(R.string.err_not_found)
        db.withTransaction {
            users.update(user.copy(pinHash = hashed.hash, pinSalt = hashed.salt, hashIterations = hashed.iterations, updatedAt = time.nowMillis()))
            audit.log(AuditLogger.Actions.PIN_CHANGED, "user", me.id, "Own PIN changed")
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCK_BASE_MILLIS = 30_000L
    }
}
