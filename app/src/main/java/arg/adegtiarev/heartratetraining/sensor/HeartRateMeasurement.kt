package arg.adegtiarev.heartratetraining.sensor

sealed interface HeartRateMeasurement {
    data class Pulse(val bpm: Int) : HeartRateMeasurement
    data object ContactLost : HeartRateMeasurement
}

/** Decodes the standard Heart Rate Measurement characteristic. Null means malformed. */
fun decodeHeartRate(bytes: ByteArray): HeartRateMeasurement? {
    if (bytes.isEmpty()) return null
    val flags = bytes[0].toInt()
    val wide = flags and 0x01 != 0
    val bpmEnd = if (wide) 3 else 2
    val rrStart = bpmEnd + if (flags and 0x08 != 0) 2 else 0
    val rrBytes = bytes.size - rrStart
    val hasIntervals = flags and 0x10 != 0
    if (hasIntervals && (rrBytes < 2 || rrBytes % 2 != 0)) return null
    if (!hasIntervals && rrBytes != 0) return null
    if (flags and 0x06 == 0x04) return HeartRateMeasurement.ContactLost
    val low = bytes[1].toInt() and 0xff
    val bpm = if (wide) low or ((bytes[2].toInt() and 0xff) shl 8) else low
    return HeartRateMeasurement.Pulse(bpm)
}
