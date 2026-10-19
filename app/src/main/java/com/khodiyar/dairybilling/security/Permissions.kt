package com.khodiyar.dairybilling.security

import com.khodiyar.dairybilling.data.db.UserEntity
import com.khodiyar.dairybilling.domain.model.UserRole

/**
 * Every protected operation names a permission. Admins hold all of them. Staff hold
 * [STAFF_BASELINE] plus whatever the Admin grants from [STAFF_GRANTABLE]. Permissions in
 * [ADMIN_ONLY] can never be granted to staff.
 */
enum class Permission {
    RECORD_DELIVERIES,
    EDIT_PAST_DELIVERIES,
    RECORD_PAYMENTS,
    REVERSE_PAYMENTS,
    VIEW_REPORTS,
    VIEW_BILLING,
    MANAGE_CUSTOMERS,
    MANAGE_PRODUCTS,
    MANAGE_RATES,
    ADJUSTMENTS,
    FINALIZE_INVOICES,
    LOCK_PERIODS,
    MANAGE_USERS,
    BACKUP,
    RESTORE,
    SETTINGS,
    VIEW_AUDIT;

    companion object {
        val STAFF_BASELINE = setOf(RECORD_DELIVERIES)
        val STAFF_GRANTABLE = listOf(
            RECORD_PAYMENTS, EDIT_PAST_DELIVERIES, VIEW_REPORTS, VIEW_BILLING,
            MANAGE_CUSTOMERS, MANAGE_RATES, FINALIZE_INVOICES,
        )
        val ADMIN_ONLY = setOf(MANAGE_USERS, RESTORE, LOCK_PERIODS, SETTINGS, REVERSE_PAYMENTS, BACKUP, VIEW_AUDIT, MANAGE_PRODUCTS, ADJUSTMENTS)

        fun parse(csv: String): Set<Permission> =
            csv.split(',').mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }.toSet()

        fun encode(perms: Collection<Permission>): String =
            perms.filter { it in STAFF_GRANTABLE }.sortedBy { it.ordinal }.joinToString(",") { it.name }
    }
}

object Access {
    fun permissionsOf(user: UserEntity): Set<Permission> = when (user.role) {
        UserRole.ADMIN -> Permission.entries.toSet()
        UserRole.STAFF -> Permission.STAFF_BASELINE + (Permission.parse(user.permissions) - Permission.ADMIN_ONLY)
    }

    fun has(user: UserEntity?, permission: Permission): Boolean =
        user != null && user.active && permission in permissionsOf(user)
}
