package com.devansh.habittracker.ui.today;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.devansh.core.model.Habit;
import com.devansh.habittracker.R;
import com.devansh.habittracker.databinding.FragmentTodayBinding;
import com.devansh.habittracker.ui.create.CreateEditHabitActivity;
import com.devansh.habittracker.ui.detail.HabitDetailActivity;
import com.devansh.habittracker.ui.dialog.EditDayLogDialogFragment;
import com.devansh.habittracker.ui.model.TodayHabitUiModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * Tab 1: Today home screen.
 *
 * <p>Presents:
 * <ul>
 *   <li>Horizontal date selector strip for viewing and correcting previous days.</li>
 *   <li>Habits scheduled today (plus N-per-week habits showing week progress).</li>
 *   <li>Streak badge 🔥, progress bars, and quick logging controls (Yes/No checkbox,
 *       Countable +/- stepper and exact tap, Timed Pomodoro timer + manual).</li>
 * </ul>
 */
public class TodayFragment extends Fragment implements TodayHabitAdapter.OnHabitActionListener {

    private FragmentTodayBinding binding;
    private TodayViewModel viewModel;

    private DateStripAdapter dateStripAdapter;
    private TodayHabitAdapter habitsAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTodayBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TodayViewModel.class);

        setupDateStrip();
        setupHabitsRecyclerView();
        setupTasksRecyclerView();
        setupModeToggle();
        observeViewModel();

        binding.btnEmptyAddHabit.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), CreateEditHabitActivity.class));
        });

        // Add task button & keyboard action
        binding.btnAddTask.setOnClickListener(v -> addNewTaskFromInput());
        binding.etNewTaskTitle.setOnEditorActionListener((v, actionId, event) -> {
            addNewTaskFromInput();
            return true;
        });
    }

    private TodoTaskAdapter tasksAdapter;

    private void setupDateStrip() {
        dateStripAdapter = new DateStripAdapter(date -> viewModel.setSelectedDate(date));
        binding.rvDateStrip.setAdapter(dateStripAdapter);
    }

    private void setupHabitsRecyclerView() {
        habitsAdapter = new TodayHabitAdapter(this);
        binding.rvTodayHabits.setAdapter(habitsAdapter);
    }

    private void setupTasksRecyclerView() {
        tasksAdapter = new TodoTaskAdapter(
                task -> viewModel.toggleTodoTask(task),
                task -> viewModel.deleteTodoTask(task)
        );
        binding.rvTodoTasks.setAdapter(tasksAdapter);
    }

    private void setupModeToggle() {
        binding.toggleGroupMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            if (checkedId == R.id.btn_mode_habits) {
                binding.rvDateStrip.setVisibility(View.VISIBLE);
                binding.tvSelectedDateLabel.setVisibility(View.VISIBLE);
                boolean isEmpty = habitsAdapter.getCurrentList().isEmpty();
                binding.rvTodayHabits.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                binding.layoutTodayEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                binding.layoutTasksContainer.setVisibility(View.GONE);
            } else if (checkedId == R.id.btn_mode_tasks) {
                binding.rvDateStrip.setVisibility(View.GONE);
                binding.tvSelectedDateLabel.setVisibility(View.GONE);
                binding.rvTodayHabits.setVisibility(View.GONE);
                binding.layoutTodayEmpty.setVisibility(View.GONE);
                binding.layoutTasksContainer.setVisibility(View.VISIBLE);
            }
        });
    }

    private void addNewTaskFromInput() {
        if (binding.etNewTaskTitle.getText() == null) return;
        String title = binding.etNewTaskTitle.getText().toString().trim();
        if (!title.isEmpty()) {
            viewModel.addTodoTask(title);
            binding.etNewTaskTitle.setText("");
        }
    }

    private void updateGreetingAndHeaderDate(@NonNull LocalDate date) {
        int hour = java.time.LocalTime.now().getHour();
        String greeting;
        if (hour >= 4 && hour < 12) {
            greeting = getString(R.string.greeting_morning);
        } else if (hour >= 12 && hour < 17) {
            greeting = getString(R.string.greeting_afternoon);
        } else {
            greeting = getString(R.string.greeting_evening);
        }
        binding.tvGreeting.setText(greeting);
        binding.tvHeaderDate.setText(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")));
    }

    private void observeViewModel() {
        // Selected Date
        viewModel.getSelectedDate().observe(getViewLifecycleOwner(), date -> {
            dateStripAdapter.setSelectedDate(date);
            updateGreetingAndHeaderDate(date);

            boolean isToday = date.equals(LocalDate.now());
            String formatted = date.format(DateTimeFormatter.ofPattern("EEE, d MMM"));
            binding.tvSelectedDateLabel.setText(isToday ? getString(R.string.today_highlight_label) : formatted);

            updateProgressChip(viewModel.getTodayHabits().getValue(), date);
        });

        // 7-Day All Done Statuses for Date Strip
        viewModel.getDatesAllDoneStatus().observe(getViewLifecycleOwner(), statuses -> {
            dateStripAdapter.setAllDoneDates(statuses);
        });

        // Habits List
        viewModel.getTodayHabits().observe(getViewLifecycleOwner(), items -> {
            habitsAdapter.submitList(items);
            if (binding.toggleGroupMode.getCheckedButtonId() == R.id.btn_mode_habits) {
                boolean isEmpty = (items == null || items.isEmpty());
                binding.layoutTodayEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                binding.rvTodayHabits.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            }
            LocalDate currentDate = viewModel.getSelectedDate().getValue();
            updateProgressChip(items, currentDate != null ? currentDate : LocalDate.now());
        });

        // To-Do Tasks List
        viewModel.getTodoTasks().observe(getViewLifecycleOwner(), tasks -> {
            tasksAdapter.submitList(tasks);
            boolean isEmpty = (tasks == null || tasks.isEmpty());
            binding.layoutTasksEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.rvTodoTasks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });

        // Timer Observables
        viewModel.getTimerElapsedSeconds().observe(getViewLifecycleOwner(), seconds -> updateAdapterTimer());
        viewModel.getIsTimerRunning().observe(getViewLifecycleOwner(), running -> updateAdapterTimer());
        viewModel.getActiveTimerHabitId().observe(getViewLifecycleOwner(), id -> updateAdapterTimer());
    }

    private void updateProgressChip(@Nullable java.util.List<TodayHabitUiModel> items, @NonNull LocalDate date) {
        int total = (items != null) ? items.size() : 0;
        int completed = 0;
        if (items != null) {
            for (TodayHabitUiModel item : items) {
                if (item.isCompleted()) completed++;
            }
        }
        boolean isToday = date.equals(LocalDate.now());
        if (total == 0) {
            binding.chipTodayProgress.setText(getString(R.string.no_habits_scheduled));
        } else if (completed >= total) {
            binding.chipTodayProgress.setText(getString(R.string.all_habits_done_chip, completed, total));
        } else if (isToday) {
            binding.chipTodayProgress.setText(getString(R.string.today_progress_chip, completed, total));
        } else {
            binding.chipTodayProgress.setText(getString(R.string.past_progress_chip, completed, total));
        }
    }

    private void updateAdapterTimer() {
        Long habitId = viewModel.getActiveTimerHabitId().getValue();
        Boolean running = viewModel.getIsTimerRunning().getValue();
        Integer seconds = viewModel.getTimerElapsedSeconds().getValue();

        habitsAdapter.updateTimerState(
                habitId != null ? habitId : -1,
                Boolean.TRUE.equals(running),
                seconds != null ? seconds : 0
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TodayHabitAdapter.OnHabitActionListener Callbacks
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onHabitClick(@NonNull TodayHabitUiModel item) {
        Intent intent = new Intent(requireContext(), HabitDetailActivity.class);
        intent.putExtra(HabitDetailActivity.EXTRA_HABIT_ID, item.getHabit().getId());
        startActivity(intent);
    }

    @Override
    public void onToggleYesNo(@NonNull TodayHabitUiModel item) {
        viewModel.toggleYesNo(item);
    }

    @Override
    public void onAdjustCountable(@NonNull TodayHabitUiModel item, double delta) {
        viewModel.adjustCountable(item, delta);
    }

    @Override
    public void onExactValueClick(@NonNull TodayHabitUiModel item) {
        LocalDate date = viewModel.getSelectedDate().getValue();
        if (date == null) date = LocalDate.now();

        EditDayLogDialogFragment dialog = EditDayLogDialogFragment.newInstance(
                item.getHabit().getName(),
                date,
                item.getCurrentValue(),
                item.getTargetValue(),
                item.getHabit().getUnit()
        );
        dialog.setOnLogSavedListener((d, val) -> viewModel.setExactValue(item, val));
        dialog.show(getChildFragmentManager(), "edit_log");
    }

    @Override
    public void onTimerToggleClick(@NonNull TodayHabitUiModel item) {
        long habitId = item.getHabit().getId();
        Long activeHabit = viewModel.getActiveTimerHabitId().getValue();
        Boolean isRunning = viewModel.getIsTimerRunning().getValue();

        if (activeHabit != null && activeHabit == habitId && Boolean.TRUE.equals(isRunning)) {
            // Timer is running -> Pause and prompt to save
            viewModel.pauseTimer();
            Integer sec = viewModel.getTimerElapsedSeconds().getValue();
            int mins = (sec != null && sec > 0) ? Math.max(1, (int) Math.round(sec / 60.0)) : 0;

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Pomodoro Timer")
                    .setMessage(String.format("Timer paused. Do you want to add %d minutes to today's log?", mins))
                    .setPositiveButton("Add " + mins + " min", (d, w) -> viewModel.saveTimerMinutes(habitId, item))
                    .setNegativeButton("Keep Running Later", null)
                    .setNeutralButton("Reset Timer", (d, w) -> viewModel.resetTimer())
                    .show();
        } else {
            // Start or resume timer
            viewModel.startTimer(habitId);
        }
    }

    @Override
    public void onTimerManualClick(@NonNull TodayHabitUiModel item) {
        onExactValueClick(item);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
