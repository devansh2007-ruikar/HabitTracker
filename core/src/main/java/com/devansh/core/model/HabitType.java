package com.devansh.core.model;

/**
 * Defines the three kinds of habit tracking supported by the app.
 *
 * YES_NO     – binary: either done or not (target is always 1).
 * COUNTABLE  – numeric: e.g. "Read 20 pages" (target = 20).
 * TIMED      – duration in minutes: e.g. "Meditate 15 min" (target = 15).
 */
public enum HabitType {

    /** A simple yes/no habit. The implicit target is always 1. */
    YES_NO,

    /** A countable habit with a user-defined numeric target. */
    COUNTABLE,

    /** A timed habit where value and target are measured in minutes. */
    TIMED
}
