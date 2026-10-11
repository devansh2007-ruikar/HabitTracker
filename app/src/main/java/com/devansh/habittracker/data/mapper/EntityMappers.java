package com.devansh.habittracker.data.mapper;

import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.core.model.HabitType;
import com.devansh.core.model.Schedule;
import com.devansh.core.model.ScheduleType;
import com.devansh.habittracker.data.entity.HabitEntity;
import com.devansh.habittracker.data.entity.HabitLogEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Bidirectional mappers between Room entities and :core domain models.
 *
 * These mappers are the single bridge between the Android persistence layer
 * and the pure-Java domain logic. The :core module never sees Room entities;
 * the :app module never passes entities into core calculators.
 *
 * <h3>Schedule encoding</h3>
 * The schedule is stored as three flat columns in HabitEntity:
 * <ul>
 *   <li>{@code scheduleType} — enum name as string</li>
 *   <li>{@code weekdaysBitmask} — 7-bit integer, bit 0 = Monday, bit 6 = Sunday</li>
 *   <li>{@code timesPerWeek} — integer (1–7)</li>
 * </ul>
 *
 * The bitmask encoding uses {@link DayOfWeek#getValue()} (Monday=1 .. Sunday=7)
 * shifted to 0-indexed bits: {@code bit = 1 << (dayOfWeek.getValue() - 1)}.
 */
public final class EntityMappers {

    // Prevent instantiation
    private EntityMappers() {}

    // ═══════════════════════════════════════════════════════════════════
    //  Habit ↔ HabitEntity
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Converts a Room HabitEntity into a :core Habit domain model.
     *
     * @param entity the Room entity (never null)
     * @return the equivalent Habit domain model
     */
    public static Habit toHabit(HabitEntity entity) {
        // Reconstruct the HabitType enum from the stored string
        HabitType type = HabitType.valueOf(entity.habitType);

        // Reconstruct the Schedule from the three flat columns
        Schedule schedule = toSchedule(
                entity.scheduleType,
                entity.weekdaysBitmask,
                entity.timesPerWeek
        );

        return new Habit(
                entity.id,
                entity.name,
                type,
                schedule,
                entity.target,
                entity.unit,
                entity.reminderTime,
                entity.createdDate,
                entity.archived
        );
    }

    /**
     * Converts a :core Habit domain model into a Room HabitEntity.
     *
     * If the habit's id is 0, Room will auto-generate a new ID on insert.
     *
     * @param habit the domain model (never null)
     * @return the equivalent HabitEntity for Room
     */
    public static HabitEntity toEntity(Habit habit) {
        HabitEntity entity = new HabitEntity();
        entity.id = habit.getId();
        entity.name = habit.getName();
        entity.habitType = habit.getType().name();
        entity.scheduleType = habit.getSchedule().getType().name();
        entity.weekdaysBitmask = toBitmask(habit.getSchedule());
        entity.timesPerWeek = habit.getSchedule().getTimesPerWeek();
        entity.target = habit.getTarget();
        entity.unit = habit.getUnit();
        entity.reminderTime = habit.getReminderTime();
        entity.createdDate = habit.getCreatedDate();
        entity.archived = habit.isArchived();
        return entity;
    }

    /**
     * Converts a list of HabitEntity objects into Habit domain models.
     *
     * @param entities list of Room entities
     * @return list of Habit domain models (same order)
     */
    public static List<Habit> toHabitList(List<HabitEntity> entities) {
        List<Habit> habits = new ArrayList<>(entities.size());
        for (HabitEntity entity : entities) {
            habits.add(toHabit(entity));
        }
        return habits;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  HabitLog ↔ HabitLogEntity
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Converts a Room HabitLogEntity into a :core HabitLog domain model.
     *
     * @param entity the Room entity (never null)
     * @return the equivalent HabitLog domain model
     */
    public static HabitLog toHabitLog(HabitLogEntity entity) {
        return new HabitLog(entity.habitId, entity.date, entity.value);
    }

    /**
     * Converts a :core HabitLog domain model into a Room HabitLogEntity.
     *
     * The entity's {@code id} is left as 0 so Room auto-generates it on insert.
     * If this is an upsert, the UNIQUE(habit_id, date) constraint triggers REPLACE.
     *
     * @param log the domain model (never null)
     * @return the equivalent HabitLogEntity for Room
     */
    public static HabitLogEntity toEntity(HabitLog log) {
        HabitLogEntity entity = new HabitLogEntity();
        // id = 0 → Room auto-generates. REPLACE on unique index handles upsert.
        entity.habitId = log.getHabitId();
        entity.date = log.getDate();
        entity.value = log.getValue();
        return entity;
    }

    /**
     * Converts a list of HabitLogEntity objects into HabitLog domain models.
     *
     * @param entities list of Room entities
     * @return list of HabitLog domain models (same order)
     */
    public static List<HabitLog> toHabitLogList(List<HabitLogEntity> entities) {
        List<HabitLog> logs = new ArrayList<>(entities.size());
        for (HabitLogEntity entity : entities) {
            logs.add(toHabitLog(entity));
        }
        return logs;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Schedule bitmask helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Reconstructs a Schedule from the three flat DB columns.
     */
    private static Schedule toSchedule(String scheduleTypeStr, int bitmask, int timesPerWeek) {
        ScheduleType scheduleType = ScheduleType.valueOf(scheduleTypeStr);
        return switch (scheduleType) {
            case DAILY -> Schedule.daily();
            case SPECIFIC_DAYS -> Schedule.specificDays(fromBitmask(bitmask));
            case TIMES_PER_WEEK -> Schedule.timesPerWeek(timesPerWeek);
        };
    }

    /**
     * Encodes a Schedule's selected days into a 7-bit integer bitmask.
     *
     * Bit 0 = Monday (DayOfWeek.getValue() == 1)
     * Bit 6 = Sunday (DayOfWeek.getValue() == 7)
     *
     * @param schedule the schedule to encode
     * @return the bitmask (0 if not SPECIFIC_DAYS)
     */
    private static int toBitmask(Schedule schedule) {
        if (schedule.getType() != ScheduleType.SPECIFIC_DAYS) {
            return 0;
        }
        int mask = 0;
        for (DayOfWeek day : schedule.getDaysOfWeek()) {
            // Monday=1 → bit 0, Tuesday=2 → bit 1, ... Sunday=7 → bit 6
            mask |= (1 << (day.getValue() - 1));
        }
        return mask;
    }

    /**
     * Decodes a 7-bit bitmask back into a Set of DayOfWeek values.
     *
     * @param bitmask the encoded bitmask
     * @return set of DayOfWeek values (never empty if bitmask > 0)
     */
    private static Set<DayOfWeek> fromBitmask(int bitmask) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            int bit = 1 << (day.getValue() - 1);
            if ((bitmask & bit) != 0) {
                days.add(day);
            }
        }
        return days;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TodoTask ↔ TodoTaskEntity
    // ═══════════════════════════════════════════════════════════════════

    public static com.devansh.core.model.TodoTask toTodoTask(com.devansh.habittracker.data.entity.TodoTaskEntity entity) {
        return new com.devansh.core.model.TodoTask(entity.id, entity.title, entity.isCompleted, entity.dueDate, entity.createdDate);
    }

    public static com.devansh.habittracker.data.entity.TodoTaskEntity toEntity(com.devansh.core.model.TodoTask task) {
        return new com.devansh.habittracker.data.entity.TodoTaskEntity(
                task.getId(), task.getTitle(), task.isCompleted(), task.getDueDate(), task.getCreatedDate()
        );
    }

    public static List<com.devansh.core.model.TodoTask> toTodoList(List<com.devansh.habittracker.data.entity.TodoTaskEntity> entities) {
        List<com.devansh.core.model.TodoTask> tasks = new ArrayList<>(entities.size());
        for (com.devansh.habittracker.data.entity.TodoTaskEntity entity : entities) {
            tasks.add(toTodoTask(entity));
        }
        return tasks;
    }
}
