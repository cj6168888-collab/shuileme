package com.gouxiong.sleep;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.gouxiong.sleep.util.PreferenceStore;

public class HydrationReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (CareReminderScheduler.ACTION_HYDRATION_OK.equals(action)) {
            new PreferenceStore(context).markHydrationAcknowledgedNow();
            if (manager != null) manager.cancel(CareReminderScheduler.NOTIFICATION_HYDRATION);
            CareReminderScheduler.scheduleNextHydration(context);
            return;
        }
        if (CareReminderScheduler.ACTION_HYDRATION_LATER.equals(action)) {
            if (manager != null) manager.cancel(CareReminderScheduler.NOTIFICATION_HYDRATION);
            CareReminderScheduler.scheduleHydrationLater(context, 30);
            return;
        }

        if (!CareReminderScheduler.isHydrationWindowNow(context)) {
            CareReminderScheduler.scheduleNextHydration(context);
            return;
        }

        CareReminderScheduler.createChannel(context);
        Intent open = new Intent(context, ProactiveCareActivity.class);
        open.putExtra(ProactiveCareActivity.EXTRA_TYPE, ProactiveCareActivity.TYPE_HYDRATION);
        PendingIntent openPi = PendingIntent.getActivity(context, 4501, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent ok = new Intent(context, HydrationReminderReceiver.class);
        ok.setAction(CareReminderScheduler.ACTION_HYDRATION_OK);
        PendingIntent okPi = PendingIntent.getBroadcast(context, 4503, ok,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent later = new Intent(context, HydrationReminderReceiver.class);
        later.setAction(CareReminderScheduler.ACTION_HYDRATION_LATER);
        PendingIntent laterPi = PendingIntent.getBroadcast(context, 4504, later,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int icon = context.getResources().getIdentifier("ic_launcher", "drawable", context.getPackageName());
        Notification notification = new Notification.Builder(context, CareReminderScheduler.CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle("小助手正在叫您喝水")
                .setContentText("点“喝了”后提醒会消失，等下一次再提醒。")
                .setContentIntent(openPi)
                .addAction(icon, "喝了", okPi)
                .addAction(icon, "等会儿", laterPi)
                .setAutoCancel(true)
                .build();
        if (manager != null) {
            manager.notify(CareReminderScheduler.NOTIFICATION_HYDRATION, notification);
        }
        CareReminderScheduler.scheduleNextHydration(context);
    }
}
