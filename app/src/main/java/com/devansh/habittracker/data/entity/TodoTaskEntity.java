package com.devansh.habittracker.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.LocalDate;

/**
 * Room entity representing a one-off to-do task stored in the local SQLite database.
 * Stored in a separate table {@code todo_tasks}.
 */
@Entity(tableName = "todo_tasks")
public class TodoTaskEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "is_completed")
    public boolean isCompleted;

    @Nullable
    @ColumnInfo(name = "due_date")
    public LocalDate dueDate;

    @NonNull
    @ColumnInfo(name = "created_date")
    public LocalDate createdDate;

    public TodoTaskEntity() {
        this.title = "";
        this.createdDate = LocalDate.now();
    }

    @androidx.room.Ignore
    public TodoTaskEntity(long id, @NonNull String title, boolean isCompleted,
                          @Nullable LocalDate dueDate, @NonNull LocalDate createdDate) {
        this.id = id;
        this.title = title;
        this.isCompleted = isCompleted;
        this.dueDate = dueDate;
        this.createdDate = createdDate;
    }
}
