package com.devansh.habittracker.data.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.core.model.HabitType;
import com.devansh.core.model.Schedule;
import com.devansh.core.model.ScheduleType;
import com.devansh.habittracker.data.entity.HabitEntity;
import com.devansh.habittracker.data.entity.HabitLogEntity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for {@link EntityMappers}.
 *
 * <p>Verifies lossless conversions between:
 * <ul>
 *   <li>Room entities (flattened DB format) and :core domain models.</li>
 *   <li>Schedule day-of-week bitmasks (Monday=bit 0 .. Sunday=bit 6).</li>
 *   <li>Nullable fields (reminderTime, unit).</li>
 * </ul>
 */
class EntityMappersTest {

    @Test
    @DisplayName("Daily habit: entity <-> model round trip")
    void testDailyHabitRoundTrip() {
        Habit habit = new Habit(
                42L,
                "Meditation",
                HabitType.TIMED,
                Schedule.daily(),
                15.0,
                "min",
                LocalTime.of(8, 0),
                LocalDate.of(2026, 1, 1),
                false
        );

        HabitEntity entity = EntityMappers.toEntity(habit);
        assertEquals(42L, entity.id);
        assertEquals("Meditation", entity.name);
        assertEquals("TIMED", entity.habitType);
        assertEquals("DAILY", entity.scheduleType);
        assertEquals(0, entity.weekdaysBitmask);
        assertEquals(0, entity.timesPerWeek);
        assertEquals(15.0, entity.target);
        assertEquals("min", entity.unit);
        assertEquals(LocalTime.of(8, 0), entity.reminderTime);
        assertEquals(LocalDate.of(2026, 1, 1), entity.createdDate);
        assertFalse(entity.archived);

        Habit restored = EntityMappers.toHabit(entity);
        assertEquals(habit.getId(), restored.getId());
        assertEquals(habit.getName(), restored.getName());
        assertEquals(habit.getType(), restored.getType());
        assertEquals(habit.getSchedule().getType(), restored.getSchedule().getType());
        assertEquals(habit.getTarget(), restored.getTarget());
        assertEquals(habit.getUnit(), restored.getUnit());
        assertEquals(habit.getReminderTime(), restored.getReminderTime());
        assertEquals(habit.getCreatedDate(), restored.getCreatedDate());
        assertEquals(habit.isArchived(), restored.isArchived());
    }

    @Test
    @DisplayName("Specific days schedule: bitmask encoding and decoding round trip")
    void testSpecificDaysScheduleBitmask() {
        // Monday (bit 0), Wednesday (bit 2), Friday (bit 4) -> 1 + 4 + 16 = 21 (0b0010101)
        Set<DayOfWeek> days = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        Habit habit = new Habit(
                10L,
                "Gym Workout",
                HabitType.COUNTABLE,
                Schedule.specificDays(days),
                1.0,
                "session",
                null,
                LocalDate.of(2026, 2, 1),
                true
        );

        HabitEntity entity = EntityMappers.toEntity(habit);
        assertEquals("SPECIFIC_DAYS", entity.scheduleType);
        assertEquals(21, entity.weekdaysBitmask); // 1 + 4 + 16 = 21
        assertNull(entity.reminderTime);
        assertTrue(entity.archived);

        Habit restored = EntityMappers.toHabit(entity);
        assertEquals(ScheduleType.SPECIFIC_DAYS, restored.getSchedule().getType());
        assertEquals(days, restored.getSchedule().getDaysOfWeek());
        assertNull(restored.getReminderTime());
        assertTrue(restored.isArchived());
    }

    @Test
    @DisplayName("Specific days: Weekend only bitmask (Saturday bit 5 + Sunday bit 6)")
    void testWeekendBitmask() {
        // Saturday (day value 6 -> bit 5 = 32), Sunday (day value 7 -> bit 6 = 64) -> 96
        Set<DayOfWeek> weekend = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        Habit habit = new Habit(
                11L,
                "Weekend Hiking",
                HabitType.YES_NO,
                Schedule.specificDays(weekend),
                1.0,
                null,
                null,
                LocalDate.of(2026, 3, 1),
                false
        );

        HabitEntity entity = EntityMappers.toEntity(habit);
        assertEquals(96, entity.weekdaysBitmask); // 32 + 64 = 96

        Habit restored = EntityMappers.toHabit(entity);
        assertEquals(weekend, restored.getSchedule().getDaysOfWeek());
    }

