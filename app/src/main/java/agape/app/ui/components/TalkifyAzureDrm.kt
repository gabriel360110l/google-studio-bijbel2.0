package agape.app.ui.components

import java.util.Locale
import kotlin.math.floor

/**
 * Ported directly from LonePheasantWarrior/TalkifyTTS (`AzureDrm.kt`).
 *
 * Generates the Microsoft Edge / Azure ReadAloud `Sec-MS-GEC` v2 token (`<16-char CS64>-<64-char fused SHA256>`)
 * required by `wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1` with
 * `Sec-MS-GEC-Version=2-143.0.3650.75`.
 */
object TalkifyAzureDrm {
    const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
    const val CHROMIUM_MAJOR_VERSION = "143"
    const val SEC_MS_GEC_VERSION = "2-$CHROMIUM_FULL_VERSION"

    private const val WIN_EPOCH = 11644473600.0
    private const val WINDOW_SECONDS = 300.0
    private const val SECONDS_TO_TICKS = 1e7
    private const val U32_MASK = 0xFFFFFFFFL

    fun generateSecMsGecV2(unixTimestampSeconds: Double = System.currentTimeMillis() / 1000.0): String {
        var ticks = unixTimestampSeconds + WIN_EPOCH
        ticks -= ticks - WINDOW_SECONDS * floor(ticks / WINDOW_SECONDS)
        ticks *= SECONDS_TO_TICKS
        val strToHash = String.format(Locale.US, "%.0f", ticks) + TRUSTED_CLIENT_TOKEN
        val data = strToHash.toByteArray(Charsets.US_ASCII)
        val digest = sha256Digest(data)
        val cs64 = cs64Digest(data, digest)
        return le32Hex(cs64.first) + le32Hex(cs64.second) + "-" + digest.toHexUpper()
    }

    private fun cs64Digest(data: ByteArray, digest: ByteArray): Pair<Long, Long> {
        var keyA = leU32(digest, 0)
        var keyB = leU32(digest, 4)
        keyA = keyA or 1L
        keyB = keyB or 1L

        val factors = longArrayOf((keyA * CS64_FACTOR) and U32_MASK, (keyB * CS64_FACTOR) and U32_MASK)
        val words = wordsLe4Even(prependZeroByte(data))
        val coefficientCount = CS64_COEFFICIENTS.size
        val total = coefficientCount + words.size
        val terms = LongArray(total)
        val offsets = LongArray(total)

        for (index in 0 until coefficientCount) {
            val (coefficient, offset) = CS64_COEFFICIENTS[index]
            terms[index] = (coefficient * factors[index % 2]) and U32_MASK
            offsets[index] = offset
        }

        for (index in words.indices) {
            val word = words[index]
            val key = if (index % 2 == 0) keyA else keyB
            var term = word * key
            var offset = rol16(word) * (if (index % 2 == 0) 0xEF0569FBL else 0xC31713DBL)
            if (index == 0) {
                term += CS64_ADJUSTMENT0 * factors[0]
                offset += CS64_ADJUSTMENT1
            }
            terms[coefficientCount + index] = term and U32_MASK
            offsets[coefficientCount + index] = offset and U32_MASK
        }

        var swapAcc = 0L
        var swapLast = 0L
        var state = 0L
        var acc = 0L
        var index = 0
        while (index < total) {
            val x = terms[index]
            val y = terms[index + 1]
            var t = rol16((offsets[index] + x) and U32_MASK)
            var q = (t * 0x76F50F34L) and U32_MASK
            swapAcc += (rol16(q) * 0xC3EFEA97L + q) and U32_MASK
            t = rol16((offsets[index + 1] + y) and U32_MASK)
            q = (t * 0x1881CE3CL) and U32_MASK
            swapLast = (rol16(q) * 0x35BD1EC9L + q) and U32_MASK
            swapAcc = (swapAcc + swapLast) and U32_MASK

            state = (state * keyA + x) and U32_MASK
            state = (rol16(state) * 0xCF98B111L) and U32_MASK
            state = (rol16(state) * 0x87085B9FL) and U32_MASK
            state = (rol16(state) * 0x12CEB96DL) and U32_MASK
            state = (rol16(state) * 0x257E1D83L) and U32_MASK
            acc += state

            t = (state * keyB + y) and U32_MASK
            t = (rol16(t) * 0xA27416F5L) and U32_MASK
            t = (rol16(t) * 0xD38396FFL) and U32_MASK
            state = (rol16((rol16(t) * 0x7C932B89L) and U32_MASK) * 0xBFA49F69L) and U32_MASK
            acc = (acc + state) and U32_MASK
            index += 2
        }
        return Pair(swapLast xor state, swapAcc xor acc)
    }

