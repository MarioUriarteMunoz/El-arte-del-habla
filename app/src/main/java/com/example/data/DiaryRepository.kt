package com.example.data

import kotlinx.coroutines.flow.Flow

class DiaryRepository(private val diaryDao: DiaryDao) {
    val allEntries: Flow<List<DiaryEntry>> = diaryDao.getAllEntries()

    suspend fun insert(entry: DiaryEntry): Long {
        return diaryDao.insertEntry(entry)
    }

    suspend fun deleteById(id: Int) {
        diaryDao.deleteEntryById(id)
    }

    suspend fun delete(entry: DiaryEntry) {
        diaryDao.deleteEntry(entry)
    }
}
