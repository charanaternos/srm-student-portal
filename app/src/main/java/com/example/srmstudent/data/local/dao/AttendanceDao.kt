package com.example.srmstudent.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.srmstudent.data.local.entity.AttendanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Query("SELECT * FROM attendance ORDER BY subject ASC")
    fun getAllAttendance(): Flow<List<AttendanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(items: List<AttendanceEntity>)

    @Query("DELETE FROM attendance")
    suspend fun clearAttendance()

    @Transaction
    suspend fun replaceAttendance(items: List<AttendanceEntity>) {
        clearAttendance()
        insertAttendance(items)
    }
}

