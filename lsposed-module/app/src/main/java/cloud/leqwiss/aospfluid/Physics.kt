package cloud.leqwiss.aospfluid

import android.animation.TimeInterpolator
import android.view.animation.Interpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class CubicBezierInterpolator(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float
) : TimeInterpolator, Interpolator {
    override fun getInterpolation(input: Float): Float {
        if (input <= 0f) return 0f
        if (input >= 1f) return 1f
        var parameter = input
        repeat(8) {
            val x = curveX(parameter)
            val difference = x - input
            if (abs(difference) < 0.0001f) return@repeat
            val derivative = curveXDerivative(parameter)
            if (abs(derivative) < 0.000001f) return@repeat
            parameter -= difference / derivative
            parameter = parameter.coerceIn(0f, 1f)
        }
        return curveY(parameter).coerceIn(0f, 1f)
    }

    private fun basis(parameter: Float, start: Float, control: Float, end: Float): Float {
        val inverse = 1f - parameter
        return 3f * inverse * inverse * parameter * start +
            3f * inverse * parameter * parameter * control +
            parameter * parameter * parameter * end
    }

    private fun curveX(parameter: Float): Float = basis(parameter, x1, x2, 1f)

    private fun curveY(parameter: Float): Float = basis(parameter, y1, y2, 1f)

    private fun curveXDerivative(parameter: Float): Float {
        val inverse = 1f - parameter
        return 3f * inverse * inverse * x1 +
            6f * inverse * parameter * (x2 - x1) +
            3f * parameter * parameter * (1f - x2)
    }
}

class SpringInterpolator(
    stiffness: Float,
    private val dampingRatio: Float,
    private val duration: Long
) : TimeInterpolator, Interpolator {
    private val naturalFrequency = sqrt(stiffness)

    override fun getInterpolation(input: Float): Float {
        if (input <= 0f) return 0f
        if (input >= 1f) return 1f
        val time = input * duration / 1000f
        if (dampingRatio < 1f) {
            val dampedFrequency = naturalFrequency * sqrt(1f - dampingRatio * dampingRatio)
            val phase = dampedFrequency * time
            val decay = exp(-dampingRatio * naturalFrequency * time)
            return 1f - decay * (
                cos(phase) +
                    dampingRatio * naturalFrequency / dampedFrequency * sin(phase)
                )
        }
        if (dampingRatio == 1f) {
            val decay = exp(-naturalFrequency * time)
            return 1f - decay * (1f + naturalFrequency * time)
        }
        val overdamped = naturalFrequency * sqrt(dampingRatio * dampingRatio - 1f)
        val decay = exp(-naturalFrequency * dampingRatio * time)
        val hyperbolic = overdamped * time
        return 1f - decay * (
            cosh(hyperbolic.toFloat()) +
                dampingRatio * naturalFrequency / overdamped * sinh(hyperbolic.toFloat())
            )
    }

    private fun cosh(value: Float): Float = ((exp(value) + exp(-value)) / 2f).toFloat()

    private fun sinh(value: Float): Float = ((exp(value) - exp(-value)) / 2f).toFloat()
}

internal fun createInterpolator(profile: AnimationProfile, enter: Boolean): Interpolator {
    return when (profile) {
        AnimationProfile.HYPEROS -> CubicBezierInterpolator(0.20f, 0.00f, 0.00f, 1.00f)
        AnimationProfile.COLOROS -> SpringInterpolator(
            stiffness = 300f,
            dampingRatio = if (enter) 0.80f else 0.86f,
            duration = if (enter) profile.openDuration else profile.closeDuration
        )
        AnimationProfile.ORIGINOS -> SpringInterpolator(
            stiffness = if (enter) 260f else 300f,
            dampingRatio = if (enter) 0.52f else 0.62f,
            duration = if (enter) profile.openDuration else profile.closeDuration
        )
    }
}
