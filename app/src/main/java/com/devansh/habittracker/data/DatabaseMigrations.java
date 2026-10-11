package com.devansh.habittracker.data;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Database migration strategy and definitions for {@link HabitTrackerDatabase}.
 *
 * <p><b>Strategy:</b>
 * <ul>
 *   <li>The database starts at <b>Version 1</b>.</li>
 *   <li>{@code exportSchema = true} ensures Room writes schema JSON definitions to
 *       {@code app/schemas/com.devansh.habittracker.data.HabitTrackerDatabase/1.json}
 *       during build, enabling automated schema diffing and verification.</li>
 *   <li>When new tables or columns are needed in future versions:
 *     <ol>
 *       <li>Bump {@code version} in {@link HabitTrackerDatabase}.</li>
 *       <li>Define an explicit {@link Migration} instance below (e.g. {@code MIGRATION_1_2}).</li>
 *       <li>Add the new migration to {@link #ALL_MIGRATIONS}.</li>
 *       <li>Write an automated migration test to verify data integrity across versions.</li>
 *     </ol>
 *   </li>
 *   <li>We avoid {@code fallbackToDestructiveMigration()} in production to protect
 *       user habit streaks and logs from accidental data wipes.</li>
 * </ul>
 */
public final class DatabaseMigrations {

    private DatabaseMigrations() {
        // Prevent instantiation — utility class
    }

    /**
     * Migration from Version 1 to Version 2:
     * Adds the todo_tasks table for one-off tasks.
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `todo_tasks` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`title` TEXT NOT NULL, "
                    + "`is_completed` INTEGER NOT NULL, "
                    + "`due_date` TEXT, "
                    + "`created_date` TEXT NOT NULL)");
        }
    };

    /**
     * All migrations registered for {@link HabitTrackerDatabase}.
     */
    public static final Migration[] ALL_MIGRATIONS = new Migration[] {
        MIGRATION_1_2
    };
}
