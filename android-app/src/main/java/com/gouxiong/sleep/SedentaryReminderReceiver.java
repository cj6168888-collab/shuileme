package com.gouxiong.sleep;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.gouxiong.sleep.util.PreferenceStore;

public class SedentaryReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        PreferenceStore store = new PreferenceStore(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (CareReminderScheduler.ACTION_SEDENTARY_OK.equals(action)) {
            store.markSedentaryAcknowledgedNow();
            if (manager != null) manager.cancel(CareReminderScheduler.NOTIFICATION_SEDENTARY);
            CareReminderScheduler.scheduleNextSedentary(context);
            return;
        }
        if (CareReminderScheduler.ACTION_SEDENTARY_LATER.equals(action)) {
            if (manager != null) manager.cancel(CareReminderScheduler.NOTIFICATION_SEDENTARY);
            CareReminderScheduler.scheduleSedentaryLater(context, 30);
            return;
        }

        if (!CareReminderScheduler.isSedentaryWindowNow(context)) {
            CareReminderScheduler.scheduleNextSedentary(context);
            return;
        }

        CareReminderScheduler.createChannel(context);
        Intent ok = new Intent(context, SedentaryReminderReceiver.class);
        ok.setAction(CareReminderScheduler.ACTION_SEDENTARY_OK);
        PendingIntent okPi = PendingIntent.getBroadcast(context, 4553, ok,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent later = new Intent(context, SedentaryReminderReceiver.class);
        later.setAction(CareReminderScheduler.ACTION_SEDENTARY_LATER);
        PendingIntent laterPi = PendingIntent.getBroadcast(context, 4554, later,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int icon = context.getResources().getIdentifier("ic_launcher", "drawable", context.getPackageName());
        Notification notification = new Notification.Builder(context, CareReminderScheduler.CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle("起来活动一下")
                .setContentText("久坐一会儿了，站起来伸展、走几步，舒服一点再继续。")
                .addAction(icon, "动过了", okPi)
                .addAction(icon, "等会儿", laterPi)
                .setAutoCancel(true)
                .build();
        if (manager != null) {
            manager.notify(CareReminderScheduler.NOTIFICATION_SEDENTARY, notification);
        }
        CareReminderScheduler.scheduleNextSedentary(context);
    }
}
