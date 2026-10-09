// AppModule.kt - Dagger Hilt dependency injection module providing singletons across the engine.
package com.jupiter.vision.di

import android.content.Context
import androidx.room.Room
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.jupiter.vision.engine.VisionEngine
import com.jupiter.vision.engine.VisionEngineFacade
import com.jupiter.vision.engine.data.dao.VisionDao
import com.jupiter.vision.engine.data.db.VisionDatabase
import com.jupiter.vision.engine.data.repository.LocationRepository
import com.jupiter.vision.engine.data.repository.UsageRepository
import com.jupiter.vision.engine.provider.AppTemplateProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Volatile
    private var databaseInstance: VisionDatabase? = null

    @Volatile
    private var locationRepoInstance: LocationRepository? = null

    @Volatile
    private var usageRepoInstance: UsageRepository? = null

    @Volatile
    private var visionEngineInstance: VisionEngine? = null

    @Volatile
    private var facadeInstance: VisionEngineFacade? = null

    @Provides
    @Singleton
    fun provideVisionDatabase(
        @ApplicationContext context: Context
    ): VisionDatabase {
        return databaseInstance ?: synchronized(this) {
            databaseInstance ?: Room.databaseBuilder(
                context.applicationContext,
                VisionDatabase::class.java,
                "vision_database.db"
            ).fallbackToDestructiveMigration().build().also { databaseInstance = it }
        }
    }

    @Provides
    @Singleton
    fun provideVisionDao(
        @ApplicationContext context: Context
    ): VisionDao {
        return provideVisionDatabase(context).visionDao()
    }

    @Provides
    @Singleton
    fun provideVisionDao(database: VisionDatabase): VisionDao {
        return database.visionDao()
    }

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context.applicationContext)
    }

    @Provides
    @Singleton
    fun provideAppTemplateProvider(): AppTemplateProvider {
        return AppTemplateProvider()
    }

    @Provides
    @Singleton
    fun provideLocationRepository(
        @ApplicationContext context: Context,
        dao: VisionDao,
        fusedLocationClient: FusedLocationProviderClient
    ): LocationRepository {
        return locationRepoInstance ?: synchronized(this) {
            locationRepoInstance ?: LocationRepository(
                context.applicationContext,
                dao,
                fusedLocationClient
            ).also { locationRepoInstance = it }
        }
    }

    fun provideLocationRepository(@ApplicationContext context: Context): LocationRepository {
        return provideLocationRepository(
            context,
            provideVisionDao(context),
            provideFusedLocationProviderClient(context)
        )
    }

    @Provides
    @Singleton
    fun provideUsageRepository(
        @ApplicationContext context: Context,
        dao: VisionDao,
        locationRepository: LocationRepository
    ): UsageRepository {
        return usageRepoInstance ?: synchronized(this) {
            usageRepoInstance ?: UsageRepository(
                context.applicationContext,
                dao,
                locationRepository
            ).also { usageRepoInstance = it }
        }
    }

    fun provideUsageRepository(@ApplicationContext context: Context): UsageRepository {
        return provideUsageRepository(
            context,
            provideVisionDao(context),
            provideLocationRepository(context)
        )
    }

    @Provides
    @Singleton
    fun provideVisionEngine(
        dao: VisionDao,
        locationRepository: LocationRepository,
        usageRepository: UsageRepository,
        @ApplicationContext context: Context
    ): VisionEngine {
        return visionEngineInstance ?: synchronized(this) {
            visionEngineInstance ?: VisionEngine(
                dao,
                locationRepository,
                usageRepository,
                context.applicationContext
            ).also { visionEngineInstance = it }
        }
    }

    fun provideVisionEngine(@ApplicationContext context: Context): VisionEngine {
        return provideVisionEngine(
            provideVisionDao(context),
            provideLocationRepository(context),
            provideUsageRepository(context),
            context
        )
    }

    @Provides
    @Singleton
    fun provideVisionEngineFacade(
        visionEngine: VisionEngine,
        locationRepository: LocationRepository,
        usageRepository: UsageRepository,
        dao: VisionDao,
        templateProvider: AppTemplateProvider
    ): VisionEngineFacade {
        return facadeInstance ?: synchronized(this) {
            facadeInstance ?: VisionEngineFacade(
                visionEngine,
                locationRepository,
                usageRepository,
                dao,
                templateProvider
            ).also { facadeInstance = it }
        }
    }

    fun provideVisionEngineFacade(@ApplicationContext context: Context): VisionEngineFacade {
        return provideVisionEngineFacade(
            provideVisionEngine(context),
            provideLocationRepository(context),
            provideUsageRepository(context),
            provideVisionDao(context),
            provideAppTemplateProvider()
        )
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun provideWeatherRepository(okHttpClient: okhttp3.OkHttpClient): com.jupiter.vision.data.weather.WeatherRepository {
        return com.jupiter.vision.data.weather.WeatherRepository(okHttpClient)
    }

    fun provideWeatherRepository(@ApplicationContext context: Context): com.jupiter.vision.data.weather.WeatherRepository {
        return com.jupiter.vision.data.weather.WeatherRepository(provideOkHttpClient())
    }

    @Provides
    @Singleton
    fun provideWallpaperProvider(@ApplicationContext context: Context): com.jupiter.vision.data.wallpaper.WallpaperProvider {
        return com.jupiter.vision.data.wallpaper.WallpaperProvider(context.applicationContext)
    }
}
