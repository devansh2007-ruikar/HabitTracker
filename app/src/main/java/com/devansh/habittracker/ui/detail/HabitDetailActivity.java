package com.devansh.habittracker.ui.detail;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.devansh.habittracker.R;
import com.devansh.habittracker.databinding.ActivityHabitDetailBinding;
import com.devansh.habittracker.ui.stats.StatsFragment;

/**
 * Dedicated activity for deep-diving into a specific habit's statistics, calendar, and charts.
 */
public class HabitDetailActivity extends AppCompatActivity {

    public static final String EXTRA_HABIT_ID = "extra_habit_id";

    private ActivityHabitDetailBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHabitDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        long habitId = getIntent().getLongExtra(EXTRA_HABIT_ID, 0);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.detail_fragment_container, StatsFragment.newInstance(habitId))
                    .commit();
        }
    }
}
