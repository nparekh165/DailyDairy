package com.khodiyar.dairybilling.domain

import com.khodiyar.dairybilling.data.db.UserEntity
import com.khodiyar.dairybilling.data.repo.HouseNumberComparator
import com.khodiyar.dairybilling.domain.model.UserRole
import com.khodiyar.dairybilling.export.CsvWriter
import com.khodiyar.dairybilling.security.Access
import com.khodiyar.dairybilling.security.PasswordHasher
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.share.ShareHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilityTest {

    @Test fun passwordHash_saltedAndVerifiable() {
        val a = PasswordHasher.hash("ghee#2026", 2000)
        val b = PasswordHasher.hash("ghee#2026", 2000)
        assertNotEquals("same secret must get different salts", a.salt, b.salt)
        assertNotEquals(a.hash, b.hash)
        assertFalse("hash must not contain the secret", a.hash.contains("ghee"))
        assertTrue(PasswordHasher.verify("ghee#2026", a.hash, a.salt, a.iterations))
        assertFalse(PasswordHasher.verify("ghee#2027", a.hash, a.salt, a.iterations))
        assertFalse(PasswordHasher.verify("ghee#2026", a.hash, "!!notbase64", a.iterations))
    }

    @Test fun pinPolicy() {
        assertFalse(PasswordHasher.isAcceptable("12345"))
        assertFalse(PasswordHasher.isAcceptable("123456"))
        assertFalse(PasswordHasher.isAcceptable("654321"))
        assertFalse(PasswordHasher.isAcceptable("000000"))
        assertTrue(PasswordHasher.isAcceptable("482916"))
        assertFalse(PasswordHasher.isAcceptable("milk@home"))
        assertFalse(PasswordHasher.isAcceptable("48a916"))
    }

    private fun user(role: UserRole, perms: String = "", active: Boolean = true) =
        UserEntity(1, "u", "U", role, "h", "s", 1, perms, active, 0, 0, 0, 0)

    @Test fun permissions_adminHasAll_staffLimited() {
        val admin = user(UserRole.ADMIN)
        Permission.entries.forEach { assertTrue(Access.has(admin, it)) }
        val staff = user(UserRole.STAFF)
        assertTrue(Access.has(staff, Permission.RECORD_DELIVERIES))
        assertFalse(Access.has(staff, Permission.RECORD_PAYMENTS))
        assertFalse(Access.has(staff, Permission.MANAGE_RATES))
        assertFalse(Access.has(staff, Permission.FINALIZE_INVOICES))
    }

    @Test fun permissions_adminOnlyCannotBeGrantedEvenIfStored() {
        val staff = user(UserRole.STAFF, "RECORD_PAYMENTS,MANAGE_USERS,RESTORE,SETTINGS,MANAGE_RATES")
        assertTrue(Access.has(staff, Permission.RECORD_PAYMENTS))
        assertTrue(Access.has(staff, Permission.MANAGE_RATES))
        assertFalse(Access.has(staff, Permission.MANAGE_USERS))
        assertFalse(Access.has(staff, Permission.RESTORE))
        assertFalse(Access.has(staff, Permission.SETTINGS))
        assertEquals("RECORD_PAYMENTS,MANAGE_RATES", Permission.encode(setOf(Permission.MANAGE_RATES, Permission.RESTORE, Permission.RECORD_PAYMENTS)))
    }

    @Test fun permissions_inactiveUserHasNone() {
        assertFalse(Access.has(user(UserRole.ADMIN, active = false), Permission.RECORD_DELIVERIES))
        assertFalse(Access.has(null, Permission.RECORD_DELIVERIES))
    }

    @Test fun houseNumbersSortNaturally() {
        val sorted = listOf("A-10", "A-2", "B-1", "A-1", "a-3", "10", "9").sortedWith(HouseNumberComparator)
        assertEquals(listOf("9", "10", "A-1", "A-2", "a-3", "A-10", "B-1"), sorted)
    }

    @Test fun csvEscapingAndFormulaInjection() {
        val csv = CsvWriter.toCsv(listOf("Name", "Note"), listOf(listOf("Patel, R", "said \"hi\""), listOf("=SUM(A1)", "-12.50")))
        val lines = csv.split("\r\n")
        assertEquals("Name,Note", lines[0])
        assertEquals("\"Patel, R\",\"said \"\"hi\"\"\"", lines[1])
        assertEquals("\"'=SUM(A1)\",-12.50", lines[2])
    }

    @Test fun indianMobileNormalisation() {
        assertEquals("919876543210", ShareHelper.normalizeIndianMobile("98765 43210"))
        assertEquals("919876543210", ShareHelper.normalizeIndianMobile("+91-98765-43210"))
        assertEquals("919876543210", ShareHelper.normalizeIndianMobile("09876543210"))
        assertNull(ShareHelper.normalizeIndianMobile("12345"))
        assertNull(ShareHelper.normalizeIndianMobile("1234567890"))
    }
}
