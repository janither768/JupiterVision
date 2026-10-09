package com.jupiter.vision

import com.jupiter.vision.dotpro.classifier.DisplayLabelFormatter
import com.jupiter.vision.dotpro.classifier.GateTriggerReason
import com.jupiter.vision.dotpro.classifier.LabelStabilizer
import com.jupiter.vision.dotpro.classifier.YamNetGate
import com.jupiter.vision.dotpro.classifier.YamNetToTaxonomy
import com.jupiter.vision.dotpro.features.FrameFeatures
import com.jupiter.vision.dotpro.model.Motion
import com.jupiter.vision.dotpro.model.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YamNetPipelineUnitTest {

    @Test
    fun testTaxonomyMapping() {
        assertEquals(Signal.FOOTSTEPS, YamNetToTaxonomy.mapToSignal("Walk, footsteps"))
        assertEquals(Signal.FOOTSTEPS, YamNetToTaxonomy.mapToSignal("Footsteps"))
        assertEquals(Signal.CHATTER, YamNetToTaxonomy.mapToSignal("Conversation"))
        assertEquals(Signal.CHATTER, YamNetToTaxonomy.mapToSignal("Chatter"))
        assertEquals(Signal.VOICES, YamNetToTaxonomy.mapToSignal("Speech"))
        assertEquals(Signal.VOICES, YamNetToTaxonomy.mapToSignal("Whispering"))
        assertEquals(Signal.DOOR, YamNetToTaxonomy.mapToSignal("Door"))
        assertEquals(Signal.DOOR, YamNetToTaxonomy.mapToSignal("Knock"))
        assertEquals(Signal.RUSTLE, YamNetToTaxonomy.mapToSignal("Rustle"))
        assertEquals(Signal.MACHINERY, YamNetToTaxonomy.mapToSignal("Engine"))
        assertEquals(Signal.TRAFFIC, YamNetToTaxonomy.mapToSignal("Car"))
        assertEquals(Signal.TRAFFIC, YamNetToTaxonomy.mapToSignal("Traffic noise, roadway noise"))
        assertEquals(Signal.WIND, YamNetToTaxonomy.mapToSignal("Wind"))
        assertEquals(Signal.MUSIC, YamNetToTaxonomy.mapToSignal("Music"))
        assertEquals(Signal.MUSIC, YamNetToTaxonomy.mapToSignal("Piano"))
        assertEquals(Signal.TRANSIENT, YamNetToTaxonomy.mapToSignal("Clicking"))
        assertEquals(Signal.UNKNOWN, YamNetToTaxonomy.mapToSignal("Underwater sound"))
    }

    @Test
    fun testDisplayLabelFormatting() {
        val formatter = DisplayLabelFormatter()

        val mockFeatures = FrameFeatures(
            frameRms = 0.035f,
            baselineRms = 0.030f,
            changeMagnitude = 0.005f,
            isTransient = false,
            envelopeVelocity = 0.001f,
            motion = Motion.STATIC,
            spectralCentroid = 1000f,
            spectralBandwidth = 500f,
            spectralRolloff = 2000f,
            spectralFlatness = 0.2f,
            zeroCrossingRate = 0.1f,
            periodicityEstimate = 0.5f,
            timestampMs = 1000L
        )

        val label = formatter.format("Music", Signal.MUSIC, isConfident = true, currentFeatures = mockFeatures)
        assertTrue(label.startsWith("Music, "))

        // Low confidence fallback
        val unknownLabel = formatter.format("Unknown", Signal.UNKNOWN, isConfident = false, currentFeatures = mockFeatures)
        assertTrue(unknownLabel.startsWith("Unknown, "))
    }

    @Test
    fun testYamNetGateQuietRoomIsIdle() {
        val gate = YamNetGate()

        val quietFeatures = FrameFeatures(
            frameRms = 0.005f,
            baselineRms = 0.005f,
            changeMagnitude = 0.001f,
            isTransient = false,
            envelopeVelocity = 0.0f,
            motion = Motion.STATIC,
            spectralCentroid = 200f,
            spectralBandwidth = 100f,
            spectralRolloff = 400f,
            spectralFlatness = 0.1f,
            zeroCrossingRate = 0.05f,
            periodicityEstimate = 0.0f,
            timestampMs = 1000L
        )

        // Silent room -> YAMNet must be idle (battery contract)
        val shouldInvoke = gate.shouldInvoke(quietFeatures, hasActivePresenceTrack = false, currentTimestampMs = 1000L)
        assertFalse(shouldInvoke)
        assertEquals(GateTriggerReason.NONE, gate.lastTriggerReason)
    }

    @Test
    fun testYamNetGateTriggersOnActivityAndTransient() {
        val gate = YamNetGate()

        val transientFeatures = FrameFeatures(
            frameRms = 0.045f,
            baselineRms = 0.010f,
            changeMagnitude = 0.035f,
            isTransient = true,
            envelopeVelocity = 0.02f,
            motion = Motion.APPROACHING,
            spectralCentroid = 1500f,
            spectralBandwidth = 600f,
            spectralRolloff = 3000f,
            spectralFlatness = 0.3f,
            zeroCrossingRate = 0.15f,
            periodicityEstimate = 0.2f,
            timestampMs = 1000L
        )

        val shouldInvoke = gate.shouldInvoke(transientFeatures, hasActivePresenceTrack = false, currentTimestampMs = 1000L)
        assertTrue(shouldInvoke)
        assertEquals(GateTriggerReason.TRANSIENT_SPIKE, gate.lastTriggerReason)
    }

    @Test
    fun testLabelStabilizerTemporalSmoothing() {
        val labels = listOf("Speech", "Music", "Door", "Unknown")
        val stabilizer = LabelStabilizer(labels = labels, windowDurationMs = 3000L, consecutiveOutputsRequired = 2)

        // Ingest scores strongly indicating Music (index 1)
        val musicScores = floatArrayOf(0.05f, 0.85f, 0.05f, 0.05f)
        val label1 = stabilizer.process(musicScores, isTransient = false, timestampMs = 1000L)
        assertEquals("Music", label1.yamnetClass)
        assertEquals(Signal.MUSIC, label1.taxonomySignal)
    }
}
