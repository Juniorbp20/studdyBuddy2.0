package com.example.studybuddy.data

import android.content.Context
import com.example.studybuddy.model.AppDatabase

object ServiceLocator {

    @Volatile
    private var repository: TaskRepository? = null

    fun getRepository(context: Context): TaskRepository {
        return repository ?: synchronized(this) {
            repository ?: TaskRepository(context.applicationContext).also { repository = it }
        }
    }

    fun clearForTests() {
        repository = null
    }
}