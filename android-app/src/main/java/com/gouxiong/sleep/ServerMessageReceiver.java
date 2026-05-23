package com.gouxiong.sleep;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import com.gouxiong.sleep.util.PreferenceStore;
import com.gouxiong.sleep.util.ServerApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

public class ServerMessageReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "companion_messages_voice_v2";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !CareReminderScheduler.ACTION_SERVER_MESSAGE_POLL.equals(intent.getAction())) {
            return;
        }
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                pollServerMessages(context);
            } finally {
                CareReminderScheduler.scheduleNextServerMessagePoll(context);
                result.finish();
            }
        }, "GouXiongServerMessagePoll").start();
    }

    private void pollServerMessages(Context context) {
        PreferenceStore prefs = new PreferenceStore(context);
        if (!prefs.serverRegistered()) {
            return;
        }
        try {
            JSONArray messages = ServerApiClient.pendingMessages(prefs.serverBaseUrl(), prefs.serverAuthToken());
            if (messages.length() == 0) {
                return;
            }
            JSONObject first = messages.getJSONObject(0);
            int priority = first.optInt("priority", 1);
            boolean sleeping = prefs.isMonitoring();
            boolean urgent = priority >= 4;
            if (!sleeping || urgent) {
                startVoiceReader(context, first);
                showMessageNotification(context, first, messages.length(), true);
            } else {
                showMessageNotification(context, first, messages.length(), false);
            }
        } catch (Exception ignored) {
        }
    }

    private void startVoiceReader(Context context, JSONObject message) {
        try {
            Intent voice = voiceIntent(context, message);
            voice.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(voice);
        } catch (Exception ignored) {
        }
    }

    private Intent voiceIntent(Context context, JSONObject message) {
        Intent open = new Intent(context, ProactiveCareActivity.class);
        open.putExtra(ProactiveCareActivity.EXTRA_TYPE, ProactiveCareActivity.TYPE_SERVER_MESSAGE);
        open.putExtra(ProactiveCareActivity.EXTRA_MESSAGE, message.optString("body", "我有一条话想慢慢说给您听。"));
        open.putExtra(ProactiveCareActivity.EXTRA_SERVER_MESSAGE_ID, message.optInt("id", 0));
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return open;
    }

    private void showMessageNotification(Context context, JSONObject message, int count, boolean fullScreenFallback) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        createChannel(context, manager);
        Intent open = voiceIntent(context, message);
        PendingIntent openPi = PendingIntent.getActivity(context, 4603, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = message.optString("title", "小助手提醒");
        String body = message.optString("body", "我有一条话想慢慢说给您听。");
        if (count > 1) {
            title = "小助手有 " + count + " 条提醒";
        }
        int icon = context.getResources().getIdentifier("ic_launcher", "drawable", context.getPackageName());
        Notification.Builder builder = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setPriority(fullScreenFallback ? Notification.PRIORITY_HIGH : Notification.PRIORITY_DEFAULT);
        if (fullScreenFallback) {
            builder.setFullScreenIntent(openPi, true);
        }
        Notification notification = builder.build();
        manager.notify(CareReminderScheduler.NOTIFICATION_SERVER_MESSAGE, notification);
    }

    private void createChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL, "小助手语音消息", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("后台下发的小助手待朗读消息；非睡眠守护时会尝试直接读给用户听");
        channel.setSound((Uri) null, (AudioAttributes) null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }
}
