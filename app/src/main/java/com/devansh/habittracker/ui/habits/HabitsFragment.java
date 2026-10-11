package com.devansh.habittracker.ui.habits;

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
import com.devansh.habittracker.databinding.FragmentHabitsBinding;
import com.devansh.habittracker.ui.create.CreateEditHabitActivity;
import com.devansh.habittracker.ui.detail.HabitDetailActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

/**
 * Tab 2: Habits catalog screen with filtering, swipe/menu actions,
 * confirm dialog, and undo Snackbar.
 */
public class HabitsFragment extends Fragment implements HabitsAdapter.OnHabitMenuListener {

    private FragmentHabitsBinding binding;
    private HabitsViewModel viewModel;
    private HabitsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHabitsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HabitsViewModel.class);

        setupRecyclerView();
        setupFilterChips();
        observeViewModel();

        binding.btnEmptyAddHabit.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), CreateEditHabitActivity.class));
        });
    }

    private void setupRecyclerView() {
        adapter = new HabitsAdapter(this);
        binding.rvHabits.setAdapter(adapter);
    }

    private void setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_active)) {
                viewModel.setFilterMode(HabitsViewModel.FilterMode.ACTIVE);
            } else if (checkedIds.contains(R.id.chip_archived)) {
                viewModel.setFilterMode(HabitsViewModel.FilterMode.ARCHIVED);
            } else if (checkedIds.contains(R.id.chip_all)) {
                viewModel.setFilterMode(HabitsViewModel.FilterMode.ALL);
            }
        });
    }

    private void observeViewModel() {
        viewModel.getHabits().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            boolean isEmpty = (list == null || list.isEmpty());
            binding.layoutHabitsEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.rvHabits.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //  HabitsAdapter.OnHabitMenuListener Callbacks
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onHabitClick(@NonNull Habit habit) {
        Intent intent = new Intent(requireContext(), HabitDetailActivity.class);
        intent.putExtra(HabitDetailActivity.EXTRA_HABIT_ID, habit.getId());
        startActivity(intent);
    }

    @Override
    public void onEdit(@NonNull Habit habit) {
        Intent intent = new Intent(requireContext(), CreateEditHabitActivity.class);
        intent.putExtra(CreateEditHabitActivity.EXTRA_HABIT_ID, habit.getId());
        startActivity(intent);
    }

    @Override
    public void onToggleArchive(@NonNull Habit habit) {
        viewModel.toggleArchive(habit);
    }

    @Override
    public void onDelete(@NonNull Habit habit) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_delete_title)
                .setMessage(getString(R.string.confirm_delete_message, habit.getName()))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    viewModel.deleteHabitWithUndo(habit, () -> {
                        // Show undo snackbar on main UI thread
                        if (getView() != null) {
                            Snackbar.make(requireView(), R.string.habit_deleted, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.action_undo, v -> viewModel.restoreLastDeleted())
                                    .show();
                        }
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
