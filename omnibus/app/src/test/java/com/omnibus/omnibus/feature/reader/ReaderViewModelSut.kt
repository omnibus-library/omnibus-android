package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.bookmarks.BookmarksRepository
import com.omnibus.omnibus.data.bookmarks.TestBookmarksRepository
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.progress.ProgressRepository
import com.omnibus.omnibus.data.progress.TestProgressRepository
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.local.ReaderSettingsStore
import com.omnibus.omnibus.data.reader.local.TestReaderSettingsStore
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The ViewModel under test in the `ReaderViewModel*Test` specs. Time stands still unless a test passes a clock. */
internal fun sut(
    bookUuid: String = randomString(),
    libraryRepository: LibraryRepository = TestLibraryRepository(),
    progressRepository: ProgressRepository = TestProgressRepository(),
    settingsStore: ReaderSettingsStore = TestReaderSettingsStore(),
    bookmarksRepository: BookmarksRepository = TestBookmarksRepository(),
    timeSource: TimeSource = randomLong().let { now -> TimeSource { now } },
    startCfi: String? = null,
) = ReaderViewModel(
    bookUuid = bookUuid,
    libraryRepository = libraryRepository,
    assetLoaderFactory = ReaderAssetLoaderFactory(
        mockk(relaxed = true),
        Dispatchers.Unconfined,
        BookFileSource { BookFileResult.NotFound },
    ),
    progressRepository = progressRepository,
    settingsStore = settingsStore,
    bookmarksRepository = bookmarksRepository,
    timeSource = timeSource,
    persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    startCfi = startCfi,
)
