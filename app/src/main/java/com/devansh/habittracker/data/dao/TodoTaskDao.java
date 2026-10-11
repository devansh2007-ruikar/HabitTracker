package com.devansh.habittracker.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.devansh.habittracker.data.entity.TodoTaskEntity;

import java.util.List;

/**
 * Data Access Object for one-off to-do tasks.
 */
@Dao
public interface TodoTaskDao {

    @Query("SELECT * FROM todo_tasks ORDER BY is_completed ASC, due_date ASC, id DESC")
    LiveData<List<TodoTaskEntity>> getAllTasks();

    @Query("SELECT * FROM todo_tasks WHERE is_completed = 0 ORDER BY due_date ASC, id DESC")
    LiveData<List<TodoTaskEntity>> getActiveTasks();

    @Query("SELECT * FROM todo_tasks ORDER BY is_completed ASC, id DESC")
    List<TodoTaskEntity> getAllTasksSync();

    @Query("SELECT * FROM todo_tasks WHERE id = :id LIMIT 1")
    TodoTaskEntity getByIdSync(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TodoTaskEntity task);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TodoTaskEntity> tasks);

    @Update
    void update(TodoTaskEntity task);

    @Delete
    void delete(TodoTaskEntity task);

    @Query("DELETE FROM todo_tasks")
    void deleteAll();
}
