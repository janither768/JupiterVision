package com.jupiter.vision.dotpro.model

/**
 * DoTPro Event Schema as specified in the DoTPro doctrine.
 * Doctrine: DoTPro is a reflex, not a brain. It reports facts about the signal it hears.
 * It never identifies subjects, never judges, never warns.
 * It names the sound. It does not name who or what made it.
 */
data class DoTProEvent(
    val signal: Signal,
    val source: Source,
    val presence: Presence,
    val proximity: Proximity,
    val bearing: Bearing,
    val motion: Motion,
    val change: Boolean,
    val confidence: Float,
    val timestampMs: Long,
    val displayLabel: String = "",
    val state: AmbientState = AmbientState.ACTIVE
)

/**
 * Three-state ambient model:
 * ACTIVE: Presence track firing, active sound source changing
 * AMBIENT: Stable room tone/fan/hum above hardware noise floor
 * SILENT: Signal at or below mic hardware self-noise floor
 */
enum class AmbientState {
    ACTIVE,
    AMBIENT,
    SILENT
}

/**
 * Taxonomy of signal types (v1).
 * Signal names what was heard, never who or what produced it.
 */
enum class Signal {
    FOOTSTEPS,   // transient, broadband, periodic at walking cadence
    CHATTER,     // voice-band energy, non-periodic, modulated envelope
    VOICES,      // single voice-band source, sustained
    DOOR,        // sharp transient, low-frequency thump, fast decay
    RUSTLE,      // broadband, low-energy, non-rhythmic
    MACHINERY,   // steady tonal or harmonic hum, low variance
    TRAFFIC,     // low-frequency rolling, sustained, high variance
    WIND,        // broadband, no transient, no periodicity
    MUSIC,       // pitched, rhythmic, sustained
    TRANSIENT,   // detected change but unclassified
    UNKNOWN      // always permitted when confidence below threshold
}

enum class Source {
    AUDIO,
    AUDIO_CAMERA,
    AUDIO_EARBUD,
    AUDIO_CAMERA_EARBUD
}

enum class Presence {
    ENTER,
    SUSTAINED,
    EXIT
}

enum class Proximity {
    NEAR,
    MID,
    FAR
}

enum class Bearing {
    FRONT,
    FRONT_LEFT,
    LEFT,
    REAR_LEFT,
    REAR,
    REAR_RIGHT,
    RIGHT,
    FRONT_RIGHT,
    UNKNOWN
}

enum class Motion {
    APPROACHING,
    RECEDING,
    STATIC,
    PASSING,
    UNKNOWN
}
