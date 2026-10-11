package com.devansh.habittracker.ui.today;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.devansh.core.logic.ScheduleRules;
import com.devansh.core.logic.StreakCalculator;
import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.core.model.ScheduleType;
import com.devansh.habittracker.data.repository.HabitRepository;
import com.devansh.habittracker.ui.model.TodayHabitUiModel;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel for the Today (home) screen.
 *
 * <p>Architectural responsibilities:
 * <ul>
 *   <li>Maintains the {@code selectedDate} state (allowing past-day view and correction).</li>
 *   <li>Combines active habits and logs via {@link MediatorLiveData}.</li>
 *   <li>Delegates ALL streak, scheduling, and completion calculations to the pure-Java :core module.</li>
 *   <li>Exposes quick-logging mutators (checkbox toggle, steppers, exact input, Pomodoro timer).</li>
 * </ul>
 */
public class TodayViewModel extends AndroidViewModel {

    private final HabitRepository repository;

    private final MutableLiveData<LocalDate> selectedDate = new MutableLiveData<>(LocalDate.now());
    private final MediatorLiveData<List<TodayHabitUiModel>> todayHabits = new MediatorLiveData<>();

    // Cached raw data from repository
    private List<Habit> cachedActiveHabits = Collections.emptyList();
    private List<HabitLog> cachedAllLogs = Collections.emptyList();

