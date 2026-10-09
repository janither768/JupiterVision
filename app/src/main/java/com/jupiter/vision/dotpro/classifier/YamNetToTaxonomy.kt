package com.jupiter.vision.dotpro.classifier

import com.jupiter.vision.dotpro.model.Signal

/**
 * 4. TAXONOMY MAPPING — YamNetToTaxonomy
 * Explicit mapping from YAMNet's 521 AudioSet classes to the fixed DoTPro taxonomy:
 * FOOTSTEPS, CHATTER, VOICES, DOOR, RUSTLE, MACHINERY, TRAFFIC, WIND, MUSIC, TRANSIENT, UNKNOWN.
 *
 * Multiple YAMNet classes collapse into one taxonomy entry.
 * YAMNet classes that do not fit any taxonomy entry map to UNKNOWN.
 */
object YamNetToTaxonomy {

    private val EXPLICIT_MAPPING = mapOf<String, Signal>(
        // FOOTSTEPS
        "Walk, footsteps" to Signal.FOOTSTEPS,
        "Footsteps" to Signal.FOOTSTEPS,
        "Patter" to Signal.FOOTSTEPS,
        "Shuffling feet" to Signal.FOOTSTEPS,
        "Running" to Signal.FOOTSTEPS,
        "Marching" to Signal.FOOTSTEPS,

        // CHATTER
        "Conversation" to Signal.CHATTER,
        "Chatter" to Signal.CHATTER,
        "Crowd" to Signal.CHATTER,
        "Hubbub, speech noise, speech babble" to Signal.CHATTER,
        "Babbling" to Signal.CHATTER,

        // VOICES
        "Speech" to Signal.VOICES,
        "Child speech, kid speaking" to Signal.VOICES,
        "Narration, monologue" to Signal.VOICES,
        "Speech synthesizer" to Signal.VOICES,
        "Whispering" to Signal.VOICES,
        "Shout" to Signal.VOICES,
        "Bellow" to Signal.VOICES,
        "Whoop" to Signal.VOICES,
        "Yell" to Signal.VOICES,
        "Children shouting" to Signal.VOICES,
        "Screaming" to Signal.VOICES,
        "Laughter" to Signal.VOICES,
        "Baby laughter" to Signal.VOICES,
        "Giggle" to Signal.VOICES,
        "Snicker" to Signal.VOICES,
        "Belly laugh" to Signal.VOICES,
        "Chuckle, chortle" to Signal.VOICES,
        "Crying, sobbing" to Signal.VOICES,
        "Baby cry, infant cry" to Signal.VOICES,
        "Whimper" to Signal.VOICES,
        "Wail, moan" to Signal.VOICES,
        "Sigh" to Signal.VOICES,
        "Gasp" to Signal.VOICES,
        "Pant" to Signal.VOICES,
        "Grunt" to Signal.VOICES,
        "Throat clearing" to Signal.VOICES,
        "Cough" to Signal.VOICES,

        // DOOR
        "Door" to Signal.DOOR,
        "Doorbell" to Signal.DOOR,
        "Sliding door" to Signal.DOOR,
        "Slam" to Signal.DOOR,
        "Knock" to Signal.DOOR,

        // RUSTLE
        "Rustle" to Signal.RUSTLE,
        "Rustling leaves" to Signal.RUSTLE,
        "Crumpling, crinkling" to Signal.RUSTLE,
        "Crackle" to Signal.RUSTLE,

        // MACHINERY
        "Engine" to Signal.MACHINERY,
        "Light engine (high frequency)" to Signal.MACHINERY,
        "Medium engine (mid frequency)" to Signal.MACHINERY,
        "Heavy engine (low frequency)" to Signal.MACHINERY,
        "Engine starting" to Signal.MACHINERY,
        "Idling" to Signal.MACHINERY,
        "Electric motor" to Signal.MACHINERY,
        "Air conditioning" to Signal.MACHINERY,
        "Mechanical fan" to Signal.MACHINERY,
        "Ceiling fan" to Signal.MACHINERY,
        "Pump" to Signal.MACHINERY,
        "Compressor" to Signal.MACHINERY,
        "Power tool" to Signal.MACHINERY,
        "Drill" to Signal.MACHINERY,
        "Chainsaw" to Signal.MACHINERY,
        "Lawn mower" to Signal.MACHINERY,
        "Sewing machine" to Signal.MACHINERY,
        "Vacuum cleaner" to Signal.MACHINERY,
        "Blender" to Signal.MACHINERY,
        "Hair dryer" to Signal.MACHINERY,
        "Hum" to Signal.MACHINERY,
        "Humming" to Signal.MACHINERY,
        "Buzz" to Signal.MACHINERY,
        "Gears" to Signal.MACHINERY,

        // TRAFFIC
        "Vehicle" to Signal.TRAFFIC,
        "Car" to Signal.TRAFFIC,
        "Car passing by" to Signal.TRAFFIC,
        "Motor vehicle (road)" to Signal.TRAFFIC,
        "Traffic noise, roadway noise" to Signal.TRAFFIC,
        "Vehicle horn, car horn, honking" to Signal.TRAFFIC,
        "Car alarm" to Signal.TRAFFIC,
        "Bus" to Signal.TRAFFIC,
        "Truck" to Signal.TRAFFIC,
        "Motorcycle" to Signal.TRAFFIC,
        "Emergency vehicle" to Signal.TRAFFIC,
        "Police car (siren)" to Signal.TRAFFIC,
        "Ambulance (siren)" to Signal.TRAFFIC,
        "Fire engine, fire truck (siren)" to Signal.TRAFFIC,
        "Train" to Signal.TRAFFIC,
        "Rail transport" to Signal.TRAFFIC,
        "Subway, metro, underground" to Signal.TRAFFIC,
        "Railroad car, train wagon" to Signal.TRAFFIC,
        "Train horn" to Signal.TRAFFIC,
        "Train wheels squealing" to Signal.TRAFFIC,
        "Aircraft" to Signal.TRAFFIC,
        "Aircraft engine" to Signal.TRAFFIC,
        "Jet engine" to Signal.TRAFFIC,
        "Helicopter" to Signal.TRAFFIC,
        "Bicycle" to Signal.TRAFFIC,
        "Skidding" to Signal.TRAFFIC,
        "Tire squeal" to Signal.TRAFFIC,
        "Braking" to Signal.TRAFFIC,

        // WIND
        "Wind" to Signal.WIND,
        "Wind noise (microphone)" to Signal.WIND,
        "Breeze" to Signal.WIND,
        "Gust" to Signal.WIND,
        "Howl" to Signal.WIND,

        // MUSIC
        "Music" to Signal.MUSIC,
        "Musical instrument" to Signal.MUSIC,
        "Singing" to Signal.MUSIC,
        "Choir" to Signal.MUSIC,
        "Yodeling" to Signal.MUSIC,
        "Chant" to Signal.MUSIC,
        "Mantra" to Signal.MUSIC,
        "Acoustic guitar" to Signal.MUSIC,
        "Electric guitar" to Signal.MUSIC,
        "Bass guitar" to Signal.MUSIC,
        "Plucked string instrument" to Signal.MUSIC,
        "Piano" to Signal.MUSIC,
        "Electric piano" to Signal.MUSIC,
        "Organ" to Signal.MUSIC,
        "Keyboard (musical)" to Signal.MUSIC,
        "Synthesizer" to Signal.MUSIC,
        "Percussion" to Signal.MUSIC,
        "Drum" to Signal.MUSIC,
        "Drum kit" to Signal.MUSIC,
        "Snare drum" to Signal.MUSIC,
        "Bass drum" to Signal.MUSIC,
        "Cymbal" to Signal.MUSIC,
        "Hi-hat" to Signal.MUSIC,
        "Tambourine" to Signal.MUSIC,
        "Maraca" to Signal.MUSIC,
        "Brass instrument" to Signal.MUSIC,
        "Trumpet" to Signal.MUSIC,
        "Trombone" to Signal.MUSIC,
        "Saxophone" to Signal.MUSIC,
        "Flute" to Signal.MUSIC,
        "Violin, fiddle" to Signal.MUSIC,
        "Cello" to Signal.MUSIC,
        "Harp" to Signal.MUSIC,
        "Harmonica" to Signal.MUSIC,
        "Accordion" to Signal.MUSIC,
        "Bell" to Signal.MUSIC,
        "Church bell" to Signal.MUSIC,
        "Chime" to Signal.MUSIC,
        "Wind chime" to Signal.MUSIC,
        "Pop music" to Signal.MUSIC,
        "Hip hop music" to Signal.MUSIC,
        "Rock music" to Signal.MUSIC,
        "Heavy metal" to Signal.MUSIC,
        "Punk rock" to Signal.MUSIC,
        "Jazz" to Signal.MUSIC,
        "Blues" to Signal.MUSIC,
        "Soul music" to Signal.MUSIC,
        "Reggae" to Signal.MUSIC,
        "Country" to Signal.MUSIC,
        "Folk music" to Signal.MUSIC,
        "Middle Eastern music" to Signal.MUSIC,
        "Classical music" to Signal.MUSIC,
        "Opera" to Signal.MUSIC,
        "Electronic music" to Signal.MUSIC,
        "House music" to Signal.MUSIC,
        "Techno" to Signal.MUSIC,
        "Dubstep" to Signal.MUSIC,
        "Electronic dance music" to Signal.MUSIC,
        "Ambient music" to Signal.MUSIC,
        "Trance music" to Signal.MUSIC,
        "Music of Latin America" to Signal.MUSIC,
        "Salsa music" to Signal.MUSIC,
        "Music for children" to Signal.MUSIC,
        "New-age music" to Signal.MUSIC,
        "Vocal music" to Signal.MUSIC,
        "Music of Africa" to Signal.MUSIC,
        "Christian music" to Signal.MUSIC,
        "Gospel music" to Signal.MUSIC,
        "Music of Asia" to Signal.MUSIC,
        "Carnatic music" to Signal.MUSIC,
        "Music of Bollywood" to Signal.MUSIC,
        "Traditional music" to Signal.MUSIC,
        "Independent music" to Signal.MUSIC,
        "Background music" to Signal.MUSIC,
        "Theme music" to Signal.MUSIC,
        "Jingle (music)" to Signal.MUSIC,
        "Soundtrack music" to Signal.MUSIC,
        "Lullaby" to Signal.MUSIC,
        "Video game music" to Signal.MUSIC,
        "Christmas music" to Signal.MUSIC,
        "Dance music" to Signal.MUSIC,
        "Wedding music" to Signal.MUSIC,
        "Happy music" to Signal.MUSIC,
        "Sad music" to Signal.MUSIC,
        "Tender music" to Signal.MUSIC,
        "Exciting music" to Signal.MUSIC,
        "Angry music" to Signal.MUSIC,
        "Scary music" to Signal.MUSIC,

        // TRANSIENT
        "Tap" to Signal.TRANSIENT,
        "Clicking" to Signal.TRANSIENT,
        "Clickety-clack" to Signal.TRANSIENT,
        "Thump, thud" to Signal.TRANSIENT,
        "Bang" to Signal.TRANSIENT,
        "Slap, smack" to Signal.TRANSIENT,
        "Snap" to Signal.TRANSIENT,
        "Finger snapping" to Signal.TRANSIENT,
        "Tick" to Signal.TRANSIENT,
        "Tick-tock" to Signal.TRANSIENT,
        "Burst, pop" to Signal.TRANSIENT,
        "Crack" to Signal.TRANSIENT,
        "Clang" to Signal.TRANSIENT,
        "Clatter" to Signal.TRANSIENT
    )

