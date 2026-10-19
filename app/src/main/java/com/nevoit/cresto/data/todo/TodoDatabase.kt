package com.nevoit.cresto.data.todo

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nevoit.cresto.data.utils.Converters

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE todo_items ADD COLUMN repeatRuleId TEXT")
        db.execSQL("ALTER TABLE todo_items ADD COLUMN seriesId TEXT")
        db.execSQL("ALTER TABLE todo_items ADD COLUMN occurrenceDate TEXT")
        db.execSQL("ALTER TABLE todo_items ADD COLUMN generatedFromTodoId INTEGER")
        db.execSQL("ALTER TABLE todo_items ADD COLUMN occurrenceEditedAt TEXT")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_repeatRuleId` ON `todo_items` (`repeatRuleId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_todo_items_seriesId_occurrenceDate` ON `todo_items` (`seriesId`, `occurrenceDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_generatedFromTodoId` ON `todo_items` (`generatedFromTodoId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `repeat_rules` (
                `id` TEXT NOT NULL, 
                `anchorDate` TEXT NOT NULL, 
                `createNextOnCompletion` INTEGER NOT NULL, 
                `endDate` TEXT, 
                `frequency` TEXT NOT NULL, 
                `interval` INTEGER NOT NULL, 
                `maxOccurrences` INTEGER, 
                `monthDay` INTEGER, 
                `seriesId` TEXT NOT NULL, 
                `weekdays` TEXT, 
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_repeat_rules_seriesId` ON `repeat_rules` (`seriesId`)")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `todo_groups` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `color` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_todo_groups_name` ON `todo_groups` (`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_groups_sortOrder` ON `todo_groups` (`sortOrder`)")

        db.execSQL("ALTER TABLE todo_items ADD COLUMN groupId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_groupId_isCompleted` ON `todo_items` (`groupId`, `isCompleted`)")
    }
}

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE repeat_rules ADD COLUMN monthDays TEXT")
        db.execSQL("ALTER TABLE repeat_rules ADD COLUMN months TEXT")
        db.execSQL(
            "UPDATE repeat_rules SET monthDays = CAST(monthDay AS TEXT) WHERE monthDay IS NOT NULL"
        )
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE todo_items ADD COLUMN deletedAt TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_todo_items_deletedAt` ON `todo_items` (`deletedAt`)"
        )
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE todo_items ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE todo_items ADD COLUMN location TEXT NOT NULL DEFAULT ''"
        )
    }
}

val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE todo_items ADD COLUMN endDate TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_todo_items_endDate` ON `todo_items` (`endDate`)"
        )
    }
}

@Database(
    entities = [TodoItem::class, SubTodoItem::class, RepeatRule::class, TodoGroup::class],
    version = 32,
    exportSchema = true
)

@TypeConverters(Converters::class)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
}
