package arg.adegtiarev.heartratetraining.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateMeasurementTest {
    @Test fun readsUnsignedBpmInBothFormats() {
        assertEquals(HeartRateMeasurement.Pulse(72), decodeHeartRate(byteArrayOf(0, 0x48)))
        assertEquals(HeartRateMeasurement.Pulse(255), decodeHeartRate(byteArrayOf(0, -1)))
        assertEquals(HeartRateMeasurement.Pulse(300), decodeHeartRate(byteArrayOf(1, 0x2c, 1)))
    }

    @Test fun readsBpmBeforeEnergyAndIntervals() {
        assertEquals(HeartRateMeasurement.Pulse(72), decodeHeartRate(byteArrayOf(0x10, 72, 0, 4)))
        assertEquals(HeartRateMeasurement.Pulse(300),
            decodeHeartRate(byteArrayOf(0x19, 0x2c, 1, 5, 0, 0, 4, 0, 3)))
    }

    @Test fun clearsPulseOnlyWhenContactDetectionReportsLoss() {
        assertEquals(HeartRateMeasurement.ContactLost, decodeHeartRate(byteArrayOf(4, 72)))
        for (flags in listOf(0, 2, 6)) {
            assertEquals(HeartRateMeasurement.Pulse(72), decodeHeartRate(byteArrayOf(flags.toByte(), 72)))
        }
    }

    @Test fun rejectsTruncatedOrInconsistentMeasurements() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(0), byteArrayOf(1, 72),
            byteArrayOf(8, 72, 0), byteArrayOf(16, 72), byteArrayOf(16, 72, 1),
            byteArrayOf(0, 72, 0))) {
            assertNull(decodeHeartRate(bytes))
        }
    }
}
