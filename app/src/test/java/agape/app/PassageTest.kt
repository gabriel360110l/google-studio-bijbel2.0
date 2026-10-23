package agape.app

import agape.app.core.model.Passage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassageTest {
    @Test fun parsesEveryReadingShapeUsedInContent() {
        assertEquals(Passage("JHN", 3, null, 3, null), Passage.parse("JHN 3"))
        assertEquals(Passage("JHN", 3, 16, 3, 16), Passage.parse("JHN 3:16"))
        assertEquals(Passage("LUK", 2, 29, 2, 32), Passage.parse("LUK 2:29-32"))
        assertEquals(Passage("ISA", 52, 13, 53, 12), Passage.parse("ISA 52:13-53:12"))
        assertEquals(Passage("1CO", 13, null, 14, null), Passage.parse("1CO 13-14"))
        assertTrue(Passage.parse("PSA 51")!!.isWholeChapter)
    }

    @Test fun roundTripsThroughToString() {
        listOf("JHN 3", "JHN 3:16", "LUK 2:29-32", "ISA 52:13-53:12", "MRK 1-2").forEach {
            assertEquals(it, Passage.parse(it).toString())
        }
    }

    @Test fun rejectsMalformedReferences() {
        assertNull(Passage.parse("John 3:16"))
        assertNull(Passage.parse("JHN"))
        assertNull(Passage.parse("JHN 3:"))
    }
}