    @Test
    @DisplayName("Times per week schedule: timesPerWeek preserved")
    void testTimesPerWeekPreserved() {
        Habit habit = new Habit(
                12L,
                "Read Book",
                HabitType.COUNTABLE,
                Schedule.timesPerWeek(4),
                20.0,
                "pages",
                null,
                LocalDate.of(2026, 1, 1),
                false
        );

        HabitEntity entity = EntityMappers.toEntity(habit);
        assertEquals("TIMES_PER_WEEK", entity.scheduleType);
        assertEquals(4, entity.timesPerWeek);
        assertEquals(0, entity.weekdaysBitmask);

        Habit restored = EntityMappers.toHabit(entity);
        assertEquals(ScheduleType.TIMES_PER_WEEK, restored.getSchedule().getType());
        assertEquals(4, restored.getSchedule().getTimesPerWeek());
    }

    @Test
    @DisplayName("HabitLog <-> HabitLogEntity round trip")
    void testHabitLogRoundTrip() {
        HabitLog log = new HabitLog(5L, LocalDate.of(2026, 5, 20), 45.5);

        HabitLogEntity entity = EntityMappers.toEntity(log);
        assertEquals(5L, entity.habitId);
        assertEquals(LocalDate.of(2026, 5, 20), entity.date);
        assertEquals(45.5, entity.value, 0.001);

        HabitLog restored = EntityMappers.toHabitLog(entity);
        assertEquals(log.getHabitId(), restored.getHabitId());
        assertEquals(log.getDate(), restored.getDate());
        assertEquals(log.getValue(), restored.getValue(), 0.001);
    }

    @Test
    @DisplayName("List mappers convert collections properly")
    void testListMappers() {
        HabitEntity e1 = new HabitEntity();
        e1.id = 1L;
        e1.name = "H1";
        e1.habitType = "YES_NO";
        e1.scheduleType = "DAILY";
        e1.target = 1.0;
        e1.createdDate = LocalDate.of(2026, 1, 1);

        HabitEntity e2 = new HabitEntity();
        e2.id = 2L;
        e2.name = "H2";
        e2.habitType = "TIMED";
        e2.scheduleType = "DAILY";
        e2.target = 30.0;
        e2.createdDate = LocalDate.of(2026, 1, 2);

        List<Habit> habits = EntityMappers.toHabitList(List.of(e1, e2));
        assertEquals(2, habits.size());
        assertEquals("H1", habits.get(0).getName());
        assertEquals("H2", habits.get(1).getName());

        HabitLogEntity l1 = new HabitLogEntity();
        l1.habitId = 1L;
        l1.date = LocalDate.of(2026, 1, 1);
        l1.value = 1.0;

        HabitLogEntity l2 = new HabitLogEntity();
        l2.habitId = 1L;
        l2.date = LocalDate.of(2026, 1, 2);
        l2.value = 1.0;

        List<HabitLog> logs = EntityMappers.toHabitLogList(List.of(l1, l2));
        assertEquals(2, logs.size());
        assertEquals(LocalDate.of(2026, 1, 1), logs.get(0).getDate());
        assertEquals(LocalDate.of(2026, 1, 2), logs.get(1).getDate());
    }

    @Test
    @DisplayName("TodoTask: entity <-> model round trip")
    void testTodoTaskRoundTrip() {
        com.devansh.core.model.TodoTask task = new com.devansh.core.model.TodoTask(
                10L, "Buy groceries", false, LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 10)
        );

        com.devansh.habittracker.data.entity.TodoTaskEntity entity = EntityMappers.toEntity(task);
        assertEquals(10L, entity.id);
        assertEquals("Buy groceries", entity.title);
        assertFalse(entity.isCompleted);
        assertEquals(LocalDate.of(2026, 3, 15), entity.dueDate);
        assertEquals(LocalDate.of(2026, 3, 10), entity.createdDate);

        com.devansh.core.model.TodoTask mappedBack = EntityMappers.toTodoTask(entity);
        assertEquals(task, mappedBack);
    }
}
