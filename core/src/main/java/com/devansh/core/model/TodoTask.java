package com.devansh.core.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Pure Java domain model representing an ad-hoc one-off task.
 *
 * <p>Unlike repeating habits with complex streak/frequency calculations,
 * a {@code TodoTask} represents a single actionable item with optional due date.
 */
public final class TodoTask {

    private final long id;
    private final String title;
    private final boolean completed;
    private final LocalDate dueDate;
    private final LocalDate createdDate;

    public TodoTask(long id, String title, boolean completed, LocalDate dueDate, LocalDate createdDate) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("TodoTask title cannot be empty");
        }
        this.id = id;
        this.title = title.trim();
        this.completed = completed;
        this.dueDate = dueDate;
        this.createdDate = createdDate != null ? createdDate : LocalDate.now();
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean isCompleted() {
        return completed;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public TodoTask withCompleted(boolean newCompleted) {
        return new TodoTask(id, title, newCompleted, dueDate, createdDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TodoTask other)) return false;
        return id == other.id &&
                completed == other.completed &&
                title.equals(other.title) &&
                Objects.equals(dueDate, other.dueDate) &&
                createdDate.equals(other.createdDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, completed, dueDate, createdDate);
    }

    @Override
    public String toString() {
        return "TodoTask{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", completed=" + completed +
                ", dueDate=" + dueDate +
                ", createdDate=" + createdDate +
                '}';
    }
}
