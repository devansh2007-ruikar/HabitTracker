package com.devansh.habittracker.data.backup;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.core.model.HabitType;
import com.devansh.core.model.Schedule;
import com.devansh.core.model.ScheduleType;
import com.devansh.core.model.TodoTask;
import com.devansh.habittracker.data.repository.HabitRepository;
import com.devansh.habittracker.reminder.ReminderScheduler;
import com.devansh.habittracker.widget.HabitAppWidgetProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Robust JSON backup and restore engine adhering to Android Storage Access Framework (SAF).
 *
 * <p>Handles:
 * <ul>
 *   <li>Structured schema export of habits, logs, and one-off tasks.</li>
 *   <li>Format validation and schema checking before database writes.</li>
 *   <li>"Replace" or "Merge" restore strategies with ID remapping.</li>
 * </ul>
 */
public final class JsonBackupManager {

    private static final int BACKUP_SCHEMA_VERSION = 1;

    private JsonBackupManager() {
        // Utility class
    }

    public static class HabitBackupItem {
        public final Habit habit;
        public final List<HabitLog> logs;

        public HabitBackupItem(Habit habit, List<HabitLog> logs) {
            this.habit = habit;
            this.logs = logs;
        }
    }

    public static class BackupPayload {
        public final int version;
        public final String exportDate;
        public final List<HabitBackupItem> habits;
        public final List<TodoTask> todos;

        public BackupPayload(int version, String exportDate, List<HabitBackupItem> habits, List<TodoTask> todos) {
            this.version = version;
            this.exportDate = exportDate;
            this.habits = habits;
            this.todos = todos;
        }
    }

    public static class RestoreSummary {
        public final int habitsRestored;
        public final int logsRestored;
        public final int todosRestored;

        public RestoreSummary(int habitsRestored, int logsRestored, int todosRestored) {
            this.habitsRestored = habitsRestored;
            this.logsRestored = logsRestored;
            this.todosRestored = todosRestored;
        }
    }

