package com.nandroid.wavecodev1.wavecode

import kotlin.random.Random

/**
 * Generates random publicCodes compatible with the WaveCode format.
 *
 * Does NOT change the binary format — it only produces strings that [WaveCodeEncoder] accepts:
 * exactly [WaveCodeSpec.PAYLOAD_LENGTH] characters, all drawn from [WaveCodeSpec.ALPHABET]
 * (the 32-char unambiguous set, no 0/1/I/O).
 */
object PublicCodeGenerator {

    /** Returns a random valid publicCode. */
    fun generate(random: Random = Random.Default): String {
        val alphabet = WaveCodeSpec.ALPHABET
        return buildString(WaveCodeSpec.PAYLOAD_LENGTH) {
            repeat(WaveCodeSpec.PAYLOAD_LENGTH) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
    }

    /**
     * Returns a random publicCode not already used, per [isTaken].
     * Falls back to the last generated code after [maxAttempts] (collisions are astronomically
     * unlikely with 32^8 ≈ 1.1e12 codes, so this guard is just defensive).
     */
    fun generateUnique(isTaken: (String) -> Boolean, maxAttempts: Int = 20): String {
        var code = generate()
        var attempts = 0
        while (isTaken(code) && attempts < maxAttempts) {
            code = generate()
            attempts++
        }
        return code
    }
}