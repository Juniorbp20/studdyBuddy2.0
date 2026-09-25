package com.example.studybuddy

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.studybuddy.model.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        "com.example.studybuddy.model.AppDatabase"
    )

    @Test
    fun migrate1To4KeepsDataAndUsesKeys() {
        createRawDatabase(1, TEST_DB_1_4) { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS tasks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "description TEXT NOT NULL, " +
                    "date TEXT NOT NULL, " +
                    "time TEXT NOT NULL, " +
                    "is_completed INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL(
                "INSERT INTO tasks (title, description, date, time, is_completed) " +
                    "VALUES ('Estudiar', 'Capítulo 3', '2026-08-16', '18:00', 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB_1_4, 4, true,
            AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4
        )

        db.query("SELECT * FROM tasks").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.count)
            assertEquals("Estudiar", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("category_id")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("priority")))
            assertTrue(cursor.getLong(cursor.getColumnIndexOrThrow("due_date")) > 0)
        }
        db.query("SELECT COUNT(*) FROM categories").use { cursor ->
            cursor.moveToFirst()
            assertEquals(4L, cursor.getLong(0))
        }
        db.close()
    }

    @Test
    fun migrate2To4ConvertsCategoriesAndCreatesSubtasks() {
        createRawDatabase(2, TEST_DB_2_4) { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS tasks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "description TEXT NOT NULL, " +
                    "due_date INTEGER NOT NULL DEFAULT 0, " +
                    "is_completed INTEGER NOT NULL DEFAULT 0, " +
                    "category TEXT NOT NULL DEFAULT 'GENERAL', " +
                    "priority INTEGER NOT NULL DEFAULT 1, " +
                    "reminder_enabled INTEGER NOT NULL DEFAULT 0, " +
                    "created_at INTEGER NOT NULL DEFAULT 0, " +
                    "completed_at INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL(
                "INSERT INTO tasks (title, description, category, priority, due_date) " +
                    "VALUES ('Leer', 'Cap 2', 'STUDY', 2, 1786800000000)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB_2_4, 4, true,
            AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4
        )
        db.execSQL("PRAGMA foreign_keys = ON")

        db.query("SELECT category_id, repeat_interval, tags FROM tasks").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals("", cursor.getString(2))
        }
        db.execSQL("INSERT INTO subtasks (task_id, title, is_completed) VALUES (1, 'Parte 1', 0)")
        db.query("SELECT COUNT(*) FROM subtasks").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1L, cursor.getLong(0))
        }
        db.execSQL("DELETE FROM tasks WHERE id = 1")
        db.query("SELECT COUNT(*) FROM subtasks").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0L, cursor.getLong(0))
        }
        db.close()
    }

    @Test
    fun migrate3To4CreatesCategoriesAndMapsTasks() {
        createRawDatabase(3, TEST_DB_3_4) { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS tasks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "description TEXT NOT NULL, " +
                    "due_date INTEGER NOT NULL DEFAULT 0, " +
                    "is_completed INTEGER NOT NULL DEFAULT 0, " +
                    "category TEXT NOT NULL DEFAULT 'GENERAL', " +
                    "priority INTEGER NOT NULL DEFAULT 1, " +
                    "reminder_enabled INTEGER NOT NULL DEFAULT 0, " +
                    "created_at INTEGER NOT NULL DEFAULT 0, " +
                    "completed_at INTEGER NOT NULL DEFAULT 0, " +
                    "repeat_interval INTEGER NOT NULL DEFAULT 0, " +
                    "tags TEXT NOT NULL DEFAULT '')"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS subtasks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "task_id INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "is_completed INTEGER NOT NULL, " +
                    "FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_subtasks_task_id ON subtasks (task_id)"
            )
            db.execSQL(
                "INSERT INTO tasks (title, description, category, due_date) VALUES " +
                    "('Trabajo', 'Reporte', 'WORK', 1786800000000), " +
                    "('Hogar', 'Limpieza', 'PERSONAL', 1786800000000), " +
                    "('Clase', 'Algebra', 'STUDY', 1786800000000)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_3_4, 4, true, AppDatabase.MIGRATION_3_4)

        db.query("SELECT title, category_id FROM tasks ORDER BY id").use { cursor ->
            assertEquals(3, cursor.count)
            cursor.moveToFirst()
            assertEquals("Trabajo", cursor.getString(0))
            assertEquals(3, cursor.getInt(1))
            cursor.moveToNext()
            assertEquals("Hogar", cursor.getString(0))
            assertEquals(4, cursor.getInt(1))
            cursor.moveToNext()
            assertEquals("Clase", cursor.getString(0))
            assertEquals(2, cursor.getInt(1))
        }
        db.query("SELECT name, icon FROM categories ORDER BY id").use { cursor ->
            assertEquals(4, cursor.count)
            cursor.moveToFirst()
            assertEquals("general", cursor.getString(0))
            assertEquals("ic_cat_star", cursor.getString(1))
            cursor.moveToLast()
            assertEquals("personal", cursor.getString(0))
            assertEquals("ic_cat_home", cursor.getString(1))
        }
        db.close()
    }

    private fun createRawDatabase(version: Int, name: String, create: (SupportSQLiteDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    create(db)
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                }
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase.close()
    }

    companion object {
        private const val TEST_DB_1_4 = "migration-test-1-4"
        private const val TEST_DB_2_4 = "migration-test-2-4"
        private const val TEST_DB_3_4 = "migration-test-3-4"
    }
}