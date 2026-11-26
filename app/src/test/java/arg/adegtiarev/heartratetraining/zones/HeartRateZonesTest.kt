package arg.adegtiarev.heartratetraining.zones

import arg.adegtiarev.heartratetraining.sensor.PulseSample
import arg.adegtiarev.heartratetraining.sensor.SensorState
import arg.adegtiarev.heartratetraining.ui.HomeUiState
import arg.adegtiarev.heartratetraining.ui.ZoneDraft
import arg.adegtiarev.heartratetraining.ui.parseRgb
import org.junit.Assert.*
import org.junit.Test

class HeartRateZonesTest {
    private val manual = listOf(108, 126, 144, 162)
    private val colors = listOf(0, 0x113355, 0xFFFFFF, 0x113355, 0x441122)

    @Test fun manualAndBothCalculatedSetsIncludeEachThresholdInTheHigherZone() {
        for (starts in listOf(manual, suggestZones(MaximumSource.Known, "180")!!.thresholds,
            suggestZones(MaximumSource.AdultAge, "35")!!.thresholds)) {
            val zones = ZoneConfiguration.create(starts, colors)!!
            for ((index, start) in starts.withIndex()) {
                assertEquals(HeartRateZone(index + 1, colors[index]), zones.zoneFor(start - 1))
                for (bpm in listOf(start, start + 1)) assertEquals(HeartRateZone(index + 2, colors[index + 1]), zones.zoneFor(bpm))
            }
            assertEquals(1, zones.zoneFor(0).number)
            for (bpm in listOf(180, 181, 65535)) assertEquals(5, zones.zoneFor(bpm).number)
        }
    }

    @Test fun estimatedMaximumIsRoundedOnlyAtTheFinalThreshold() {
        assertEquals(ZoneSuggestion(1800, manual), suggestZones(MaximumSource.Known, "180"))
        assertEquals(ZoneSuggestion(1800, manual), suggestZones(MaximumSource.AdultAge, "40"))
        assertEquals(ZoneSuggestion(1835, listOf(110, 128, 147, 165)), suggestZones(MaximumSource.AdultAge, "35"))
        assertEquals(listOf(63, 74, 84, 95), suggestZones(MaximumSource.Known, "105")!!.thresholds)
        for (bad in listOf("", "0", "-1", "2.5", "1", "65536", "3000000000")) assertNull(suggestZones(MaximumSource.Known, bad))
        for (bad in listOf("17", "350", "2147483647")) assertNull(suggestZones(MaximumSource.AdultAge, bad))
    }

    @Test fun manualValidationAndRangePreviewsFollowTheSameFourStarts() {
        val config = ZoneConfiguration.create(manual, colors)!!
        val draft = ZoneDraft.from(config)
        assertEquals(config, draft.configuration())
        assertEquals(listOf(0 to 107, 108 to 125, 126 to 143, 144 to 161, 162 to null),
            (0 until ZONE_COUNT).map(draft::range))
        for (bad in listOf("", "0", "-1", "10.5", "65536", "4294967297", "+108")) {
            val invalid = draft.copy(thresholds = listOf(bad, "126", "144", "162"))
            assertEquals(ThresholdError.OutOfRange, invalid.errors.first())
            assertNull(invalid.errors[1])
            assertNull(invalid.configuration())
        }
        for (bad in listOf("108", "107")) {
            val invalid = draft.copy(thresholds = listOf("108", bad, "144", "162"))
            assertEquals(ThresholdError.OutOfOrder, invalid.errors[1])
            assertNull(invalid.range(1))
            assertNull(invalid.configuration())
        }
        assertNull(ZoneConfiguration.create(manual.drop(1), colors))
        assertNull(ZoneConfiguration.create(manual, colors + 2))
        assertNull(ZoneConfiguration.create(manual, listOf(-1) + colors.drop(1)))
        assertEquals(5, ZoneConfiguration.create(listOf(1, 2, 3, MAX_BPM), colors)!!.zoneFor(MAX_BPM).number)
        assertEquals(0x00AAFF, parseRgb("#00aAfF"))
        for (bad in listOf("0000", "ZZ0000", "00-100", "12345678")) assertNull(parseRgb(bad))
    }

    @Test fun zeroIsAReadingAndMissingSettingsOrSamplesHaveNoZone() {
        val settings = ZoneSettings.Saved(ZoneConfiguration.create(manual, colors)!!)
        val sensor = SensorState(sample = PulseSample(0, 0))
        assertEquals(1, HomeUiState(sensor, settings).zone?.number)
        assertNull(HomeUiState(sensor, ZoneSettings.Unconfigured).zone)
        assertNull(HomeUiState(sensor, ZoneSettings.ReadError).zone)
        assertNull(HomeUiState(SensorState(), settings).zone)
        val steady = sensor.copy(sample = PulseSample(130, 10))
        assertEquals(3, HomeUiState(steady, settings).zone?.number)
        val edited = ZoneSettings.Saved(ZoneConfiguration.create(listOf(108, 135, 144, 162), colors)!!)
        assertEquals(HeartRateZone(2, colors[1]), HomeUiState(steady, edited).zone)
    }
}
