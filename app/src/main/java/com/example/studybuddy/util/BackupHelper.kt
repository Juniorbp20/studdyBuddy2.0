package com.example.studybuddy.util

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object BackupHelper {

    private const val BACKUP_DIR = "backups"
    private const val BACKUP_INTERVAL_MS = 24L * 60 * 60 * 1000

    fun maybeBackup(context: Context) {
        try {
            val dbFile = context.getDatabasePath("task_database")
            if (!dbFile.exists()) return

            val backupDir = File(context.filesDir, BACKUP_DIR).apply { mkdirs() }
            val backupFile = File(backupDir, "task_database.db")
            if (backupFile.exists() &&
                System.currentTimeMillis() - backupFile.lastModified() < BACKUP_INTERVAL_MS
            ) {
                return
            }

            FileInputStream(dbFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            // No romper el arranque si el backup falla
        }
    }

    fun listBackups(context: Context): List<File> {
        val backupDir = File(context.filesDir, BACKUP_DIR)
        return backupDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
}