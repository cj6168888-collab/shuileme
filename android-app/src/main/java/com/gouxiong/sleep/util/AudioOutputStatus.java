package com.gouxiong.sleep.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;

public final class AudioOutputStatus {
    private AudioOutputStatus() {
    }

    public static Snapshot inspect(Context context) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        Snapshot snapshot = new Snapshot();
        snapshot.alarmVolume = manager == null ? 0 : manager.getStreamVolume(AudioManager.STREAM_ALARM);
        snapshot.maxAlarmVolume = manager == null ? 0 : manager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        snapshot.bluetoothPermissionMissing = bluetoothPermissionMissing(context);
        if (manager == null || Build.VERSION.SDK_INT < 23) {
            snapshot.routeType = "unknown";
            snapshot.routeName = "系统音频";
            return snapshot;
        }

        AudioDeviceInfo[] outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        AudioDeviceInfo fallback = null;
        for (AudioDeviceInfo device : outputs) {
            if (device == null) continue;
            int type = device.getType();
            if (isBluetoothOutput(type)) {
                snapshot.routeType = "bluetooth";
                snapshot.routeName = deviceName(device, "蓝牙音频设备");
                return snapshot;
            }
            if (isWiredOutput(type) && fallback == null) {
                fallback = device;
            } else if (isSpeakerOutput(type) && fallback == null) {
                fallback = device;
            }
        }
        if (fallback != null) {
            snapshot.routeType = isWiredOutput(fallback.getType()) ? "wired" : "speaker";
            snapshot.routeName = deviceName(fallback, isWiredOutput(fallback.getType()) ? "有线耳机/外放" : "手机扬声器");
        } else {
            snapshot.routeType = "speaker";
            snapshot.routeName = "手机扬声器";
        }
        return snapshot;
    }

    public static void playAlarmTestTone() {
        ToneGenerator generator = new ToneGenerator(AudioManager.STREAM_ALARM, 90);
        generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700);
        new Thread(() -> {
            try {
                Thread.sleep(900);
            } catch (InterruptedException ignored) {
            }
            generator.release();
        }, "GouXiongAudioRouteTone").start();
    }

    private static boolean bluetoothPermissionMissing(Context context) {
        return Build.VERSION.SDK_INT >= 31
                && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isBluetoothOutput(int type) {
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= 28 && type == AudioDeviceInfo.TYPE_HEARING_AID) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= 31) {
            return type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER;
        }
        return false;
    }

    private static boolean isWiredOutput(int type) {
        return type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || type == AudioDeviceInfo.TYPE_USB_HEADSET
                || type == AudioDeviceInfo.TYPE_USB_DEVICE;
    }

    private static boolean isSpeakerOutput(int type) {
        return type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
    }

    private static String deviceName(AudioDeviceInfo device, String fallback) {
        CharSequence name = device.getProductName();
        if (name == null || name.toString().trim().length() == 0) {
            return fallback;
        }
        return name.toString().trim();
    }

    public static final class Snapshot {
        private String routeType = "speaker";
        private String routeName = "手机扬声器";
        private int alarmVolume;
        private int maxAlarmVolume;
        private boolean bluetoothPermissionMissing;

        public boolean isBluetooth() {
            return "bluetooth".equals(routeType);
        }

        public boolean isWired() {
            return "wired".equals(routeType);
        }

        public boolean bluetoothPermissionMissing() {
            return bluetoothPermissionMissing;
        }

        public String routeName() {
            return routeName;
        }

        public String shortLabel() {
            if (isBluetooth()) {
                return "蓝牙音箱：" + routeName;
            }
            if (isWired()) {
                return "当前输出：" + routeName;
            }
            return "当前输出：手机扬声器";
        }

        public String routeLine() {
            StringBuilder b = new StringBuilder();
            if (isBluetooth()) {
                b.append("✓ 已检测到蓝牙音频输出：").append(routeName).append("\n");
                b.append("强唤醒声音会走系统当前音频路由；如果夜间断连，会回退手机扬声器和震动。\n");
            } else if (isWired()) {
                b.append("! 当前输出是 ").append(routeName).append("，不等同于蓝牙音箱。\n");
                b.append("如果老人睡着后耳机脱落，强唤醒可能不够响，建议改用蓝牙音箱或手机外放。\n");
            } else {
                b.append("! 未检测到蓝牙音箱，强唤醒将使用手机扬声器和震动。\n");
                b.append("需要更大声音时，请先连接蓝牙音箱，再回到本页刷新检测。\n");
            }
            if (bluetoothPermissionMissing) {
                b.append("! 蓝牙连接权限未授权，部分设备名称可能无法读取。\n");
            }
            b.append(volumeLine());
            return b.toString();
        }

        public String preSleepLine() {
            if (isBluetooth()) {
                return "✓ 唤醒输出：" + routeName + "（蓝牙）";
            }
            if (isWired()) {
                return "! 唤醒输出：" + routeName + "，建议确认老人能听到";
            }
            return "! 唤醒输出：手机扬声器，未检测到蓝牙音箱";
        }

        public String alarmLine() {
            if (isBluetooth()) {
                return "声音会通过当前蓝牙输出播放：" + routeName;
            }
            if (isWired()) {
                return "当前输出是 " + routeName + "；如果听不到，请拔下耳机或打开外放。";
            }
            return "当前未检测到蓝牙音箱，正在使用手机扬声器和震动。";
        }

        private String volumeLine() {
            if (maxAlarmVolume <= 0) {
                return "闹钟音量：系统未返回。";
            }
            int percent = Math.round(alarmVolume * 100f / maxAlarmVolume);
            return "闹钟音量：" + alarmVolume + "/" + maxAlarmVolume + "（约 " + percent + "%）。";
        }
    }
}
