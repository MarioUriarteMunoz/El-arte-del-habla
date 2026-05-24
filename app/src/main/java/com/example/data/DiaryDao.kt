package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<DiaryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DiaryEntry): Long

    @Delete
    suspend fun deleteEntry(entry: DiaryEntry)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Int)
}
