package com.omnibus.omnibus.feature.requests.add

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.requests.LibraryHit
import com.omnibus.omnibus.data.requests.ProviderBook
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.screenshot.Shot
import kotlinx.serialization.json.JsonObject

@Composable
private fun SheetShot(state: AddRequestUIState, requested: Set<String> = emptySet()) {
    Shot {
        // A loud ground behind the content, so a column narrower than the sheet would show strips of it at its edges.
        Box(Modifier.fillMaxWidth().background(Color.Magenta)) {
            AddRequestSheetContent(state, requested, RequestWording(), {}, {}, {}, {}, {})
        }
    }
}

private fun hit(uuid: String, title: String, formats: List<String>) =
    LibraryHit(uuid, title, "Susanna Clarke", "2020", formats, null, null)

private fun record(title: String, source: String) =
    ProviderBook(JsonObject(emptyMap()), title, listOf("Susanna Clarke"), "2020", null, source)

private fun results() = AddRequestUIState(
    query = "piranesi",
    library = SearchResults.Done(
        listOf(
            hit("a", "Piranesi", listOf("PRINT")),
            hit("b", "Piranesi (Audiobook edition with a very long title that wraps)", listOf("EPUB")),
            hit("c", "Piranesi: A Novel", listOf("M4B")),
        ),
    ),
    providers = SearchResults.Done(listOf(record("Piranesi", "hardcover"), record("Piranesi", "open_library"))),
)

/** The idle sheet: its ground spans the full width even though its text is short. */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AddRequestIdle() {
    SheetShot(AddRequestUIState())
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AddRequestResults() {
    SheetShot(results(), requested = setOf("a"))
}

@PreviewTest
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AddRequestStarting() {
    SheetShot(results().copy(starting = true))
}

@PreviewTest
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AddRequestFailedAndEmpty() {
    SheetShot(
        AddRequestUIState(
            query = "zzz",
            library = SearchResults.Done(emptyList()),
            providers = SearchResults.Failed,
            message = "Dune is already in the library.",
        ),
    )
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AddRequestWide() {
    SheetShot(results())
}
