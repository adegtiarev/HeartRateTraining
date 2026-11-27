package arg.adegtiarev.heartratetraining.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayTravelTest {
    @Test fun positionIsRelativeToTheSpaceLeftForTheWholeWindow() {
        val portrait = OverlayTravel(displaySize = 420, windowSize = 80, margin = 10)
        val wide = OverlayTravel(displaySize = 820, windowSize = 160, margin = 10)
        assertEquals(10, portrait.offset(0f))
        assertEquals(330, portrait.offset(1f))
        val saved = portrait.fraction(90f, 0f)
        assertEquals(0.25f, saved, 0f)
        assertEquals(170, wide.offset(saved))
        assertEquals(0f, wide.fraction(-500f, saved), 0f)
        assertEquals(1f, wide.fraction(2000f, saved), 0f)
    }

    @Test fun fittedWindowKeepsItsAnchorForTheNextRotation() {
        val fitted = OverlayTravel(displaySize = 200, windowSize = 180, margin = 10)
        assertEquals(10, fitted.offset(0.8f))
        assertEquals(0.8f, fitted.fraction(99f, 0.8f), 0f)
    }
}
