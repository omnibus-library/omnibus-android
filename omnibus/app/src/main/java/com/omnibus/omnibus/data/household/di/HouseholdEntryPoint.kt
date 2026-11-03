package com.omnibus.omnibus.data.household.di

import com.omnibus.omnibus.data.household.HouseholdRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets [com.omnibus.omnibus.ui.components.ReaderAvatar], a plain composable, reach the repository that loads pictures. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HouseholdEntryPoint {
    fun householdRepository(): HouseholdRepository
}
