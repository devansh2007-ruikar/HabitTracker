package com.devansh.habittracker.ui.habits;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitType;
import com.devansh.core.model.ScheduleType;
import com.devansh.habittracker.R;
import com.devansh.habittracker.databinding.ItemHabitBinding;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Adapter presenting the full catalog of habits with management actions.
 */
public class HabitsAdapter extends ListAdapter<Habit, HabitsAdapter.HabitViewHolder> {

    public interface OnHabitMenuListener {
        void onHabitClick(@NonNull Habit habit);
        void onEdit(@NonNull Habit habit);
        void onToggleArchive(@NonNull Habit habit);
        void onDelete(@NonNull Habit habit);
    }

    private final OnHabitMenuListener listener;

    public HabitsAdapter(@NonNull OnHabitMenuListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Habit> DIFF_CALLBACK = new DiffUtil.ItemCallback<Habit>() {
        @Override
        public boolean areItemsTheSame(@NonNull Habit oldItem, @NonNull Habit newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Habit oldItem, @NonNull Habit newItem) {
            return oldItem.getName().equals(newItem.getName())
                    && oldItem.getType().equals(newItem.getType())
                    && Double.compare(oldItem.getTarget(), newItem.getTarget()) == 0
                    && oldItem.isArchived() == newItem.isArchived()
                    && oldItem.getSchedule().equals(newItem.getSchedule());
        }
    };

    @NonNull
    @Override
    public HabitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHabitBinding binding = ItemHabitBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new HabitViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull HabitViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class HabitViewHolder extends RecyclerView.ViewHolder {
        final ItemHabitBinding binding;

        HabitViewHolder(@NonNull ItemHabitBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Habit habit, @NonNull OnHabitMenuListener listener) {
            binding.tvHabitName.setText(habit.getName());

            // Schedule badge description
            String scheduleDesc = formatSchedule(habit);
            binding.tvScheduleBadge.setText(scheduleDesc);

            // Target description
            String targetDesc = formatTarget(habit);
            binding.tvTargetBadge.setText(targetDesc);

            // Reminder time badge
            if (habit.getReminderTime() != null) {
                binding.tvReminderBadge.setVisibility(View.VISIBLE);
                binding.tvReminderBadge.setText("⏰ " + habit.getReminderTime().toString());
            } else {
                binding.tvReminderBadge.setVisibility(View.GONE);
            }

            // Archived badge
            binding.tvArchivedBadge.setVisibility(habit.isArchived() ? View.VISIBLE : View.GONE);

            // 3-dot overflow menu
            binding.ibMenu.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(v.getContext(), v);
                popup.inflate(R.menu.habit_item_menu);

                // Dynamically update archive title
                popup.getMenu().findItem(R.id.menu_archive)
                        .setTitle(habit.isArchived() ? R.string.action_unarchive : R.string.action_archive);

                popup.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();
                    if (id == R.id.menu_edit) {
                        listener.onEdit(habit);
                        return true;
                    } else if (id == R.id.menu_archive) {
                        listener.onToggleArchive(habit);
                        return true;
                    } else if (id == R.id.menu_delete) {
                        listener.onDelete(habit);
                        return true;
                    }
                    return false;
                });
                popup.show();
            });

            itemView.setOnClickListener(v -> listener.onHabitClick(habit));
        }

        private String formatSchedule(Habit habit) {
            ScheduleType type = habit.getSchedule().getType();
            switch (type) {
                case DAILY -> {
                    return "Daily";
                }
                case SPECIFIC_DAYS -> {
                    List<DayOfWeek> days = new ArrayList<>(habit.getSchedule().getDaysOfWeek());
                    Collections.sort(days);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < days.size(); i++) {
                        sb.append(days.get(i).getDisplayName(TextStyle.SHORT, Locale.getDefault()));
                        if (i < days.size() - 1) sb.append(", ");
                    }
                    return sb.toString();
                }
                case TIMES_PER_WEEK -> {
                    return habit.getSchedule().getTimesPerWeek() + "x / week";
                }
                default -> {
                    return "Custom";
                }
            }
        }

        private String formatTarget(Habit habit) {
            HabitType type = habit.getType();
            if (type == HabitType.YES_NO) {
                return "Yes / No";
            }
            String unit = habit.getUnit() != null ? " " + habit.getUnit() : "";
            if (habit.getTarget() == (long) habit.getTarget()) {
                return String.format("%d%s/day", (long) habit.getTarget(), unit);
            }
            return String.format("%.1f%s/day", habit.getTarget(), unit);
        }
    }
}
