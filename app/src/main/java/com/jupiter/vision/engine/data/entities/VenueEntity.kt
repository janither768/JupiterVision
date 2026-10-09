// VenueEntity.kt - Room Entity representing physical detected locations/venues.
package com.jupiter.vision.engine.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "venues",
    indices = [Index(value = ["latitude", "longitude"])]
)
data class VenueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 100f,
    val inferredType: String = "UNKNOWN", // UNKNOWN, HOME, WORK, GYM, OTHER
    val label: String = "", // "LOCATION 1", "HOME", etc.
    val firstSeen: Long,
    val lastSeen: Long,
    val visitCount: Int = 0
)
