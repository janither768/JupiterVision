// VisionDatabase.kt - Room Database definition storing venues, visits, app events, and inferred lifestyle phases locally.
package com.jupiter.vision.engine.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jupiter.vision.engine.data.dao.VisionDao
import com.jupiter.vision.engine.data.entities.AppEventEntity
import com.jupiter.vision.engine.data.entities.PhaseLogEntity
import com.jupiter.vision.engine.data.entities.VenueEntity
import com.jupiter.vision.engine.data.entities.VisitLogEntity

@Database(
    entities = [
        VenueEntity::class,
        VisitLogEntity::class,
        AppEventEntity::class,
        PhaseLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VisionDatabase : RoomDatabase() {
    abstract fun visionDao(): VisionDao
}
