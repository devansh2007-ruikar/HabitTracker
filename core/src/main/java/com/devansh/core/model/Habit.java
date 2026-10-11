package com.devansh.core.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Domain model representing a single habit the user wants to track.
 *
 * This is a plain Java object with no Android dependencies so it can live
 * in the :core module and be unit-tested without an emulator.
 *
 * The Room entity in :app will mirror these fields.
 */
public final class Habit {

    /** Unique identifier (matches the Room primary key). */
    private final long id;

    /** User-visible name, e.g. "Read", "Gym". */
    private final String name;

    /** What kind of value the user logs (YES_NO, COUNTABLE, TIMED). */
    private final HabitType type;

    /** When the habit is due (daily, specific days, or N times per week). */
    private final Schedule schedule;

    /**
     * Numeric target per logging event.
     * YES_NO habits always have target = 1.
     * COUNTABLE might be 20 (pages). TIMED might be 15 (minutes).
     */
    private final double target;

    /** Display unit for countable/timed habits (e.g. "pages", "min"). Nullable. */
    private final String unit;

    /** Optional daily reminder time. Null means no reminder. */
    private final LocalTime reminderTime;

    /** The date the user created this habit. Used to avoid false "missed" days. */
    private final LocalDate createdDate;

    /** Whether the habit is archived (hidden from daily view but kept in stats). */
    private final boolean archived;

    // ── Constructor ──────────────────────────────────────────────────

    public Habit(long id,
                 String name,
                 HabitType type,
                 Schedule schedule,
                 double target,
                 String unit,
                 LocalTime reminderTime,
                 LocalDate createdDate,
                 boolean archived) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.schedule = Objects.requireNonNull(schedule, "schedule must not be null");
        // YES_NO habits are always binary: target must be 1
        this.target = (type == HabitType.YES_NO) ? 1.0 : target;
        this.unit = unit;
        this.reminderTime = reminderTime;
        this.createdDate = Objects.requireNonNull(createdDate, "createdDate must not be null");
        this.archived = archived;
    }

    // ── Getters ──────────────────────────────────────────────────────

    public long getId()              { return id; }
    public String getName()          { return name; }
    public HabitType getType()       { return type; }
    public Schedule getSchedule()    { return schedule; }
    public double getTarget()        { return target; }
    public String getUnit()          { return unit; }
    public LocalTime getReminderTime() { return reminderTime; }
    public LocalDate getCreatedDate()  { return createdDate; }
    public boolean isArchived()      { return archived; }

    // ── Object overrides ─────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Habit that)) return false;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Habit{id=" + id + ", name='" + name + "', type=" + type + "}";
    }
}
