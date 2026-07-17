package com.wonderfood.core.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object WonderFoodMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
