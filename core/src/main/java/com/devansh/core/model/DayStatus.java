package com.devansh.core.model;

/**
 * Represents the status of a habit on a specific day in the calendar view.
 *
 * Used by {@link com.devansh.core.logic.StatsCalculator#buildDayStatusMap}
 * to paint each calendar cell with the correct color/icon.
 */
public enum DayStatus {

    /** The user met or exceeded the target for this day. */
    COMPLETE,

    /** The user logged some progress but did not meet the target. */
    PARTIAL,

    /** The day was scheduled but the user did not log anything (or logged 0). */
    MISSED,

    /** The habit was not scheduled on this day (e.g. a weekday-only habit on Saturday). */
    NOT_SCHEDULED,

    /** This day is in the future and cannot be evaluated yet. */
    FUTURE
}
