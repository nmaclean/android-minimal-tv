package com.nmaclean.minimaltv

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        // A release without pre-release tag has higher precedence than a pre-release.
        return when {
            preRelease == null && other.preRelease == null -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> comparePreRelease(preRelease, other.preRelease)
        }
    }

    private fun comparePreRelease(a: String, b: String): Int {
        val partsA = a.split(".")
        val partsB = b.split(".")
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val partA = partsA.getOrNull(i) ?: return -1
            val partB = partsB.getOrNull(i) ?: return 1
            val numA = partA.toIntOrNull()
            val numB = partB.toIntOrNull()
            val cmp = when {
                numA != null && numB != null -> numA.compareTo(numB)
                numA != null -> -1 // Numeric identifiers have lower precedence than non-numeric.
                numB != null -> 1
                else -> partA.compareTo(partB)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    companion object {
        private val SEMVER_REGEX = Regex("""^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        fun parse(raw: String): SemVer? {
            val trimmed = raw.trim()
            val match = SEMVER_REGEX.matchEntire(trimmed) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: return null
            val patch = match.groupValues[3].ifEmpty { "0" }.toIntOrNull() ?: return null
            val preRelease = match.groupValues[4].ifEmpty { null }
            return SemVer(major, minor, patch, preRelease)
        }

        fun isNewer(candidate: String, current: String): Boolean {
            val candVer = parse(candidate) ?: return false
            val currVer = parse(current) ?: return false
            return candVer > currVer
        }
    }
}
