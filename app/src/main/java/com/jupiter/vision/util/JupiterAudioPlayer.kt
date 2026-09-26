package com.jupiter.vision.util

import android.content.ContentUris
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val currentTrack: AppLoader.AudioTrackInfo? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedPosition: String
        get() {
            val totalSec = currentPositionMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%d:%02d", min, sec)
        }

    val formattedDuration: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%d:%02d", min, sec)
        }
}

object JupiterAudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var trackList: List<AppLoader.AudioTrackInfo> = emptyList()
    private var currentTrackIndex: Int = -1

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    fun setPlaylist(tracks: List<AppLoader.AudioTrackInfo>) {
        trackList = tracks
        if (_playbackState.value.currentTrack == null && tracks.isNotEmpty()) {
            _playbackState.value = _playbackState.value.copy(
                currentTrack = tracks[0],
                durationMs = tracks[0].durationMs
            )
            currentTrackIndex = 0
        }
    }

    fun playTrack(context: Context, track: AppLoader.AudioTrackInfo) {
        val index = trackList.indexOfFirst { it.id == track.id }
        if (index != -1) currentTrackIndex = index

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            val trackUri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id)
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, trackUri)
                prepare()
                start()
            }

            mediaPlayer = mp
            _playbackState.value = PlaybackState(
                currentTrack = track,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = mp.duration.toLong().coerceAtLeast(track.durationMs)
            )

            mp.setOnCompletionListener {
                playNext(context)
            }

            startProgressUpdates()
        } catch (e: Exception) {
            // Fallback for simulated/demo state if file cannot be read directly
            _playbackState.value = PlaybackState(
                currentTrack = track,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = track.durationMs
            )
            startSimulatedProgress()
        }
    }

    fun togglePlayPause(context: Context) {
        val mp = mediaPlayer
        if (mp != null) {
            try {
                if (mp.isPlaying) {
                    mp.pause()
                    _playbackState.value = _playbackState.value.copy(isPlaying = false)
                } else {
                    mp.start()
                    _playbackState.value = _playbackState.value.copy(isPlaying = true)
                    startProgressUpdates()
                }
            } catch (_: Exception) {
                toggleSimulatedPlayPause(context)
            }
        } else {
            val track = _playbackState.value.currentTrack
            if (track != null) {
                playTrack(context, track)
            } else if (trackList.isNotEmpty()) {
                playTrack(context, trackList[0])
            }
        }
    }

    private fun toggleSimulatedPlayPause(context: Context) {
        val curr = _playbackState.value
        if (curr.currentTrack == null && trackList.isNotEmpty()) {
            playTrack(context, trackList[0])
            return
        }
        val nextPlaying = !curr.isPlaying
        _playbackState.value = curr.copy(isPlaying = nextPlaying)
        if (nextPlaying) {
            startSimulatedProgress()
        }
    }

    fun seekTo(positionFraction: Float) {
        val dur = _playbackState.value.durationMs
        if (dur <= 0) return
        val targetPos = (dur * positionFraction.coerceIn(0f, 1f)).toLong()
        try {
            mediaPlayer?.seekTo(targetPos.toInt())
        } catch (_: Exception) {}
        _playbackState.value = _playbackState.value.copy(currentPositionMs = targetPos)
    }

    fun playNext(context: Context) {
        if (trackList.isEmpty()) return
        currentTrackIndex = (currentTrackIndex + 1) % trackList.size
        playTrack(context, trackList[currentTrackIndex])
    }

    fun playPrevious(context: Context) {
        if (trackList.isEmpty()) return
        currentTrackIndex = if (currentTrackIndex - 1 < 0) trackList.size - 1 else currentTrackIndex - 1
        playTrack(context, trackList[currentTrackIndex])
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                delay(300)
                val mp = mediaPlayer ?: break
                if (mp.isPlaying) {
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = mp.currentPosition.toLong(),
                        durationMs = mp.duration.toLong().coerceAtLeast(_playbackState.value.durationMs),
                        isPlaying = true
                    )
                }
            }
        }
    }

    private fun startSimulatedProgress() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _playbackState.value.isPlaying) {
                delay(1000)
                val curr = _playbackState.value
                val nextPos = curr.currentPositionMs + 1000
                if (curr.durationMs > 0 && nextPos >= curr.durationMs) {
                    _playbackState.value = curr.copy(currentPositionMs = 0L)
                } else {
                    _playbackState.value = curr.copy(currentPositionMs = nextPos)
                }
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _playbackState.value = PlaybackState()
    }
}
