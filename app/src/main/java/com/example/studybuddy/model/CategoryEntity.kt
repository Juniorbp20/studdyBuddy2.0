package com.example.studybuddy.model

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.studybuddy.R

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "icon")
    val icon: String = DEFAULT_ICON,

    @ColumnInfo(name = "color")
    val color: Int = DEFAULT_COLOR
) {
    val isDefault: Boolean
        get() = id in 1..4

    companion object {
        const val DEFAULT_ICON = "ic_cat_star"
        const val DEFAULT_COLOR = 0xFF607D8B.toInt()

        const val ID_GENERAL = 1
        const val ID_STUDY = 2
        const val ID_WORK = 3
        const val ID_PERSONAL = 4

        val DEFAULT_KEYS = listOf("general", "study", "work", "personal")

        val CUSTOM_COLORS = listOf(
            0xFF7B1FA2.toInt(),
            0xFFC62828.toInt(),
            0xFF3949AB.toInt(),
            0xFF6D4C41.toInt(),
            0xFF0097A7.toInt(),
            0xFFE91E63.toInt(),
            0xFF5E35B1.toInt(),
            0xFF1E88E5.toInt()
        )

        val ICON_KEYS = listOf(
            "ic_cat_star",
            "ic_cat_book",
            "ic_cat_briefcase",
            "ic_cat_home",
            "ic_cat_heart",
            "ic_cat_fitness",
            "ic_cat_cart",
            "ic_cat_music"
        )

        fun defaultCategories(): List<CategoryEntity> = listOf(
            CategoryEntity(id = ID_GENERAL, name = "general", icon = "ic_cat_star", color = 0xFF607D8B.toInt()),
            CategoryEntity(id = ID_STUDY, name = "study", icon = "ic_cat_book", color = 0xFF3F51B5.toInt()),
            CategoryEntity(id = ID_WORK, name = "work", icon = "ic_cat_briefcase", color = 0xFFF57C00.toInt()),
            CategoryEntity(id = ID_PERSONAL, name = "personal", icon = "ic_cat_home", color = 0xFF00897B.toInt())
        )
    }
}

fun CategoryEntity.displayName(context: Context): String {
    val res = when (name) {
        "general" -> R.string.category_general
        "study" -> R.string.category_study
        "work" -> R.string.category_work
        "personal" -> R.string.category_personal
        else -> null
    }
    return res?.let { context.getString(it) } ?: name
}

fun String.categoryIconRes(): Int? {
    return if (this.isBlank()) null else
        try {
            com.example.studybuddy.R.drawable::class.java
                .getField(this)
                .getInt(null)
        } catch (e: Exception) {
            null
        }
}