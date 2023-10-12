package com.cosmos.unreddit

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.cosmos.stealth.sdk.Stealth
import com.cosmos.unreddit.data.model.preferences.UiPreferences
import com.cosmos.unreddit.data.repository.PreferencesRepository
import com.cosmos.unreddit.util.FileUncaughtExceptionHandler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class UnredditApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    var appTheme: Int = -1
        set(mode) {
            field = if (!UiPreferences.NightMode.isAmoled(mode)) {
                AppCompatDelegate.setDefaultNightMode(mode)
                R.style.AppTheme
            } else {
                // Force dark mode when amoled is set
                AppCompatDelegate.setDefaultNightMode(UiPreferences.NightMode.DARK.mode)
                R.style.AmoledAppTheme
            }
        }

    override fun onCreate() {
        super.onCreate()

        runBlocking {
            val stealthInstance = preferencesRepository.getStealthInstance().first()
            val proxyMode = preferencesRepository.getProxyModeEnabled().first()
            initStealth(stealthInstance, proxyMode)
        }

        runBlocking {
            val nightMode = preferencesRepository.getNightMode().first()
            appTheme = nightMode
        }

        Thread.setDefaultUncaughtExceptionHandler(FileUncaughtExceptionHandler(this))
    }

    fun initStealth(instance: String, proxyMode: Boolean) {
        Stealth.init {
            url(instance)
            if (proxyMode) useProxyMode()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(applicationContext)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
