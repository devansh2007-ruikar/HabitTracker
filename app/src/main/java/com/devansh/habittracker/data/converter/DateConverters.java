package com.devansh.habittracker.data.converter;

import androidx.room.TypeConverter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Room TypeConverters for java.time types.
 *
 * Room cannot store LocalDate or LocalTime natively, so we convert them
 * to/from ISO-8601 strings (e.g. "2024-01-15", "08:30").
 *
 * These converters are registered globally in
 * {@link com.devansh.habittracker.data.HabitTrackerDatabase}.
 */
public final class DateConverters {

    // Prevent instantiation — Room uses the static methods via reflection
    private DateConverters() {}

    // ── LocalDate ↔ String ("yyyy-MM-dd") ────────────────────────────

    /**
     * Converts an ISO date string (e.g. "2024-01-15") to a LocalDate.
     * Returns null if the input is null (Room stores NULL for nullable columns).
     */
    @TypeConverter
    public static LocalDate fromDateString(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    /**
     * Converts a LocalDate to an ISO date string (e.g. "2024-01-15").
     * Returns null if the input is null.
     */
    @TypeConverter
    public static String toDateString(LocalDate date) {
        return date == null ? null : date.toString(); // ISO-8601 by default
    }

    // ── LocalTime ↔ String ("HH:mm") ────────────────────────────────

    /**
     * Converts an ISO time string (e.g. "08:30") to a LocalTime.
     * Returns null if the input is null (reminder is optional).
     */
    @TypeConverter
    public static LocalTime fromTimeString(String value) {
        return value == null ? null : LocalTime.parse(value);
    }

    /**
     * Converts a LocalTime to an ISO time string (e.g. "08:30").
     * Returns null if the input is null.
     */
    @TypeConverter
    public static String toTimeString(LocalTime time) {
        return time == null ? null : time.toString(); // ISO-8601 by default
    }
}
