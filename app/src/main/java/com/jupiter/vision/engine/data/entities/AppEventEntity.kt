// AppEventEntity.kt - Room Entity recording foreground app launch telemetry.
package com.jupiter.vision.engine.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["packageName"])
    ]
)
data class AppEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long,
    val venueId: Long? = null,
    val phase: String? = null
)
