package com.example.srmstudent.di

import android.content.Context
import androidx.room.Room
import com.example.srmstudent.data.local.SrmDatabase
import com.example.srmstudent.data.local.dao.AttendanceDao
import com.example.srmstudent.data.local.dao.ClassDao
import com.example.srmstudent.data.local.dao.MarkDao
import com.example.srmstudent.data.local.dao.ProfileDao
import com.example.srmstudent.data.local.dao.SyncMetaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SrmDatabase {
        return Room.databaseBuilder(
            context,
            SrmDatabase::class.java,
            "srm_student_portal.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideClassDao(database: SrmDatabase): ClassDao = database.classDao()

    @Provides
    fun provideAttendanceDao(database: SrmDatabase): AttendanceDao = database.attendanceDao()

    @Provides
    fun provideMarkDao(database: SrmDatabase): MarkDao = database.markDao()

    @Provides
    fun provideProfileDao(database: SrmDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideSyncMetaDao(database: SrmDatabase): SyncMetaDao = database.syncMetaDao()
}

