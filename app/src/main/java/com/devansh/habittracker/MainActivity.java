package com.devansh.habittracker;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

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

    private static final String TAG_TODAY = "tag_today";
    private static final String TAG_HABITS = "tag_habits";
    private static final String TAG_STATS = "tag_stats";
    private static final String TAG_SETTINGS = "tag_settings";
    private static final String KEY_SELECTED_NAV_ID = "key_selected_nav_id";

    private ActivityMainBinding binding;
    private int selectedNavId = R.id.navigation_today;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup FloatingActionButton to launch habit creation
        binding.fabAddHabit.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, CreateEditHabitActivity.class));
        });

        setupBottomNavigation();

        if (savedInstanceState != null) {
            selectedNavId = savedInstanceState.getInt(KEY_SELECTED_NAV_ID, R.id.navigation_today);
        } else {
            selectedNavId = R.id.navigation_today;
        }

        switchFragment(selectedNavId);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_NAV_ID, selectedNavId);
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id != selectedNavId) {
                selectedNavId = id;
                switchFragment(id);
            }
            return true;
        });
    }

    private void switchFragment(int navId) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        String targetTag;
        String title;
        boolean showFab;

        if (navId == R.id.navigation_habits) {
            targetTag = TAG_HABITS;
            title = getString(R.string.nav_habits);
            showFab = true;
        } else if (navId == R.id.navigation_stats) {
            targetTag = TAG_STATS;
            title = getString(R.string.nav_stats);
            showFab = false;
        } else if (navId == R.id.navigation_settings) {
            targetTag = TAG_SETTINGS;
            title = getString(R.string.nav_settings);
            showFab = false;
        } else {
            targetTag = TAG_TODAY;
            title = getString(R.string.nav_today);
            showFab = true;
        }

        binding.toolbar.setTitle(title);
        if (showFab) {
            binding.fabAddHabit.show();
        } else {
            binding.fabAddHabit.hide();
        }

        Fragment targetFragment = fm.findFragmentByTag(targetTag);
        if (targetFragment == null) {
            targetFragment = createFragmentForTag(targetTag);
            transaction.add(R.id.fragment_container, targetFragment, targetTag);
        } else {
            transaction.show(targetFragment);
        }

        String[] allTags = {TAG_TODAY, TAG_HABITS, TAG_STATS, TAG_SETTINGS};
        for (String tag : allTags) {
            if (!tag.equals(targetTag)) {
                Fragment other = fm.findFragmentByTag(tag);
                if (other != null && !other.isHidden()) {
                    transaction.hide(other);
                }
            }
        }

        transaction.commit();

        if (binding.bottomNavigation.getSelectedItemId() != navId) {
            binding.bottomNavigation.setSelectedItemId(navId);
        }
    }

    private Fragment createFragmentForTag(String tag) {
        switch (tag) {
            case TAG_HABITS:
                return new HabitsFragment();
            case TAG_STATS:
                return new StatsFragment();
            case TAG_SETTINGS:
                return new SettingsFragment();
            case TAG_TODAY:
            default:
                return new TodayFragment();
        }
    }
}
