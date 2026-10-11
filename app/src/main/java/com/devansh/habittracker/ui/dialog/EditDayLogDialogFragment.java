package com.devansh.habittracker.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.devansh.habittracker.databinding.DialogEditLogBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * Material dialog enabling precise numeric log entry and correction for any date.
 */
public class EditDayLogDialogFragment extends DialogFragment {

    public interface OnLogSavedListener {
        void onLogSaved(@NonNull LocalDate date, double value);
    }

    private static final String ARG_HABIT_NAME = "habit_name";
    private static final String ARG_DATE = "date";
    private static final String ARG_CURRENT_VALUE = "current_value";
    private static final String ARG_TARGET = "target";
    private static final String ARG_UNIT = "unit";

    private OnLogSavedListener listener;

    public static EditDayLogDialogFragment newInstance(@NonNull String habitName,
                                                       @NonNull LocalDate date,
                                                       double currentValue,
                                                       double target,
                                                       @Nullable String unit) {
        EditDayLogDialogFragment fragment = new EditDayLogDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_HABIT_NAME, habitName);
        args.putString(ARG_DATE, date.toString());
        args.putDouble(ARG_CURRENT_VALUE, currentValue);
        args.putDouble(ARG_TARGET, target);
        args.putString(ARG_UNIT, unit);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnLogSavedListener(@NonNull OnLogSavedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        String habitName = args.getString(ARG_HABIT_NAME, "");
        LocalDate date = LocalDate.parse(args.getString(ARG_DATE));
        double currentValue = args.getDouble(ARG_CURRENT_VALUE);
        double target = args.getDouble(ARG_TARGET);
        String unit = args.getString(ARG_UNIT);
        String unitSuffix = (unit != null && !unit.isEmpty()) ? " " + unit : "";

        DialogEditLogBinding binding = DialogEditLogBinding.inflate(LayoutInflater.from(requireContext()));

        String formattedDate = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
        binding.tvDialogTitle.setText(habitName + " (" + formattedDate + ")");
        binding.tvDialogTarget.setText(String.format("Daily Target: %.0f%s", target, unitSuffix));

        if (currentValue > 0) {
            if (currentValue == (long) currentValue) {
                binding.etLogValue.setText(String.valueOf((long) currentValue));
            } else {
                binding.etLogValue.setText(String.valueOf(currentValue));
            }
        }

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(binding.getRoot())
                .setPositiveButton("Save", (dialog, which) -> {
                    String input = binding.etLogValue.getText() != null
                            ? binding.etLogValue.getText().toString().trim() : "";
                    double value = 0.0;
                    if (!input.isEmpty()) {
                        try {
                            value = Double.parseDouble(input);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (listener != null) {
                        listener.onLogSaved(date, Math.max(0.0, value));
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
    }
}
