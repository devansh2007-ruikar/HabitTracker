package com.devansh.habittracker;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.devansh.habittracker.databinding.ActivityMainBinding;
import com.devansh.habittracker.ui.create.CreateEditHabitActivity;
import com.devansh.habittracker.ui.habits.HabitsFragment;
import com.devansh.habittracker.ui.settings.SettingsFragment;
import com.devansh.habittracker.ui.stats.StatsFragment;
import com.devansh.habittracker.ui.today.TodayFragment;

/**
 * Main entry activity hosting BottomNavigationView and Fragment switching.
 * Written in 100% pure Java 17 using ViewBinding and Material 3 components.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "habit_tracker_prefs";
    private static final String KEY_THEME = "key_theme";

    private ActivityMainBinding binding;

    private final TodayFragment todayFragment = new TodayFragment();
    private final HabitsFragment habitsFragment = new HabitsFragment();
    private final StatsFragment statsFragment = new StatsFragment();
    private final SettingsFragment settingsFragment = new SettingsFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);

        // Apply persisted theme preference before inflating views
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(themeMode);

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup FloatingActionButton to launch habit creation
        binding.fabAddHabit.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, CreateEditHabitActivity.class));
        });

        setupBottomNavigation();

        // Default to Today screen on initial launch
        if (savedInstanceState == null) {
            switchFragment(todayFragment, getString(R.string.nav_today));
        }
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_today) {
                switchFragment(todayFragment, getString(R.string.nav_today));
                binding.fabAddHabit.show();
                return true;
            } else if (id == R.id.navigation_habits) {
                switchFragment(habitsFragment, getString(R.string.nav_habits));
                binding.fabAddHabit.show();
                return true;
            } else if (id == R.id.navigation_stats) {
                switchFragment(statsFragment, getString(R.string.nav_stats));
                binding.fabAddHabit.hide();
                return true;
            } else if (id == R.id.navigation_settings) {
                switchFragment(settingsFragment, getString(R.string.nav_settings));
                binding.fabAddHabit.hide();
                return true;
            }
            return false;
        });
    }

    private void switchFragment(Fragment fragment, String title) {
        binding.toolbar.setTitle(title);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
