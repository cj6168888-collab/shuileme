package com.gouxiong.sleep;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.gouxiong.sleep.util.PreferenceStore;

public class MedicationReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        PreferenceStore store = new PreferenceStore(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (CareReminderScheduler.ACTION_MEDICATION_DONE.equals(action)) {
            store.confirmMedicationNow();
            if (manager != null) manager.cancel(CareReminderScheduler.NOTIFICATION_MEDICATION);
            CareReminderScheduler.scheduleNextMedicationMorning(context);
            return;
        }
        if (CareReminderScheduler.ACTION_MEDICATION_LATER.equals(action)) {
            if (manager != null) manager.cancel(CareReminderScheduler.NOTIFICATION_MEDICATION);
            CareReminderScheduler.scheduleMedicationLater(context, store.medicationRepeatMinutes());
            return;
        }
        if (!store.medicationEnabled() || store.medicationConfirmedToday()) {
            CareReminderScheduler.scheduleNextMedicationMorning(context);
            return;
        }
        Intent voice = new Intent(context, ProactiveCareActivity.class);
        voice.putExtra(ProactiveCareActivity.EXTRA_TYPE, ProactiveCareActivity.TYPE_MEDICATION);
        voice.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(voice);
        } catch (Exception ignored) {
        }

        CareReminderScheduler.createChannel(context);
        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("open_morning_care", true);
        PendingIntent pi = PendingIntent.getActivity(context, 4401, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent done = new Intent(context, MedicationReminderReceiver.class);
        done.setAction(CareReminderScheduler.ACTION_MEDICATION_DONE);
        PendingIntent donePi = PendingIntent.getBroadcast(context, 4403, done,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent later = new Intent(context, MedicationReminderReceiver.class);
        later.setAction(CareReminderScheduler.ACTION_MEDICATION_LATER);
        PendingIntent laterPi = PendingIntent.getBroadcast(context, 4404, later,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int icon = context.getResources().getIdentifier("ic_launcher", "drawable", context.getPackageName());
        Notification notification = new Notification.Builder(context, CareReminderScheduler.CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle("记得吃药哦")
                .setContentText(store.medicationName().length() > 0 ? store.medicationName() : "按您自己设定的早晨用药")
                .setContentIntent(pi)
                .addAction(icon, "吃过了", donePi)
                .addAction(icon, "晚点儿吃", laterPi)
                .setAutoCancel(true)
                .build();
        if (manager != null) {
            manager.notify(CareReminderScheduler.NOTIFICATION_MEDICATION, notification);
        }
    }
}
