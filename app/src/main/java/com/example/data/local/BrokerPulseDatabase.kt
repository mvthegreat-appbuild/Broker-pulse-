package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RecommendationEntity::class, AlertEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BrokerPulseDatabase : RoomDatabase() {
    abstract fun brokerPulseDao(): BrokerPulseDao

    companion object {
        @Volatile
        private var INSTANCE: BrokerPulseDatabase? = null

        fun getInstance(context: Context): BrokerPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BrokerPulseDatabase::class.java,
                    "broker_pulse_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
