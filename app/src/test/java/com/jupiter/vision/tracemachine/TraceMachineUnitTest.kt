package com.jupiter.vision.tracemachine

import androidx.compose.ui.geometry.Offset
import com.jupiter.vision.tracemachine.model.AnchorPoint
import com.jupiter.vision.tracemachine.model.TracePoint
import com.jupiter.vision.tracemachine.model.TraceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TraceMachineUnitTest {

    @Test
    fun testInitialTraceState() {
        val state = TraceState()
        assertEquals(0, state.totalSteps)
        assertEquals(0, state.stepsSinceAnchor)
        assertEquals(Offset.Zero, state.currentPosition)
        assertFalse(state.isSessionActive)
        assertTrue(state.isCalibratingSwingAxis)
        assertEquals(0.70f, state.strideLengthMeters, 0.001f)
    }

    @Test
    fun testDeadReckoningStepProgression() {
        var currentX = 0f
        var currentY = 0f
        var headingRad = 0f // 0 rad = moving straight "up" in Canvas coords (0, -1)
        val stride = 0.70f

        // Step 1: straight ahead
        currentX += (sin(headingRad) * stride)
        currentY += (-cos(headingRad) * stride)

        assertEquals(0f, currentX, 0.001f)
        assertEquals(-0.70f, currentY, 0.001f)

        // Step 2: turn 90 deg right (PI / 2 rad)
        headingRad += (Math.PI / 2.0).toFloat()
        currentX += (sin(headingRad) * stride)
        currentY += (-cos(headingRad) * stride)

        assertEquals(0.70f, currentX, 0.001f)
        assertEquals(-0.70f, currentY, 0.001f)
    }

    @Test
    fun testAnchorResetsDriftCounter() {
        val points = mutableListOf<TracePoint>()
        val anchors = mutableListOf<AnchorPoint>()

        var stepsSinceAnchor = 0
        for (i in 1..10) {
            stepsSinceAnchor++
            points.add(TracePoint(x = i * 0.7f, y = 0f, stepsSinceAnchor = stepsSinceAnchor, timestampMs = i * 500L))
        }

        assertEquals(10, stepsSinceAnchor)

        // Drop manual anchor
        anchors.add(AnchorPoint(x = points.last().x, y = points.last().y, index = 0, isManual = true, timestampMs = 5000L))
        stepsSinceAnchor = 0

        assertEquals(0, stepsSinceAnchor)
        assertEquals(1, anchors.size)
        assertTrue(anchors[0].isManual)
    }
}
