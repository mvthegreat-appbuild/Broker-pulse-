package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BrokerPulseDao {

    @Query("SELECT * FROM recommendations ORDER BY createdAt DESC")
    fun observeAllRecommendations(): Flow<List<RecommendationEntity>>

    @Query("SELECT * FROM recommendations")
    suspend fun getAllRecommendationsOnce(): List<RecommendationEntity>

    @Query("SELECT * FROM recommendations WHERE id = :id")
    suspend fun getRecommendationById(id: Long): RecommendationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecommendation(item: RecommendationEntity): Long

    @Update
    suspend fun updateRecommendation(item: RecommendationEntity)

    @Query("DELETE FROM recommendations WHERE id = :id")
    suspend fun deleteRecommendationById(id: Long)

    @Query("SELECT * FROM alert_events ORDER BY timestamp DESC")
    fun observeAllAlertEvents(): Flow<List<AlertEventEntity>>

    @Query("SELECT COUNT(*) FROM alert_events WHERE isRead = 0")
    fun observeUnreadAlertCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlertEvent(event: AlertEventEntity): Long

    @Query("UPDATE alert_events SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAlertsRead()

    @Query("DELETE FROM alert_events WHERE recommendationId = :recId")
    suspend fun deleteAlertsForRecommendation(recId: Long)
}
