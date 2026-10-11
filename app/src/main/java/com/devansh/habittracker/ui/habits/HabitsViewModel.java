package com.devansh.habittracker.ui.habits;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.habittracker.data.repository.HabitRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * ViewModel managing the full list of habits, filtering, deletion with undo,
 * and archiving states.
 */
public class HabitsViewModel extends AndroidViewModel {

    public enum FilterMode {
        ACTIVE,
        ARCHIVED,
        ALL
    }

    private final HabitRepository repository;
    private final MutableLiveData<FilterMode> filterMode = new MutableLiveData<>(FilterMode.ACTIVE);
    private final MediatorLiveData<List<Habit>> habits = new MediatorLiveData<>();

    private List<Habit> cachedAllHabits = Collections.emptyList();

    // Undo buffer for deleted habit
    private Habit lastDeletedHabit;
    private List<HabitLog> lastDeletedLogs;

    public HabitsViewModel(@NonNull Application application) {
        super(application);
        this.repository = HabitRepository.getInstance(application);

        LiveData<List<Habit>> repoHabits = repository.getAllHabits();

        habits.addSource(repoHabits, list -> {
            cachedAllHabits = list != null ? list : Collections.emptyList();
            applyFilter();
        });

        habits.addSource(filterMode, mode -> applyFilter());
    }

    public LiveData<List<Habit>> getHabits() {
        return habits;
    }

    public LiveData<FilterMode> getFilterMode() {
        return filterMode;
    }

    public void setFilterMode(@NonNull FilterMode mode) {
        filterMode.setValue(mode);
    }

    private void applyFilter() {
        FilterMode mode = filterMode.getValue();
        if (mode == null) mode = FilterMode.ACTIVE;

        List<Habit> result = new ArrayList<>();
        for (Habit h : cachedAllHabits) {
            switch (mode) {
                case ACTIVE -> {
                    if (!h.isArchived()) result.add(h);
                }
                case ARCHIVED -> {
                    if (h.isArchived()) result.add(h);
                }
                case ALL -> result.add(h);
            }
        }
        habits.setValue(result);
    }

    /**
     * Toggles the archived state of a habit.
     */
    public void toggleArchive(@NonNull Habit habit) {
        repository.setHabitArchived(habit.getId(), !habit.isArchived(), null);
    }

    /**
     * Prepares and executes deletion with undo capability.
     */
    public void deleteHabitWithUndo(@NonNull Habit habit, @Nullable Runnable onComplete) {
        com.devansh.habittracker.reminder.ReminderScheduler.cancelReminder(getApplication(), habit.getId());
        Executors.newSingleThreadExecutor().execute(() -> {
            // 1. Snapshot habit and all its logs before deletion
            lastDeletedHabit = habit;
            lastDeletedLogs = repository.getLogsForHabitSync(habit.getId());

            // 2. Perform cascade deletion in Room
            repository.deleteHabit(habit, onComplete);
        });
    }

    /**
     * Restores the most recently deleted habit and all its historical logs.
     */
    public void restoreLastDeleted() {
        if (lastDeletedHabit == null) return;

        Habit toRestore = lastDeletedHabit;
        List<HabitLog> logsToRestore = lastDeletedLogs != null ? new ArrayList<>(lastDeletedLogs) : Collections.emptyList();

        repository.insertHabit(toRestore, newHabitId -> {
            // Restore child logs associated with the newly generated habit ID
            for (HabitLog log : logsToRestore) {
                repository.upsertLog(new HabitLog(newHabitId, log.getDate(), log.getValue()));
            }
            if (toRestore.getReminderTime() != null) {
                Habit restoredWithId = new Habit(
                        newHabitId, toRestore.getName(), toRestore.getType(),
                        toRestore.getSchedule(), toRestore.getTarget(), toRestore.getUnit(),
                        toRestore.getReminderTime(), toRestore.getCreatedDate(), toRestore.isArchived()
                );
                com.devansh.habittracker.reminder.ReminderScheduler.scheduleReminder(getApplication(), restoredWithId);
            }
        });

        lastDeletedHabit = null;
        lastDeletedLogs = null;
    }
}
