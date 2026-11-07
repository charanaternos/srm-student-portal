package com.example.srmstudent.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.srmstudent.data.local.dao.AttendanceDao
import com.example.srmstudent.data.local.dao.ClassDao
import com.example.srmstudent.data.local.dao.MarkDao
import com.example.srmstudent.data.local.dao.ProfileDao
import com.example.srmstudent.data.local.dao.SyncMetaDao
import com.example.srmstudent.data.local.entity.AttendanceEntity
import com.example.srmstudent.data.local.entity.ClassEntity
import com.example.srmstudent.data.local.entity.MarkEntity
import com.example.srmstudent.data.local.entity.ProfileEntity
import com.example.srmstudent.data.local.entity.SyncMetaEntity

@Database(
    entities = [
        ClassEntity::class,
        AttendanceEntity::class,
        MarkEntity::class,
        ProfileEntity::class,
        SyncMetaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SrmDatabase : RoomDatabase() {
    abstract fun classDao(): ClassDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun markDao(): MarkDao
    abstract fun profileDao(): ProfileDao
    abstract fun syncMetaDao(): SyncMetaDao
}

