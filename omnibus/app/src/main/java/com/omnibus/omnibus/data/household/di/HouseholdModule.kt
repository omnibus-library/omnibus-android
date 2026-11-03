package com.omnibus.omnibus.data.household.di

import com.omnibus.omnibus.data.household.DefaultHouseholdRepository
import com.omnibus.omnibus.data.household.HouseholdRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HouseholdModule {

    @Binds
    @Singleton
    abstract fun bindHouseholdRepository(repository: DefaultHouseholdRepository): HouseholdRepository
}