    fun mapToSignal(yamnetClassName: String): Signal {
        EXPLICIT_MAPPING[yamnetClassName]?.let { return it }

        // Case-insensitive lookup fallback
        val lower = yamnetClassName.lowercase().trim()
        for ((key, value) in EXPLICIT_MAPPING) {
            if (key.equals(lower, ignoreCase = true)) {
                return value
            }
        }

        // Substring fallback for related AudioSet class descriptions
        if (lower.contains("music") || lower.contains("song")) return Signal.MUSIC
        if (lower.contains("speech") || lower.contains("speaking") || lower.contains("voice")) return Signal.VOICES
        if (lower.contains("chatter") || lower.contains("conversation")) return Signal.CHATTER
        if (lower.contains("footstep") || lower.contains("walk")) return Signal.FOOTSTEPS
        if (lower.contains("door") || lower.contains("knock")) return Signal.DOOR
        if (lower.contains("traffic") || lower.contains("vehicle") || lower.contains("car")) return Signal.TRAFFIC
        if (lower.contains("motor") || lower.contains("engine") || lower.contains("machine")) return Signal.MACHINERY
        if (lower.contains("wind")) return Signal.WIND
        if (lower.contains("rustle")) return Signal.RUSTLE
        if (lower.contains("transient") || lower.contains("click") || lower.contains("thump")) return Signal.TRANSIENT

        // YAMNet classes that do not fit any taxonomy entry map to UNKNOWN.
        return Signal.UNKNOWN
    }
}
