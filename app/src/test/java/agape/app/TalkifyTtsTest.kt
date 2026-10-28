package agape.app

import agape.app.ui.components.TalkifyAzureDrm
import agape.app.ui.components.TalkifyNeuralSynthesizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkifyTtsTest {
    private val vectors: List<Pair<Double, String>> = listOf(
        Pair(0.0, "52BC7A17B50DFEC9-28C4394775C8751CD2F6859C030C06DB1489ACAB3B3A7651831FC26895177ED6"),
        Pair(1700000000.0, "2CD23A2CA0B2595A-F3C846C370E05F9CC83ACC5F995C5F5D87A4D0F14CE8936A0F902E64099E38A2"),
        Pair(1792999699.0, "438AE531E72CDC9F-FE2EB3B79D67BC38FC68D019BCD7293CBC9F13E91DCCE404EB7840B7D6137E94"),
        Pair(1793000000.0, "781C1B76C9F739B3-1A772624F17530DF13FBF300637917F70D55C163861ADEC2DA70A248A0DE438A"),
        Pair(1793000300.0, "2D53AE79BABA1836-B8B1E1EB63A0281B58B809609078BF1D6D5147978D4835960D423F7C73F4B098"),
        Pair(2000000000.0, "2D62557F1142BC5D-FD9C30C68FF194A5EE6B45FEFC5E2BA1D1239F88A90569AB55CFFFAA7799F40E"),
    )

    @Test
    fun `TalkifyAzureDrm matches TalkifyTTS v2 reference vectors`() {
        for ((timestamp, expected) in vectors) {
            assertEquals("timestamp=$timestamp", expected, TalkifyAzureDrm.generateSecMsGecV2(timestamp))
        }
    }

    @Test
    fun `TalkifyNeuralSynthesizer synthesizes Dutch male neural voice Maarten`() = runBlocking {
        val bytes = TalkifyNeuralSynthesizer.synthesizeBytes(
            text = "In het begin schiep God de hemel en de aarde.",
            voice = "nl-NL-MaartenNeural",
            rate = "-6%",
            pitch = "-2Hz",
        )
        println("Synthesized nl-NL-MaartenNeural MP3 bytes: ${bytes.size}")
        assertTrue("Expected non-empty MP3 audio bytes from MaartenNeural", bytes.size > 2000)
    }
}
