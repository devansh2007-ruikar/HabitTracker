package com.devansh.habittracker.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.devansh.core.model.HabitType;
import com.devansh.core.model.ScheduleType;
import com.devansh.habittracker.data.dao.HabitDao;
import com.devansh.habittracker.data.dao.HabitLogDao;
import com.devansh.habittracker.data.entity.HabitEntity;
import com.devansh.habittracker.data.entity.HabitLogEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Instrumented test verifying the SQLite/Room persistence layer.
 *
 * <p>Requirements covered:
 * <ul>
 *   <li>Insert habit and logs.</li>
 *   <li>Restart the DB (close connection, reopen from the same disk file). Data is still there.</li>
 *   <li>Upsert overwrites the same day: inserting a log for an existing (habitId, date)
 *       replaces the previous value without creating a duplicate row.</li>
 *   <li>Foreign key CASCADE delete: deleting a habit purges all associated logs.</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseInstrumentationTest {

    private static final String TEST_DB_NAME = "test_habits.db";

    private Context context;
    private HabitTrackerDatabase db;
    private HabitDao habitDao;
    private HabitLogDao habitLogDao;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        // Ensure clean state before test
        context.deleteDatabase(TEST_DB_NAME);

        // Open persistent file-backed database (allowing main thread queries for test assertions)
        db = Room.databaseBuilder(context, HabitTrackerDatabase.class, TEST_DB_NAME)
                .allowMainThreadQueries()
                .build();
        habitDao = db.habitDao();
        habitLogDao = db.habitLogDao();
    }

    @After
    public void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
        context.deleteDatabase(TEST_DB_NAME);
    }

    @Test
    public void testInsertHabitAndLogs_survivesDatabaseRestart() {
        // 1. Insert a habit
        HabitEntity habit = new HabitEntity();
        habit.name = "Morning Running";
        habit.habitType = HabitType.COUNTABLE.name();
        habit.scheduleType = ScheduleType.DAILY.name();
        habit.weekdaysBitmask = 0;
        habit.timesPerWeek = 0;
        habit.target = 5.0;
        habit.unit = "km";
        habit.reminderTime = LocalTime.of(7, 30);
        habit.createdDate = LocalDate.of(2026, 1, 10);
        habit.archived = false;

        long habitId = habitDao.insert(habit);
        assertTrue("Generated habit ID should be greater than 0", habitId > 0);

        // 2. Insert two distinct logs for this habit
        LocalDate date1 = LocalDate.of(2026, 1, 10);
        LocalDate date2 = LocalDate.of(2026, 1, 11);

        HabitLogEntity log1 = new HabitLogEntity();
        log1.habitId = habitId;
        log1.date = date1;
        log1.value = 5.0;
        habitLogDao.upsert(log1);

        HabitLogEntity log2 = new HabitLogEntity();
        log2.habitId = habitId;
        log2.date = date2;
        log2.value = 6.2;
        habitLogDao.upsert(log2);

        // 3. Restart the database: close DB instance 1, reopen DB instance 2 on the same disk file
        db.close();

        HabitTrackerDatabase reopenedDb = Room.databaseBuilder(
                context,
                HabitTrackerDatabase.class,
                TEST_DB_NAME
        ).allowMainThreadQueries().build();

        try {
            HabitDao reopenedHabitDao = reopenedDb.habitDao();
            HabitLogDao reopenedHabitLogDao = reopenedDb.habitLogDao();

            // 4. Verify the habit survived the restart
            HabitEntity restoredHabit = reopenedHabitDao.getHabitById(habitId);
            assertNotNull("Restored habit should not be null", restoredHabit);
            assertEquals("Morning Running", restoredHabit.name);
            assertEquals(HabitType.COUNTABLE.name(), restoredHabit.habitType);
            assertEquals(5.0, restoredHabit.target, 0.001);
            assertEquals("km", restoredHabit.unit);
            assertEquals(LocalTime.of(7, 30), restoredHabit.reminderTime);
            assertEquals(LocalDate.of(2026, 1, 10), restoredHabit.createdDate);

            // 5. Verify both logs survived the restart
            List<HabitLogEntity> restoredLogs = reopenedHabitLogDao.getLogsForHabitSync(habitId);
            assertEquals("Should restore exactly 2 logs", 2, restoredLogs.size());
            assertEquals(date1, restoredLogs.get(0).date);
            assertEquals(5.0, restoredLogs.get(0).value, 0.001);
            assertEquals(date2, restoredLogs.get(1).date);
            assertEquals(6.2, restoredLogs.get(1).value, 0.001);
        } finally {
            reopenedDb.close();
        }
    }

    @Test
    public void testUpsertLog_overwritesSameDayWithoutCreatingDuplicates() {
        // 1. Insert parent habit
        HabitEntity habit = new HabitEntity();
        habit.name = "Read Books";
        habit.habitType = HabitType.COUNTABLE.name();
        habit.scheduleType = ScheduleType.DAILY.name();
        habit.weekdaysBitmask = 0;
        habit.timesPerWeek = 0;
        habit.target = 20.0;
        habit.unit = "pages";
        habit.createdDate = LocalDate.of(2026, 2, 1);
        habit.archived = false;

        long habitId = habitDao.insert(habit);

        LocalDate testDate = LocalDate.of(2026, 2, 5);

        // 2. Initial log for the day: 10 pages logged
        HabitLogEntity initialLog = new HabitLogEntity();
        initialLog.habitId = habitId;
        initialLog.date = testDate;
        initialLog.value = 10.0;
        habitLogDao.upsert(initialLog);

        List<HabitLogEntity> logsAfterFirstInsert = habitLogDao.getLogsForHabitSync(habitId);
        assertEquals(1, logsAfterFirstInsert.size());
        assertEquals(10.0, logsAfterFirstInsert.get(0).value, 0.001);

        // 3. User updates past-day log: now 25 pages logged on the SAME day
        HabitLogEntity updatedLog = new HabitLogEntity();
        updatedLog.habitId = habitId;
        updatedLog.date = testDate;
        updatedLog.value = 25.0; // Updated value
        habitLogDao.upsert(updatedLog);

        // 4. Verify UNIQUE index + REPLACE: row count is STILL 1, value is updated to 25.0
        List<HabitLogEntity> logsAfterUpsert = habitLogDao.getLogsForHabitSync(habitId);
        assertEquals("Upsert on same day must not create duplicate entries", 1, logsAfterUpsert.size());
        assertEquals("Log value should be updated to latest edit", 25.0, logsAfterUpsert.get(0).value, 0.001);
    }

    @Test
    public void testCascadeDelete_deletingHabitPurgesAllAssociatedLogs() {
        // 1. Insert habit
        HabitEntity habit = new HabitEntity();
        habit.name = "Temporary Habit";
        habit.habitType = HabitType.YES_NO.name();
        habit.scheduleType = ScheduleType.DAILY.name();
        habit.target = 1.0;
        habit.createdDate = LocalDate.of(2026, 3, 1);

        long habitId = habitDao.insert(habit);

        // 2. Insert logs
        HabitLogEntity log1 = new HabitLogEntity();
        log1.habitId = habitId;
        log1.date = LocalDate.of(2026, 3, 2);
        log1.value = 1.0;
        habitLogDao.upsert(log1);

        HabitLogEntity log2 = new HabitLogEntity();
        log2.habitId = habitId;
        log2.date = LocalDate.of(2026, 3, 3);
        log2.value = 1.0;
        habitLogDao.upsert(log2);

        assertEquals(2, habitLogDao.getLogsForHabitSync(habitId).size());

        // 3. Delete the habit
        habit.id = habitId;
        habitDao.delete(habit);

        // 4. Verify logs were automatically cascade-deleted
        List<HabitLogEntity> remainingLogs = habitLogDao.getLogsForHabitSync(habitId);
        assertTrue("All logs should be cascade-deleted when parent habit is deleted", remainingLogs.isEmpty());
    }

    @Test
    public void testTodoTaskPersistenceAndRestart() {
        com.devansh.habittracker.data.dao.TodoTaskDao todoDao = db.todoTaskDao();
        com.devansh.habittracker.data.entity.TodoTaskEntity task = new com.devansh.habittracker.data.entity.TodoTaskEntity(
                0, "Buy coffee beans", false, LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 11)
        );
        long taskId = todoDao.insert(task);
        assertTrue(taskId > 0);

        // Restart DB
        db.close();
        db = Room.databaseBuilder(context, HabitTrackerDatabase.class, TEST_DB_NAME)
                .allowMainThreadQueries()
                .build();
        todoDao = db.todoTaskDao();

        com.devansh.habittracker.data.entity.TodoTaskEntity loaded = todoDao.getByIdSync(taskId);
        assertNotNull(loaded);
        assertEquals("Buy coffee beans", loaded.title);
        assertEquals(false, loaded.isCompleted);
    }
}
