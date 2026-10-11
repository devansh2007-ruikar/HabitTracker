package com.devansh.habittracker.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.devansh.core.logic.ScheduleRules;
import com.devansh.core.logic.StreakCalculator;
import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.core.model.HabitType;
import com.devansh.core.model.ScheduleType;
import com.devansh.habittracker.R;
import com.devansh.habittracker.data.repository.HabitRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * RemoteViewsService supplying list items for the Today's Habits home screen widget.
 */
public class HabitWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new HabitRemoteViewsFactory(getApplicationContext());
    }

    private static class HabitRemoteViewsFactory implements RemoteViewsFactory {

        private final Context context;
        private final HabitRepository repository;
        private final List<WidgetItem> items = new ArrayList<>();

        private static class WidgetItem {
            final Habit habit;
            final boolean completed;
            final int streak;

            WidgetItem(Habit habit, boolean completed, int streak) {
                this.habit = habit;
                this.completed = completed;
                this.streak = streak;
            }
        }

        HabitRemoteViewsFactory(Context context) {
            this.context = context;
            this.repository = HabitRepository.getInstance(context);
        }

        @Override
        public void onCreate() {
            // Initialization
        }

        @Override
        public void onDataSetChanged() {
            items.clear();
            LocalDate today = LocalDate.now();

            List<Habit> activeHabits = repository.getActiveHabitsSync();
            for (Habit habit : activeHabits) {
                // Check if scheduled today
                boolean scheduled = ScheduleRules.isScheduled(habit, today)
                        || habit.getSchedule().getType() == ScheduleType.TIMES_PER_WEEK;
                if (!scheduled) {
                    continue;
                }

                HabitLog todayLog = repository.getLogForDateSync(habit.getId(), today);
                boolean completed = todayLog != null && ScheduleRules.isComplete(habit, todayLog);

                List<HabitLog> allLogs = repository.getLogsForHabitSync(habit.getId());
                int streak = StreakCalculator.currentStreak(habit, allLogs, today);

                items.add(new WidgetItem(habit, completed, streak));
            }
        }

        @Override
        public void onDestroy() {
            items.clear();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= items.size()) {
                return null;
            }

            WidgetItem item = items.get(position);
            Habit habit = item.habit;

            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_habit_item);
            rv.setTextViewText(R.id.widget_item_title, habit.getName());

            String unit = habit.getUnit() != null ? " " + habit.getUnit() : "";
            String subtitle;
            if (habit.getType() == HabitType.YES_NO) {
                subtitle = item.streak > 0 ? (item.streak + " day streak 🔥") : "Not started yet";
            } else {
                subtitle = String.format("Target: %.0f%s • %d streak 🔥", habit.getTarget(), unit, item.streak);
            }
            rv.setTextViewText(R.id.widget_item_subtitle, subtitle);

            // Completion appearance
            if (item.completed) {
                rv.setImageViewResource(R.id.widget_item_check, R.drawable.ic_check);
                rv.setTextColor(R.id.widget_item_title, Color.parseColor("#4CAF50")); // Green
            } else {
                rv.setImageViewResource(R.id.widget_item_check, R.drawable.ic_circle_outline);
                rv.setTextColor(R.id.widget_item_title, Color.WHITE);
            }

            // Fill-in Intent for tap-to-complete
            Intent fillInIntent = new Intent();
            fillInIntent.putExtra(HabitAppWidgetProvider.EXTRA_HABIT_ID, habit.getId());
            rv.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent);
            rv.setOnClickFillInIntent(R.id.widget_item_check, fillInIntent);

            return rv;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            if (position >= 0 && position < items.size()) {
                return items.get(position).habit.getId();
            }
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
