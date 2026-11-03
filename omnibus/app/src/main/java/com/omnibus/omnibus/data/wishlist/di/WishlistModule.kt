package com.omnibus.omnibus.data.wishlist.di

import com.omnibus.omnibus.data.wishlist.DefaultWishlistRepository
import com.omnibus.omnibus.data.wishlist.WishlistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WishlistModule {

    @Binds
    @Singleton
    abstract fun bindWishlistRepository(repository: DefaultWishlistRepository): WishlistRepository
}
