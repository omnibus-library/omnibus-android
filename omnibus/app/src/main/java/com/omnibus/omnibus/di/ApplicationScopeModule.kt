package com.omnibus.omnibus.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Provides the application's [CoroutineScope] for work that must finish after a screen's
 * ViewModel is cleared, such as the final reading-position flush. Repositories it calls are main-safe.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

/** Marks the application-wide [CoroutineScope], to keep it apart from other scopes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
