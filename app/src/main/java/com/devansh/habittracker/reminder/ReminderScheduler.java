package com.devansh.habittracker.reminder;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.devansh.core.logic.ReminderCalculator;
import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.habittracker.data.repository.HabitRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Manages scheduling and cancelling alarms via Android's {@link AlarmManager}.
 *
 * <p>Key requirements addressed:
 * <ul>
 *   <li>Computes next trigger via pure-Java {@link ReminderCalculator} in :core.</li>
 *   <li>Unique PendingIntent per habit ID with {@code FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT}.</li>
 *   <li>Checks {@link AlarmManager#canScheduleExactAlarms()} on Android 12+ (API 31+);
 *       falls back to {@link AlarmManager#setWindow} if denied so reminders still arrive.</li>
 *   <li>Cancels alarms on habit edit, deletion, or reminder removal.</li>
 *   <li>Provides asynchronous {@link #rescheduleAll(Context)} for boot and timezone changes.</li>
 * </ul>
 */
public final class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";

    public static final String CHANNEL_ID = "habit_reminders_channel";
    public static final String EXTRA_HABIT_ID = "extra_habit_id";
    public static final String EXTRA_IS_TEST = "extra_is_test";

    private static final int TEST_REMINDER_ID = 999999;

    private ReminderScheduler() {
        // Utility class
    }

    /**
     * Creates the "Habit reminders" notification channel required for Android 8.0+ (API 26+).
     */
    public static void createNotificationChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Habit reminders",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Daily reminders to stay consistent with your habits");
                channel.enableVibration(true);
                channel.enableLights(true);
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Schedules the next exact or window alarm for the given habit.
     * Computes the trigger on a background thread.
     */
    public static void scheduleReminder(@NonNull Context context, @NonNull Habit habit) {
        if (habit.getReminderTime() == null || habit.isArchived()) {
            cancelReminder(context, habit.getId());
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            HabitRepository repository = HabitRepository.getInstance(context);
            List<HabitLog> logs = repository.getLogsForHabitSync(habit.getId());

            LocalDateTime nextTrigger = ReminderCalculator.computeNextTrigger(
                    habit, logs, LocalDateTime.now()
            );

            if (nextTrigger == null) {
                cancelReminder(context, habit.getId());
                return;
            }

            long triggerMillis = nextTrigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            setAlarm(context, (int) habit.getId(), triggerMillis, false);
        });
    }

    /**
     * Cancels an active alarm for a habit.
     */
    public static void cancelReminder(@NonNull Context context, long habitId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(EXTRA_HABIT_ID, habitId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) habitId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
    }

    /**
     * Schedules a test reminder to fire in {@code delaySeconds} (for the demo video).
     */
    public static void scheduleTestReminder(@NonNull Context context, int delaySeconds) {
        createNotificationChannel(context);
        long triggerMillis = System.currentTimeMillis() + (delaySeconds * 1000L);
        setAlarm(context, TEST_REMINDER_ID, triggerMillis, true);
    }

    /**
     * Reschedules all active habit reminders asynchronously (e.g. after device reboot or timezone change).
     */
    public static void rescheduleAll(@NonNull Context context) {
        createNotificationChannel(context);
        Executors.newSingleThreadExecutor().execute(() -> {
            HabitRepository repository = HabitRepository.getInstance(context);
            List<Habit> activeHabits = repository.getActiveHabitsSync();
            for (Habit habit : activeHabits) {
                if (habit.getReminderTime() != null && !habit.isArchived()) {
                    scheduleReminder(context, habit);
                }
            }
        });
    }

    private static void setAlarm(@NonNull Context context, int requestCode, long triggerMillis, boolean isTest) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        createNotificationChannel(context);

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(EXTRA_HABIT_ID, (long) requestCode);
        intent.putExtra(EXTRA_IS_TEST, isTest);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Android 12+ (API 31+) Exact Alarm Check & Fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent);
            } else {
                // Fallback: use setWindow with a 10-minute window so the reminder still arrives
                alarmManager.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        10 * 60 * 1000L, // 10 minute window
                        pendingIntent
                );
            }
        } else {
            // Android 6.0 to 11
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent);
        }
    }
}
