package com.omnibus.omnibus.feature.libraries.switching

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.feature.libraries.displayName
import com.omnibus.omnibus.feature.libraries.monogramOf
import com.omnibus.omnibus.screenshot.Shot

// Fixed ids and names, not random: spine heights come from the ids, so the screenshots must be stable.
private val names = listOf(
    "Home", "Reading group", "Work book club", "Poetry", "Mysteries", "Archive", "Comics", "Kids", "Drama",
    "Letters", "Sagas", "Uncle's", "Travel", "Nonfiction", "Opera", "Zines",
)

private fun libraries(count: Int): List<Library> = List(count) { index ->
    val name = names[index % names.size].let { if (index < names.size) it else "$it ${index / names.size + 1}" }
    Library(
        id = "library-$index",
        address = "https://library-$index.lan",
        name = name,
        username = "hoon",
        addedAt = index.toLong(),
        lastUsedAt = index.toLong(),
        isActive = index == 0,
    )
}

/** Opening "Work book club" (the third library) among [count] libraries: the settled end of a lift. */
@Composable
private fun Opening(count: Int) {
    val all = libraries(count)
    val work = all[2]
    Shot {
        SwitchingScreenContent(
            uiState = SwitchingUIState(
                stage = SwitchingStage.Opening,
                current = all[0],
                target = work,
                bookcase = BookcaseState(
                    spines = all.map { BookcaseSpine(it.id, monogramOf(it.displayName()), SpineHeights.of(it.id)) },
                    raisedLibraryId = work.id,
                    transition = BookcaseTransition.Still,
                ),
            ),
            onStay = {},
            onSwitchAnyway = {},
        )
    }
}

/** #370 AC1, AC5: eleven libraries on a phone, a full first shelf and the rest on a second one below. */
@PreviewTest
@Preview(name = "Light", widthDp = 360, heightDp = 780)
@Preview(name = "Dark", widthDp = 360, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BookcaseShelves() {
    Opening(count = 11)
}

/** #370 AC3, AC5: sixteen libraries on a wide window, shelves capped at 560 dp and centred with the name and status. */
@PreviewTest
@Preview(name = "Light", widthDp = 900, heightDp = 640)
@Preview(name = "Dark", widthDp = 900, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BookcaseShelvesWide() {
    Opening(count = 16)
}

/** #370 AC2, AC5: more shelves than a small phone holds. The bookcase keeps to the space above the name and status. */
@PreviewTest
@Preview(name = "Light", widthDp = 320, heightDp = 568)
@Composable
fun BookcaseShelvesOverflow() {
    Opening(count = 40)
}
