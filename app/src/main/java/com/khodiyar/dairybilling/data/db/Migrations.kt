package com.khodiyar.dairybilling.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Explicit, versioned migrations. Schemas for every version are exported to app/schemas. */
object Migrations {

    /** v1 -> v2: delivery route order for customers. Existing rows get 0 (unordered). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE customers ADD COLUMN routeOrder INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
