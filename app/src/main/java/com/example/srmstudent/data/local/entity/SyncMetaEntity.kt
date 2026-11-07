package com.example.srmstudent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetaEntity(
    @PrimaryKey
    val key: String = "last_sync",
    val lastSyncTime: String,
    val syncStatus: String,
    val source: String
)

