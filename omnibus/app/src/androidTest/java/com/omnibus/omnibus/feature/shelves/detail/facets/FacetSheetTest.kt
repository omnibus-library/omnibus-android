package com.omnibus.omnibus.feature.shelves.detail.facets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.shelves.FilterClause
import com.omnibus.omnibus.data.shelves.FilterField
import com.omnibus.omnibus.data.shelves.FilterMode
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The facet sheet's options, switch, and buttons, on hand-built state. The sheet's look is covered by screenshot tests. */
class FacetSheetTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue232_tc28_ticksAValueAndSwitchingToExcludesAppliesOneClause() {
        val applied = mutableListOf<FilterClause>()
        setSheet(loaded(), onApply = { applied += it })

        rule.onNodeWithTag(FacetSheetTestTags.option("Fantasy")).performClick()
        rule.onNodeWithTag(FacetSheetTestTags.EXCLUDE).performClick()
        rule.onNodeWithTag(FacetSheetTestTags.APPLY).performClick()

        assertEquals(listOf(FilterClause(FilterField.Genre, FilterMode.Exclude, listOf("Fantasy"))), applied)
    }

    @Test
    fun issue232_tc28_showsEachOptionsCount() {
        setSheet(loaded())

        rule.onNodeWithText("9").assertIsDisplayed()
        rule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun issue232_tc28_offersIncludesAndExcludesSegments() {
        setSheet(loaded())

        rule.onNodeWithText(string(R.string.shelf_detail_facet_includes)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.shelf_detail_facet_excludes)).assertIsDisplayed()
    }

    @Test
    fun issue232_tc28_cancelReportsNothing() {
        val applied = mutableListOf<FilterClause>()
        var cancelled = 0
        setSheet(loaded(), onApply = { applied += it }, onCancel = { cancelled++ })

        rule.onNodeWithTag(FacetSheetTestTags.option("Fantasy")).performClick()
        rule.onNodeWithTag(FacetSheetTestTags.CANCEL).performClick()

        assertEquals(1, cancelled)
        assertTrue(applied.isEmpty())
    }

    @Test
    fun issue232_tc28_showsASpinnerWhileTheOptionsLoad() {
        setSheet(FacetOptionsState.Loading)

        rule.onNodeWithTag(FacetSheetTestTags.LOADING).assertIsDisplayed()
        rule.onNodeWithTag(FacetSheetTestTags.APPLY).assertIsNotEnabled()
    }

    @Test
    fun issue232_tc28_aFailedReadSaysSoAndRetries() {
        var retried = 0
        setSheet(FacetOptionsState.Failed, onRetry = { retried++ })

        rule.onNodeWithText(string(R.string.shelf_detail_facet_error, string(R.string.shelf_detail_facet_plural_genre)))
            .assertIsDisplayed()
        rule.onNodeWithTag(FacetSheetTestTags.APPLY).assertIsNotEnabled()
        rule.onNodeWithTag(FacetSheetTestTags.RETRY).performClick()

        assertEquals(1, retried)
    }

    private fun loaded() = FacetOptionsState.Loaded(
        listOf(
            FacetOption(value = "Fantasy", label = "Fantasy", count = 9L),
            FacetOption(value = "Poetry", label = "Poetry", count = 3L),
        ),
    )

    private fun setSheet(
        state: FacetOptionsState,
        onApply: (FilterClause) -> Unit = {},
        onCancel: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                SheetHost(state = state, onApply = onApply, onCancel = onCancel, onRetry = onRetry)
            }
        }
    }

    private fun string(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
}

/** Holds the mode and picked values the way the sheet does, so the stateless body can be driven. */
@Composable
private fun SheetHost(
    state: FacetOptionsState,
    onApply: (FilterClause) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    var mode by remember { mutableStateOf(FilterMode.Include) }
    var picked by remember { mutableStateOf(emptyList<String>()) }
    FacetSheetContent(
        field = FilterField.Genre,
        state = state,
        mode = mode,
        picked = picked,
        onModeChange = { mode = it },
        onToggle = { picked = toggleValue(picked, it) },
        onRetry = onRetry,
        onApply = onApply,
        onCancel = onCancel,
        onClose = onCancel,
        modifier = Modifier,
    )
}
