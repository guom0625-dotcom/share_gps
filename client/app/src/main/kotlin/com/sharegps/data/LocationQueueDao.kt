package com.sharegps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocationQueueDao {
    @Insert
    suspend fun insert(loc: LocationQueueEntity)

    @Query("SELECT * FROM location_queue ORDER BY timestamp ASC LIMIT 50")
    suspend fun getOldest(): List<LocationQueueEntity>

    @Query("DELETE FROM location_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
