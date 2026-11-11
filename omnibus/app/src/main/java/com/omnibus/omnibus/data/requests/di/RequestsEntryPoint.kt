package com.omnibus.omnibus.data.requests.di

import com.omnibus.omnibus.data.requests.RequestsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets the request screens' plain composables reach the repository that loads a download's cover. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RequestsEntryPoint {
    fun requestsRepository(): RequestsRepository
}
