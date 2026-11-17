package com.thiefalertsequre.app

enum class IntegrityStatus { VERIFIED, FAILED, UNKNOWN, UNSUPPORTED }

data class IntegritySignal(
    val label: String,
    val status: IntegrityStatus,
    val weight: Int
)

data class ProtectionIntegrityScore(
    val percent: Int,
    val earnedPoints: Int,
    val possiblePoints: Int,
    val verifiedCount: Int,
    val failed: List<String>,
    val unconfirmed: List<String>,
    val unsupported: List<String>
)

/** Transparent score over checks that the app can actually verify on this device. */
object ProtectionIntegrityPolicy {
    fun calculate(signals: List<IntegritySignal>): ProtectionIntegrityScore {
        require(signals.all { it.weight > 0 })
        require(signals.map { it.label }.distinct().size == signals.size)
        val applicable = signals.filter { it.status != IntegrityStatus.UNSUPPORTED }
        val possible = applicable.sumOf { it.weight }
        val earned = applicable.filter { it.status == IntegrityStatus.VERIFIED }.sumOf { it.weight }
        return ProtectionIntegrityScore(
            percent = if (possible == 0) 0 else (earned * 100 / possible).coerceIn(0, 100),
            earnedPoints = earned,
            possiblePoints = possible,
            verifiedCount = signals.count { it.status == IntegrityStatus.VERIFIED },
            failed = signals.filter { it.status == IntegrityStatus.FAILED }.map { it.label },
            unconfirmed = signals.filter { it.status == IntegrityStatus.UNKNOWN }.map { it.label },
            unsupported = signals.filter { it.status == IntegrityStatus.UNSUPPORTED }.map { it.label }
        )
    }
}