    /**
     * Serializes all current habits, logs, and tasks into a validated JSON string.
     */
    public static String exportToJson(@NonNull Context context) throws JSONException {
        HabitRepository repo = HabitRepository.getInstance(context);
        List<Habit> habits = repo.getAllHabitsSync();
        List<TodoTask> todos = repo.getAllTodoTasksSync();

        JSONObject root = new JSONObject();
        root.put("app", "HabitTracker");
        root.put("version", BACKUP_SCHEMA_VERSION);
        root.put("exportDate", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 1. Export Habits and their logs
        JSONArray habitsArray = new JSONArray();
        for (Habit habit : habits) {
            JSONObject habitObj = new JSONObject();
            habitObj.put("id", habit.getId());
            habitObj.put("name", habit.getName());
            habitObj.put("type", habit.getType().name());

            // Schedule object
            JSONObject scheduleObj = new JSONObject();
            scheduleObj.put("type", habit.getSchedule().getType().name());
            if (habit.getSchedule().getType() == ScheduleType.SPECIFIC_DAYS) {
                JSONArray daysArr = new JSONArray();
                for (DayOfWeek day : habit.getSchedule().getDaysOfWeek()) {
                    daysArr.put(day.name());
                }
                scheduleObj.put("days", daysArr);
            } else if (habit.getSchedule().getType() == ScheduleType.TIMES_PER_WEEK) {
                scheduleObj.put("timesPerWeek", habit.getSchedule().getTimesPerWeek());
            }
            habitObj.put("schedule", scheduleObj);

            habitObj.put("target", habit.getTarget());
            if (habit.getUnit() != null) {
                habitObj.put("unit", habit.getUnit());
            }
            if (habit.getReminderTime() != null) {
                habitObj.put("reminderTime", habit.getReminderTime().format(DateTimeFormatter.ISO_LOCAL_TIME));
            }
            habitObj.put("createdDate", habit.getCreatedDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            habitObj.put("archived", habit.isArchived());

            // Logs array for this habit
            List<HabitLog> logs = repo.getLogsForHabitSync(habit.getId());
            JSONArray logsArray = new JSONArray();
            for (HabitLog log : logs) {
                JSONObject logObj = new JSONObject();
                logObj.put("date", log.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                logObj.put("value", log.getValue());
                logsArray.put(logObj);
            }
            habitObj.put("logs", logsArray);

            habitsArray.put(habitObj);
        }
        root.put("habits", habitsArray);

        // 2. Export To-Do Tasks
        JSONArray todosArray = new JSONArray();
        for (TodoTask task : todos) {
            JSONObject taskObj = new JSONObject();
            taskObj.put("id", task.getId());
            taskObj.put("title", task.getTitle());
            taskObj.put("completed", task.isCompleted());
            if (task.getDueDate() != null) {
                taskObj.put("dueDate", task.getDueDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            taskObj.put("createdDate", task.getCreatedDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            todosArray.put(taskObj);
        }
        root.put("todos", todosArray);

        return root.toString(2); // Indented 2 spaces
    }

    /**
     * Parses and validates a JSON backup string.
     * Throws {@link IllegalArgumentException} if schema is invalid or required fields are missing.
     */
    public static BackupPayload parseAndValidate(@NonNull String jsonString)
            throws JSONException, IllegalArgumentException {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            throw new IllegalArgumentException("Backup file is empty");
        }

        JSONObject root = new JSONObject(jsonString);

        if (!root.has("habits")) {
            throw new IllegalArgumentException("Invalid backup format: missing 'habits' section");
        }

        int version = root.optInt("version", 1);
        String exportDate = root.optString("exportDate", LocalDate.now().toString());

        JSONArray habitsArr = root.getJSONArray("habits");
        List<HabitBackupItem> habitItems = new ArrayList<>();

        for (int i = 0; i < habitsArr.length(); i++) {
            JSONObject hObj = habitsArr.getJSONObject(i);

            String name = hObj.getString("name");
            if (name.trim().isEmpty()) {
                throw new IllegalArgumentException("Habit at index " + i + " has an empty name");
            }

            HabitType type = HabitType.valueOf(hObj.getString("type"));
            double target = hObj.optDouble("target", 1.0);
            String unit = hObj.isNull("unit") ? null : hObj.optString("unit", null);

            LocalTime reminderTime = null;
            if (hObj.has("reminderTime") && !hObj.isNull("reminderTime")) {
                reminderTime = LocalTime.parse(hObj.getString("reminderTime"));
            }

            LocalDate createdDate = LocalDate.parse(hObj.getString("createdDate"));
            boolean archived = hObj.optBoolean("archived", false);
            long originalId = hObj.optLong("id", i + 1);

            // Schedule
            JSONObject sObj = hObj.getJSONObject("schedule");
            ScheduleType sType = ScheduleType.valueOf(sObj.getString("type"));
            Schedule schedule;
            switch (sType) {
                case DAILY -> schedule = Schedule.daily();
                case SPECIFIC_DAYS -> {
                    JSONArray daysArr = sObj.getJSONArray("days");
                    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
                    for (int d = 0; d < daysArr.length(); d++) {
                        days.add(DayOfWeek.valueOf(daysArr.getString(d)));
                    }
                    if (days.isEmpty()) {
                        throw new IllegalArgumentException("Habit '" + name + "' has no days configured");
                    }
                    schedule = Schedule.specificDays(days);
                }
                case TIMES_PER_WEEK -> {
                    int tpw = sObj.getInt("timesPerWeek");
                    schedule = Schedule.timesPerWeek(tpw);
                }
                default -> schedule = Schedule.daily();
            }

            Habit habit = new Habit(originalId, name, type, schedule, target, unit, reminderTime, createdDate, archived);

            // Logs
            List<HabitLog> logs = new ArrayList<>();
            if (hObj.has("logs")) {
                JSONArray lArr = hObj.getJSONArray("logs");
                for (int j = 0; j < lArr.length(); j++) {
                    JSONObject lObj = lArr.getJSONObject(j);
                    LocalDate date = LocalDate.parse(lObj.getString("date"));
                    double value = lObj.getDouble("value");
                    logs.add(new HabitLog(originalId, date, value));
                }
            }

            habitItems.add(new HabitBackupItem(habit, logs));
        }

        // To-dos
        List<TodoTask> todos = new ArrayList<>();
        if (root.has("todos")) {
            JSONArray tArr = root.getJSONArray("todos");
            for (int k = 0; k < tArr.length(); k++) {
                JSONObject tObj = tArr.getJSONObject(k);
                String title = tObj.getString("title");
                boolean completed = tObj.optBoolean("completed", false);
                LocalDate dueDate = null;
                if (tObj.has("dueDate") && !tObj.isNull("dueDate")) {
                    dueDate = LocalDate.parse(tObj.getString("dueDate"));
                }
                LocalDate createdDate = tObj.has("createdDate")
                        ? LocalDate.parse(tObj.getString("createdDate"))
                        : LocalDate.now();
                todos.add(new TodoTask(0, title, completed, dueDate, createdDate));
            }
        }

        return new BackupPayload(version, exportDate, habitItems, todos);
    }

    /**
     * Executes the restore process asynchronously on a background thread.
     *
     * @param replaceExisting if true, wipes existing database tables first; if false, merges.
     * @param onComplete callback with restore counts, posted on the main thread.
     */
    public static void restoreBackup(@NonNull Context context,
                                     @NonNull BackupPayload payload,
                                     boolean replaceExisting,
                                     @NonNull Consumer<RestoreSummary> onComplete) {
        Executors.newSingleThreadExecutor().execute(() -> {
            HabitRepository repo = HabitRepository.getInstance(context);

            if (replaceExisting) {
                // Wipe existing data completely
                repo.deleteAllDataSync();
            }

            int habitsCount = 0;
            int logsCount = 0;
            int todosCount = 0;

            // Existing habits map for merging
            Map<String, Habit> existingHabitsByName = new HashMap<>();
            if (!replaceExisting) {
                for (Habit h : repo.getAllHabitsSync()) {
                    existingHabitsByName.put(h.getName().trim().toLowerCase(), h);
                }
            }

            for (HabitBackupItem item : payload.habits) {
                Habit backupHabit = item.habit;
                long targetHabitId;

                Habit existing = existingHabitsByName.get(backupHabit.getName().trim().toLowerCase());
                if (existing != null) {
                    targetHabitId = existing.getId();
                } else {
                    // Insert habit with id = 0 so Room generates a new primary key
                    Habit newHabit = new Habit(
                            0, backupHabit.getName(), backupHabit.getType(), backupHabit.getSchedule(),
                            backupHabit.getTarget(), backupHabit.getUnit(), backupHabit.getReminderTime(),
                            backupHabit.getCreatedDate(), backupHabit.isArchived()
                    );
                    targetHabitId = repo.insertHabitSync(newHabit);
                    habitsCount++;
                }

                // Insert all child logs mapped to the resolved habit ID
                for (HabitLog log : item.logs) {
                    repo.upsertLogSync(new HabitLog(targetHabitId, log.getDate(), log.getValue()));
                    logsCount++;
                }
            }

            // Restore To-dos
            for (TodoTask task : payload.todos) {
                repo.insertTodoTaskSync(task);
                todosCount++;
            }

            // Reschedule reminders and update widgets
            ReminderScheduler.rescheduleAll(context);
            HabitAppWidgetProvider.updateAllWidgets(context);

            RestoreSummary summary = new RestoreSummary(habitsCount, logsCount, todosCount);
            new Handler(Looper.getMainLooper()).post(() -> onComplete.accept(summary));
        });
    }
}
