package com.gouxiong.sleep.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.telephony.SmsManager;
import android.widget.Toast;

public class EmergencyNotifier {
    public static void trigger(Activity activity, String reason) {
        PreferenceStore store = new PreferenceStore(activity);
        String phone = store.emergencyPhone();
        if (!store.emergencyEnabled() || phone.length() == 0) {
            Toast.makeText(activity, "未设置紧急联系人，继续本地强唤醒", Toast.LENGTH_LONG).show();
            return;
        }

        String message = "狗熊睡眠提醒：我触发高风险睡眠异常唤醒，超过 60 秒未确认。请尝试联系我。原因：" + reason;
        if (store.emergencySms()) {
            sendSms(activity, phone, message);
        }
        if (store.emergencyCall()) {
            call(activity, phone);
        }
    }

    private static void sendSms(Context context, String phone, String message) {
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                SmsManager.getDefault().sendTextMessage(phone, null, message, null, null);
                Toast.makeText(context, "已尝试发送紧急短信", Toast.LENGTH_SHORT).show();
            } catch (Exception ex) {
                Toast.makeText(context, "短信发送失败，已记录为本地失败", Toast.LENGTH_LONG).show();
            }
        } else {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)));
            intent.putExtra("sms_body", message);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    private static void call(Context context, String phone) {
        Intent intent;
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(phone)));
        } else {
            intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
