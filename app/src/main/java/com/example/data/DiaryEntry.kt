package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mision: String,
    val enfoque: String,
    val tip: String,
    val audioPath: String? = null,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
