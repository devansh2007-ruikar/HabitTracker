package com.devansh.habittracker.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Room entity representing a habit stored in the local SQLite database.
 *
 * Schedule information is flattened into three columns:
 * <ul>
 *   <li>{@code scheduleType} — "DAILY", "SPECIFIC_DAYS", or "TIMES_PER_WEEK"</li>
 *   <li>{@code weekdaysBitmask} — a 7-bit integer encoding which days are selected
 *       (Monday = bit 0, Sunday = bit 6). Only meaningful for SPECIFIC_DAYS.</li>
 *   <li>{@code timesPerWeek} — how many times per week. Only meaningful for TIMES_PER_WEEK.</li>
 * </ul>
 *
 * Dates are stored as ISO-8601 strings via
 * {@link com.devansh.habittracker.data.converter.DateConverters}.
 */
@Entity(tableName = "habits")
public class HabitEntity {

    /** Auto-generated primary key. */
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** User-visible name (e.g. "Read", "Gym"). */
    @ColumnInfo(name = "name")
    public String name;

    /**
     * Habit type as a string: "YES_NO", "COUNTABLE", or "TIMED".
     * Stored as text so the schema is human-readable and forward-compatible.
     */
    @ColumnInfo(name = "habit_type")
    public String habitType;

    /**
     * Schedule strategy: "DAILY", "SPECIFIC_DAYS", or "TIMES_PER_WEEK".
     */
    @ColumnInfo(name = "schedule_type")
    public String scheduleType;

    /**
     * Bitmask encoding selected days for SPECIFIC_DAYS schedules.
     *
     * Bit layout (LSB → MSB):
     *   bit 0 = Monday, bit 1 = Tuesday, ... , bit 6 = Sunday.
     *
     * Examples:
     *   Mon+Wed+Fri = 0b0010101 = 21
     *   Every day   = 0b1111111 = 127
     *
     * For DAILY and TIMES_PER_WEEK schedules, this value is 0.
     */
    @ColumnInfo(name = "weekdays_bitmask")
    public int weekdaysBitmask;

    /**
     * How many times per week (1–7) for TIMES_PER_WEEK schedules.
     * 0 for DAILY and SPECIFIC_DAYS.
     */
    @ColumnInfo(name = "times_per_week")
    public int timesPerWeek;

    /**
     * Numeric target per logging event.
     * YES_NO → 1. COUNTABLE → e.g. 20 pages. TIMED → e.g. 15 minutes.
     */
    @ColumnInfo(name = "target")
    public double target;

    /** Display unit for countable/timed habits (e.g. "pages", "min"). Nullable. */
    @ColumnInfo(name = "unit")
    public String unit;

    /**
     * Optional daily reminder time, stored as ISO string (e.g. "08:30").
     * Null means no reminder.
     */
    @ColumnInfo(name = "reminder_time")
    public LocalTime reminderTime;

    /** The date the user created this habit (ISO string, never null). */
    @ColumnInfo(name = "created_date")
    public LocalDate createdDate;

    /** Whether the habit is archived (hidden from daily view). */
    @ColumnInfo(name = "archived")
    public boolean archived;
}
