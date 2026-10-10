package com.jupiter.vision

import com.jupiter.vision.dotpro.audio.RefinementPipeline
import com.jupiter.vision.dotpro.classifier.DisplayLabelFormatter
import com.jupiter.vision.dotpro.classifier.GateTriggerReason
import com.jupiter.vision.dotpro.classifier.LabelStabilizer
import com.jupiter.vision.dotpro.classifier.YamNetGate
import com.jupiter.vision.dotpro.classifier.YamNetToTaxonomy
import com.jupiter.vision.dotpro.features.FrameFeatures
import com.jupiter.vision.dotpro.model.AmbientState
import com.jupiter.vision.dotpro.model.Motion
import com.jupiter.vision.dotpro.model.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

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

    @Test
    fun testRefinementPipelineProcessingAndDownsampling() {
        val pipeline = RefinementPipeline()

        // 2400 samples (50ms @ 48kHz) with DC offset and 100 Hz tone
        val raw48k = FloatArray(2400) { i ->
            0.15f + 0.05f * sin(2.0 * PI * 100.0 * i / 48000.0).toFloat()
        }

        val output = pipeline.process(raw48k)

        // Verifies output dimensions
        assertEquals(2400, output.refined48kSamples.size)
        assertEquals(800, output.refined16kSamples.size)

        // Verifies DC blocking and limiter (< 1.0f)
        assertTrue(output.refinedRms > 0f)
        assertTrue(output.estimatedSnrDb > -100f)
        for (s in output.refined48kSamples) {
            assertTrue(s <= 1.0f && s >= -1.0f)
        }
    }

    @Test
    fun testScaleNormalizationAndClamping() {
        val pipeline = RefinementPipeline()

        // Feed quiet room audio (amplitude ~ 0.005)
        val quietAudio = FloatArray(2400) { i ->
            0.005f * sin(2.0 * PI * 440.0 * i / 48000.0).toFloat()
        }

        var lastOutput = pipeline.process(quietAudio)
        for (i in 0 until 50) {
            lastOutput = pipeline.process(quietAudio)
        }

        // Offset must be positive lift and clamped within [-6 dB, +40 dB]
        assertTrue(lastOutput.normalizationOffsetDb >= -6.0f)
        assertTrue(lastOutput.normalizationOffsetDb <= 40.0f)
        assertTrue(lastOutput.referenceLoudnessDb < 0f)
    }

    @Test
    fun testSnrGateSilentStateProtection() {
        val pipeline = RefinementPipeline()

        // Feed near-zero noise audio
        val silenceAudio = FloatArray(2400) { 0.0001f }

        var output = pipeline.process(silenceAudio)
        for (i in 0 until 50) {
            output = pipeline.process(silenceAudio)
        }

        // Silent gate must close on sustained low SNR
        assertTrue(output.isSilentGateClosed)
        // In silent gate, band lifts must be disabled (0 dB)
        for (lift in output.bandLiftsDb) {
            assertEquals(0f, lift, 0.01f)
        }
    }

    @Test
    fun testThreeStateAmbientModelTransitions() {
        val gate = YamNetGate()

        val quietFeatures = FrameFeatures(
            frameRms = 0.002f,
            baselineRms = 0.002f,
            changeMagnitude = 0.0f,
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

        // Feed quiet frames below floor until SILENT state locks in
        var time = 1000L
        for (i in 0 until 40) {
            time += 50L
            gate.evaluate(
                features = quietFeatures,
                refinedRms = 0.002f,
                hardwareFloorRms = 0.003f,
                hasActivePresenceTrack = false,
                currentTimestampMs = time,
                snrDb = 1.0f,
                isSilentGateClosed = true
            )
        }
        assertEquals(AmbientState.SILENT, gate.currentState)

        // When ambient room tone appears (> floor, SNR > 3dB)
        for (i in 0 until 15) {
            time += 50L
            gate.evaluate(
                features = quietFeatures,
                refinedRms = 0.015f,
                hardwareFloorRms = 0.003f,
                hasActivePresenceTrack = false,
                currentTimestampMs = time,
                snrDb = 15.0f,
                isSilentGateClosed = false
            )
        }
        assertEquals(AmbientState.AMBIENT, gate.currentState)

        // When active presence track begins
        time += 50L
        gate.evaluate(
            features = quietFeatures,
            refinedRms = 0.030f,
            hardwareFloorRms = 0.003f,
            hasActivePresenceTrack = true,
            currentTimestampMs = time,
            snrDb = 20.0f,
            isSilentGateClosed = false
        )
        assertEquals(AmbientState.ACTIVE, gate.currentState)
    }
}
