package com.devansh.habittracker.reminder;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.devansh.core.logic.ScheduleRules;
import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.habittracker.MainActivity;
import com.devansh.habittracker.R;
import com.devansh.habittracker.data.repository.HabitRepository;

import java.time.LocalDate;
import java.util.concurrent.Executors;

/**
 * BroadcastReceiver triggered by {@link android.app.AlarmManager}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Checks whether the habit is already completed today; skips notification if complete.</li>
 *   <li>Builds and displays a high-priority notification with "Mark done" and "Open" actions.</li>
 *   <li>Immediately schedules the NEXT occurrence via {@link ReminderScheduler}.</li>
 * </ul>
 */
public class AlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_MARK_DONE = "com.devansh.habittracker.ACTION_MARK_DONE";

    @Override
    public void onReceive(Context context, Intent intent) {
        long habitId = intent.getLongExtra(ReminderScheduler.EXTRA_HABIT_ID, -1);
        boolean isTest = intent.getBooleanExtra(ReminderScheduler.EXTRA_IS_TEST, false);

        if (isTest) {
            showTestNotification(context);
            return;
        }

        if (habitId <= 0) return;

        PendingResult pendingResult = goAsync();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                HabitRepository repository = HabitRepository.getInstance(context);
                Habit habit = repository.getHabitByIdSync(habitId);
                if (habit == null || habit.isArchived()) {
                    return;
                }

                LocalDate today = LocalDate.now();
                HabitLog todayLog = repository.getLogForDateSync(habitId, today);

                // Skip notification if already completed today!
                boolean isCompletedToday = (todayLog != null && ScheduleRules.isComplete(habit, todayLog));
                if (!isCompletedToday) {
                    showHabitNotification(context, habit);
                }

                // Immediately schedule the next occurrence
                ReminderScheduler.scheduleReminder(context, habit);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void showHabitNotification(Context context, Habit habit) {
        ReminderScheduler.createNotificationChannel(context);

        // Content intent -> Open MainActivity
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                (int) habit.getId(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Action intent -> Mark done directly from notification
        Intent markDoneIntent = new Intent(context, NotificationActionReceiver.class);
        markDoneIntent.setAction(ACTION_MARK_DONE);
        markDoneIntent.putExtra(ReminderScheduler.EXTRA_HABIT_ID, habit.getId());
        PendingIntent markDonePendingIntent = PendingIntent.getBroadcast(
                context,
                (int) habit.getId(),
                markDoneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String unit = habit.getUnit() != null ? " " + habit.getUnit() : "";
        String contentText = String.format("Time to complete your habit! Daily target: %.0f%s",
                habit.getTarget(), unit);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(habit.getName())
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_check, "Mark done", markDonePendingIntent)
                .addAction(R.drawable.ic_today, "Open", openPendingIntent);

        try {
            NotificationManagerCompat.from(context).notify((int) habit.getId(), builder.build());
        } catch (SecurityException ignored) {
            // Handled when notification permission is denied
        }
    }

    private void showTestNotification(Context context) {
        ReminderScheduler.createNotificationChannel(context);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                999999,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("Test Habit Reminder")
                .setContentText("Great job! Your notification channel and alarms are fully working.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_today, "Open App", openPendingIntent);

        try {
            NotificationManagerCompat.from(context).notify(999999, builder.build());
        } catch (SecurityException ignored) {
        }
    }
}
