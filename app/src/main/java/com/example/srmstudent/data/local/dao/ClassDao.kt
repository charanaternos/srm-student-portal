package com.example.srmstudent.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.srmstudent.data.local.entity.ClassEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassDao {

    @Query("SELECT * FROM classes ORDER BY dayOrder ASC, startTime ASC")
    fun getAllClasses(): Flow<List<ClassEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClasses(classes: List<ClassEntity>)

    @Query("DELETE FROM classes")
    suspend fun clearClasses()

    @Transaction
    suspend fun replaceClasses(classes: List<ClassEntity>) {
        clearClasses()
        insertClasses(classes)
    }
}

