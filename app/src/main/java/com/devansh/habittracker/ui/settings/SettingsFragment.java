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
                if (binding == null || !isAdded()) return;
                if (isGranted) {
                    triggerTestReminder();
                } else {
                    Toast.makeText(requireContext(),
                            getString(R.string.settings_notif_denied),
                            Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String> exportBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (binding == null || !isAdded()) return;
                if (uri != null) {
                    exportBackupToUri(uri);
                }
            });

    private final ActivityResultLauncher<String[]> importBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (binding == null || !isAdded()) return;
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
        setupAboutSection();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkExactAlarmPermission();
    }

    private void setupAboutSection() {
        if (binding == null || !isAdded()) return;
        binding.tvAppVersion.setText(getString(R.string.about_version_format, com.devansh.habittracker.BuildConfig.VERSION_NAME));
        binding.tvGithubLink.setOnClickListener(v -> {
            if (binding == null || !isAdded()) return;
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(getString(R.string.about_github_url)));
            startActivity(intent);
        });
    }

    private void setupThemeToggles() {
        if (binding == null || !isAdded()) return;
        int currentTheme = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (currentTheme == AppCompatDelegate.MODE_NIGHT_NO) {
            binding.rbThemeLight.setChecked(true);
        } else if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
            binding.rbThemeDark.setChecked(true);
        } else {
            binding.rbThemeSystem.setChecked(true);
        }

        binding.rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding == null || !isAdded()) return;
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
        if (binding == null || !isAdded()) return;
        binding.btnTestReminder.setOnClickListener(v -> {
            if (binding == null || !isAdded()) return;
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
            if (binding == null || !isAdded()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        });
    }

    private void checkExactAlarmPermission() {
        if (binding == null || !isAdded()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                binding.btnExactAlarmSettings.setVisibility(View.VISIBLE);
                binding.btnExactAlarmSettings.setText(R.string.settings_grant_exact_alarm);
            } else {
                binding.btnExactAlarmSettings.setVisibility(View.GONE);
            }
        } else {
            binding.btnExactAlarmSettings.setVisibility(View.GONE);
        }
    }

    private void triggerTestReminder() {
        if (binding == null || !isAdded()) return;
        // If exact alarm is disabled on Android 12+, inform user but proceed with fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.settings_exact_alarm_dialog_title)
                        .setMessage(R.string.settings_exact_alarm_dialog_msg)
                        .setPositiveButton(R.string.settings_open_settings, (d, w) -> {
                            if (binding == null || !isAdded()) return;
                            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                            startActivity(intent);
                        })
                        .setNegativeButton(R.string.settings_continue_anyway, (d, w) -> {
                            if (binding == null || !isAdded()) return;
                            scheduleAndNotify();
                        })
                        .show();
                return;
            }
        }

        scheduleAndNotify();
    }

    private void scheduleAndNotify() {
        if (binding == null || !isAdded()) return;
        ReminderScheduler.scheduleTestReminder(requireContext(), 10);
        Toast.makeText(requireContext(),
                getString(R.string.settings_test_reminder_toast),
                Toast.LENGTH_LONG).show();
    }

    private void setupBackupSection() {
        if (binding == null || !isAdded()) return;
        binding.btnExportBackup.setOnClickListener(v -> {
            if (binding == null || !isAdded()) return;
            String defaultFileName = "habit_tracker_backup_" + java.time.LocalDate.now() + ".json";
            exportBackupLauncher.launch(defaultFileName);
        });

        binding.btnImportBackup.setOnClickListener(v -> {
            if (binding == null || !isAdded()) return;
            importBackupLauncher.launch(new String[]{"application/json", "text/*"});
        });
    }

    private void exportBackupToUri(android.net.Uri uri) {
        Context appContext = requireContext().getApplicationContext();
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String json = com.devansh.habittracker.data.backup.JsonBackupManager.exportToJson(appContext);
                try (java.io.OutputStream out = appContext.getContentResolver().openOutputStream(uri)) {
                    if (out != null) {
                        out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        out.flush();
                    }
                }
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding == null || !isAdded()) return;
                        Toast.makeText(requireContext(), getString(R.string.backup_export_success), Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding == null || !isAdded()) return;
                        Toast.makeText(requireContext(), getString(R.string.backup_export_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private void importBackupFromUri(android.net.Uri uri) {
        Context appContext = requireContext().getApplicationContext();
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String json;
                try (java.io.InputStream in = appContext.getContentResolver().openInputStream(uri)) {
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
                    requireActivity().runOnUiThread(() -> {
                        if (binding == null || !isAdded()) return;
                        showRestoreChoiceDialog(payload);
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding == null || !isAdded()) return;
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.backup_import_failed_title)
                                .setMessage(getString(R.string.backup_import_failed_msg, e.getMessage()))
                                .setPositiveButton(R.string.action_ok, null)
                                .show();
                    });
                }
            }
        });
    }

    private void showRestoreChoiceDialog(com.devansh.habittracker.data.backup.JsonBackupManager.BackupPayload payload) {
        if (binding == null || !isAdded()) return;
        String msg = getString(R.string.backup_restore_dialog_msg, payload.habits.size(), payload.todos.size());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_restore_dialog_title)
                .setMessage(msg)
                .setPositiveButton(R.string.backup_merge_option, (d, w) -> {
                    if (binding == null || !isAdded()) return;
                    executeRestore(payload, false);
                })
                .setNeutralButton(R.string.backup_replace_option, (d, w) -> {
                    if (binding == null || !isAdded()) return;
                    executeRestore(payload, true);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void executeRestore(com.devansh.habittracker.data.backup.JsonBackupManager.BackupPayload payload, boolean replace) {
        if (binding == null || !isAdded()) return;
        com.devansh.habittracker.data.backup.JsonBackupManager.restoreBackup(
                requireContext(), payload, replace, summary -> {
                    if (binding == null || !isAdded()) return;
                    String result = getString(R.string.backup_restored_summary,
                            summary.habitsRestored, summary.logsRestored, summary.todosRestored);
                    Toast.makeText(requireContext(), result, Toast.LENGTH_LONG).show();
                }
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
