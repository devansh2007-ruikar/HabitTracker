package com.devansh.habittracker.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.devansh.habittracker.data.entity.HabitLogEntity;

import java.time.LocalDate;
import java.util.List;

/**
 * Data Access Object for {@link HabitLogEntity}.
 *
 * The key operation is {@link #upsert(HabitLogEntity)}, which uses
 * {@code OnConflictStrategy.REPLACE} on the UNIQUE(habit_id, date) index.
 * This means inserting a log for a date that already has an entry will
 * overwrite it — implementing "latest edit wins" for past-day corrections.
 */
@Dao
public interface HabitLogDao {

    /**
     * Inserts or replaces a log entry for a given habit + date combination.
     *
     * Because habit_logs has a UNIQUE index on (habit_id, date), inserting a
     * duplicate triggers REPLACE: the old row is deleted and the new one is
     * inserted. This is the upsert pattern for past-day edits.
     *
     * @param log the log entry to insert or replace
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(HabitLogEntity log);

    /**
     * Returns all logs for a specific habit, ordered by date descending.
     * LiveData ensures the UI updates when logs are added/edited.
     */
    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId ORDER BY date DESC")
    LiveData<List<HabitLogEntity>> getLogsForHabit(long habitId);

    /**
     * Returns all logs for a habit within a date range [from, to], inclusive.
     * Dates are stored as ISO strings, so lexicographic comparison works correctly.
     * LiveData auto-refreshes the UI when data changes.
     */
    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId " +
           "AND date >= :from AND date <= :to ORDER BY date ASC")
    LiveData<List<HabitLogEntity>> getLogsForHabitInRange(long habitId,
                                                          LocalDate from, LocalDate to);

    /**
     * Blocking version: returns all logs for a habit (for background calculations).
     */
    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId ORDER BY date ASC")
    List<HabitLogEntity> getLogsForHabitSync(long habitId);

    /**
     * Returns the log for a specific habit on a specific date, or null.
     * Blocking — used in background threads for streak/stats calculations.
     */
    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId AND date = :date LIMIT 1")
    HabitLogEntity getLogForDate(long habitId, LocalDate date);

    /**
     * Deletes a specific log entry by its primary key ID.
     */
    @Query("DELETE FROM habit_logs WHERE id = :logId")
    void deleteById(long logId);

    /**
     * Returns all logs for a habit, as LiveData, ordered ascending by date.
     * Used for streak/stats calculations that observe changes in real-time.
     */
    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId ORDER BY date ASC")
    LiveData<List<HabitLogEntity>> getAllLogsForHabitLive(long habitId);

    /**
     * Returns all logs across all habits as LiveData.
     */
    @Query("SELECT * FROM habit_logs ORDER BY date ASC")
    LiveData<List<HabitLogEntity>> getAllLogsLive();

    /**
     * Returns all logs across all habits synchronously.
     */
    @Query("SELECT * FROM habit_logs ORDER BY date ASC")
    List<HabitLogEntity> getAllLogsSync();

    /**
     * Deletes all logs in the table.
     */
    @Query("DELETE FROM habit_logs")
    void deleteAll();
}
