package com.gouxiong.sleep;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.gouxiong.sleep.util.PreferenceStore;

import java.util.Calendar;

public final class CareReminderScheduler {
    public static final String CHANNEL = "life_care_v2";
    public static final String ACTION_MEDICATION_REMIND = "com.gouxiong.sleep.action.MEDICATION_REMIND";
    public static final String ACTION_MEDICATION_DONE = "com.gouxiong.sleep.action.MEDICATION_DONE";
    public static final String ACTION_MEDICATION_LATER = "com.gouxiong.sleep.action.MEDICATION_LATER";
    public static final String ACTION_HYDRATION_REMIND = "com.gouxiong.sleep.action.HYDRATION_REMIND";
    public static final String ACTION_HYDRATION_OK = "com.gouxiong.sleep.action.HYDRATION_OK";
    public static final String ACTION_HYDRATION_LATER = "com.gouxiong.sleep.action.HYDRATION_LATER";
    public static final String ACTION_SEDENTARY_REMIND = "com.gouxiong.sleep.action.SEDENTARY_REMIND";
    public static final String ACTION_SEDENTARY_OK = "com.gouxiong.sleep.action.SEDENTARY_OK";
    public static final String ACTION_SEDENTARY_LATER = "com.gouxiong.sleep.action.SEDENTARY_LATER";
    public static final String ACTION_SERVER_MESSAGE_POLL = "com.gouxiong.sleep.action.SERVER_MESSAGE_POLL";

    public static final int NOTIFICATION_MEDICATION = 4402;
    public static final int NOTIFICATION_HYDRATION = 4502;
    public static final int NOTIFICATION_SEDENTARY = 4552;
    public static final int NOTIFICATION_SERVER_MESSAGE = 4602;

    private static final int REQUEST_MEDICATION_REMIND = 4411;
    private static final int REQUEST_HYDRATION_REMIND = 4511;
    private static final int REQUEST_SEDENTARY_REMIND = 4551;
    private static final int REQUEST_SERVER_MESSAGE_POLL = 4611;
    private static final int MORNING_MEDICATION_END_HOUR = 10;
    private static final int DAY_START_HOUR = 8;
    private static final int DAY_END_HOUR = 21;

    private CareReminderScheduler() {
    }

    public static void ensureCareReminders(Context context) {
        createChannel(context);
        scheduleNextMedicationMorning(context);
        scheduleNextHydration(context);
        scheduleNextSedentary(context);
        scheduleNextServerMessagePoll(context);
    }

    public static void scheduleMedicationLater(Context context, int minutes) {
        long delay = Math.max(5, Math.min(180, minutes)) * 60L * 1000L;
        scheduleMedicationAt(context, System.currentTimeMillis() + delay);
    }

    public static void scheduleHydrationLater(Context context, int minutes) {
        long delay = Math.max(5, Math.min(120, minutes)) * 60L * 1000L;
        scheduleHydrationAt(context, System.currentTimeMillis() + delay);
    }

    public static void scheduleSedentaryLater(Context context, int minutes) {
        long delay = Math.max(15, Math.min(240, minutes)) * 60L * 1000L;
        scheduleSedentaryAt(context, System.currentTimeMillis() + delay);
    }

    public static void scheduleNextMedicationMorning(Context context) {
        PreferenceStore store = new PreferenceStore(context);
        if (!store.medicationEnabled()) {
            cancelMedication(context);
            return;
        }
        scheduleMedicationAt(context, nextMedicationMorningAt(context, store.medicationConfirmedToday()));
    }

    public static void scheduleNextHydration(Context context) {
        PreferenceStore store = new PreferenceStore(context);
        if (!store.hydrationReminderEnabled()) {
            cancelHydration(context);
            return;
        }
        scheduleHydrationAt(context, nextHydrationAt(System.currentTimeMillis(), store.hydrationIntervalMinutes()));
    }

    public static void scheduleNextSedentary(Context context) {
        PreferenceStore store = new PreferenceStore(context);
        if (!store.sedentaryReminderEnabled()) {
            cancelSedentary(context);
            return;
        }
        scheduleSedentaryAt(context, nextSedentaryAt(System.currentTimeMillis(), store.sedentaryIntervalMinutes()));
    }

    public static void scheduleNextServerMessagePoll(Context context) {
        PreferenceStore store = new PreferenceStore(context);
        if (!store.serverRegistered()) {
            cancelServerMessagePoll(context);
            return;
        }
        Intent intent = new Intent(context, ServerMessageReceiver.class);
        intent.setAction(ACTION_SERVER_MESSAGE_POLL);
        schedule(context, intent, REQUEST_SERVER_MESSAGE_POLL, System.currentTimeMillis() + 30L * 60L * 1000L);
    }

    public static void scheduleServerMessagePollLater(Context context, int minutes) {
        PreferenceStore store = new PreferenceStore(context);
        if (!store.serverRegistered()) {
            cancelServerMessagePoll(context);
            return;
        }
        long delay = Math.max(5, Math.min(90, minutes)) * 60L * 1000L;
        Intent intent = new Intent(context, ServerMessageReceiver.class);
        intent.setAction(ACTION_SERVER_MESSAGE_POLL);
        schedule(context, intent, REQUEST_SERVER_MESSAGE_POLL, System.currentTimeMillis() + delay);
    }