    private fun sha256Digest(data: ByteArray): ByteArray {
        require(data.size >= 7) { "Token salt must contain at least seven bytes" }
        val length = 57 + data.size
        val tail = data.copyOfRange(7, data.size)
        val zeroCount = ((55 - length) % 64 + 64) % 64
        val padded = ByteArray(tail.size + 1 + zeroCount + 8)
        System.arraycopy(tail, 0, padded, 0, tail.size)
        padded[tail.size] = 0x80.toByte()
        val bitLength = length.toLong() * 8
        for (i in 0 until 8) {
            padded[padded.size - 8 + i] = ((bitLength ushr (56 - i * 8)) and 0xFF).toByte()
        }

        var state = sha256Init(data.copyOfRange(0, 7))
        var offset = 0
        while (offset < padded.size) {
            val words = LongArray(64)
            for (i in 0 until 16) {
                val o = offset + i * 4
                words[i] = ((padded[o].toLong() and 0xFF) shl 24) or
                    ((padded[o + 1].toLong() and 0xFF) shl 16) or
                    ((padded[o + 2].toLong() and 0xFF) shl 8) or
                    (padded[o + 3].toLong() and 0xFF)
            }
            expandSchedule(words, 16)
            val working = sha256Rounds(state, words, 0)
            val next = LongArray(8)
            for (i in 0 until 8) {
                next[i] = (state[i] + working[i]) and U32_MASK
            }
            state = next
            offset += 64
        }

        val out = ByteArray(32)
        for (i in 0 until 8) {
            val value = state[i]
            out[i * 4] = ((value ushr 24) and 0xFF).toByte()
            out[i * 4 + 1] = ((value ushr 16) and 0xFF).toByte()
            out[i * 4 + 2] = ((value ushr 8) and 0xFF).toByte()
            out[i * 4 + 3] = (value and 0xFF).toByte()
        }
        return out
    }

    private fun sha256Init(first: ByteArray): LongArray {
        val words = LongArray(64)
        words[14] = ((first[0].toLong() and 0xFF) shl 16) or
            ((first[1].toLong() and 0xFF) shl 8) or
            (first[2].toLong() and 0xFF)
        words[15] = ((first[3].toLong() and 0xFF) shl 24) or
            ((first[4].toLong() and 0xFF) shl 16) or
            ((first[5].toLong() and 0xFF) shl 8) or
            (first[6].toLong() and 0xFF)

        for (index in 16..29) {
            var value = SHA256_SCHEDULE[index - 16]
            value += if (index == 16) {
                smallSigma1(words[index - 2]) xor SHA256_SIGMA1_ADJUSTMENT
            } else {
                smallSigma1(words[index - 2])
            }
            if (index - 7 >= 14) {
                value += words[index - 7]
            }
            if (index - 15 >= 14) {
                value += smallSigma0(words[index - 15]) xor SHA256_SIGMA0_ADJUSTMENT
            }
            words[index] = value and U32_MASK
        }
        words[30] = (smallSigma1(words[28]) + words[23] + smallSigma0(words[15]) + words[14] +
            SHA256_ADJUSTMENT - SHA256_ROUND_CONSTANTS[14]) and U32_MASK
        expandSchedule(words, 31)
        val working = sha256Rounds(SHA256_STATE, words, 14)
        val result = LongArray(8)
        for (i in 0 until 8) {
            result[i] = (SHA256_INITIAL[i] + working[i]) and U32_MASK
        }
        return result
    }

