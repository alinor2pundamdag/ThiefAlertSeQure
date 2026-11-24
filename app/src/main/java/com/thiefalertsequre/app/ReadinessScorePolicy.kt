package com.thiefalertsequre.app

data class ReadinessSignal(val label: String, val satisfied: Boolean, val weight: Int)

data class ReadinessScore(val points: Int, val possiblePoints: Int, val missing: List<String>) {
    val percent: Int get() = if (possiblePoints <= 0) 0 else (points * 100 / possiblePoints).coerceIn(0, 100)
}

/** Transparent setup-readiness score, not a security/integrity guarantee. */
object ReadinessScorePolicy {
    fun calculate(signals: List<ReadinessSignal>): ReadinessScore {
        require(signals.all { it.weight > 0 })
        val possible = signals.sumOf { it.weight }
        val earned = signals.filter { it.satisfied }.sumOf { it.weight }
        return ReadinessScore(
            points = earned,
            possiblePoints = possible,
            missing = signals.filterNot { it.satisfied }.map { it.label }
        )
    }
}
