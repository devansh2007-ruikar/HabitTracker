package com.devansh.habittracker.ui.create;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitType;
import com.devansh.core.model.Schedule;
import com.devansh.core.model.ScheduleType;
import com.devansh.habittracker.data.repository.HabitRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * ViewModel managing the creation or modification of habits, including
 * form state holding and input validation.
 */
public class CreateEditHabitViewModel extends AndroidViewModel {

    private final HabitRepository repository;

    private long habitId = 0;
    private LocalDate createdDate = LocalDate.now();
    private boolean isArchived = false;

    private final MutableLiveData<String> name = new MutableLiveData<>("");
    private final MutableLiveData<HabitType> habitType = new MutableLiveData<>(HabitType.YES_NO);
    private final MutableLiveData<ScheduleType> scheduleType = new MutableLiveData<>(ScheduleType.DAILY);
    private final MutableLiveData<Set<DayOfWeek>> selectedDays = new MutableLiveData<>(EnumSet.noneOf(DayOfWeek.class));
    private final MutableLiveData<Integer> timesPerWeek = new MutableLiveData<>(3);
    private final MutableLiveData<Double> target = new MutableLiveData<>(1.0);
    private final MutableLiveData<String> unit = new MutableLiveData<>("");
    private final MutableLiveData<LocalTime> reminderTime = new MutableLiveData<>(null);

    // Form feedback
    private final MutableLiveData<String> validationError = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> saveSuccess = new MutableLiveData<>(false);

    public CreateEditHabitViewModel(@NonNull Application application) {
        super(application);
        this.repository = HabitRepository.getInstance(application);
    }

    public void loadHabit(long id) {
        if (id <= 0) return;
        this.habitId = id;
        Executors.newSingleThreadExecutor().execute(() -> {
            Habit habit = repository.getHabitByIdSync(id);
            if (habit != null) {
                createdDate = habit.getCreatedDate();
                isArchived = habit.isArchived();
                name.postValue(habit.getName());
                habitType.postValue(habit.getType());
                scheduleType.postValue(habit.getSchedule().getType());
                selectedDays.postValue(habit.getSchedule().getDaysOfWeek());
                timesPerWeek.postValue(habit.getSchedule().getTimesPerWeek());
                target.postValue(habit.getTarget());
                unit.postValue(habit.getUnit() != null ? habit.getUnit() : "");
                reminderTime.postValue(habit.getReminderTime());
            }
        });
    }

    public long getHabitId() {
        return habitId;
    }

    public MutableLiveData<String> getName() {
        return name;
    }

    public MutableLiveData<HabitType> getHabitType() {
        return habitType;
    }

    public MutableLiveData<ScheduleType> getScheduleType() {
        return scheduleType;
    }

    public MutableLiveData<Set<DayOfWeek>> getSelectedDays() {
        return selectedDays;
    }

    public MutableLiveData<Integer> getTimesPerWeek() {
        return timesPerWeek;
    }

    public MutableLiveData<Double> getTarget() {
        return target;
    }

    public MutableLiveData<String> getUnit() {
        return unit;
    }

    public MutableLiveData<LocalTime> getReminderTime() {
        return reminderTime;
    }

    public LiveData<String> getValidationError() {
        return validationError;
    }

    public LiveData<Boolean> getSaveSuccess() {
        return saveSuccess;
    }

    public void toggleWeekday(@NonNull DayOfWeek day, boolean selected) {
        Set<DayOfWeek> current = selectedDays.getValue();
        Set<DayOfWeek> next = current != null ? EnumSet.copyOf(current) : EnumSet.noneOf(DayOfWeek.class);
        if (selected) {
            next.add(day);
        } else {
            next.remove(day);
        }
        selectedDays.setValue(next);
    }

    /**
     * Validates input values and commits creation or update to repository.
     *
     * @return true if valid and saved, false if validation failed
     */
    public boolean save() {
        String habitName = name.getValue() != null ? name.getValue().trim() : "";
        if (habitName.isEmpty()) {
            validationError.setValue("Habit name cannot be empty");
            return false;
        }

        HabitType type = habitType.getValue() != null ? habitType.getValue() : HabitType.YES_NO;
        double targetVal = 1.0;
        if (type != HabitType.YES_NO) {
            Double t = target.getValue();
            if (t == null || t <= 0) {
                validationError.setValue("Target must be greater than 0");
                return false;
            }
            targetVal = t;
        }

        ScheduleType sType = scheduleType.getValue() != null ? scheduleType.getValue() : ScheduleType.DAILY;
        Schedule schedule;
        switch (sType) {
            case DAILY -> schedule = Schedule.daily();
            case SPECIFIC_DAYS -> {
                Set<DayOfWeek> days = selectedDays.getValue();
                if (days == null || days.isEmpty()) {
                    validationError.setValue("Please select at least one day of the week");
                    return false;
                }
                schedule = Schedule.specificDays(days);
            }
            case TIMES_PER_WEEK -> {
                Integer tpw = timesPerWeek.getValue();
                int times = tpw != null ? Math.max(1, Math.min(7, tpw)) : 3;
                schedule = Schedule.timesPerWeek(times);
            }
            default -> schedule = Schedule.daily();
        }

        String unitVal = unit.getValue() != null && !unit.getValue().trim().isEmpty()
                ? unit.getValue().trim() : null;
        if (type == HabitType.TIMED && unitVal == null) {
            unitVal = "min";
        }

        LocalTime reminder = reminderTime.getValue();

        Habit habit = new Habit(
                habitId,
                habitName,
                type,
                schedule,
                targetVal,
                unitVal,
                reminder,
                createdDate,
                isArchived
        );

        if (habitId == 0) {
            repository.insertHabit(habit, newId -> {
                Habit savedHabit = new Habit(
                        newId, habit.getName(), habit.getType(), habit.getSchedule(),
                        habit.getTarget(), habit.getUnit(), habit.getReminderTime(),
                        habit.getCreatedDate(), habit.isArchived()
                );
                if (savedHabit.getReminderTime() != null) {
                    com.devansh.habittracker.reminder.ReminderScheduler.scheduleReminder(getApplication(), savedHabit);
                }
                saveSuccess.postValue(true);
            });
        } else {
            repository.updateHabit(habit, () -> {
                if (habit.getReminderTime() != null) {
                    com.devansh.habittracker.reminder.ReminderScheduler.scheduleReminder(getApplication(), habit);
                } else {
                    com.devansh.habittracker.reminder.ReminderScheduler.cancelReminder(getApplication(), habit.getId());
                }
                saveSuccess.postValue(true);
            });
        }

        return true;
    }
}
