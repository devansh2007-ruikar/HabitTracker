package com.devansh.habittracker.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * BroadcastReceiver listening for system lifecycle events to reschedule habit alarms:
 * <ul>
 *   <li>{@code BOOT_COMPLETED} and {@code LOCKED_BOOT_COMPLETED}</li>
 *   <li>{@code TIME_SET} and {@code TIMEZONE_CHANGED}</li>
 *   <li>{@code MY_PACKAGE_REPLACED}</li>
 * </ul>
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            ReminderScheduler.rescheduleAll(context);
        }
    }
}
