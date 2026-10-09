// VisitLogEntity.kt - Room Entity recording arrivals and departures at specific venues.
package com.jupiter.vision.engine.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "visit_logs",
    indices = [
        Index(value = ["venueId"]),
        Index(value = ["arrivalTime"])
    ]
)
data class VisitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val venueId: Long,
    val arrivalTime: Long,
    val departureTime: Long? = null
)
