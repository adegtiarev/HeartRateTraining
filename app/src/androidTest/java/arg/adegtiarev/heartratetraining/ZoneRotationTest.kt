package arg.adegtiarev.heartratetraining

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ZoneRotationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun settingsRouteAndUnfinishedDraftSurviveActivityRecreation() {
        fun text(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
        text(R.string.zone_settings).performClick()
        compose.onNodeWithTag("threshold-2").performScrollTo().performTextReplacement("0")
        compose.activityRule.scenario.recreate()
        text(R.string.zone_title).assertIsDisplayed()
        compose.onNodeWithTag("threshold-2").performScrollTo().assertTextContains("0")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        text(R.string.zone_discard).performClick()
        text(R.string.zone_settings).assertIsDisplayed()
    }
}
