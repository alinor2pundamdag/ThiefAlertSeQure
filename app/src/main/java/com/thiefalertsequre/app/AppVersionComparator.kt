package com.thiefalertsequre.app

internal object AppVersionComparator {
    private val versionPattern = Regex("^[0-9]+(\\.[0-9]+){1,3}$")

    fun compare(left: String, right: String): Int {
        require(versionPattern.matches(left) && versionPattern.matches(right)) {
            "Versions must contain two to four numeric components."
        }
        val a = left.split(".").map { it.toInt() }
        val b = right.split(".").map { it.toInt() }
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }
}