    private fun expandSchedule(words: LongArray, firstWord: Int) {
        for (index in firstWord until 64) {
            val x = words[index - 2]
            val y = words[index - 15]
            val sigma1 = (x ushr 17) xor (x shl 15) xor (x ushr 19) xor (x shl 13) xor (x ushr 10)
            val sigma0 = (y ushr 7) xor (y shl 25) xor (y ushr 18) xor (y shl 14) xor (y ushr 3)
            words[index] = (sigma1 + words[index - 7] + sigma0 + words[index - 16]) and U32_MASK
        }
    }

    private fun sha256Rounds(state: LongArray, words: LongArray, firstRound: Int): LongArray {
        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]
        val constants = if (firstRound == 14) SHA256_BLOCK_CONSTANTS else SHA256_ROUND_CONSTANTS
        for (index in firstRound until 64) {
            val t1 = (h + bigSigma1(e) + choose(e, f, g) + constants[index] + words[index]) and U32_MASK
            val t2 = (bigSigma0(a) + majority(a, b, c)) and U32_MASK
            h = g
            g = f
            f = e
            e = (d + t1) and U32_MASK
            d = c
            c = b
            b = a
            a = (t1 + t2) and U32_MASK
        }
        return longArrayOf(a, b, c, d, e, f, g, h)
    }

    private fun rotr(value: Long, bits: Int): Long = ((value ushr bits) or (value shl (32 - bits))) and U32_MASK
    private fun rol16(value: Long): Long = ((value shl 16) or (value ushr 16)) and U32_MASK
    private fun bigSigma0(value: Long): Long = rotr(value, 2) xor rotr(value, 13) xor rotr(value, 22)
    private fun bigSigma1(value: Long): Long = rotr(value, 6) xor rotr(value, 11) xor rotr(value, 25)
    private fun smallSigma0(value: Long): Long =
        ((value ushr 7) xor (value shl 25) xor (value ushr 18) xor (value shl 14) xor (value ushr 3)) and U32_MASK
    private fun smallSigma1(value: Long): Long =
        ((value ushr 17) xor (value shl 15) xor (value ushr 19) xor (value shl 13) xor (value ushr 10)) and U32_MASK
    private fun choose(e: Long, f: Long, g: Long): Long = g xor (e and (f xor g))
    private fun majority(a: Long, b: Long, c: Long): Long = (a and b) or (c and (a or b))

    private fun leU32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFF)) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)

    private fun le32Hex(value: Long): String = String.format(
        Locale.US,
        "%02X%02X%02X%02X",
        value and 0xFF,
        (value ushr 8) and 0xFF,
        (value ushr 16) and 0xFF,
        (value ushr 24) and 0xFF,
    )

    private fun ByteArray.toHexUpper(): String {
        val digits = "0123456789ABCDEF"
        val builder = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            builder.append(digits[value ushr 4])
            builder.append(digits[value and 0x0F])
        }
        return builder.toString()
    }

    private fun prependZeroByte(data: ByteArray): ByteArray {
        val result = ByteArray(data.size + 1)
        System.arraycopy(data, 0, result, 1, data.size)
        return result
    }

    private fun wordsLe4Even(data: ByteArray): LongArray {
        val pad = (4 - data.size % 4) % 4
        val padded = ByteArray(data.size + pad)
        System.arraycopy(data, 0, padded, 0, data.size)
        if (padded.isEmpty()) return LongArray(0)
        val words = LongArray(padded.size / 4)
        for (index in words.indices) {
            words[index] = leU32(padded, index * 4)
        }
        return if (words.size % 2 == 1) words.copyOf(words.size + 1) else words
    }

    private val CS64_COEFFICIENTS = arrayOf(
        Pair(0xE82E91B1L, 0xB32700D0L),
        Pair(0x44DE24C6L, 0x88DFF51DL),
        Pair(0x3BEB65BCL, 0x29514038L),
        Pair(0xC89C5933L, 0xB328CBDFL),
        Pair(0x8D1EC211L, 0xD405B4D5L),
        Pair(0x314EF5D5L, 0x26E55F3CL),
        Pair(0xE92491B1L, 0x607801C6L),
        Pair(0xF0A5B9D5L, 0x82B2C1FBL),
        Pair(0x06064CF3L, 0xFA51A3DAL),
        Pair(0xDA79FA60L, 0x207CC464L),
        Pair(0xBED20560L, 0x3C3C74F3L),
        Pair(0x89F9C9F8L, 0xD2D14B61L),
        Pair(0x35FA38C6L, 0xBB04A2C1L),
        Pair(0xCB49E3C6L, 0xC5626167L),
    )

    private const val CS64_FACTOR = 0x82893933L
    private const val CS64_ADJUSTMENT0 = 0xF2F90B1AL
    private const val CS64_ADJUSTMENT1 = 0x0B1A0000L

    private const val SHA256_ADJUSTMENT = 0xC9DC06A7L
    private const val SHA256_SIGMA0_ADJUSTMENT = 0x059C0B80L
    private const val SHA256_SIGMA1_ADJUSTMENT = 0x000B92C0L

    private val SHA256_INITIAL = longArrayOf(
        0x6A09E667L, 0xBB67AE85L, 0x3C6EF372L, 0xA54FF53AL,
        0x510E527FL, 0x9B05688CL, 0x1F83D9ABL, 0x5BE0CD19L,
    )

    private val SHA256_STATE = longArrayOf(
        0xFD9FBEA2L, 0x91243CE7L, 0xBE499094L, 0xCD2E1E0AL,
        0x6C006F49L, 0x04BD35ABL, 0xFEB0A7E4L, 0x03EFFBD2L,
    )

    private val SHA256_SCHEDULE = longArrayOf(
        0xEB24BF9EL, 0x1558691FL, 0xA0C09EE2L, 0x0CFA32BAL, 0xA622EE8CL, 0xD1DD9491L, 0xC0DD25A0L,
        0xF9E70AB6L, 0xF0C31573L, 0x7058CE4EL, 0x7188E606L, 0x00E5AB33L, 0xD258B060L, 0x72766564L,
    )

    private val SHA256_ROUND_CONSTANTS = longArrayOf(
        0x428A2F98L, 0x71374491L, 0xB5C0FBCFL, 0xE9B5DBA5L, 0x3956C25BL, 0x59F111F1L, 0x923F82A4L, 0xAB1C5ED5L,
        0xD807AA98L, 0x12835B01L, 0x243185BEL, 0x550C7DC3L, 0x72BE5D74L, 0x80DEB1FEL, 0x9BDC06A7L, 0xC19BF174L,
        0xE49B69C1L, 0xEFBE4786L, 0x0FC19DC6L, 0x240CA1CCL, 0x2DE92C6FL, 0x4A7484AAL, 0x5CB0A9DCL, 0x76F988DAL,
        0x983E5152L, 0xA831C66DL, 0xB00327C8L, 0xBF597FC7L, 0xC6E00BF3L, 0xD5A79147L, 0x06CA6351L, 0x14292967L,
        0x27B70A85L, 0x2E1B2138L, 0x4D2C6DFCL, 0x53380D13L, 0x650A7354L, 0x766A0ABBL, 0x81C2C92EL, 0x92722C85L,
        0xA2BFE8A1L, 0xA81A664BL, 0xC24B8B70L, 0xC76C51A3L, 0xD192E819L, 0xD6990624L, 0xF40E3585L, 0x106AA070L,
        0x19A4C116L, 0x1E376C08L, 0x2748774CL, 0x34B0BCB5L, 0x391C0CB3L, 0x4ED8AA4AL, 0x5B9CCA4FL, 0x682E6FF3L,
        0x748F82EEL, 0x78A5636FL, 0x84C87814L, 0x8CC70208L, 0x90BEFFFAL, 0xA4506CEBL, 0xBEF9A3F7L, 0xC67178F2L,
    )

    private val SHA256_BLOCK_CONSTANTS = LongArray(64) {
        if (it == 14) SHA256_ADJUSTMENT else SHA256_ROUND_CONSTANTS[it]
    }
}
