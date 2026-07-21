package com.wonderfood.core.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

object WonderFoodDatabaseFactory {
    const val DATABASE_NAME = "wonderfood-v105-household.db"

    fun create(
        context: Context,
        name: String = DATABASE_NAME,
    ): WonderFoodDatabase =
        Room.databaseBuilder(context.applicationContext, WonderFoodDatabase::class.java, name)
            .addMigrations(*WonderFoodMigrations.ALL)
            .addCallback(foreignKeyCallback)
            .build()

    fun createInMemory(context: Context): WonderFoodDatabase =
        Room.inMemoryDatabaseBuilder(context.applicationContext, WonderFoodDatabase::class.java)
            .addMigrations(*WonderFoodMigrations.ALL)
            .addCallback(foreignKeyCallback)
            .build()

    private val foreignKeyCallback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }
    }
}
