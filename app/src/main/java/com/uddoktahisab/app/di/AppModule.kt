package com.uddoktahisab.app.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.uddoktahisab.app.BuildConfig
import com.uddoktahisab.app.data.local.db.*
import com.uddoktahisab.app.data.remote.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class) object AppModule {
    @Provides @Singleton fun gson()=Gson()
    @Provides @Singleton fun database(@ApplicationContext context:Context)=Room.databaseBuilder(context,AppDatabase::class.java,"uddokta_hisab.db").fallbackToDestructiveMigration().build()
    @Provides fun dao(db:AppDatabase):LocalDao=db.dao()
    @Provides @Singleton fun api(gson:Gson):ApiService {
        val logger=HttpLoggingInterceptor().apply{level=if(BuildConfig.DEBUG)HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE}
        val client=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).writeTimeout(20,TimeUnit.SECONDS).retryOnConnectionFailure(true).connectionPool(ConnectionPool(5,5,TimeUnit.MINUTES)).followRedirects(true).followSslRedirects(true).addInterceptor(logger).build()
        return Retrofit.Builder().baseUrl("https://script.google.com/").client(client).addConverterFactory(GsonConverterFactory.create(gson)).build().create(ApiService::class.java)
    }
}
