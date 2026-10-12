package com.nevoit.cresto.data.todo

import androidx.room.Room
import com.nevoit.cresto.data.todo.calendar.TodoCalendarSyncManager
import com.nevoit.cresto.data.todo.reminder.TodoAlarmScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {

    single {
        Room.databaseBuilder(
            androidContext(),
            TodoDatabase::class.java,
            "todo_database"
        )
            .addMigrations(MIGRATION_25_26)
            .addMigrations(MIGRATION_26_27)
            .addMigrations(MIGRATION_27_28)
            .addMigrations(MIGRATION_28_29)
            .addMigrations(MIGRATION_29_30)
            .addMigrations(MIGRATION_30_31)
            .fallbackToDestructiveMigration(true)
            .build()
    }

    single { get<TodoDatabase>().todoDao() }
    single { TodoAlarmScheduler(androidContext()) }
    single { TodoCalendarSyncManager(androidContext()) }
    singleOf(::TodoRepository)
    viewModelOf(::TodoViewModel)
}
