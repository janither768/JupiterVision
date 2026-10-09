// VisionDao.kt - Room Data Access Object for local engine state, location, visits, and app telemetry.
package com.jupiter.vision.engine.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jupiter.vision.engine.data.entities.AppEventEntity
import com.jupiter.vision.engine.data.entities.PhaseLogEntity
import com.jupiter.vision.engine.data.entities.VenueEntity
import com.jupiter.vision.engine.data.entities.VisitLogEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Dao
interface VisionDao {

    // =========================================
    // VENUES
    // =========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVenue(venue: VenueEntity): Long

    @Update
    suspend fun updateVenue(venue: VenueEntity)

    @Query("SELECT * FROM venues WHERE id = :id LIMIT 1")
    suspend fun getVenueById(id: Long): VenueEntity?

    @Query("SELECT * FROM venues ORDER BY lastSeen DESC")
    suspend fun getAllVenues(): List<VenueEntity>

    @Query("SELECT COUNT(*) FROM venues")
    suspend fun getVenueCount(): Int

    @Query("UPDATE venues SET visitCount = visitCount + 1, lastSeen = :lastSeen WHERE id = :venueId")
    suspend fun incrementVisitCount(venueId: Long, lastSeen: Long)

    @Query("UPDATE venues SET lastSeen = :lastSeen WHERE id = :venueId")
    suspend fun updateVenueLastSeen(venueId: Long, lastSeen: Long)

    /**
     * Finds the nearest venue using the Haversine distance formula evaluated in Kotlin.
     */
    suspend fun getNearestVenue(latitude: Double, longitude: Double): Pair<VenueEntity, Float>? {
        val venues = getAllVenues()
        if (venues.isEmpty()) return null

        var nearest: VenueEntity? = null
        var minDistance = Float.MAX_VALUE

        for (venue in venues) {
            val dist = computeDistanceMeters(latitude, longitude, venue.latitude, venue.longitude)
            if (dist < minDistance) {
                minDistance = dist
                nearest = venue
            }
        }

        return nearest?.let { Pair(it, minDistance) }
    }

    // =========================================
    // VISIT LOGS
    // =========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisit(visit: VisitLogEntity): Long

    @Query("UPDATE visit_logs SET departureTime = :departureTime WHERE id = :visitId")
    suspend fun updateVisitDeparture(visitId: Long, departureTime: Long)

    @Query("UPDATE visit_logs SET departureTime = :departureTime WHERE departureTime IS NULL")
    suspend fun closeAllOpenVisits(departureTime: Long)

    @Query("SELECT * FROM visit_logs WHERE venueId = :venueId ORDER BY arrivalTime DESC")
    suspend fun getVisitsForVenue(venueId: Long): List<VisitLogEntity>

    @Query("SELECT * FROM visit_logs WHERE departureTime IS NULL ORDER BY arrivalTime DESC LIMIT 1")
    suspend fun getOpenVisit(): VisitLogEntity?

    @Query("SELECT * FROM visit_logs WHERE venueId = :venueId AND departureTime IS NULL ORDER BY arrivalTime DESC LIMIT 1")
    suspend fun getOpenVisitForVenue(venueId: Long): VisitLogEntity?

    // =========================================
    // APP EVENTS
    // =========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppEvent(event: AppEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppEvents(events: List<AppEventEntity>)

    @Query("SELECT * FROM app_events WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getAppEventsBetween(start: Long, end: Long): List<AppEventEntity>

    @Query("SELECT * FROM app_events WHERE phase = :phase AND timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getAppEventsForPhase(phase: String, sinceTimestamp: Long): List<AppEventEntity>

    // =========================================
    // PHASE LOGS
    // =========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhaseLog(phaseLog: PhaseLogEntity): Long

    @Query("SELECT * FROM phase_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentPhases(limit: Int): List<PhaseLogEntity>

    @Query("SELECT * FROM phase_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastPhase(): PhaseLogEntity?

    // =========================================
    // CLEANUP
    // =========================================

    @Query("DELETE FROM app_events WHERE timestamp < :timestamp")
    suspend fun deleteAppEventsOlderThan(timestamp: Long): Int

    @Query("DELETE FROM phase_logs WHERE timestamp < :timestamp")
    suspend fun deletePhaseLogsOlderThan(timestamp: Long): Int

    companion object {
        /**
         * Haversine distance calculation in meters between two lat/long coordinates.
         */
        fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val r = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2.0) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2.0)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return (r * c).toFloat()
        }
    }
}
