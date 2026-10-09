// PhaseLogEntity.kt - Room Entity recording inferred Lifestyle Phases over time.
package com.jupiter.vision.engine.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "phase_logs",
    indices = [Index(value = ["timestamp"])]
)
data class PhaseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phase: String,
    val timestamp: Long,
    val confidence: Float = 0.5f
)
