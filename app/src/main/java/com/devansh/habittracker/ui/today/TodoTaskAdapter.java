package com.devansh.habittracker.ui.today;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.devansh.core.model.TodoTask;
import com.devansh.habittracker.databinding.ItemTodoTaskBinding;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * RecyclerView adapter for one-off to-do tasks.
 */
public class TodoTaskAdapter extends ListAdapter<TodoTask, TodoTaskAdapter.ViewHolder> {

    private final Consumer<TodoTask> onToggleListener;
    private final Consumer<TodoTask> onDeleteListener;

    public TodoTaskAdapter(@NonNull Consumer<TodoTask> onToggleListener,
                           @NonNull Consumer<TodoTask> onDeleteListener) {
        super(DIFF_CALLBACK);
        this.onToggleListener = onToggleListener;
        this.onDeleteListener = onDeleteListener;
    }

    private static final DiffUtil.ItemCallback<TodoTask> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull TodoTask oldItem, @NonNull TodoTask newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull TodoTask oldItem, @NonNull TodoTask newItem) {
            return oldItem.getId() == newItem.getId() &&
                    oldItem.isCompleted() == newItem.isCompleted() &&
                    oldItem.getTitle().equals(newItem.getTitle()) &&
                    Objects.equals(oldItem.getDueDate(), newItem.getDueDate());
        }
    };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTodoTaskBinding binding = ItemTodoTaskBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TodoTask task = getItem(position);
        holder.bind(task, onToggleListener, onDeleteListener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemTodoTaskBinding binding;

        ViewHolder(ItemTodoTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TodoTask task, Consumer<TodoTask> onToggle, Consumer<TodoTask> onDelete) {
            binding.tvTodoTitle.setText(task.getTitle());

            // Clear listener before changing state
            binding.cbTodoComplete.setOnCheckedChangeListener(null);
            binding.cbTodoComplete.setChecked(task.isCompleted());

            // Apply strikethrough if completed
            if (task.isCompleted()) {
                binding.tvTodoTitle.setPaintFlags(binding.tvTodoTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                binding.tvTodoTitle.setAlpha(0.6f);
            } else {
                binding.tvTodoTitle.setPaintFlags(binding.tvTodoTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                binding.tvTodoTitle.setAlpha(1.0f);
            }

            binding.cbTodoComplete.setOnCheckedChangeListener((buttonView, isChecked) -> {
                onToggle.accept(task);
            });

            binding.btnTodoDelete.setOnClickListener(v -> onDelete.accept(task));
        }
    }
}
