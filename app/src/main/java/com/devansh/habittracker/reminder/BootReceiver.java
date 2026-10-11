package com.devansh.habittracker.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BroadcastReceiver listening for system lifecycle events to reschedule habit alarms:
 * <ul>
 *   <li>{@code BOOT_COMPLETED}</li>
 *   <li>{@code TIME_SET} and {@code TIMEZONE_CHANGED}</li>
 *   <li>{@code MY_PACKAGE_REPLACED}</li>
 * </ul>
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            try {
                ReminderScheduler.rescheduleAll(context);
            } catch (Exception e) {
                Log.e(TAG, "Failed to reschedule reminders on boot/time change", e);
            }
        }
    }
}
