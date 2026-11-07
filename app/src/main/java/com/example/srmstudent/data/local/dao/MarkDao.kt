package com.example.srmstudent.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.srmstudent.data.local.entity.MarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkDao {

    @Query("SELECT * FROM marks ORDER BY subject ASC, assessment ASC")
    fun getAllMarks(): Flow<List<MarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarks(marks: List<MarkEntity>)

    @Query("DELETE FROM marks")
    suspend fun clearMarks()

    @Transaction
    suspend fun replaceMarks(marks: List<MarkEntity>) {
        clearMarks()
        insertMarks(marks)
    }
}

