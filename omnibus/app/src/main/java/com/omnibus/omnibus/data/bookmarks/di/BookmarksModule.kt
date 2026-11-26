package com.omnibus.omnibus.data.bookmarks.di

import com.omnibus.omnibus.data.bookmarks.BookmarksRepository
import com.omnibus.omnibus.data.bookmarks.BookmarksSyncParticipant
import com.omnibus.omnibus.data.bookmarks.DefaultBookmarksRepository
import com.omnibus.omnibus.data.libraries.LibrarySyncParticipant
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/** Binds [DefaultBookmarksRepository] as the app-wide [BookmarksRepository]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BookmarksModule {

    @Binds
    @Singleton
    abstract fun bindBookmarksRepository(
        repository: DefaultBookmarksRepository,
    ): BookmarksRepository

    @Binds
    @IntoSet
    abstract fun bindBookmarksSyncParticipant(participant: BookmarksSyncParticipant): LibrarySyncParticipant
}
