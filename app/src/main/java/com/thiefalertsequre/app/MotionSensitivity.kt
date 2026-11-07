package com.thiefalertsequre.app

import kotlin.math.sqrt

enum class MotionSensitivity(val thresholdMetersPerSecondSquared: Float) {
    LOW(4.0f),
    MEDIUM(2.4f),
    HIGH(1.2f);

    companion object {
        fun fromStored(value: String?): MotionSensitivity =
            values().firstOrNull { it.name == value } ?: MEDIUM
    }
}

object MotionDetection {
    fun magnitude(x: Float, y: Float, z: Float): Float =
        sqrt(x * x + y * y + z * z)

    fun exceedsThreshold(x: Float, y: Float, z: Float, sensitivity: MotionSensitivity): Boolean =
        magnitude(x, y, z) >= sensitivity.thresholdMetersPerSecondSquared
}
