package com.devansh.habittracker.ui.stats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.devansh.core.model.Habit;
import com.devansh.core.model.ScheduleType;
import com.devansh.habittracker.R;
import com.devansh.habittracker.databinding.FragmentStatsBinding;
import com.devansh.habittracker.ui.dialog.EditDayLogDialogFragment;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tab 3: Detailed habit statistics, interactive month calendar, and weekly bar chart.
 */
public class StatsFragment extends Fragment implements CalendarGridAdapter.OnCalendarDayClickListener {

    private static final String ARG_INITIAL_HABIT_ID = "initial_habit_id";

    public static StatsFragment newInstance(long habitId) {
        StatsFragment fragment = new StatsFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_INITIAL_HABIT_ID, habitId);
        fragment.setArguments(args);
        return fragment;
    }

    private FragmentStatsBinding binding;
    private HabitStatsViewModel viewModel;
    private CalendarGridAdapter calendarAdapter;

    private List<Habit> loadedHabits = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HabitStatsViewModel.class);

        setupCalendarView();
        setupChart();
        setupRangeToggle();
        observeViewModel();

        long initialId = getArguments() != null ? getArguments().getLong(ARG_INITIAL_HABIT_ID, 0) : 0;
        if (initialId > 0) {
            viewModel.selectHabit(initialId);
        }
    }

    private void setupCalendarView() {
        calendarAdapter = new CalendarGridAdapter(this);
        binding.rvCalendarGrid.setAdapter(calendarAdapter);

        binding.btnPrevMonth.setOnClickListener(v -> {
            if (binding == null || !isAdded()) return;
            viewModel.previousMonth();
        });
        binding.btnNextMonth.setOnClickListener(v -> {
            if (binding == null || !isAdded()) return;
            viewModel.nextMonth();
        });
    }

    private void setupChart() {
        binding.barChartWeekly.getDescription().setEnabled(false);
        binding.barChartWeekly.getLegend().setEnabled(false);
        binding.barChartWeekly.setDrawGridBackground(false);
        binding.barChartWeekly.setDrawBarShadow(false);
        binding.barChartWeekly.setHighlightFullBarEnabled(false);
        binding.barChartWeekly.setPinchZoom(false);
        binding.barChartWeekly.setScaleEnabled(false);

        XAxis xAxis = binding.barChartWeekly.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant));

        binding.barChartWeekly.getAxisRight().setEnabled(false);
        binding.barChartWeekly.getAxisLeft().setDrawGridLines(true);
        binding.barChartWeekly.getAxisLeft().setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant));
        binding.barChartWeekly.getAxisLeft().setAxisMinimum(0f);
    }

    private void setupRangeToggle() {
        binding.toggleRangeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (binding == null || !isAdded()) return;
            if (!isChecked) return;
            if (checkedId == R.id.btn_range_7_days) {
                viewModel.setRangeMode(HabitStatsViewModel.RangeMode.DAYS_7);
            } else if (checkedId == R.id.btn_range_30_days) {
                viewModel.setRangeMode(HabitStatsViewModel.RangeMode.DAYS_30);
            } else if (checkedId == R.id.btn_range_all_time) {
                viewModel.setRangeMode(HabitStatsViewModel.RangeMode.ALL);
            }
        });
    }

    private void observeViewModel() {
        // Active habits dropdown
        viewModel.getAllActiveHabits().observe(getViewLifecycleOwner(), habits -> {
            if (binding == null || !isAdded()) return;
            loadedHabits = habits != null ? habits : new ArrayList<>();
            boolean hasHabits = !loadedHabits.isEmpty();
            binding.layoutStatsEmpty.setVisibility(hasHabits ? View.GONE : View.VISIBLE);
            binding.tilSelectHabit.setVisibility(hasHabits ? View.VISIBLE : View.GONE);
            binding.layoutStatsContent.setVisibility(hasHabits ? View.VISIBLE : View.GONE);

            if (hasHabits) {
                List<String> names = new ArrayList<>();
                for (Habit h : loadedHabits) {
                    names.add(h.getName());
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        requireContext(), android.R.layout.simple_dropdown_item_1line, names
                );
                binding.actvSelectHabit.setAdapter(adapter);

                Long currentSelected = viewModel.getSelectedHabitId().getValue();
                if (currentSelected == null || currentSelected <= 0) {
                    viewModel.selectHabit(loadedHabits.get(0).getId());
                    binding.actvSelectHabit.setText(loadedHabits.get(0).getName(), false);
                } else {
                    for (Habit h : loadedHabits) {
                        if (h.getId() == currentSelected) {
                            binding.actvSelectHabit.setText(h.getName(), false);
                            break;
                        }
                    }
                }

                binding.actvSelectHabit.setOnItemClickListener((parent, v, position, id) -> {
                    if (binding == null || !isAdded()) return;
                    Habit selected = loadedHabits.get(position);
                    viewModel.selectHabit(selected.getId());
                });
            }
        });

        // Habit details
        viewModel.getHabit().observe(getViewLifecycleOwner(), habit -> {
            if (binding == null || !isAdded()) return;
            // Updated habit details
        });

        // Streaks
        viewModel.getCurrentStreak().observe(getViewLifecycleOwner(), streak -> {
            if (binding == null || !isAdded()) return;
            Habit h = viewModel.getHabit().getValue();
            String suffix = (h != null && h.getSchedule().getType() == ScheduleType.TIMES_PER_WEEK) ? " w" : " d";
            binding.tvStatCurrentStreak.setText((streak != null ? streak : 0) + suffix);
        });

        viewModel.getLongestStreak().observe(getViewLifecycleOwner(), streak -> {
            if (binding == null || !isAdded()) return;
            Habit h = viewModel.getHabit().getValue();
            String suffix = (h != null && h.getSchedule().getType() == ScheduleType.TIMES_PER_WEEK) ? " w" : " d";
            binding.tvStatBestStreak.setText((streak != null ? streak : 0) + suffix);
        });

        viewModel.getTotalCompletions().observe(getViewLifecycleOwner(), total -> {
            if (binding == null || !isAdded()) return;
            binding.tvStatTotalCompletions.setText(String.valueOf(total != null ? total : 0));
        });

        // Completion Rate
        viewModel.getCompletionRate().observe(getViewLifecycleOwner(), rate -> {
            if (binding == null || !isAdded()) return;
            double r = rate != null ? rate : 0.0;
            int pct = (int) Math.round(r * 100);
            binding.tvCompletionRateValue.setText(pct + "%");
            binding.progressCompletionRate.setProgress(pct);
        });

        // Month Calendar
        viewModel.getSelectedMonth().observe(getViewLifecycleOwner(), month -> {
            if (binding == null || !isAdded()) return;
            updateCalendar();
        });
        viewModel.getMonthStatusMap().observe(getViewLifecycleOwner(), map -> {
            if (binding == null || !isAdded()) return;
            updateCalendar();
        });

        // Weekly Bar Chart
        viewModel.getLast7DaysData().observe(getViewLifecycleOwner(), weekData -> {
            if (binding == null || !isAdded()) return;
            updateBarChart(weekData);
        });
    }

    private void updateCalendar() {
        if (binding == null || !isAdded()) return;
        YearMonth month = viewModel.getSelectedMonth().getValue();
        if (month != null) {
            String monthName = month.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
            binding.tvCalendarMonthLabel.setText(monthName + " " + month.getYear());

            if (viewModel.getMonthStatusMap().getValue() != null) {
                calendarAdapter.updateData(month, viewModel.getMonthStatusMap().getValue());
            }
        }
    }

    private void updateBarChart(@Nullable Map<LocalDate, Double> weekData) {
        if (binding == null || !isAdded()) return;
        if (weekData == null || weekData.isEmpty()) return;

        List<BarEntry> entries = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();

        int index = 0;
        for (Map.Entry<LocalDate, Double> entry : weekData.entrySet()) {
            entries.add(new BarEntry(index, entry.getValue().floatValue()));
            String dayName = entry.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
            xLabels.add(dayName);
            index++;
        }

        int primaryColor = ContextCompat.getColor(requireContext(), R.color.primary);
        BarDataSet dataSet = new BarDataSet(entries, "Progress");
        dataSet.setColor(primaryColor);
        dataSet.setDrawValues(false);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);

        binding.barChartWeekly.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));
        binding.barChartWeekly.setData(barData);
        binding.barChartWeekly.invalidate();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CalendarGridAdapter.OnCalendarDayClickListener Callback
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onDayClicked(@NonNull LocalDate date) {
        if (binding == null || !isAdded()) return;
        Habit habit = viewModel.getHabit().getValue();
        if (habit == null) return;

        Double currentValue = 0.0;
        if (viewModel.getLast7DaysData().getValue() != null && viewModel.getLast7DaysData().getValue().containsKey(date)) {
            currentValue = viewModel.getLast7DaysData().getValue().get(date);
        }

        EditDayLogDialogFragment dialog = EditDayLogDialogFragment.newInstance(
                habit.getName(),
                date,
                currentValue != null ? currentValue : 0.0,
                habit.getTarget(),
                habit.getUnit()
        );
        dialog.setOnLogSavedListener((d, val) -> {
            if (binding == null || !isAdded()) return;
            viewModel.upsertDayLog(d, val);
        });
        dialog.show(getChildFragmentManager(), "edit_calendar_day");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
