package cloud.leqwiss.aospfluid

import android.view.animation.Animation
import android.view.animation.Transformation
import kotlin.math.roundToLong

class FluidAnimation(
    val profile: AnimationProfile,
    val enter: Boolean,
    private val speed: Float
) : Animation() {
    private var pivotX = 0f
    private var pivotY = 0f

    init {
        duration = ((if (enter) profile.openDuration else profile.closeDuration) / speed)
            .roundToLong()
            .coerceAtLeast(120L)
        setInterpolator(createInterpolator(profile, enter))
    }

    override fun initialize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
        super.initialize(width, height, parentWidth, parentHeight)
        pivotX = parentWidth / 2f
        pivotY = parentHeight / 2f
    }

    override fun applyTransformation(interpolatedTime: Float, transformation: Transformation) {
        val progress = interpolatedTime.coerceIn(0f, 1f)
        val startScale = if (enter) profile.openScale else 1f
        val endScale = if (enter) 1f else profile.closeScale
        val scale = startScale + (endScale - startScale) * progress
        val travel = if (enter) profile.openTravel else profile.closeTravel
        val travelPixels = pivotY * travel * (if (enter) 1f - progress else progress)

        val matrix = transformation.matrix
        matrix.reset()
        matrix.setScale(scale, scale, pivotX, pivotY)
        if (profile == AnimationProfile.ORIGINOS) {
            val depth = 1f - progress
            matrix.postSkew(-0.014f * depth, 0.009f * depth, pivotX, pivotY)
        }
        matrix.postTranslate(0f, travelPixels)

        transformation.alpha = if (enter) {
            0.28f + 0.72f * progress
        } else {
            1f - 0.80f * progress
        }
    }

    override fun willChangeTransformationMatrix(): Boolean = true

    override fun willChangeBounds(): Boolean = false
}
