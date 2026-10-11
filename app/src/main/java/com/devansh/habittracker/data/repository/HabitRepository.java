package com.devansh.habittracker.data.repository;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.habittracker.data.HabitTrackerDatabase;
import com.devansh.habittracker.data.dao.HabitDao;
import com.devansh.habittracker.data.dao.HabitLogDao;
import com.devansh.habittracker.data.entity.HabitEntity;
import com.devansh.habittracker.data.entity.HabitLogEntity;
import com.devansh.habittracker.data.mapper.EntityMappers;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Single entry point repository for all habit and log data operations.
 *
 * <p>Architecture & threading guarantees:
 * <ul>
 *   <li>Exposes clean domain models ({@link Habit}, {@link HabitLog}) to ViewModels and UI,
 *       insulating the presentation layer from Room database entities.</li>
 *   <li>All database write/mutation operations (insert, update, delete, upsert) are dispatched
 *       to a dedicated background {@link Executor} so they never block the main UI thread.</li>
 *   <li>All observable read queries return {@link LiveData} mapped from entities to core models.
 *       Room handles background query execution for LiveData automatically.</li>
 *   <li>Optional completion callbacks are posted back to the main Looper so ViewModels/UI
 *       can safely update UI state or trigger navigation.</li>
 * </ul>
 */
public class HabitRepository {

    private static volatile HabitRepository INSTANCE;

    private final HabitDao habitDao;
    private final HabitLogDao habitLogDao;
    private final com.devansh.habittracker.data.dao.TodoTaskDao todoTaskDao;
    private final Executor executor;
    private final Handler mainThreadHandler;
    private final Context appContext;

    /**
     * Dependency injection constructor (useful for unit and instrumented testing).
     *
     * @param habitDao    the Habit DAO
     * @param habitLogDao the HabitLog DAO
     * @param executor    the background executor for write operations
     */
    @VisibleForTesting
    public HabitRepository(@NonNull HabitDao habitDao,
                           @NonNull HabitLogDao habitLogDao,
                           @NonNull Executor executor) {
        this(habitDao, habitLogDao, null, executor, null);
    }

    public HabitRepository(@NonNull HabitDao habitDao,
                           @NonNull HabitLogDao habitLogDao,
                           @Nullable com.devansh.habittracker.data.dao.TodoTaskDao todoTaskDao,
                           @NonNull Executor executor,
                           @Nullable Context appContext) {
        this.habitDao = habitDao;
        this.habitLogDao = habitLogDao;
        this.todoTaskDao = todoTaskDao;
        this.executor = executor;
        this.mainThreadHandler = new Handler(Looper.getMainLooper());
        this.appContext = appContext != null ? appContext.getApplicationContext() : null;
    }