    // ── In-Memory Pomodoro / Timed Habit State ────────────────────────
    private final MutableLiveData<Long> activeTimerHabitId = new MutableLiveData<>(null);
    private final MutableLiveData<Integer> timerElapsedSeconds = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> isTimerRunning = new MutableLiveData<>(false);

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            Boolean running = isTimerRunning.getValue();
            if (Boolean.TRUE.equals(running)) {
                Integer current = timerElapsedSeconds.getValue();
                int next = (current != null ? current : 0) + 1;
                timerElapsedSeconds.setValue(next);
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    public TodayViewModel(@NonNull Application application) {
        super(application);
        this.repository = HabitRepository.getInstance(application);

        LiveData<List<Habit>> activeHabitsLive = repository.getActiveHabits();
        LiveData<List<HabitLog>> allLogsLive = repository.getAllLogsLive();

        todayHabits.addSource(selectedDate, date -> recompute());
        todayHabits.addSource(activeHabitsLive, habits -> {
            cachedActiveHabits = habits != null ? habits : Collections.emptyList();
            recompute();
        });
        todayHabits.addSource(allLogsLive, logs -> {
            cachedAllLogs = logs != null ? logs : Collections.emptyList();
            recompute();
        });
    }

    public LiveData<LocalDate> getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(@NonNull LocalDate date) {
        selectedDate.setValue(date);
    }

    public LiveData<List<TodayHabitUiModel>> getTodayHabits() {
        return todayHabits;
    }

    public LiveData<Long> getActiveTimerHabitId() {
        return activeTimerHabitId;
    }

    public LiveData<Integer> getTimerElapsedSeconds() {
        return timerElapsedSeconds;
    }

    public LiveData<Boolean> getIsTimerRunning() {
        return isTimerRunning;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Pure :core Recomputation
    // ═══════════════════════════════════════════════════════════════════

    private void recompute() {
        LocalDate date = selectedDate.getValue();
        if (date == null) {
            date = LocalDate.now();
        }

        // Group all logs by habit ID for rapid lookup
        Map<Long, List<HabitLog>> logsByHabit = new HashMap<>();
        for (HabitLog log : cachedAllLogs) {
            logsByHabit.computeIfAbsent(log.getHabitId(), k -> new ArrayList<>()).add(log);
        }

        List<TodayHabitUiModel> uiModels = new ArrayList<>();

        for (Habit habit : cachedActiveHabits) {
            boolean isScheduledToday = ScheduleRules.isScheduled(habit, date);
            boolean isTimesPerWeek = habit.getSchedule().getType() == ScheduleType.TIMES_PER_WEEK;

            // TIMES_PER_WEEK is loggable any day on or after createdDate
            if (!isScheduledToday && !isTimesPerWeek) {
                continue;
            }
            if (isTimesPerWeek && date.isBefore(habit.getCreatedDate())) {
                continue;
            }

            List<HabitLog> habitLogs = logsByHabit.getOrDefault(habit.getId(), Collections.emptyList());

            // Find log for the selected date (last entry for this date wins)
            HabitLog dayLog = null;
            for (HabitLog log : habitLogs) {
                if (log.getDate().equals(date)) {
                    dayLog = log;
                }
            }

            double currentValue = dayLog != null ? dayLog.getValue() : 0.0;
            boolean completed = dayLog != null && ScheduleRules.isComplete(habit, dayLog);

            // Delegate current streak calculation directly to :core
            int streak = StreakCalculator.currentStreak(habit, habitLogs, date);

            // Calculate week completions for TIMES_PER_WEEK
            int weekCompletions = 0;
            int weekTarget = 0;
            if (isTimesPerWeek) {
                weekTarget = habit.getSchedule().getTimesPerWeek();
                LocalDate weekStart = date.with(DayOfWeek.MONDAY);
                LocalDate weekEnd = date.with(DayOfWeek.SUNDAY);
                for (HabitLog log : habitLogs) {
                    if (!log.getDate().isBefore(weekStart) && !log.getDate().isAfter(weekEnd)) {
                        if (log.getValue() >= habit.getTarget()) {
                            weekCompletions++;
                        }
                    }
                }
            }

            uiModels.add(new TodayHabitUiModel(
                    habit,
                    dayLog,
                    streak,
                    completed,
                    currentValue,
                    habit.getTarget(),
                    weekCompletions,
                    weekTarget
            ));
        }

        todayHabits.setValue(uiModels);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Quick Logging Operations (Dispatched to Repository)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Toggles YES_NO completion (0.0 <-> 1.0).
     */
    public void toggleYesNo(@NonNull TodayHabitUiModel item) {
        LocalDate date = selectedDate.getValue();
        if (date == null) date = LocalDate.now();

        double nextVal = item.isCompleted() ? 0.0 : 1.0;
        repository.upsertLog(new HabitLog(item.getHabit().getId(), date, nextVal));
    }

    /**
     * Stepper adjustment (+1 or -1) for COUNTABLE habits.
     */
    public void adjustCountable(@NonNull TodayHabitUiModel item, double delta) {
        LocalDate date = selectedDate.getValue();
        if (date == null) date = LocalDate.now();

        double current = item.getCurrentValue();
        double next = Math.max(0.0, current + delta);
        repository.upsertLog(new HabitLog(item.getHabit().getId(), date, next));
    }

    /**
     * Sets exact numeric value for a habit on the selected day.
     */
    public void setExactValue(@NonNull TodayHabitUiModel item, double value) {
        LocalDate date = selectedDate.getValue();
        if (date == null) date = LocalDate.now();

        repository.upsertLog(new HabitLog(item.getHabit().getId(), date, Math.max(0.0, value)));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Pomodoro / Timed Logging
    // ═══════════════════════════════════════════════════════════════════

    public void startTimer(long habitId) {
        Long currentHabit = activeTimerHabitId.getValue();
        if (currentHabit == null || currentHabit != habitId) {
            // New timer started for different habit
            activeTimerHabitId.setValue(habitId);
            timerElapsedSeconds.setValue(0);
        }
        isTimerRunning.setValue(true);
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    public void pauseTimer() {
        isTimerRunning.setValue(false);
        timerHandler.removeCallbacks(timerRunnable);
    }

    public void resetTimer() {
        pauseTimer();
        timerElapsedSeconds.setValue(0);
        activeTimerHabitId.setValue(null);
    }

    /**
     * Saves elapsed timer time (in full minutes) to today's log.
     */
    public void saveTimerMinutes(long habitId, @NonNull TodayHabitUiModel item) {
        LocalDate date = selectedDate.getValue();
        if (date == null) date = LocalDate.now();

        Integer elapsedSec = timerElapsedSeconds.getValue();
        if (elapsedSec == null || elapsedSec <= 0) return;

        int addedMinutes = Math.max(1, (int) Math.round(elapsedSec / 60.0));
        double nextVal = item.getCurrentValue() + addedMinutes;

        repository.upsertLog(new HabitLog(habitId, date, nextVal));
        resetTimer();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        timerHandler.removeCallbacks(timerRunnable);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  To-Do Task Operations
    // ═══════════════════════════════════════════════════════════════════

    public LiveData<List<com.devansh.core.model.TodoTask>> getTodoTasks() {
        return repository.getAllTodoTasks();
    }

    public void addTodoTask(@NonNull String title) {
        if (title.trim().isEmpty()) return;
        com.devansh.core.model.TodoTask task = new com.devansh.core.model.TodoTask(
                0, title.trim(), false, null, LocalDate.now()
        );
        repository.insertTodoTask(task, null);
    }

    public void toggleTodoTask(@NonNull com.devansh.core.model.TodoTask task) {
        repository.toggleTodoTaskCompleted(task, null);
    }

    public void deleteTodoTask(@NonNull com.devansh.core.model.TodoTask task) {
        repository.deleteTodoTask(task, null);
    }
}
