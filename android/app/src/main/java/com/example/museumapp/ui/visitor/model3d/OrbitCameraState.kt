package com.example.museumapp.ui.visitor.model3d

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.lookAt
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.math.Position
import io.github.sceneview.math.Transform
import kotlin.math.cos
import kotlin.math.sin

/**
 * Constrained "museum turntable" camera: continuous 360-degree yaw from one-finger horizontal
 * drag, pitch controlled ONLY by an external elevation slider (never by touch), and radius from
 * pinch zoom. Always moves the CAMERA around a fixed [target] - the model node itself is never
 * rotated (see [ArtifactModel3DScreen]'s Model3DViewerContent).
 *
 * Implements [CameraGestureDetector.CameraManipulator] directly instead of wrapping Filament's
 * own `com.google.android.filament.utils.Manipulator` (the pinned SceneView 2.3.0's default -
 * see CameraGestureDetector.DefaultCameraManipulator): that manipulator is a free trackball that
 * changes both yaw AND pitch from a one-finger drag via its own internal quaternion math, which
 * cannot be constrained to "yaw only" without fighting it. Implementing the plain
 * grabBegin/grabUpdate/grabEnd/scroll/getTransform interface instead gives full control over
 * what a drag actually changes.
 *
 * Pure Kotlin/math - no Android or Filament engine dependency - so the camera math is unit
 * testable on the plain JVM (see OrbitCameraStateTest).
 */
class OrbitCameraState(
    val target: Position,
    baseYawDegrees: Float,
    basePitchDegrees: Float,
    baseRadius: Float
) : CameraGestureDetector.CameraManipulator {

    private val baseYawDegrees = normalizeYaw(baseYawDegrees)
    private val basePitchDegrees = basePitchDegrees.coerceIn(PITCH_MIN_DEGREES, PITCH_MAX_DEGREES)
    private val baseRadius = baseRadius.coerceAtLeast(MIN_BASE_RADIUS)

    /** Zoom limits: derived from the default framing distance, not the raw model bounds, since
     * every model is already normalized to comparable size before this camera is built (see
     * `scaleToUnits` in Model3DViewerContent). Keeps the artifact from ever being entered or
     * lost entirely. */
    val minRadius: Float = this.baseRadius * RADIUS_MIN_FACTOR
    val maxRadius: Float = this.baseRadius * RADIUS_MAX_FACTOR

    var yawDegrees: Float = this.baseYawDegrees
        private set
    var pitchDegrees: Float = this.basePitchDegrees
        private set
    var radius: Float = this.baseRadius
        private set

    private var isGrabbing = false
    private var isStrafeGrab = false
    private var lastGrabX = 0
    private var lastGrabY = 0

    /** Driven by the on-screen elevation slider only - never by a touch drag on the model. */
    fun setPitchDegrees(degrees: Float) {
        pitchDegrees = degrees.coerceIn(PITCH_MIN_DEGREES, PITCH_MAX_DEGREES)
    }

    /** Restores the default yaw/pitch/radius this state was built with. Never touches the model
     * (no reload/redownload is implied - this only changes camera numbers). */
    fun reset() {
        yawDegrees = baseYawDegrees
        pitchDegrees = basePitchDegrees
        radius = baseRadius
    }

    override fun setViewport(width: Int, height: Int) {
        // No-op: yaw/zoom sensitivity here are plain pixel-delta constants, not viewport-relative.
    }

    override fun getTransform(): Transform {
        val yawRad = Math.toRadians(yawDegrees.toDouble())
        val pitchRad = Math.toRadians(pitchDegrees.toDouble())
        val cosPitch = cos(pitchRad).toFloat()
        val eye = Float3(
            x = target.x + radius * cosPitch * sin(yawRad).toFloat(),
            y = target.y + radius * sin(pitchRad).toFloat(),
            z = target.z + radius * cosPitch * cos(yawRad).toFloat()
        )
        return lookAt(eye = eye, target = Float3(target.x, target.y, target.z), up = Float3(y = 1f))
    }

    /** One-finger drag (strafe=false) orbits yaw; a two-finger pan drag (strafe=true) is ignored
     * outright - this viewer intentionally has no pan, only orbit/zoom/slider-elevation. */
    override fun grabBegin(x: Int, y: Int, strafe: Boolean) {
        isGrabbing = true
        isStrafeGrab = strafe
        lastGrabX = x
        lastGrabY = y
    }

    override fun grabUpdate(x: Int, y: Int) {
        if (!isGrabbing || isStrafeGrab) {
            lastGrabX = x
            lastGrabY = y
            return
        }
        val deltaX = (x - lastGrabX).toFloat()
        yawDegrees = normalizeYaw(yawDegrees + deltaX * YAW_DEGREES_PER_PIXEL)
        lastGrabX = x
        lastGrabY = y
        // Vertical finger delta (y) is deliberately never read here - pitch only ever changes via
        // setPitchDegrees(), so a vertical drag over the model cannot flip or orbit the camera.
    }

    override fun grabEnd() {
        isGrabbing = false
        isStrafeGrab = false
    }

    override fun scrollBegin(x: Int, y: Int, separation: Float) {
        // Nothing to seed - scrollUpdate already receives both the previous and current
        // separation on every call.
    }

    override fun scrollUpdate(x: Int, y: Int, prevSeparation: Float, currSeparation: Float) {
        val separationDelta = currSeparation - prevSeparation
        val factor = 1f - separationDelta * ZOOM_SENSITIVITY
        radius = (radius * factor).coerceIn(minRadius, maxRadius)
    }

    override fun scrollEnd() {
        // Nothing to tear down.
    }

    override fun update(deltaTime: Float) {
        // No physics/damping - getTransform() is a pure function of the current yaw/pitch/radius.
    }

    companion object {
        const val PITCH_MIN_DEGREES = -20f
        const val PITCH_MAX_DEGREES = 60f
        private const val YAW_DEGREES_PER_PIXEL = 0.3f
        private const val ZOOM_SENSITIVITY = 0.002f
        private const val RADIUS_MIN_FACTOR = 0.4f
        private const val RADIUS_MAX_FACTOR = 3.5f
        private const val MIN_BASE_RADIUS = 0.01f

        /** Wraps into [0, 360) so continuous 360-degree rotation never overflows or needs an
         * arbitrary reset at the wrap boundary. */
        fun normalizeYaw(degrees: Float): Float {
            val wrapped = degrees % 360f
            return if (wrapped < 0f) wrapped + 360f else wrapped
        }
    }
}
