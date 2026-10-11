package com.devansh.habittracker.ui.settings;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.devansh.habittracker.R;
import com.devansh.habittracker.databinding.FragmentSettingsBinding;
import com.devansh.habittracker.reminder.ReminderScheduler;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Tab 4: Settings screen with theme toggling (Light/Dark/System),
 * notification testing, exact alarm permission controls, and architecture documentation.
 */
public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "habit_tracker_prefs";
    private static final String KEY_THEME = "key_theme";

    private FragmentSettingsBinding binding;
    private SharedPreferences prefs;

    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    triggerTestReminder();
                } else {
                    Toast.makeText(requireContext(),
                            "Notification permission denied. Reminders cannot be shown.",
                            Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String> exportBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null) {
                    exportBackupToUri(uri);
                }
            });

    private final ActivityResultLauncher<String[]> importBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importBackupFromUri(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        setupThemeToggles();
        setupRemindersSection();
        setupBackupSection();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkExactAlarmPermission();
    }

    private void setupThemeToggles() {
        int currentTheme = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (currentTheme == AppCompatDelegate.MODE_NIGHT_NO) {
            binding.rbThemeLight.setChecked(true);
        } else if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
            binding.rbThemeDark.setChecked(true);
        } else {
            binding.rbThemeSystem.setChecked(true);
        }

        binding.rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            int newMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (checkedId == R.id.rb_theme_light) {
                newMode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.rb_theme_dark) {
                newMode = AppCompatDelegate.MODE_NIGHT_YES;
            }

            prefs.edit().putInt(KEY_THEME, newMode).apply();
            AppCompatDelegate.setDefaultNightMode(newMode);
        });
    }

    private void setupRemindersSection() {
        binding.btnTestReminder.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    return;
                }
            }
            triggerTestReminder();
        });

        binding.btnExactAlarmSettings.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        });
    }

    private void checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                binding.btnExactAlarmSettings.setVisibility(View.VISIBLE);
                binding.btnExactAlarmSettings.setText("Grant Exact Alarm Permission");
            } else {
                binding.btnExactAlarmSettings.setVisibility(View.GONE);
            }
        } else {
            binding.btnExactAlarmSettings.setVisibility(View.GONE);
        }
    }

    private void triggerTestReminder() {
        // If exact alarm is disabled on Android 12+, inform user but proceed with fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Exact Alarms Not Allowed")
                        .setMessage("Exact alarms are restricted by the system. The reminder will still arrive using windowed delivery, or you can grant exact alarms in Settings.")
                        .setPositiveButton("Open Settings", (d, w) -> {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                            startActivity(intent);
                        })
                        .setNegativeButton("Continue Anyway", (d, w) -> scheduleAndNotify())
                        .show();
                return;
            }
        }

        scheduleAndNotify();
    }

    private void scheduleAndNotify() {
        ReminderScheduler.scheduleTestReminder(requireContext(), 10);
        Toast.makeText(requireContext(),
                "⏰ Test reminder scheduled! It will fire in 10 seconds.",
                Toast.LENGTH_LONG).show();
    }

    private void setupBackupSection() {
        binding.btnExportBackup.setOnClickListener(v -> {
            String defaultFileName = "habit_tracker_backup_" + java.time.LocalDate.now() + ".json";
            exportBackupLauncher.launch(defaultFileName);
        });

        binding.btnImportBackup.setOnClickListener(v -> {
            importBackupLauncher.launch(new String[]{"application/json", "text/*"});
        });
    }

    private void exportBackupToUri(android.net.Uri uri) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String json = com.devansh.habittracker.data.backup.JsonBackupManager.exportToJson(requireContext());
                try (java.io.OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                    if (out != null) {
                        out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        out.flush();
                    }
                }
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "✅ Backup exported successfully!", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private void importBackupFromUri(android.net.Uri uri) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String json;
                try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new java.io.IOException("Unable to open file stream");
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        baos.write(buf, 0, len);
                    }
                    json = baos.toString(java.nio.charset.StandardCharsets.UTF_8.name());
                }

                com.devansh.habittracker.data.backup.JsonBackupManager.BackupPayload payload =
                        com.devansh.habittracker.data.backup.JsonBackupManager.parseAndValidate(json);

                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> showRestoreChoiceDialog(payload));
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Import Failed")
                                .setMessage("The selected file is not a valid Habit Tracker backup:\n\n" + e.getMessage())
                                .setPositiveButton("OK", null)
                                .show();
                    });
                }
            }
        });
    }

    private void showRestoreChoiceDialog(com.devansh.habittracker.data.backup.JsonBackupManager.BackupPayload payload) {
        String msg = String.format("Found %d habits and %d tasks in backup.\n\nChoose restore strategy:",
                payload.habits.size(), payload.todos.size());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Restore Backup")
                .setMessage(msg)
                .setPositiveButton("Merge (Keep Existing)", (d, w) -> {
                    executeRestore(payload, false);
                })
                .setNeutralButton("Replace (Wipe Existing)", (d, w) -> {
                    executeRestore(payload, true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeRestore(com.devansh.habittracker.data.backup.JsonBackupManager.BackupPayload payload, boolean replace) {
        com.devansh.habittracker.data.backup.JsonBackupManager.restoreBackup(
                requireContext(), payload, replace, summary -> {
                    String result = String.format("Restored %d habits, %d logs, and %d tasks!",
                            summary.habitsRestored, summary.logsRestored, summary.todosRestored);
                    Toast.makeText(requireContext(), "✅ " + result, Toast.LENGTH_LONG).show();
                }
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
