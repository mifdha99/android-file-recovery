package com.eslam.recovery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.eslam.recovery.data.coil.RecoverableFetcher
import com.eslam.recovery.data.coil.RecoverableKeyer
import com.eslam.recovery.di.AppContainer

class App : Application(), SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(RecoverableFetcher.Factory())
                add(RecoverableKeyer())
                add(VideoFrameDecoder.Factory())
            }
            .build()
}
