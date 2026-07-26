package com.memorycurator.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MediaEntity::class],
    version = 3
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
}
