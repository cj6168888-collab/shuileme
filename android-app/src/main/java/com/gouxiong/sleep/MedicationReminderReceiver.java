package com.gouxiong.sleep;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.gouxiong.sleep.util.PreferenceStore;

public class MedicationReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "morning_care_v1";

    @Override
    public void onReceive(Context context, Intent intent) {
        PreferenceStore store = new PreferenceStore(context);
        if (!store.medicationEnabled() || store.medicationConfirmedToday()) {
            return;
        }
        createChannel(context);
        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("open_morning_care", true);
        PendingIntent pi = PendingIntent.getActivity(context, 4401, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(context.getResources().getIdentifier("ic_launcher", "drawable", context.getPackageName()))
                .setContentTitle("早安，记得确认用药")
                .setContentText(store.medicationName() + "：已吃药就点开确认，没吃可以稍后提醒")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(4402, notification);
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(CHANNEL, "晨间护理提醒", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("喝水、吃药和晨间护理提醒");
            manager.createNotificationChannel(channel);
        }
    }
}
