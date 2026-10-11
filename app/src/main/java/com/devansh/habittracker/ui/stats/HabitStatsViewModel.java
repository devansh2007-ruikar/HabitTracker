package com.devansh.habittracker.ui.stats;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.devansh.core.logic.StatsCalculator;
import com.devansh.core.logic.StreakCalculator;
import com.devansh.core.model.DayStatus;
import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.habittracker.data.repository.HabitRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel for habit statistics, interactive month calendar, and weekly bar chart.
 *
 * <p>All statistical calculations strictly delegate to {@link StatsCalculator}
 * and {@link StreakCalculator} in the pure-Java :core module.
 */
public class HabitStatsViewModel extends AndroidViewModel {

    public enum RangeMode {
        DAYS_7,
        DAYS_30,
        ALL
    }

    private final HabitRepository repository;

    private final MutableLiveData<Long> selectedHabitId = new MutableLiveData<>(null);
    private final MutableLiveData<RangeMode> rangeMode = new MutableLiveData<>(RangeMode.DAYS_30);
    private final MutableLiveData<YearMonth> selectedMonth = new MutableLiveData<>(YearMonth.now());

    private final MediatorLiveData<Habit> habit = new MediatorLiveData<>();
    private final MediatorLiveData<List<HabitLog>> logs = new MediatorLiveData<>();

    // Computed presentation stats
    private final MediatorLiveData<Integer> currentStreak = new MediatorLiveData<>();
    private final MediatorLiveData<Integer> longestStreak = new MediatorLiveData<>();
    private final MediatorLiveData<Integer> totalCompletions = new MediatorLiveData<>();
    private final MediatorLiveData<Double> completionRate = new MediatorLiveData<>();
    private final MediatorLiveData<Map<LocalDate, DayStatus>> monthStatusMap = new MediatorLiveData<>();
    private final MediatorLiveData<Map<LocalDate, Double>> last7DaysData = new MediatorLiveData<>();

    private Habit cachedHabit;
    private List<HabitLog> cachedLogs = Collections.emptyList();

    public HabitStatsViewModel(@NonNull Application application) {
        super(application);
        this.repository = HabitRepository.getInstance(application);

        // When selected habit ID changes, re-wire observers
        habit.addSource(selectedHabitId, this::onHabitIdChanged);
        logs.addSource(selectedHabitId, id -> {/* handled in onHabitIdChanged */});

        // Trigger recomputations when range, month, or logs change
        currentStreak.addSource(rangeMode, r -> recompute());
        currentStreak.addSource(selectedMonth, m -> recompute());
    }

    private LiveData<Habit> currentHabitSource;
    private LiveData<List<HabitLog>> currentLogsSource;

    private void onHabitIdChanged(@Nullable Long id) {
        if (currentHabitSource != null) {
            habit.removeSource(currentHabitSource);
        }
        if (currentLogsSource != null) {
            logs.removeSource(currentLogsSource);
        }

        if (id == null || id <= 0) {
            cachedHabit = null;
            cachedLogs = Collections.emptyList();
            recompute();
            return;
        }

        currentHabitSource = repository.getHabitById(id);
        currentLogsSource = repository.getAllLogsForHabitLive(id);

        habit.addSource(currentHabitSource, h -> {
            cachedHabit = h;
            habit.setValue(h);
            recompute();
        });

        logs.addSource(currentLogsSource, l -> {
            cachedLogs = l != null ? l : Collections.emptyList();
            logs.setValue(cachedLogs);
            recompute();
        });
    }

    public void selectHabit(long id) {
        selectedHabitId.setValue(id);
    }

    public LiveData<Long> getSelectedHabitId() {
        return selectedHabitId;
    }

    public LiveData<Habit> getHabit() {
        return habit;
    }

    public LiveData<List<Habit>> getAllActiveHabits() {
        return repository.getActiveHabits();
    }

    public LiveData<RangeMode> getRangeMode() {
        return rangeMode;
    }

    public void setRangeMode(@NonNull RangeMode mode) {
        rangeMode.setValue(mode);
    }

    public LiveData<YearMonth> getSelectedMonth() {
        return selectedMonth;
    }

    public void nextMonth() {
        YearMonth current = selectedMonth.getValue();
        if (current != null) {
            selectedMonth.setValue(current.plusMonths(1));
        }
    }

    public void previousMonth() {
        YearMonth current = selectedMonth.getValue();
        if (current != null) {
            selectedMonth.setValue(current.minusMonths(1));
        }
    }

    public LiveData<Integer> getCurrentStreak() {
        return currentStreak;
    }

    public LiveData<Integer> getLongestStreak() {
        return longestStreak;
    }

    public LiveData<Integer> getTotalCompletions() {
        return totalCompletions;
    }

    public LiveData<Double> getCompletionRate() {
        return completionRate;
    }

    public LiveData<Map<LocalDate, DayStatus>> getMonthStatusMap() {
        return monthStatusMap;
    }

    public LiveData<Map<LocalDate, Double>> getLast7DaysData() {
        return last7DaysData;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Recalculation via pure :core logic
    // ═══════════════════════════════════════════════════════════════════

    private void recompute() {
        if (cachedHabit == null) {
            currentStreak.setValue(0);
            longestStreak.setValue(0);
            totalCompletions.setValue(0);
            completionRate.setValue(0.0);
            monthStatusMap.setValue(Collections.emptyMap());
            last7DaysData.setValue(Collections.emptyMap());
            return;
        }

        LocalDate today = LocalDate.now();

        // 1. Current & Longest Streaks
        currentStreak.setValue(StreakCalculator.currentStreak(cachedHabit, cachedLogs, today));
        longestStreak.setValue(StreakCalculator.longestStreak(cachedHabit, cachedLogs, today));

        // 2. Total Completions
        totalCompletions.setValue(StatsCalculator.totalCompletions(cachedHabit, cachedLogs));

        // 3. Completion Rate based on selected RangeMode
        RangeMode mode = rangeMode.getValue() != null ? rangeMode.getValue() : RangeMode.DAYS_30;
        LocalDate fromDate = switch (mode) {
            case DAYS_7 -> today.minusDays(6);
            case DAYS_30 -> today.minusDays(29);
            case ALL -> cachedHabit.getCreatedDate();
        };
        double rate = StatsCalculator.completionRate(cachedHabit, cachedLogs, fromDate, today, today);
        completionRate.setValue(rate);

        // 4. Interactive Month Calendar Day Status Map
        YearMonth month = selectedMonth.getValue() != null ? selectedMonth.getValue() : YearMonth.now();
        LocalDate firstDay = month.atDay(1);
        LocalDate lastDay = month.atEndOfMonth();
        Map<LocalDate, DayStatus> statusMap = StatsCalculator.buildDayStatusMap(
                cachedHabit, cachedLogs, firstDay, lastDay, today
        );
        monthStatusMap.setValue(statusMap);

        // 5. Weekly Bar Chart Data (last 7 days values)
        Map<LocalDate, Double> weekData = new LinkedHashMap<>();
        Map<LocalDate, Double> logMap = new LinkedHashMap<>();
        for (HabitLog log : cachedLogs) {
            logMap.put(log.getDate(), log.getValue());
        }
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            weekData.put(d, logMap.getOrDefault(d, 0.0));
        }
        last7DaysData.setValue(weekData);
    }

    /**
     * Upserts a log entry for a specific calendar date (interactive edit from calendar).
     */
    public void upsertDayLog(@NonNull LocalDate date, double value) {
        if (cachedHabit == null) return;
        repository.upsertLog(new HabitLog(cachedHabit.getId(), date, Math.max(0.0, value)));
    }
}
