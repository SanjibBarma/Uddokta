package com.uddoktahisab.app.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.uddoktahisab.app.data.local.db.AppDatabase
import com.uddoktahisab.app.data.local.db.LocalDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun gson(): Gson = Gson()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "uddokta_hisab.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun dao(db: AppDatabase): LocalDao = db.dao()
}
