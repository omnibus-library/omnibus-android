package com.omnibus.omnibus.data.cover.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.omnibus.omnibus.data.cover.CoverFetcher
import com.omnibus.omnibus.data.cover.CoverKeyer
import com.omnibus.omnibus.data.cover.CoverRepository
import com.omnibus.omnibus.data.cover.CoverThumbnail
import com.omnibus.omnibus.data.cover.DefaultCoverRepository
import com.omnibus.omnibus.data.cover.local.CoverDiskStore
import com.omnibus.omnibus.data.cover.local.DefaultCoverDiskStore
import com.omnibus.omnibus.di.TimeSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okio.Path.Companion.toOkioPath
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoverModule {

    @Binds
    @Singleton
    abstract fun bindCoverRepository(repository: DefaultCoverRepository): CoverRepository

    @Binds
    @Singleton
    abstract fun bindCoverDiskStore(store: DefaultCoverDiskStore): CoverDiskStore

    companion object {
        /** Coil bounds its memory cache by bytes only, so there is no separate entry-count limit. */
        private const val MEMORY_CACHE_BYTES = 96L * 1024 * 1024
        private const val DISK_CACHE_BYTES = 256L * 1024 * 1024

        @Provides
        @Singleton
        fun provideCoverMemoryCache(): MemoryCache =
            MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_BYTES).build()

        /**
         * Lives in `filesDir`, not `cacheDir`: it is the offline cover store, so the system
         * shouldn't reclaim it when storage runs low.
         */
        @Provides
        @Singleton
        fun provideCoverDiskCache(@ApplicationContext context: Context): DiskCache =
            DiskCache.Builder()
                .directory(context.filesDir.resolve("covers").toOkioPath())
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()

        /**
         * The loader behind every [coil3.compose.AsyncImage]. Its own disk cache is off: thumbnails
         * come through [CoverFetcher], which keeps the disk tier itself.
         */
        @Provides
        @Singleton
        fun provideImageLoader(
            @ApplicationContext context: Context,
            repository: CoverRepository,
            memoryCache: MemoryCache,
            timeSource: TimeSource,
        ): ImageLoader =
            ImageLoader.Builder(context)
                .memoryCache(memoryCache)
                .diskCache(null)
                .crossfade(true)
                .components {
                    add(CoverKeyer(timeSource), CoverThumbnail::class)
                    add(CoverFetcher.Factory(repository), CoverThumbnail::class)
                }
                .build()
    }
}
