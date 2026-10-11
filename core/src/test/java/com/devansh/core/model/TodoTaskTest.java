package com.devansh.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class TodoTaskTest {

    @Test
    @DisplayName("Create valid TodoTask and verify immutability and withCompleted")
    void testValidTodoTask() {
        LocalDate created = LocalDate.of(2026, 3, 1);
        LocalDate due = LocalDate.of(2026, 3, 5);
        TodoTask task = new TodoTask(1L, "Buy groceries", false, due, created);

        assertEquals(1L, task.getId());
        assertEquals("Buy groceries", task.getTitle());
        assertFalse(task.isCompleted());
        assertEquals(due, task.getDueDate());
        assertEquals(created, task.getCreatedDate());

        TodoTask completedTask = task.withCompleted(true);
        assertTrue(completedTask.isCompleted());
        assertEquals(task.getTitle(), completedTask.getTitle());
        assertEquals(task.getId(), completedTask.getId());
    }

    @Test
    @DisplayName("Empty title throws IllegalArgumentException")
    void testEmptyTitleThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new TodoTask(1L, "   ", false, null, LocalDate.now())
        );
        assertThrows(IllegalArgumentException.class, () ->
                new TodoTask(1L, null, false, null, LocalDate.now())
        );
    }
}
