package com.nandroid.wavecodev1.wavecode

object WaveCodeEncoder {

    fun encode(publicCode: String): Result<WaveCodeData> {
        if (publicCode.length != WaveCodeSpec.PAYLOAD_LENGTH) {
            return Result.failure(
                IllegalArgumentException(
                    "Must be exactly ${WaveCodeSpec.PAYLOAD_LENGTH} characters (got ${publicCode.length})"
                )
            )
        }

        val invalidChars = publicCode.filter { it !in WaveCodeSpec.ALPHABET }
        if (invalidChars.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException(
                    "Invalid character(s): \"$invalidChars\"\nAllowed: ${WaveCodeSpec.ALPHABET}"
                )
            )
        }

        val indices = publicCode.map { WaveCodeSpec.ALPHABET.indexOf(it) }

        // 5 bits per character, MSB first
        val payloadBits = indices.joinToString("") { it.toBits(5) }

        // checksum: sum of indices mod 256, 8 bits MSB first
        val checksum = indices.sum() % 256
        val checksumBits = checksum.toBits(WaveCodeSpec.CHECKSUM_BITS)

        val bits = WaveCodeSpec.START_MARKER +
                WaveCodeSpec.VERSION_BITS +
                payloadBits +
                checksumBits +
                WaveCodeSpec.END_MARKER

        return Result.success(WaveCodeData(publicCode, bits, checksum))
    }

    // Returns an n-bit binary string, MSB first, truncated to n bits if value overflows.
    private fun Int.toBits(n: Int): String =
        Integer.toBinaryString(this).padStart(n, '0').takeLast(n)
}
