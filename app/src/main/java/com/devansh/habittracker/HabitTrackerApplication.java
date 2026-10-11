package com.devansh.habittracker;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.devansh.habittracker.reminder.ReminderScheduler;
import com.devansh.habittracker.widget.HabitAppWidgetProvider;

/**
 * Application class initializing global app configuration upon process creation:
 * <ul>
 *   <li>Persisted DayNight theme via {@link AppCompatDelegate}</li>
 *   <li>Notification channels for habit reminders</li>
 *   <li>Recurring midnight widget update alarms</li>
 * </ul>
 */
public class HabitTrackerApplication extends Application {

    private static final String PREFS_NAME = "habit_tracker_prefs";
    private static final String KEY_THEME = "key_theme";

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Initialize persisted theme
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(themeMode);

        // 2. Ensure notification channel exists
        ReminderScheduler.createNotificationChannel(this);

        // 3. Initialize midnight widget alarm
        HabitAppWidgetProvider.scheduleMidnightAlarm(this);
    }
}
