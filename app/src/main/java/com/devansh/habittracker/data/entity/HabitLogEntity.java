package com.devansh.habittracker.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.LocalDate;

/**
 * Room entity representing a single log entry for a habit on a specific date.
 *
 * Key constraints:
 * <ul>
 *   <li>UNIQUE index on (habitId, date) — enforces one log per habit per day.
 *       The DAO uses {@code OnConflictStrategy.REPLACE} so upserting a log for
 *       the same day overwrites the previous value (latest edit wins).</li>
 *   <li>Foreign key on habitId → habits.id with CASCADE delete — deleting a
 *       habit automatically deletes all its logs.</li>
 * </ul>
 *
 * Dates are stored as ISO-8601 strings via
 * {@link com.devansh.habittracker.data.converter.DateConverters}.
 */
@Entity(
    tableName = "habit_logs",
    indices = {
        // Unique composite index: one log per habit per day.
        // Also speeds up queries that filter by habitId.
        @Index(value = {"habit_id", "date"}, unique = true)
    },
    foreignKeys = {
        // When a habit is deleted, cascade-delete all its log entries.
        @ForeignKey(
            entity = HabitEntity.class,
            parentColumns = "id",
            childColumns = "habit_id",
            onDelete = ForeignKey.CASCADE
        )
    }
)
public class HabitLogEntity {

    /** Auto-generated primary key. */
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Foreign key referencing the parent habit's ID. */
    @ColumnInfo(name = "habit_id")
    public long habitId;

    /** The calendar date this log is for (ISO string, never null). */
    @ColumnInfo(name = "date")
    public LocalDate date;

    /**
     * The logged value:
     * - YES_NO: 0.0 or 1.0
     * - COUNTABLE: number of units (e.g. pages read)
     * - TIMED: number of minutes
     */
    @ColumnInfo(name = "value")
    public double value;
}
