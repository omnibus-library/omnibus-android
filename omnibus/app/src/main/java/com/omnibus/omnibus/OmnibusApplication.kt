package com.omnibus.omnibus

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class OmnibusApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var imageLoader: Provider<ImageLoader>

    override fun newImageLoader(context: Context): ImageLoader = imageLoader.get()
}
