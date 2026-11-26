package com.omnibus.omnibus.di

import android.content.Context
import android.content.Intent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Restarts the app, so that no in-memory state survives a library switch (C11, R12). */
fun interface AppRestarter {

    /** Starts the launcher activity in a fresh task and ends this process. Returns only when it can't relaunch. */
    fun restart()
}

/** Relaunches the launcher activity in a new task, then exits the process. */
class ProcessAppRestarter @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppRestarter {

    override fun restart() {
        val component = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return
        context.startActivity(Intent.makeRestartActivityTask(component))
        Runtime.getRuntime().exit(0)
    }
}

/** Binds the process restarter as the app-wide [AppRestarter]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppRestarterModule {

    @Binds
    abstract fun bindAppRestarter(restarter: ProcessAppRestarter): AppRestarter
}
