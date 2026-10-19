package com.khodiyar.dairybilling.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.workspace.DairyRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DairyRegistryTest {
    private val time = FakeTime()
    private val dir: File = Files.createTempDirectory("registry").toFile()
    private val dbDir = File(dir, "databases").apply { mkdirs() }
    private fun registry() = DairyRegistry(File(dir, "dairies.json"), dbDir, time)

    @Test fun firstRunHasOriginalDairyOnOriginalDatabase() {
        val r = registry()
        assertEquals(1, r.state.value.dairies.size)
        assertEquals(DairyRegistry.MAIN, r.active.id)
        assertEquals(AppDatabase.NAME, DairyRegistry.databaseName(r.active.id))
    }

    @Test fun addSwitchRenameSurviveRestart() {
        val r = registry()
        r.rename(DairyRegistry.MAIN, "Khodiyar Dairy")
        val shree = r.add("  Shree Dairy ")
        assertEquals("Shree Dairy", shree.name)
        assertNotEquals(AppDatabase.NAME, DairyRegistry.databaseName(shree.id))
        r.setActive(shree.id)
        val again = registry()
        assertEquals(listOf("Khodiyar Dairy", "Shree Dairy"), again.state.value.dairies.map { it.name })
        assertEquals(shree.id, again.active.id)
    }

    @Test fun blankAndDuplicateNamesRejected() {
        val r = registry()
        r.add("Shree Dairy")
        for (bad in listOf("", "   ", "shree dairy")) {
            try { r.add(bad); fail("accepted '$bad'") } catch (e: AppException) { }
        }
        assertEquals(2, r.state.value.dairies.size)
    }

    @Test fun lostListFileDoesNotOrphanDatabases() {
        val r = registry()
        val a = r.add("A")
        File(dbDir, DairyRegistry.databaseName(a.id)).writeText("db")
        File(dbDir, AppDatabase.NAME).writeText("db") // the original database is not picked up as an extra dairy
        File(dir, "dairies.json").delete()
        val healed = registry()
        assertEquals(setOf(DairyRegistry.MAIN, a.id), healed.state.value.dairies.map { it.id }.toSet())
    }

    @Test fun corruptListFileFallsBackSafely() {
        File(dir, "dairies.json").writeText("{not json")
        assertEquals(DairyRegistry.MAIN, registry().active.id)
    }

    @Test fun dairiesNeverShareData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val one = AppContainer(context, time, "iso_one.db", "one")
        val two = AppContainer(context, time, "iso_two.db", "two")
        try {
            one.db.openHelper.writableDatabase.execSQL(
                "INSERT INTO customers (code, name, houseNo, mobile, address, notes, openingBalancePaise, defaultProductId, defaultQuantityMilli, active, createdAt, updatedAt) " +
                    "VALUES ('C-1', 'Asha', 'B-1', '', '', '', 0, NULL, 0, 1, 0, 0)"
            )
            fun count(c: AppContainer) = c.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM customers").use { it.moveToFirst(); it.getInt(0) }
            assertEquals(1, count(one))
            assertEquals(0, count(two))
            assertNotEquals(one.safetyBackupDir, two.safetyBackupDir)
            assertTrue(two.safetyBackupDir.name.contains("two"))
        } finally {
            one.close(); two.close()
            context.deleteDatabase("iso_one.db"); context.deleteDatabase("iso_two.db")
        }
    }
}
