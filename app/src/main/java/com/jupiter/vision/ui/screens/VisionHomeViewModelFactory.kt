// VisionHomeViewModelFactory.kt - Factory providing VisionHomeViewModel with injected singletons from AppModule.
package com.jupiter.vision.ui.screens

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jupiter.vision.di.AppModule

class VisionHomeViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VisionHomeViewModel::class.java)) {
            val engineFacade = AppModule.provideVisionEngineFacade(application)
            val weatherRepo = AppModule.provideWeatherRepository(application)
            val wallpaperProvider = AppModule.provideWallpaperProvider(application)
            return VisionHomeViewModel(
                engineFacade = engineFacade,
                weatherRepository = weatherRepo,
                wallpaperProvider = wallpaperProvider,
                application = application
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
