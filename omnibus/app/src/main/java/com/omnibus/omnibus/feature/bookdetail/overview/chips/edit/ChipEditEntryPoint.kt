package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import com.omnibus.omnibus.data.auth.AuthRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets the chip editor's composable read the signed-in user before any ViewModel exists for it. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChipEditEntryPoint {
    fun authRepository(): AuthRepository
}
