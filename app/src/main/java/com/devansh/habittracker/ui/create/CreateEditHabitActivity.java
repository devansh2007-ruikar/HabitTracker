package com.devansh.habittracker.ui.create;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.devansh.core.model.HabitType;
import com.devansh.core.model.ScheduleType;
import com.devansh.habittracker.R;
import com.devansh.habittracker.databinding.ActivityCreateEditHabitBinding;
import com.google.android.material.chip.Chip;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Material 3 screen for creating or editing habits.
 */
public class CreateEditHabitActivity extends AppCompatActivity {

    public static final String EXTRA_HABIT_ID = "extra_habit_id";

    private ActivityCreateEditHabitBinding binding;
    private CreateEditHabitViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateEditHabitBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(CreateEditHabitViewModel.class);

        long habitId = getIntent().getLongExtra(EXTRA_HABIT_ID, 0);
        if (habitId > 0) {
            binding.toolbar.setTitle(R.string.edit_habit_title);
            viewModel.loadHabit(habitId);
        } else {
            binding.toolbar.setTitle(R.string.create_habit_title);
        }

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupInputListeners();
        setupToggleGroups();
        setupWeekdayChips();
        setupReminderPicker();
        observeViewModel();
    }

    private void setupInputListeners() {
        binding.etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.tilName.setError(null);
                viewModel.getName().setValue(s != null ? s.toString() : "");
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.etTarget.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    double val = Double.parseDouble(s.toString());
                    viewModel.getTarget().setValue(val);
                } catch (NumberFormatException ignored) {}
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.etUnit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.getUnit().setValue(s != null ? s.toString() : "");
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.sliderTimesPerWeek.addOnChangeListener((slider, value, fromUser) -> {
            int tpw = Math.round(value);
            viewModel.getTimesPerWeek().setValue(tpw);
            binding.tvTimesPerWeekLabel.setText(String.format("Goal: %d times per week", tpw));
        });

        binding.btnSave.setOnClickListener(v -> viewModel.save());
    }

    private void setupToggleGroups() {
        // Habit Type Toggle
        binding.toggleHabitType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_type_yes_no) {
                viewModel.getHabitType().setValue(HabitType.YES_NO);
                binding.layoutTargetUnit.setVisibility(View.GONE);
            } else if (checkedId == R.id.btn_type_countable) {
                viewModel.getHabitType().setValue(HabitType.COUNTABLE);
                binding.layoutTargetUnit.setVisibility(View.VISIBLE);
                binding.tilUnit.setEnabled(true);
            } else if (checkedId == R.id.btn_type_timed) {
                viewModel.getHabitType().setValue(HabitType.TIMED);
                binding.layoutTargetUnit.setVisibility(View.VISIBLE);
                binding.etUnit.setText("min");
                binding.tilUnit.setEnabled(false);
            }
        });

        // Schedule Strategy Toggle
        binding.toggleScheduleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_schedule_daily) {
                viewModel.getScheduleType().setValue(ScheduleType.DAILY);
                binding.layoutWeekdays.setVisibility(View.GONE);
                binding.layoutTimesPerWeek.setVisibility(View.GONE);
            } else if (checkedId == R.id.btn_schedule_specific_days) {
                viewModel.getScheduleType().setValue(ScheduleType.SPECIFIC_DAYS);
                binding.layoutWeekdays.setVisibility(View.VISIBLE);
                binding.layoutTimesPerWeek.setVisibility(View.GONE);
            } else if (checkedId == R.id.btn_schedule_times_per_week) {
                viewModel.getScheduleType().setValue(ScheduleType.TIMES_PER_WEEK);
                binding.layoutWeekdays.setVisibility(View.GONE);
                binding.layoutTimesPerWeek.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupWeekdayChips() {
        binding.chipMon.setOnCheckedChangeListener((b, isChecked) -> viewModel.toggleWeekday(DayOfWeek.MONDAY, isChecked));
        binding.chipTue.setOnCheckedChangeListener((b, isChecked) -> viewModel.toggleWeekday(DayOfWeek.TUESDAY, isChecked));
        binding.chipWed.setOnCheckedChangeListener((b, isChecked) -> viewModel.toggleWeekday(DayOfWeek.WEDNESDAY, isChecked));
        binding.chipThu.setOnCheckedChangeListener((b, isChecked) -> viewModel.toggleWeekday(DayOfWeek.THURSDAY, isChecked));
        binding.chipFri.setOnCheckedChangeListener((b, isChecked) -> viewModel.toggleWeekday(DayOfWeek.FRIDAY, isChecked));
        binding.chipSat.setOnCheckedChangeListener((b, isChecked) -> viewModel.toggleWeekday(DayOfWeek.SATURDAY, isChecked));
        binding.chipSun.setOnCheckedChangeListener((b, isChecked) -> viewModel.toggleWeekday(DayOfWeek.SUNDAY, isChecked));
    }

    private void setupReminderPicker() {
        binding.btnPickReminder.setOnClickListener(v -> {
            LocalTime existing = viewModel.getReminderTime().getValue();
            int hour = existing != null ? existing.getHour() : 8;
            int minute = existing != null ? existing.getMinute() : 30;

            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(hour)
                    .setMinute(minute)
                    .setTitleText("Select Daily Reminder")
                    .build();

            picker.addOnPositiveButtonClickListener(dialog -> {
                LocalTime selected = LocalTime.of(picker.getHour(), picker.getMinute());
                viewModel.getReminderTime().setValue(selected);
            });

            picker.show(getSupportFragmentManager(), "reminder_picker");
        });

        binding.btnClearReminder.setOnClickListener(v -> viewModel.getReminderTime().setValue(null));
    }

    private void observeViewModel() {
        // Name
        viewModel.getName().observe(this, name -> {
            if (!binding.etName.getText().toString().equals(name)) {
                binding.etName.setText(name);
            }
        });

        // Habit Type
        viewModel.getHabitType().observe(this, type -> {
            switch (type) {
                case YES_NO -> binding.toggleHabitType.check(R.id.btn_type_yes_no);
                case COUNTABLE -> binding.toggleHabitType.check(R.id.btn_type_countable);
                case TIMED -> binding.toggleHabitType.check(R.id.btn_type_timed);
            }
        });

        // Target & Unit
        viewModel.getTarget().observe(this, target -> {
            String targetStr = (target == (long) (double) target) ? String.valueOf((long) (double) target) : String.valueOf(target);
            if (!binding.etTarget.getText().toString().equals(targetStr)) {
                binding.etTarget.setText(targetStr);
            }
        });
        viewModel.getUnit().observe(this, unit -> {
            if (!binding.etUnit.getText().toString().equals(unit)) {
                binding.etUnit.setText(unit);
            }
        });

        // Schedule Type
        viewModel.getScheduleType().observe(this, sType -> {
            switch (sType) {
                case DAILY -> binding.toggleScheduleType.check(R.id.btn_schedule_daily);
                case SPECIFIC_DAYS -> binding.toggleScheduleType.check(R.id.btn_schedule_specific_days);
                case TIMES_PER_WEEK -> binding.toggleScheduleType.check(R.id.btn_schedule_times_per_week);
            }
        });

        // Selected Days
        viewModel.getSelectedDays().observe(this, days -> {
            binding.chipMon.setChecked(days.contains(DayOfWeek.MONDAY));
            binding.chipTue.setChecked(days.contains(DayOfWeek.TUESDAY));
            binding.chipWed.setChecked(days.contains(DayOfWeek.WEDNESDAY));
            binding.chipThu.setChecked(days.contains(DayOfWeek.THURSDAY));
            binding.chipFri.setChecked(days.contains(DayOfWeek.FRIDAY));
            binding.chipSat.setChecked(days.contains(DayOfWeek.SATURDAY));
            binding.chipSun.setChecked(days.contains(DayOfWeek.SUNDAY));
        });

        // Times Per Week
        viewModel.getTimesPerWeek().observe(this, times -> {
            binding.sliderTimesPerWeek.setValue(times);
            binding.tvTimesPerWeekLabel.setText(String.format("Goal: %d times per week", times));
        });

        // Reminder Time
        viewModel.getReminderTime().observe(this, time -> {
            if (time != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");
                binding.tvReminderTime.setText(time.format(formatter));
                binding.btnClearReminder.setVisibility(View.VISIBLE);
                binding.btnPickReminder.setText("Change");
            } else {
                binding.tvReminderTime.setText("No reminder set");
                binding.btnClearReminder.setVisibility(View.GONE);
                binding.btnPickReminder.setText(R.string.reminder_set);
            }
        });

        // Validation Error
        viewModel.getValidationError().observe(this, error -> {
            if (error != null) {
                binding.tilName.setError(error);
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });

        // Save Success
        viewModel.getSaveSuccess().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                finish();
            }
        });
    }
}
