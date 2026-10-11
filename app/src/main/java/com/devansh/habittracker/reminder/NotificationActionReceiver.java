package com.devansh.habittracker.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.core.model.HabitType;
import com.devansh.habittracker.data.repository.HabitRepository;

import java.time.LocalDate;
import java.util.concurrent.Executors;

/**
 * BroadcastReceiver executing the "Mark done" action directly from a notification.
 *
 * <p>Writes today's log to the database via {@link HabitRepository}:
 * <ul>
 *   <li>YES_NO = 1.0 (completed)</li>
 *   <li>COUNTABLE = target value</li>
 *   <li>TIMED = target minutes</li>
 * </ul>
 * Also dismisses the notification and updates the next reminder schedule.
 */
public class NotificationActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!AlarmReceiver.ACTION_MARK_DONE.equals(intent.getAction())) {
            return;
        }

        long habitId = intent.getLongExtra(ReminderScheduler.EXTRA_HABIT_ID, -1);
        if (habitId <= 0) return;

        PendingResult pendingResult = goAsync();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                HabitRepository repository = HabitRepository.getInstance(context);
                Habit habit = repository.getHabitByIdSync(habitId);
                if (habit == null) return;

                double completionValue;
                if (habit.getType() == HabitType.YES_NO) {
                    completionValue = 1.0;
                } else {
                    completionValue = habit.getTarget();
                }

                // Write completion log for today
                repository.upsertLog(new HabitLog(habitId, LocalDate.now(), completionValue));

                // Dismiss notification
                NotificationManagerCompat.from(context).cancel((int) habitId);

                // Update next reminder occurrence
                ReminderScheduler.scheduleReminder(context, habit);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
