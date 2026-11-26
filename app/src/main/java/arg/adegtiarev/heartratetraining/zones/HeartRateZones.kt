package arg.adegtiarev.heartratetraining.zones

import java.util.Collections

const val ZONE_COUNT = 5
const val MAX_BPM = 65535

data class HeartRateZone(val number: Int, val rgb: Int)

enum class ThresholdError { OutOfRange, OutOfOrder }

fun thresholdErrors(values: List<Int?>): List<ThresholdError?> = values.mapIndexed { index, value ->
    val previous = values.getOrNull(index - 1)?.takeIf { it in 1..MAX_BPM }
    when {
        value == null || value !in 1..MAX_BPM -> ThresholdError.OutOfRange
        previous != null && value <= previous -> ThresholdError.OutOfOrder
        else -> null
    }
}

@ConsistentCopyVisibility
data class ZoneConfiguration private constructor(val thresholds: List<Int>, val colors: List<Int>) {
    fun zoneFor(bpm: Int): HeartRateZone {
        require(bpm >= 0)
        val index = thresholds.count { bpm >= it }
        return HeartRateZone(index + 1, colors[index])
    }

    companion object {
        fun create(thresholds: List<Int>, colors: List<Int>): ZoneConfiguration? {
            if (thresholds.size != ZONE_COUNT - 1 || thresholdErrors(thresholds).any { it != null }) return null
            if (colors.size != ZONE_COUNT || colors.any { it !in 0..0xFFFFFF }) return null
            return ZoneConfiguration(
                Collections.unmodifiableList(thresholds.toList()),
                Collections.unmodifiableList(colors.toList()),
            )
        }
    }
}

enum class MaximumSource { Known, AdultAge }

data class ZoneSuggestion(val maximumTenths: Int, val thresholds: List<Int>)

fun positiveInteger(text: String): Int? =
    text.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()?.takeIf { it > 0 }

fun suggestZones(source: MaximumSource, input: String): ZoneSuggestion? {
    val value = positiveInteger(input) ?: return null
    // Tenths preserve the Tanaka estimate through the final half-up rounding of each threshold.
    val tenths = when (source) {
        MaximumSource.Known -> if (value <= MAX_BPM) value * 10L else return null
        MaximumSource.AdultAge -> if (value >= 18) 2080L - 7L * value else return null
    }
    if (tenths <= 0) return null
    val thresholds = listOf(60, 70, 80, 90).map { ((tenths * it + 500) / 1000).toInt() }
    if (thresholdErrors(thresholds).any { it != null }) return null
    return ZoneSuggestion(tenths.toInt(), thresholds)
}
