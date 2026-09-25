package com.example.studybuddy.model

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubTaskDao {

    @Insert
    suspend fun insert(subTask: SubTask): Long

    @Update
    suspend fun update(subTask: SubTask)

    @Delete
    suspend fun delete(subTask: SubTask)

    @Query("SELECT * FROM subtasks WHERE task_id = :taskId ORDER BY id ASC")
    fun getSubtasksForTask(taskId: Int): Flow<List<SubTask>>

    @Query("DELETE FROM subtasks WHERE task_id = :taskId")
    suspend fun deleteForTask(taskId: Int)
}