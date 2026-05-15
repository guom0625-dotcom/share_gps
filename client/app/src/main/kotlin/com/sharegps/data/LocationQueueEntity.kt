package com.sharegps.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_queue")
data class LocationQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val battery: Int? = null,
    val timestamp: Long,
    val speed: Double? = null,
)
