package com.omnibus.omnibus.data.wishlist.di

import com.omnibus.omnibus.data.wishlist.WishlistRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets the Book Detail composables, which are not Hilt-injected, reach the shared wishlist repository. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WishlistEntryPoint {
    fun wishlistRepository(): WishlistRepository
}