    /**
     * Returns the singleton instance of {@link HabitRepository}.
     *
     * @param context Android application or activity context
     * @return the repository instance
     */
    public static HabitRepository getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (HabitRepository.class) {
                if (INSTANCE == null) {
                    Context appCtx = context.getApplicationContext();
                    HabitTrackerDatabase db = HabitTrackerDatabase.getInstance(appCtx);
                    Executor diskExecutor = Executors.newFixedThreadPool(2);
                    INSTANCE = new HabitRepository(db.habitDao(), db.habitLogDao(), db.todoTaskDao(), diskExecutor, appCtx);
                }
            }
        }
        return INSTANCE;
    }

    private void notifyWidgets() {
        if (appContext != null) {
            com.devansh.habittracker.widget.HabitAppWidgetProvider.updateAllWidgets(appContext);
        }
    }

    /**
     * Allows overriding the singleton instance in tests.
     */
    @VisibleForTesting
    public static void setInstanceForTesting(@Nullable HabitRepository repository) {
        synchronized (HabitRepository.class) {
            INSTANCE = repository;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Habit Operations (Observable LiveData)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Observes all active (non-archived) habits, ordered newest first.
     */
    public LiveData<List<Habit>> getActiveHabits() {
        return Transformations.map(habitDao.getActiveHabits(), EntityMappers::toHabitList);
    }

    /**
     * Observes all habits (including archived), ordered newest first.
     */
    public LiveData<List<Habit>> getAllHabits() {
        return Transformations.map(habitDao.getAllHabits(), EntityMappers::toHabitList);
    }

    /**
     * Observes a single habit by ID as LiveData.
     */
    public LiveData<Habit> getHabitById(long habitId) {
        return Transformations.map(habitDao.getHabitByIdLive(habitId),
                entity -> entity == null ? null : EntityMappers.toHabit(entity));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Habit Mutations (Background Executor)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Inserts a new habit asynchronously on the background executor.
     *
     * @param habit the habit domain model
     */
    public void insertHabit(@NonNull Habit habit) {
        insertHabit(habit, null);
    }

    /**
     * Inserts a new habit asynchronously and delivers the auto-generated row ID
     * to {@code onComplete} on the main thread.
     *
     * @param habit      the habit domain model
     * @param onComplete callback receiving the new row ID (invoked on main thread, nullable)
     */
    public void insertHabit(@NonNull Habit habit, @Nullable Consumer<Long> onComplete) {
        executor.execute(() -> {
            HabitEntity entity = EntityMappers.toEntity(habit);
            long newId = habitDao.insert(entity);
            notifyWidgets();
            if (onComplete != null) {
                mainThreadHandler.post(() -> onComplete.accept(newId));
            }
        });
    }

    /**
     * Updates an existing habit asynchronously.
     */
    public void updateHabit(@NonNull Habit habit) {
        updateHabit(habit, null);
    }

    /**
     * Updates an existing habit asynchronously and runs callback on main thread upon completion.
     */
    public void updateHabit(@NonNull Habit habit, @Nullable Runnable onComplete) {
        executor.execute(() -> {
            habitDao.update(EntityMappers.toEntity(habit));
            notifyWidgets();
            if (onComplete != null) {
                mainThreadHandler.post(onComplete);
            }
        });
    }

    /**
     * Deletes a habit asynchronously (foreign key CASCADE removes associated logs).
     */
    public void deleteHabit(@NonNull Habit habit) {
        deleteHabit(habit, null);
    }

    /**
     * Deletes a habit asynchronously and runs callback on main thread upon completion.
     */
    public void deleteHabit(@NonNull Habit habit, @Nullable Runnable onComplete) {
        executor.execute(() -> {
            habitDao.delete(EntityMappers.toEntity(habit));
            notifyWidgets();
            if (onComplete != null) {
                mainThreadHandler.post(onComplete);
            }
        });
    }

    /**
     * Archives or unarchives a habit asynchronously.
     */
    public void setHabitArchived(long habitId, boolean archived, @Nullable Runnable onComplete) {
        executor.execute(() -> {
            HabitEntity entity = habitDao.getHabitById(habitId);
            if (entity != null) {
                entity.archived = archived;
                habitDao.update(entity);
            }
            if (onComplete != null) {
                mainThreadHandler.post(onComplete);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Habit Log Operations
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Inserts or replaces a habit log for a specific date (upsert pattern).
     *
     * <p>Because habit_logs enforces UNIQUE(habit_id, date), re-logging for the
     * same date will replace the existing record ("latest edit wins").
     *
     * @param log the log domain model
     */
    public void upsertLog(@NonNull HabitLog log) {
        upsertLog(log, null);
    }

    /**
     * Inserts or replaces a habit log asynchronously, running callback on main thread upon completion.
     */
    public void upsertLog(@NonNull HabitLog log, @Nullable Runnable onComplete) {
        executor.execute(() -> {
            HabitLogEntity entity = EntityMappers.toEntity(log);
            habitLogDao.upsert(entity);
            notifyWidgets();
            if (onComplete != null) {
                mainThreadHandler.post(onComplete);
            }
        });
    }

    /**
     * Observes all logs for a habit ordered by date descending.
     */
    public LiveData<List<HabitLog>> getLogsForHabit(long habitId) {
        return Transformations.map(habitLogDao.getLogsForHabit(habitId), EntityMappers::toHabitLogList);
    }

    /**
     * Observes all logs for a habit ordered by date ascending (ideal for streak/stats tracking).
     */
    public LiveData<List<HabitLog>> getAllLogsForHabitLive(long habitId) {
        return Transformations.map(habitLogDao.getAllLogsForHabitLive(habitId), EntityMappers::toHabitLogList);
    }

    /**
     * Observes logs for a habit within a closed date range [from, to] inclusive.
     */
    public LiveData<List<HabitLog>> getLogsForHabitInRange(long habitId,
                                                          @NonNull LocalDate from,
                                                          @NonNull LocalDate to) {
        return Transformations.map(habitLogDao.getLogsForHabitInRange(habitId, from, to),
                EntityMappers::toHabitLogList);
    }

    /**
     * Observes all logs across all habits as LiveData.
     */
    public LiveData<List<HabitLog>> getAllLogsLive() {
        return Transformations.map(habitLogDao.getAllLogsLive(), EntityMappers::toHabitLogList);
    }

    /**
     * Synchronously loads all logs across all habits.
     */
    public List<HabitLog> getAllLogsSync() {
        return EntityMappers.toHabitLogList(habitLogDao.getAllLogsSync());
    }

    /**
     * Deletes a specific log entry by primary key ID.
     */
    public void deleteLog(long logId, @Nullable Runnable onComplete) {
        executor.execute(() -> {
            habitLogDao.deleteById(logId);
            if (onComplete != null) {
                mainThreadHandler.post(onComplete);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Synchronous / Direct Query Helpers (For background workers & tests)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Synchronously loads a habit by ID (must be called from a background thread or test).
     */
    @Nullable
    public Habit getHabitByIdSync(long habitId) {
        HabitEntity entity = habitDao.getHabitById(habitId);
        return entity == null ? null : EntityMappers.toHabit(entity);
    }

    /**
     * Synchronously loads all habits (must be called from a background thread or test).
     */
    public List<Habit> getAllHabitsSync() {
        return EntityMappers.toHabitList(habitDao.getAllHabitsSync());
    }

    /**
     * Synchronously loads all active habits (must be called from a background thread or test).
     */
    public List<Habit> getActiveHabitsSync() {
        return EntityMappers.toHabitList(habitDao.getActiveHabitsSync());
    }

    /**
     * Synchronously loads all logs for a habit in date ascending order.
     */
    public List<HabitLog> getLogsForHabitSync(long habitId) {
        return EntityMappers.toHabitLogList(habitLogDao.getLogsForHabitSync(habitId));
    }

    /**
     * Synchronously loads a single day log for a habit.
     */
    @Nullable
    public HabitLog getLogForDateSync(long habitId, @NonNull LocalDate date) {
        HabitLogEntity entity = habitLogDao.getLogForDate(habitId, date);
        return entity == null ? null : EntityMappers.toHabitLog(entity);
    }

    /**
     * Synchronously inserts a habit (for backup restoration).
     */
    public long insertHabitSync(@NonNull Habit habit) {
        HabitEntity entity = EntityMappers.toEntity(habit);
        long id = habitDao.insert(entity);
        notifyWidgets();
        return id;
    }

    /**
     * Synchronously upserts a habit log (for backup restoration).
     */
    public void upsertLogSync(@NonNull HabitLog log) {
        HabitLogEntity entity = EntityMappers.toEntity(log);
        habitLogDao.upsert(entity);
        notifyWidgets();
    }

    /**
     * Synchronously wipes all database tables (for clean backup replace).
     */
    public void deleteAllDataSync() {
        if (todoTaskDao != null) {
            todoTaskDao.deleteAll();
        }
        habitLogDao.deleteAll();
        habitDao.deleteAll();
        notifyWidgets();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  To-Do Task Operations
    // ═══════════════════════════════════════════════════════════════════

    public LiveData<List<com.devansh.core.model.TodoTask>> getAllTodoTasks() {
        if (todoTaskDao == null) return new androidx.lifecycle.MutableLiveData<>(java.util.Collections.emptyList());
        return Transformations.map(todoTaskDao.getAllTasks(), EntityMappers::toTodoList);
    }

    public LiveData<List<com.devansh.core.model.TodoTask>> getActiveTodoTasks() {
        if (todoTaskDao == null) return new androidx.lifecycle.MutableLiveData<>(java.util.Collections.emptyList());
        return Transformations.map(todoTaskDao.getActiveTasks(), EntityMappers::toTodoList);
    }

    public List<com.devansh.core.model.TodoTask> getAllTodoTasksSync() {
        if (todoTaskDao == null) return java.util.Collections.emptyList();
        return EntityMappers.toTodoList(todoTaskDao.getAllTasksSync());
    }

    public void insertTodoTask(@NonNull com.devansh.core.model.TodoTask task, @Nullable Consumer<Long> onComplete) {
        executor.execute(() -> {
            if (todoTaskDao != null) {
                long id = todoTaskDao.insert(EntityMappers.toEntity(task));
                if (onComplete != null) {
                    mainThreadHandler.post(() -> onComplete.accept(id));
                }
            }
        });
    }

    public void insertTodoTaskSync(@NonNull com.devansh.core.model.TodoTask task) {
        if (todoTaskDao != null) {
            todoTaskDao.insert(EntityMappers.toEntity(task));
        }
    }

    public void updateTodoTask(@NonNull com.devansh.core.model.TodoTask task, @Nullable Runnable onComplete) {
        executor.execute(() -> {
            if (todoTaskDao != null) {
                todoTaskDao.update(EntityMappers.toEntity(task));
                if (onComplete != null) {
                    mainThreadHandler.post(onComplete);
                }
            }
        });
    }

    public void toggleTodoTaskCompleted(@NonNull com.devansh.core.model.TodoTask task, @Nullable Runnable onComplete) {
        executor.execute(() -> {
            if (todoTaskDao != null) {
                com.devansh.core.model.TodoTask updated = task.withCompleted(!task.isCompleted());
                todoTaskDao.update(EntityMappers.toEntity(updated));
                if (onComplete != null) {
                    mainThreadHandler.post(onComplete);
                }
            }
        });
    }

    public void deleteTodoTask(@NonNull com.devansh.core.model.TodoTask task, @Nullable Runnable onComplete) {
        executor.execute(() -> {
            if (todoTaskDao != null) {
                todoTaskDao.delete(EntityMappers.toEntity(task));
                if (onComplete != null) {
                    mainThreadHandler.post(onComplete);
                }
            }
        });
    }
}
