// LocationRepository.kt - Low-power passive location tracking and venue discovery engine.
package com.jupiter.vision.engine.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.jupiter.vision.engine.data.dao.VisionDao
import com.jupiter.vision.engine.data.entities.VenueEntity
import com.jupiter.vision.engine.data.entities.VisitLogEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: VisionDao,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _currentVenue = MutableStateFlow<VenueEntity?>(null)
    val currentVenue: StateFlow<VenueEntity?> = _currentVenue.asStateFlow()

    private val _lastLocation = MutableStateFlow<Location?>(null)
    val lastLocation: StateFlow<Location?> = _lastLocation.asStateFlow()

    private var lastMovementTimestamp: Long = System.currentTimeMillis()
    private var isTracking = false

    // Candidate visit tracker for automatic new venue creation
    private data class CandidateVisit(
        val latitude: Double,
        val longitude: Double,
        val firstDetectedTime: Long,
        var lastUpdatedTime: Long
    )

    private var candidateVisit: CandidateVisit? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleNewLocation(location)
        }
    }

    private val systemLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /**
     * Checks location permissions without prompting the user.
     */
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Starts passive location updates every 2 minutes (balanced power accuracy).
     */
    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (isTracking || !hasLocationPermission()) return

        // Immediately check last known location to have instant fix on launch
        checkImmediateLastKnown()

        try {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                120_000L // 2 minutes polling
            ).setMinUpdateIntervalMillis(60_000L) // Min 1 minute
                .setMaxUpdateDelayMillis(180_000L) // Batching allowed for battery saving
                .build()

            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
            isTracking = true
            Log.d(TAG, "LocationRepository tracking started (interval: 120s)")
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission missing when starting location tracking: ${e.message}")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start location tracking: ${t.message}")
        }
    }

    /**
     * Stops location updates to preserve battery when idle.
     */
    fun stopTracking() {
        if (!isTracking) return
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isTracking = false
            Log.d(TAG, "LocationRepository tracking stopped")
        } catch (t: Throwable) {
            Log.e(TAG, "Error stopping location updates: ${t.message}")
        }
    }

    private var isLiveTracking = false

    /**
     * Starts high-frequency live tracking (5s interval, 5m displacement)
     * when the launcher is in the foreground on either page.
     */
    @SuppressLint("MissingPermission")
    fun startLiveTracking() {
        if (isLiveTracking || !hasLocationPermission()) return
        isLiveTracking = true

        // 1. Immediate last known check
        checkImmediateLastKnown()

        // 2. Register FusedLocationClient with high accuracy (2 sec interval, 1m displacement)
        try {
            val liveRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2_000L // 2 seconds
            ).setMinUpdateIntervalMillis(1_000L)
                .setMinUpdateDistanceMeters(1f)
                .build()

            fusedLocationClient.requestLocationUpdates(
                liveRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Live tracking started with FusedLocationClient (2s, 1m)")
        } catch (t: Throwable) {
            Log.w(TAG, "Fused live tracking not available, falling back to LocationManager: ${t.message}")
        }

        // 3. Register LocationManager GPS and Network providers as direct reliable live feeds
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm != null) {
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        2_000L,
                        1f,
                        systemLocationListener,
                        Looper.getMainLooper()
                    )
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        2_000L,
                        1f,
                        systemLocationListener,
                        Looper.getMainLooper()
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "LocationManager live tracking setup failed: ${t.message}")
        }
    }

    /**
     * Stops live location updates when launcher enters background.
     */
    fun stopLiveTracking() {
        if (!isLiveTracking) return
        isLiveTracking = false
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Throwable) {}

        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            lm?.removeUpdates(systemLocationListener)
        } catch (_: Throwable) {}

        Log.d(TAG, "Live location tracking stopped (launcher backgrounded)")
    }

    @SuppressLint("MissingPermission")
    private fun checkImmediateLastKnown() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) handleNewLocation(loc)
            }
        } catch (_: Throwable) {}

        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val gpsLoc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLoc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val passiveLoc = lm?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            val bestLoc = gpsLoc ?: netLoc ?: passiveLoc
            if (bestLoc != null) handleNewLocation(bestLoc)
        } catch (_: Throwable) {}
    }

    /**
     * One-shot current location query with immediate fallback to last known location.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) return null

        val current = _lastLocation.value
        if (current != null && System.currentTimeMillis() - current.time < 5 * 60 * 1000L) {
            return current
        }

        return suspendCancellableCoroutine { cont ->
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            handleNewLocation(loc)
                            if (cont.isActive) cont.resume(loc)
                        } else {
                            try {
                                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                                val gpsLoc = lm?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                val netLoc = lm?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                                val bestLoc = gpsLoc ?: netLoc
                                if (bestLoc != null) handleNewLocation(bestLoc)
                                if (cont.isActive) cont.resume(bestLoc)
                            } catch (_: Throwable) {
                                if (cont.isActive) cont.resume(null)
                            }
                        }
                    }
                    .addOnFailureListener {
                        try {
                            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                            val gpsLoc = lm?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                            val netLoc = lm?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                            val bestLoc = gpsLoc ?: netLoc
                            if (bestLoc != null) handleNewLocation(bestLoc)
                            if (cont.isActive) cont.resume(bestLoc)
                        } catch (_: Throwable) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
            } catch (t: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /**
     * Process each incoming location fix.
     */
    private fun handleNewLocation(location: Location) {
        val now = System.currentTimeMillis()

        // Track movement distance to determine stationary duration
        val previous = _lastLocation.value
        if (previous != null) {
            val movedMeters = location.distanceTo(previous)
            if (movedMeters > 30f) {
                lastMovementTimestamp = now
            }
        } else {
            lastMovementTimestamp = now
        }
        _lastLocation.value = location

        scope.launch {
            processVenueAndVisit(location, now)
        }
    }

    private suspend fun processVenueAndVisit(location: Location, now: Long) {
        val nearestPair = dao.getNearestVenue(location.latitude, location.longitude)
        val nearestVenue = nearestPair?.first
        val distanceMeters = nearestPair?.second ?: Float.MAX_VALUE

        if (nearestVenue != null && distanceMeters <= nearestVenue.radiusMeters) {
            // User is inside an established venue
            candidateVisit = null // Discard any candidate search

            val prevVenue = _currentVenue.value
            _currentVenue.value = nearestVenue

            // Ensure an open visit log exists for this venue
            val openVisit = dao.getOpenVisitForVenue(nearestVenue.id)
            if (openVisit == null) {
                // Close any other open visits
                dao.closeAllOpenVisits(now)
                // Open new visit log
                dao.insertVisit(
                    VisitLogEntity(
                        venueId = nearestVenue.id,
                        arrivalTime = now,
                        departureTime = null
                    )
                )
                // Increment visit count on arrival
                dao.incrementVisitCount(nearestVenue.id, now)
            } else {
                dao.updateVenueLastSeen(nearestVenue.id, now)
            }
        } else {
            // User is not within any existing venue
            _currentVenue.value = null

            // Close any currently open visits
            dao.closeAllOpenVisits(now)

            // Evaluate or update candidate visit tracker
            evaluateCandidateVisit(location, now)
        }
    }

    /**
     * If the user stays within 100m for 15+ minutes, automatically create a new VenueEntity.
     */
    private suspend fun evaluateCandidateVisit(location: Location, now: Long) {
        val currentCandidate = candidateVisit

        if (currentCandidate == null) {
            candidateVisit = CandidateVisit(
                latitude = location.latitude,
                longitude = location.longitude,
                firstDetectedTime = now,
                lastUpdatedTime = now
            )
        } else {
            val dist = VisionDao.computeDistanceMeters(
                location.latitude, location.longitude,
                currentCandidate.latitude, currentCandidate.longitude
            )

            if (dist <= 100f) {
                currentCandidate.lastUpdatedTime = now
                val dwellDuration = now - currentCandidate.firstDetectedTime
                val fifteenMinutes = 15 * 60 * 1000L

                if (dwellDuration >= fifteenMinutes) {
                    // Dwell threshold reached: Establish a new venue
                    val currentCount = dao.getVenueCount()
                    val venueLabel = "LOCATION ${currentCount + 1}"

                    val newVenueId = dao.insertVenue(
                        VenueEntity(
                            latitude = currentCandidate.latitude,
                            longitude = currentCandidate.longitude,
                            radiusMeters = 100f,
                            inferredType = "UNKNOWN",
                            label = venueLabel,
                            firstSeen = currentCandidate.firstDetectedTime,
                            lastSeen = now,
                            visitCount = 1
                        )
                    )

                    // Insert initial visit log
                    dao.insertVisit(
                        VisitLogEntity(
                            venueId = newVenueId,
                            arrivalTime = currentCandidate.firstDetectedTime,
                            departureTime = null
                        )
                    )

                    val createdVenue = dao.getVenueById(newVenueId)
                    _currentVenue.value = createdVenue
                    candidateVisit = null

                    Log.d(TAG, "Created new inferred venue: $venueLabel (id=$newVenueId)")
                }
            } else {
                // Moved outside 100m radius -> restart candidate tracker
                candidateVisit = CandidateVisit(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    firstDetectedTime = now,
                    lastUpdatedTime = now
                )
            }
        }
    }

    /**
     * Milliseconds elapsed since the user last moved > 30 meters.
     */
    fun getTimeSinceLastMovement(): Long {
        return System.currentTimeMillis() - lastMovementTimestamp
    }

    companion object {
        private const val TAG = "LocationRepository"
    }
}
