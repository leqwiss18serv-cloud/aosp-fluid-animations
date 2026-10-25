package cloud.leqwiss.aospfluid

import android.graphics.Matrix
import android.view.animation.Animation
import android.view.animation.Transformation
import kotlin.math.roundToLong

class FluidAnimation(
    val profile: AnimationProfile,
    val enter: Boolean,
    private val speed: Float
) : Animation() {
    private val interpolator = createInterpolator(profile, enter)
    private var pivotX = 0f
    private var pivotY = 0f

    init {
        duration = ((if (enter) profile.openDuration else profile.closeDuration) / speed)
            .roundToLong()
            .coerceAtLeast(90L)
        this.interpolator?.let { setInterpolator(it) }
    }

    override fun initialize(
        width: Int,
        height: Int,
        parentWidth: Int,
        parentHeight: Int
    ) {
        super.initialize(width, height, parentWidth, parentHeight)
        pivotX = parentWidth / 2f
        pivotY = parentHeight / 2f
    }

    override fun applyTransformation(interpolatedTime: Float, transformation: Transformation) {
        val rawProgress = interpolator.getInterpolation(interpolatedTime)
        val clampedProgress = rawProgress.coerceIn(0f, 1f)
        val startScale = if (enter) profile.openScale else 1f
        val endScale = if (enter) 1f else profile.closeScale
        val scale = startScale + (endScale - startScale) * clampedProgress
        val travel = if (enter) profile.openTravel else profile.closeTravel
        val travelPixels = pivotY * travel * (if (enter) 1f - clampedProgress else clampedProgress)

        val matrix = transformation.matrix
        matrix.reset()
        matrix.setScale(scale, scale, pivotX, pivotY)
        if (profile == AnimationProfile.ORIGINOS) {
            val depth = 1f - clampedProgress
            matrix.postSkew(-0.018f * depth, 0.012f * depth, pivotX, pivotY)
        }
        matrix.postTranslate(0f, travelPixels)

        val alpha = if (enter) {
            0.42f + 0.58f * clampedProgress
        } else {
            1f - 0.72f * clampedProgress
        }
        transformation.alpha = alpha.coerceIn(0f, 1f)
    }

    override fun willChangeTransformationMatrix(): Boolean = true

    override fun willChangeBounds(): Boolean = false
}
