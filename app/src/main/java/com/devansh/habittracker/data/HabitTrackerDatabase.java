package com.devansh.habittracker.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.devansh.habittracker.data.converter.DateConverters;
import com.devansh.habittracker.data.dao.HabitDao;
import com.devansh.habittracker.data.dao.HabitLogDao;
import com.devansh.habittracker.data.entity.HabitEntity;
import com.devansh.habittracker.data.entity.HabitLogEntity;

/**
 * Room database for the Habit Tracker app.
 *
 * <ul>
 *   <li><b>Version 1</b> — initial schema with habits and habit_logs tables.</li>
 *   <li><b>exportSchema = true</b> — Room writes JSON schema files to
 *       {@code app/schemas/} for migration testing and version history.</li>
 * </ul>
 *
 * Uses the singleton pattern: only one instance exists per process,
 * obtained via {@link #getInstance(Context)}.
 */
@Database(
    entities = {HabitEntity.class, HabitLogEntity.class, com.devansh.habittracker.data.entity.TodoTaskEntity.class},
    version = 2,
    exportSchema = true
)
@TypeConverters(DateConverters.class)
public abstract class HabitTrackerDatabase extends RoomDatabase {

    /** Access the habit DAO. */
    public abstract HabitDao habitDao();

    /** Access the habit log DAO. */
    public abstract HabitLogDao habitLogDao();

    /** Access the to-do task DAO. */
    public abstract com.devansh.habittracker.data.dao.TodoTaskDao todoTaskDao();

    // ── Singleton ────────────────────────────────────────────────────

    /** Volatile ensures visibility across threads without full synchronization. */
    private static volatile HabitTrackerDatabase INSTANCE;

    /**
     * Returns the singleton database instance, creating it if necessary.
     *
     * Uses double-checked locking for thread-safe lazy initialization.
     * The database file is named "habit_tracker.db".
     *
     * @param context application context (or any context — Room uses appContext internally)
     * @return the singleton database instance
     */
    public static HabitTrackerDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (HabitTrackerDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            HabitTrackerDatabase.class,
                            "habit_tracker.db"
                    )
                    .addMigrations(DatabaseMigrations.ALL_MIGRATIONS)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
