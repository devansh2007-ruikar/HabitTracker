package com.devansh.habittracker.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import com.devansh.core.logic.ScheduleRules;
import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.core.model.HabitType;
import com.devansh.core.model.ScheduleType;
import com.devansh.habittracker.MainActivity;
import com.devansh.habittracker.R;
import com.devansh.habittracker.data.repository.HabitRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * AppWidgetProvider for the "Today's Habits" home screen widget.
 *
 * <p>Supports:
 * <ul>
 *   <li>Displaying all habits scheduled for today with their current streaks.</li>
 *   <li>Direct tap-to-complete from the home screen via collection fill-in intents.</li>
 *   <li>Real-time updates upon log changes and midnight date changes.</li>
 * </ul>
 */
public class HabitAppWidgetProvider extends AppWidgetProvider {

    public static final String EXTRA_HABIT_ID = "com.devansh.habittracker.widget.EXTRA_HABIT_ID";
    public static final String ACTION_WIDGET_COMPLETE_HABIT = "com.devansh.habittracker.widget.ACTION_WIDGET_COMPLETE_HABIT";
    public static final String ACTION_MIDNIGHT_UPDATE = "com.devansh.habittracker.widget.ACTION_MIDNIGHT_UPDATE";

    private static final int MIDNIGHT_ALARM_REQUEST_CODE = 888888;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
        scheduleMidnightAlarm(context);
    }

    private static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_today_habits);

        // Bind RemoteViewsService for the habit list
        Intent serviceIntent = new Intent(context, HabitWidgetService.class);
        rv.setRemoteAdapter(R.id.widget_list_view, serviceIntent);
        rv.setEmptyView(R.id.widget_list_view, R.id.widget_empty_view);

        // Header click opens MainActivity
        Intent mainIntent = new Intent(context, MainActivity.class);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        rv.setOnClickPendingIntent(R.id.widget_header, mainPendingIntent);

        // Template PendingIntent for collection item clicks (MUTABLE required for fill-in intent merging)
        Intent clickIntent = new Intent(context, HabitAppWidgetProvider.class);
        clickIntent.setAction(ACTION_WIDGET_COMPLETE_HABIT);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingFlags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent clickPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                clickIntent,
                pendingFlags
        );
        rv.setPendingIntentTemplate(R.id.widget_list_view, clickPendingIntent);

        // Update header summary text
        Executors.newSingleThreadExecutor().execute(() -> {
            HabitRepository repo = HabitRepository.getInstance(context);
            LocalDate today = LocalDate.now();
            List<Habit> active = repo.getActiveHabitsSync();

            int scheduledCount = 0;
            int completedCount = 0;

            for (Habit h : active) {
                boolean scheduled = ScheduleRules.isScheduled(h, today)
                        || h.getSchedule().getType() == ScheduleType.TIMES_PER_WEEK;
                if (scheduled) {
                    scheduledCount++;
                    HabitLog log = repo.getLogForDateSync(h.getId(), today);
                    if (log != null && ScheduleRules.isComplete(h, log)) {
                        completedCount++;
                    }
                }
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault());
            String summary = String.format("%s • %d/%d completed",
                    today.format(formatter), completedCount, scheduledCount);

            RemoteViews partialRv = new RemoteViews(context.getPackageName(), R.layout.widget_today_habits);
            partialRv.setTextViewText(R.id.widget_tv_summary, summary);
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, partialRv);
        });

        appWidgetManager.updateAppWidget(appWidgetId, rv);
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;

        if (ACTION_WIDGET_COMPLETE_HABIT.equals(action)) {
            long habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1);
            if (habitId <= 0) return;

            Executors.newSingleThreadExecutor().execute(() -> {
                HabitRepository repo = HabitRepository.getInstance(context);
                Habit habit = repo.getHabitByIdSync(habitId);
                if (habit == null) return;

                LocalDate today = LocalDate.now();
                HabitLog todayLog = repo.getLogForDateSync(habitId, today);
                boolean isComplete = todayLog != null && ScheduleRules.isComplete(habit, todayLog);

                // Toggle completion
                double newValue;
                if (isComplete) {
                    newValue = 0.0;
                } else {
                    newValue = (habit.getType() == HabitType.YES_NO) ? 1.0 : habit.getTarget();
                }

                repo.upsertLog(new HabitLog(habitId, today, newValue), () -> {
                    updateAllWidgets(context);
                });
            });
        } else if (ACTION_MIDNIGHT_UPDATE.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            updateAllWidgets(context);
            scheduleMidnightAlarm(context);
        }
    }

    /**
     * Broadcasts a data change notification to all active HabitTracker widgets.
     */
    public static void updateAllWidgets(@NonNull Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, HabitAppWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);

        if (appWidgetIds != null && appWidgetIds.length > 0) {
            for (int appWidgetId : appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId);
            }
        }
    }

    /**
     * Schedules an AlarmManager alarm at 00:00:05 tomorrow to trigger midnight widget refresh.
     */
    public static void scheduleMidnightAlarm(@NonNull Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime nextMidnight = LocalDateTime.of(tomorrow, LocalTime.of(0, 0, 5));
        long triggerMillis = nextMidnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        Intent intent = new Intent(context, HabitAppWidgetProvider.class);
        intent.setAction(ACTION_MIDNIGHT_UPDATE);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                MIDNIGHT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent);
            } else {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerMillis, 5 * 60 * 1000L, pendingIntent);
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent);
        }
    }
}
