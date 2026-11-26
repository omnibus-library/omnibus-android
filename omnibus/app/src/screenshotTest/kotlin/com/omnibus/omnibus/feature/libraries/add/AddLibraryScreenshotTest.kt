package com.omnibus.omnibus.feature.libraries.add

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.feature.configuration.ServerConfigSubmissionStatus
import com.omnibus.omnibus.feature.configuration.ServerConfigUIState
import com.omnibus.omnibus.feature.configuration.ui.ServerConfigurationContent
import com.omnibus.omnibus.feature.libraries.manage.ManageLibrariesScreenContent
import com.omnibus.omnibus.screenshot.Shot

// Fixed libraries, not random: the screenshots must be stable.
private val home = Library(
    "home", "http://books.home.lan", "Home", "hoon", addedAt = 1, lastUsedAt = 1, isActive = true,
)

/** TC-25. Connect in adding mode with nothing typed yet: the close button and the empty field. */
@PreviewTest
@Preview(name = "Light", widthDp = 360, heightDp = 720)
@Preview(name = "Dark", widthDp = 360, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AddLibraryConnect() {
    Shot {
        ServerConfigurationContent(
            modifier = Modifier,
            uiState = ServerConfigUIState(savedConfigurationValue = "https://books.home.lan"),
            isAdding = true,
            onConfigSubmitted = {},
        )
    }
}

/** TC-25. Connect in adding mode on a wide window. */
@PreviewTest
@Preview(name = "Wide", widthDp = 840, heightDp = 900)
@Composable
fun AddLibraryConnectWide() {
    Shot {
        ServerConfigurationContent(
            modifier = Modifier,
            uiState = ServerConfigUIState(savedConfigurationValue = "https://books.home.lan"),
            isAdding = true,
            onConfigSubmitted = {},
        )
    }
}

/** TC-25. Connect in adding mode when the address matches a saved library: the error line and Switch to, not Connect. */
@PreviewTest
@Preview(name = "Light", widthDp = 360, heightDp = 720)
@Preview(name = "Dark", widthDp = 360, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AddLibraryAlreadySaved() {
    Shot {
        ServerConfigurationContent(
            modifier = Modifier,
            uiState = ServerConfigUIState(
                submissionStatus = ServerConfigSubmissionStatus.ALREADY_SAVED,
                alreadySaved = home,
            ),
            isAdding = true,
            onConfigSubmitted = {},
        )
    }
}

/** TC-26. Manage libraries with the Add library action under the list. */
@PreviewTest
@Preview(name = "Light", widthDp = 360, heightDp = 480)
@Preview(name = "Dark", widthDp = 360, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AddLibraryManageFooter() {
    Shot {
        ManageLibrariesScreenContent(
            libraries = listOf(
                home,
                Library(
                    id = "work",
                    address = "https://library.acme.io",
                    name = "Work book club",
                    username = "hjames",
                    addedAt = 2,
                    lastUsedAt = 2,
                    isActive = false,
                ),
                Library(
                    id = "mum",
                    address = "http://10.0.0.12:3000",
                    name = "Mum's library",
                    username = "hoon",
                    addedAt = 3,
                    lastUsedAt = 3,
                    isActive = false,
                ),
                Library(
                    id = "bare",
                    address = "http://10.0.0.40:3000",
                    name = "",
                    username = null,
                    addedAt = 4,
                    lastUsedAt = 4,
                    isActive = false,
                ),
            ),
            onBack = {},
            onOpenLibrary = {},
            footer = { AddLibraryAction(onClick = {}, testTag = "add") },
        )
    }
}
