package com.devansh.habittracker.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.devansh.habittracker.data.entity.HabitEntity;

import java.util.List;

/**
 * Data Access Object for {@link HabitEntity}.
 *
 * All queries returning LiveData are observed on the main thread by the UI
 * (Room handles the background query automatically for LiveData returns).
 * Write operations (insert/update/delete) must be called from a background
 * thread — the Repository handles this via an Executor.
 */
@Dao
public interface HabitDao {

    /**
     * Inserts a new habit and returns the auto-generated row ID.
     * This is the only way to create a habit — the ID is then used as
     * the foreign key in {@link com.devansh.habittracker.data.entity.HabitLogEntity}.
     */
    @Insert
    long insert(HabitEntity habit);

    /** Updates an existing habit (matched by primary key). */
    @Update
    void update(HabitEntity habit);

    /** Deletes a habit (cascade also removes all its logs). */
    @Delete
    void delete(HabitEntity habit);

    /**
     * Returns all non-archived habits, ordered by creation date (newest first).
     * LiveData ensures the UI automatically re-renders when data changes.
     */
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY created_date DESC")
    LiveData<List<HabitEntity>> getActiveHabits();

    /**
     * Returns all habits (including archived), ordered by creation date.
     */
    @Query("SELECT * FROM habits ORDER BY created_date DESC")
    LiveData<List<HabitEntity>> getAllHabits();

    /**
     * Synchronous version: returns all habits (for background threads and tests).
     */
    @Query("SELECT * FROM habits ORDER BY created_date DESC")
    List<HabitEntity> getAllHabitsSync();

    /**
     * Synchronous version: returns active habits (for background threads and tests).
     */
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY created_date DESC")
    List<HabitEntity> getActiveHabitsSync();

    /**
     * Returns a single habit by its ID (blocking, for background use).
     */
    @Query("SELECT * FROM habits WHERE id = :habitId")
    HabitEntity getHabitById(long habitId);

    /**
     * Returns a single habit by its ID as LiveData (for UI observation).
     */
    @Query("SELECT * FROM habits WHERE id = :habitId")
    LiveData<HabitEntity> getHabitByIdLive(long habitId);

    /**
     * Deletes all habits in the table.
     */
    @Query("DELETE FROM habits")
    void deleteAll();
}
