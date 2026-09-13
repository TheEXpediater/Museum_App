package com.example.museumapp.ui.visitor.model3d

import io.github.sceneview.math.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JVM coverage for the museum turntable camera math - no Filament engine or Android
 * framework involved (see [OrbitCameraState]'s class doc for why this is possible).
 */
class OrbitCameraStateTest {

    private fun newState(
        target: Position = Position(0f, 0f, 0f),
        yaw: Float = 30f,
        pitch: Float = 20f,
        radius: Float = 2f
    ) = OrbitCameraState(target, yaw, pitch, radius)

    @Test
    fun horizontalDragUpdatesYaw() {
        val state = newState(yaw = 0f)

        state.grabBegin(0, 0, strafe = false)
        state.grabUpdate(100, 0)

        assertTrue("expected yaw to move away from 0, was ${state.yawDegrees}", state.yawDegrees != 0f)
    }

    @Test
    fun draggingRightAndLeftRotateInOppositeDirections() {
        val right = newState(yaw = 90f)
        right.grabBegin(0, 0, strafe = false)
        right.grabUpdate(50, 0)
        val yawAfterRightDrag = right.yawDegrees

        val left = newState(yaw = 90f)
        left.grabBegin(0, 0, strafe = false)
        left.grabUpdate(-50, 0)
        val yawAfterLeftDrag = left.yawDegrees

        assertTrue(yawAfterRightDrag != 90f)
        assertTrue(yawAfterLeftDrag != 90f)
        // A rightward and an equal-magnitude leftward drag move yaw by the same amount in
        // opposite directions from the same starting point.
        assertEquals(90f, (yawAfterRightDrag + yawAfterLeftDrag) / 2f, 0.001f)
    }

    @Test
    fun continuousDragCanOrbitAFull360DegreesAndWraps() {
        val state = newState(yaw = 350f)

        state.grabBegin(0, 0, strafe = false)
        state.grabUpdate(1000, 0) // a large drag, well past the 360 boundary

        assertTrue("yaw must stay within [0, 360)", state.yawDegrees in 0f..359.999f)
    }

    @Test
    fun yawNormalizationWrapsNegativeAndOverflowValues() {
        assertEquals(350f, OrbitCameraState.normalizeYaw(-10f), 0.001f)
        assertEquals(10f, OrbitCameraState.normalizeYaw(370f), 0.001f)
        assertEquals(0f, OrbitCameraState.normalizeYaw(360f), 0.001f)
    }

    @Test
    fun verticalDragOverTheModelNeverChangesPitch() {
        val state = newState(pitch = 15f)

        state.grabBegin(0, 0, strafe = false)
        state.grabUpdate(0, 500) // pure vertical movement, no horizontal component
        state.grabUpdate(0, -500)

        assertEquals(15f, state.pitchDegrees, 0.001f)
    }

    @Test
    fun twoFingerStrafeGrabIsIgnored() {
        val state = newState(yaw = 45f, pitch = 10f)

        state.grabBegin(0, 0, strafe = true)
        state.grabUpdate(200, 200)

        assertEquals(45f, state.yawDegrees, 0.001f)
        assertEquals(10f, state.pitchDegrees, 0.001f)
    }

    @Test
    fun setPitchDegreesClampsToTheSafeRange() {
        val state = newState()

        state.setPitchDegrees(90f)
        assertEquals(OrbitCameraState.PITCH_MAX_DEGREES, state.pitchDegrees, 0.001f)

        state.setPitchDegrees(-90f)
        assertEquals(OrbitCameraState.PITCH_MIN_DEGREES, state.pitchDegrees, 0.001f)

        state.setPitchDegrees(10f)
        assertEquals(10f, state.pitchDegrees, 0.001f)
    }

    @Test
    fun basePitchOutsideRangeIsClampedAtConstruction() {
        val state = newState(pitch = 500f)
        assertEquals(OrbitCameraState.PITCH_MAX_DEGREES, state.pitchDegrees, 0.001f)
    }

    @Test
    fun pinchZoomChangesRadiusWithinClampedBounds() {
        val state = newState(radius = 2f)

        state.scrollBegin(0, 0, 100f)
        state.scrollUpdate(0, 0, 100f, 200f) // fingers spreading apart -> zoom in -> smaller radius
        val radiusAfterZoomIn = state.radius
        assertTrue("expected radius to shrink, was $radiusAfterZoomIn", radiusAfterZoomIn < 2f)

        repeat(200) { state.scrollUpdate(0, 0, 200f, 50f) } // fingers pinching together -> zoom out repeatedly
        assertEquals(state.maxRadius, state.radius, 0.001f)

        repeat(200) { state.scrollUpdate(0, 0, 50f, 500f) } // spread apart repeatedly -> zoom in past the minimum
        assertEquals(state.minRadius, state.radius, 0.001f)
    }

    @Test
    fun radiusNeverReachesZeroOrInfinity() {
        val state = newState(radius = 2f)
        assertTrue(state.minRadius > 0f)
        assertTrue(state.maxRadius < Float.MAX_VALUE)
        assertTrue(state.minRadius < state.maxRadius)
    }

    @Test
    fun resetRestoresTheOriginalDefaultFraming() {
        val state = newState(yaw = 30f, pitch = 20f, radius = 2f)

        state.grabBegin(0, 0, strafe = false)
        state.grabUpdate(300, 0)
        state.setPitchDegrees(-15f)
        state.scrollBegin(0, 0, 100f)
        state.scrollUpdate(0, 0, 100f, 300f)

        assertTrue(state.yawDegrees != 30f || state.pitchDegrees != 20f || state.radius != 2f)

        state.reset()

        assertEquals(30f, state.yawDegrees, 0.001f)
        assertEquals(20f, state.pitchDegrees, 0.001f)
        assertEquals(2f, state.radius, 0.001f)
    }

    @Test
    fun pitchRangeNeverReachesStraightUpOrDown() {
        // Camera inversion (a flipped "upside-down" view) only becomes possible once pitch
        // reaches +/-90 degrees, where the eye sits directly above/below the target and the
        // lookAt up-vector becomes degenerate. The allowed range must stay strictly inside that.
        assertTrue(OrbitCameraState.PITCH_MAX_DEGREES < 90f)
        assertTrue(OrbitCameraState.PITCH_MIN_DEGREES > -90f)

        // And getTransform() must not crash across the full allowed range.
        val state = newState()
        state.setPitchDegrees(OrbitCameraState.PITCH_MAX_DEGREES)
        state.getTransform()
        state.setPitchDegrees(OrbitCameraState.PITCH_MIN_DEGREES)
        state.getTransform()
    }

    @Test
    fun pitchSliderFractionRoundTripsThroughTheSafeRange() {
        assertEquals(0f, pitchToSliderFraction(OrbitCameraState.PITCH_MIN_DEGREES), 0.001f)
        assertEquals(1f, pitchToSliderFraction(OrbitCameraState.PITCH_MAX_DEGREES), 0.001f)
        assertEquals(0.5f, pitchToSliderFraction((OrbitCameraState.PITCH_MIN_DEGREES + OrbitCameraState.PITCH_MAX_DEGREES) / 2f), 0.001f)

        assertEquals(OrbitCameraState.PITCH_MIN_DEGREES, sliderFractionToPitch(0f), 0.001f)
        assertEquals(OrbitCameraState.PITCH_MAX_DEGREES, sliderFractionToPitch(1f), 0.001f)
    }

    @Test
    fun sliderFractionIsClampedOutsideZeroToOne() {
        assertEquals(OrbitCameraState.PITCH_MIN_DEGREES, sliderFractionToPitch(-5f), 0.001f)
        assertEquals(OrbitCameraState.PITCH_MAX_DEGREES, sliderFractionToPitch(5f), 0.001f)
    }
}