    public static boolean isHydrationWindowNow(Context context) {
        PreferenceStore store = new PreferenceStore(context);
        if (!store.hydrationReminderEnabled() || store.isMonitoring()) {
            return false;
        }
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        return hour >= DAY_START_HOUR && hour < DAY_END_HOUR;
    }

    public static boolean isSedentaryWindowNow(Context context) {
        PreferenceStore store = new PreferenceStore(context);
        if (!store.sedentaryReminderEnabled() || store.isMonitoring()) {
            return false;
        }
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        return hour >= DAY_START_HOUR && hour < DAY_END_HOUR;
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL, "生活护理提醒", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("吃药、喝水、久坐等轻量生活提醒");
        manager.createNotificationChannel(channel);
    }

    private static long nextMedicationMorningAt(Context context, boolean confirmedToday) {
        PreferenceStore store = new PreferenceStore(context);
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, store.medicationHour());
        target.set(Calendar.MINUTE, store.medicationMinute());
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        Calendar dayEnd = Calendar.getInstance();
        dayEnd.set(Calendar.HOUR_OF_DAY, Math.max(store.medicationHour() + 3, MORNING_MEDICATION_END_HOUR));
        dayEnd.set(Calendar.MINUTE, 30);
        dayEnd.set(Calendar.SECOND, 0);
        dayEnd.set(Calendar.MILLISECOND, 0);

        if (confirmedToday || now.after(dayEnd)) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        } else if (now.after(target)) {
            target.setTimeInMillis(System.currentTimeMillis() + 60L * 1000L);
        }
        return target.getTimeInMillis();
    }

    private static long nextHydrationAt(long nowMillis, int intervalMinutes) {
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(nowMillis);
        target.add(Calendar.MINUTE, Math.max(30, Math.min(180, intervalMinutes)));
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        int hour = target.get(Calendar.HOUR_OF_DAY);
        if (hour < DAY_START_HOUR) {
            target.set(Calendar.HOUR_OF_DAY, DAY_START_HOUR);
        } else if (hour >= DAY_END_HOUR) {
            target.add(Calendar.DAY_OF_YEAR, 1);
            target.set(Calendar.HOUR_OF_DAY, DAY_START_HOUR);
        }
        return target.getTimeInMillis();
    }

    private static long nextSedentaryAt(long nowMillis, int intervalMinutes) {
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(nowMillis);
        target.add(Calendar.MINUTE, Math.max(30, Math.min(240, intervalMinutes)));
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        int hour = target.get(Calendar.HOUR_OF_DAY);
        if (hour < DAY_START_HOUR) {
            target.set(Calendar.HOUR_OF_DAY, DAY_START_HOUR);
            target.set(Calendar.MINUTE, 30);
        } else if (hour >= DAY_END_HOUR) {
            target.add(Calendar.DAY_OF_YEAR, 1);
            target.set(Calendar.HOUR_OF_DAY, DAY_START_HOUR);
            target.set(Calendar.MINUTE, 30);
        }
        return target.getTimeInMillis();
    }

    private static void scheduleMedicationAt(Context context, long triggerAtMillis) {
        Intent intent = new Intent(context, MedicationReminderReceiver.class);
        intent.setAction(ACTION_MEDICATION_REMIND);
        schedule(context, intent, REQUEST_MEDICATION_REMIND, triggerAtMillis);
    }

    private static void scheduleHydrationAt(Context context, long triggerAtMillis) {
        Intent intent = new Intent(context, HydrationReminderReceiver.class);
        intent.setAction(ACTION_HYDRATION_REMIND);
        schedule(context, intent, REQUEST_HYDRATION_REMIND, triggerAtMillis);
    }

    private static void scheduleSedentaryAt(Context context, long triggerAtMillis) {
        Intent intent = new Intent(context, SedentaryReminderReceiver.class);
        intent.setAction(ACTION_SEDENTARY_REMIND);
        schedule(context, intent, REQUEST_SEDENTARY_REMIND, triggerAtMillis);
    }

    private static void schedule(Context context, Intent intent, int requestCode, long triggerAtMillis) {
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                scheduleInexact(alarmManager, triggerAtMillis, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
        } catch (SecurityException ex) {
            scheduleInexact(alarmManager, triggerAtMillis, pi);
        }
    }

    private static void scheduleInexact(AlarmManager alarmManager, long triggerAtMillis, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= 23) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        }
    }

    private static void cancelMedication(Context context) {
        Intent intent = new Intent(context, MedicationReminderReceiver.class);
        intent.setAction(ACTION_MEDICATION_REMIND);
        cancel(context, intent, REQUEST_MEDICATION_REMIND);
    }

    private static void cancelHydration(Context context) {
        Intent intent = new Intent(context, HydrationReminderReceiver.class);
        intent.setAction(ACTION_HYDRATION_REMIND);
        cancel(context, intent, REQUEST_HYDRATION_REMIND);
    }

    private static void cancelSedentary(Context context) {
        Intent intent = new Intent(context, SedentaryReminderReceiver.class);
        intent.setAction(ACTION_SEDENTARY_REMIND);
        cancel(context, intent, REQUEST_SEDENTARY_REMIND);
    }

    private static void cancelServerMessagePoll(Context context) {
        Intent intent = new Intent(context, ServerMessageReceiver.class);
        intent.setAction(ACTION_SERVER_MESSAGE_POLL);
        cancel(context, intent, REQUEST_SERVER_MESSAGE_POLL);
    }

    private static void cancel(Context context, Intent intent, int requestCode) {
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pi);
        }
    }
}
