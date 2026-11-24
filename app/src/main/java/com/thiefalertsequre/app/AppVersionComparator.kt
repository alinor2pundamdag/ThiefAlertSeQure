package com.thiefalertsequre.app

internal object AppVersionComparator {
    private val versionPattern = Regex("^[0-9]+(\\.[0-9]+){1,3}$")

    fun compare(left: String, right: String): Int {
        require(versionPattern.matches(left) && versionPattern.matches(right)) {
            "Versions must contain two to four numeric components."
        }
        val a = left.split(".")
        val b = right.split(".")
        for (index in 0 until maxOf(a.size, b.size)) {
            val leftPart = a.getOrElse(index) { "0" }.trimStart('0').ifEmpty { "0" }
            val rightPart = b.getOrElse(index) { "0" }.trimStart('0').ifEmpty { "0" }
            val comparison = leftPart.length.compareTo(rightPart.length).takeIf { it != 0 }
                ?: leftPart.compareTo(rightPart)
            if (comparison != 0) return comparison
        }
        return 0
    }
}
