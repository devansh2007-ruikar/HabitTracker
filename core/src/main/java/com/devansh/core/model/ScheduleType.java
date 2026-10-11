package com.devansh.core.model;

/**
 * Defines how frequently a habit is scheduled.
 *
 * DAILY          – the habit is due every single day.
 * SPECIFIC_DAYS  – the habit is due only on chosen days of the week
 *                  (stored in {@link Schedule#getDaysOfWeek()}).
 * TIMES_PER_WEEK – the user must complete the habit N times per ISO week
 *                  (Mon–Sun). Any day within the week is valid for logging.
 */
public enum ScheduleType {

    /** Due every day. */
    DAILY,

    /** Due only on specific days of the week (e.g. Mon, Wed, Fri). */
    SPECIFIC_DAYS,

    /** Must be completed N times within each ISO week (Mon–Sun). */
    TIMES_PER_WEEK
}
