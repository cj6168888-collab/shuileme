package com.gouxiong.sleep;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.gouxiong.sleep.data.SleepDatabase;
import com.gouxiong.sleep.util.PreferenceStore;
import com.gouxiong.sleep.util.WavFileWriter;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class SleepMonitorService extends Service implements SensorEventListener {
    public static final String ACTION_STOP = "com.gouxiong.sleep.STOP_MONITOR";
    private static final String GUARD_CHANNEL = "sleep_guard_silent_v2";
    private static final String ALARM_CHANNEL = "sleep_alarm_v2";
    private static final String MORNING_CHANNEL = "morning_care_quiet_v1";
    private static final int NOTIFICATION_ID = 1001;
    private static final int MORNING_NOTIFICATION_ID = 1002;
    private static final int AUDIO_SAMPLE_RATE = 8000;
    private static final int AUDIO_EVIDENCE_SECONDS = 12;
    private static final int BASELINE_READY_SAMPLES = 20;

    private PreferenceStore prefs;
    private SleepDatabase db;
    private Handler handler;
    private SensorManager sensorManager;
    private AudioRecord recorder;
    private Thread audioThread;
    private volatile boolean running;
    private volatile double lastRms;
    private volatile int lastPeak;
    private volatile double motionScore;
    private final Object audioBufferLock = new Object();
    private final short[] audioRing = new short[AUDIO_SAMPLE_RATE * AUDIO_EVIDENCE_SECONDS];
    private int audioRingIndex;
    private int audioRingCount;
    private long sessionStart;
    private long lastEventAt;
    private int sessionEvents;
    private int sessionHighRisk;
    private int sessionAutoCancel;
    private int audioReadCount;
    private long lastAudioStatePersistAt;
    private double audioBaselineRms = 120.0;
    private double motionBaseline = 2.0;
    private int baselineSamples;
    private long lastBaselinePersistAt;

    private final Runnable monitorTick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            evaluateSignals();
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new PreferenceStore(this);
        db = new SleepDatabase(this);
        handler = new Handler(Looper.getMainLooper());
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        sessionStart = System.currentTimeMillis();
        running = true;
        loadSignalBaseline();
        prefs.setMonitoring(true);
        prefs.markHeartbeat();
        resetAudioRing();
        Notification notification = buildGuardNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        startSensors();
        startAudio();
        handler.post(monitorTick);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        prefs.setMonitoring(false);
        handler.removeCallbacksAndMessages(null);
        stopAudio();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (sessionStart > 0) {
            String confidence = sessionEvents == 0 ? "medium" : "high";
            db.insertSummary(sessionStart, System.currentTimeMillis(), sessionEvents, sessionHighRisk, sessionAutoCancel, confidence);
        }
        persistSignalBaseline();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startSensors() {
        if (sensorManager == null) return;
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void startAudio() {
        prefs.recordSleepGuardAudioState(false, 0, 0, 0, "starting");
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            prefs.recordSleepGuardAudioState(false, 0, 0, 0, "未授权麦克风");
            db.insertEvent("权限不完整", "low", 0.5, "record", "未授权麦克风，守护可信度降低");
            return;
        }
        int min = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            prefs.recordSleepGuardAudioState(false, 0, 0, 0, "AudioRecord缓冲不可用");
            db.insertEvent("麦克风不可用", "low", 0.5, "record", "AudioRecord 缓冲不可用：" + min);
            return;
        }
        int bufferSize = Math.max(min, AUDIO_SAMPLE_RATE);
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                prefs.recordSleepGuardAudioState(false, 0, 0, 0, "AudioRecord未初始化");
                db.insertEvent("麦克风不可用", "low", 0.5, "record", "AudioRecord 未初始化");
                recorder.release();
                recorder = null;
                return;
            }
            recorder.startRecording();
        } catch (Exception ex) {
            prefs.recordSleepGuardAudioState(false, 0, 0, 0, ex.getMessage() == null ? "麦克风启动失败" : ex.getMessage());
            db.insertEvent("麦克风不可用", "low", 0.5, "record", "麦克风被占用或系统限制");
            return;
        }
        audioReadCount = 0;
        lastAudioStatePersistAt = 0L;
        prefs.recordSleepGuardAudioState(true, 0, 0, 0, "");

        audioThread = new Thread(() -> {
            short[] buffer = new short[bufferSize / 2];
            while (running && recorder != null) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read > 0) {
                    long sum = 0;
                    int peak = 0;
                    for (int i = 0; i < read; i++) {
                        int sample = buffer[i];
                        int v = sample == Short.MIN_VALUE ? Short.MAX_VALUE : Math.abs(sample);
                        sum += (long) v * (long) v;
                        if (v > peak) peak = v;
                    }
                    appendAudioSamples(buffer, read);
                    double rms = Math.sqrt(sum / Math.max(1, read));
                    lastRms = Math.max(rms, peak * 0.35);
                    lastPeak = peak;
                    audioReadCount++;
                    long now = System.currentTimeMillis();
                    if (audioReadCount <= 3 || now - lastAudioStatePersistAt > 3000L) {
                        prefs.recordSleepGuardAudioState(true, audioReadCount, lastRms, lastPeak, "");
                        prefs.recordSleepAudioReadState(audioReadCount, lastRms, lastPeak);
                        lastAudioStatePersistAt = now;
                    }
                } else if (read < 0) {
                    prefs.recordSleepGuardAudioState(true, audioReadCount, lastRms, lastPeak, "read=" + read);
                }
            }
        }, "GouXiongAudio");
        audioThread.start();
    }

    private void stopAudio() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (Exception ignored) {
            }
            recorder.release();
            recorder = null;
        }
        if (audioThread != null) {
            audioThread.interrupt();
            audioThread = null;
        }
    }

    private void evaluateSignals() {
        long now = System.currentTimeMillis();
        prefs.markHeartbeat();
        maybePromptMorningCare(now, motionScore);

        double rms = lastRms;
        int peak = lastPeak;
        double motion = motionScore;
        double mediumRmsThreshold = mediumRmsThreshold();
        double highRmsThreshold = highRmsThreshold();
        double mediumMotionThreshold = mediumMotionThreshold();
        double highMotionThreshold = highMotionThreshold();
        String signalState = signalState(rms, motion, mediumRmsThreshold, highRmsThreshold, mediumMotionThreshold, highMotionThreshold);
        db.insertSignalSample(now, (int) rms, peak, (int) motion, signalState);

        if (now - lastEventAt < 20000) {
            updateSignalBaselineIfCalm(now, rms, motion, mediumRmsThreshold, mediumMotionThreshold);
            return;
        }
        motionScore = motionScore * 0.45;

        if (rms > highRmsThreshold || motion > highMotionThreshold || rms > 18000 || motion > 30) {
            lastEventAt = now;
            sessionEvents++;
            sessionHighRisk++;
            String basis = "疑似高风险异常，需结合录音、动作和外部设备数据判断；声音强度 " + (int) rms
                    + " / 高风险阈值 " + (int) highRmsThreshold
                    + "，动作 " + (int) motion + " / 高风险阈值 " + oneDecimal(highMotionThreshold)
                    + "；" + baselineEvidenceText();
            insertSignalEvent(now, "高风险睡眠异常", "high", 0.86, "alarm", basis, rms, peak, motion);
            openAlarm(basis);
        } else if (rms > mediumRmsThreshold || motion > mediumMotionThreshold) {
            lastEventAt = now;
            sessionEvents++;
            sessionAutoCancel++;
            String basis = "疑似中风险异常，声音或动作短暂升高，已轻提醒并继续观察；声音强度 " + (int) rms
                    + " / 中风险阈值 " + (int) mediumRmsThreshold
                    + "，动作 " + (int) motion + " / 中风险阈值 " + oneDecimal(mediumMotionThreshold)
                    + "；" + baselineEvidenceText();
            insertSignalEvent(now, "中风险轻提醒", "medium", 0.68, "auto_cancel", basis, rms, peak, motion);
            vibrate(500);
        } else {
            updateSignalBaselineIfCalm(now, rms, motion, mediumRmsThreshold, mediumMotionThreshold);
        }
    }

    private String signalState(double rms, double motion, double mediumRmsThreshold, double highRmsThreshold,
                               double mediumMotionThreshold, double highMotionThreshold) {
        if (rms > highRmsThreshold || motion > highMotionThreshold || rms > 18000 || motion > 30) {
            return "high";
        }
        if (rms > mediumRmsThreshold || motion > mediumMotionThreshold) {
            return "medium";
        }
        return "calm";
    }

    private void loadSignalBaseline() {
        audioBaselineRms = clamp(prefs.signalAudioBaselineRms(), 20.0, 6500.0);
        motionBaseline = clamp(prefs.signalMotionBaseline(), 0.5, 8.0);
        baselineSamples = Math.max(0, prefs.signalBaselineSamples());
        if (baselineSamples < BASELINE_READY_SAMPLES && audioBaselineRms > 500.0) {
            audioBaselineRms = 120.0;
        }
    }

    private void updateSignalBaselineIfCalm(long now, double rms, double motion,
                                            double mediumRmsThreshold, double mediumMotionThreshold) {
        if (rms < 1 || rms > Math.min(6500.0, mediumRmsThreshold * 0.72)
                || motion > Math.min(10.0, mediumMotionThreshold * 0.72)) {
            return;
        }
        double audio = clamp(rms, 20.0, 6500.0);
        double movement = clamp(motion, 0.5, 8.0);
        if (baselineSamples <= 0) {
            audioBaselineRms = audio;
            motionBaseline = movement;
            baselineSamples = 1;
        } else {
            double alpha = baselineSamples < BASELINE_READY_SAMPLES ? 0.20 : 0.06;
            audioBaselineRms = audioBaselineRms * (1.0 - alpha) + audio * alpha;
            motionBaseline = motionBaseline * (1.0 - alpha) + movement * alpha;
            baselineSamples = Math.min(10000, baselineSamples + 1);
        }
        if (now - lastBaselinePersistAt > 30000 || baselineSamples <= 3) {
            persistSignalBaseline();
            lastBaselinePersistAt = now;
        }
    }

    private void persistSignalBaseline() {
        if (prefs != null) {
            prefs.setSignalBaseline(audioBaselineRms, motionBaseline, baselineSamples);
        }
    }

    private double mediumRmsThreshold() {
        return clamp(Math.max(180.0, audioBaselineRms * 5.0), 180.0, 1800.0);
    }

    private double highRmsThreshold() {
        return clamp(Math.max(900.0, audioBaselineRms * 12.0), 900.0, 6000.0);
    }

    private double mediumMotionThreshold() {
        return clamp(Math.max(13.0, motionBaseline * 3.5), 13.0, 18.0);
    }

    private double highMotionThreshold() {
        return clamp(Math.max(22.0, motionBaseline * 5.0), 22.0, 28.0);
    }

    private String baselineEvidenceText() {
        String state = baselineSamples >= BASELINE_READY_SAMPLES ? "个人基线已启用" : "个人基线学习中";
        return state + "，安静声音基线 " + (int) audioBaselineRms
                + "，动作基线 " + oneDecimal(motionBaseline)
                + "，样本 " + baselineSamples + " 个";
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String oneDecimal(double value) {
        return String.format(Locale.CHINA, "%.1f", value);
    }

    private void maybePromptMorningCare(long now, double motion) {
        if (sessionStart <= 0 || prefs.morningPromptedToday()) {
            return;
        }
        long duration = now - sessionStart;
        if (duration < 270L * 60L * 1000L) {
            return;
        }

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(now);
        int minuteOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE);
        if (minuteOfDay < 330 || minuteOfDay > 660) {
            return;
        }

        boolean likelyAwake = motion > 8 || duration > 420L * 60L * 1000L;
        if (!likelyAwake) {
            return;
        }

        showMorningCarePrompt();
        prefs.markMorningPromptedToday();
    }

    private void showMorningCarePrompt() {
        Intent open = new Intent(this, MainActivity.class);
        open.putExtra("open_morning_care", true);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(
                this, 12, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, MORNING_CHANNEL)
                .setSmallIcon(getResources().getIdentifier("ic_launcher", "drawable", getPackageName()))
                .setContentTitle("早安，醒了吗？")
                .setContentText("如果已经醒来，点这里进入早安护理；还想睡就不用管。")
                .setContentIntent(openPi)
                .setPriority(Notification.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(MORNING_NOTIFICATION_ID, notification);
        }
    }

    private void insertSignalEvent(long timestamp, String type, String risk, double confidence, String action,
                                   String basis, double rms, int peak, double motion) {
        String audioPath = saveAudioEvidenceClip(timestamp);
        int seconds = estimateAudioSeconds();
        String audioSummary;
        if (audioPath.length() > 0) {
            audioSummary = "手机麦克风：RMS " + (int) rms + "，峰值 " + peak + "；动态阈值：中风险>"
                    + (int) mediumRmsThreshold() + "，高风险>" + (int) highRmsThreshold() + "；已保存约 " + seconds + " 秒现场录音";
        } else if (!prefs.saveAudioClips()) {
            audioSummary = "手机麦克风：RMS " + (int) rms + "，峰值 " + peak + "；动态阈值：中风险>"
                    + (int) mediumRmsThreshold() + "，高风险>" + (int) highRmsThreshold() + "；用户已关闭异常录音片段";
        } else {
            audioSummary = "手机麦克风：RMS " + (int) rms + "，峰值 " + peak + "；动态阈值：中风险>"
                    + (int) mediumRmsThreshold() + "，高风险>" + (int) highRmsThreshold() + "；录音缓冲不足，未保存现场片段";
        }
        String motionSummary = "手机加速度计：动作评分 " + (int) motion + "；动态阈值：中风险>"
                + oneDecimal(mediumMotionThreshold()) + "，高风险>" + oneDecimal(highMotionThreshold());
        String deviceSummary = db.nearestDeviceEvidence(timestamp, prefs.externalDeviceEvidence());
        String evidenceLevel = audioPath.length() > 0 ? "phone_audio_motion" : "phone_metrics_only";
        db.insertEvent(type, risk, confidence, action, basis, audioPath, audioSummary, motionSummary, deviceSummary, evidenceLevel);
    }

    private void appendAudioSamples(short[] buffer, int read) {
        synchronized (audioBufferLock) {
            for (int i = 0; i < read; i++) {
                audioRing[audioRingIndex] = buffer[i];
                audioRingIndex = (audioRingIndex + 1) % audioRing.length;
                if (audioRingCount < audioRing.length) {
                    audioRingCount++;
                }
            }
        }
    }

    private void resetAudioRing() {
        synchronized (audioBufferLock) {
            audioRingIndex = 0;
            audioRingCount = 0;
        }
        lastRms = 0;
        lastPeak = 0;
        motionScore = 0;
    }

    private String saveAudioEvidenceClip(long timestamp) {
        if (!prefs.saveAudioClips()) {
            return "";
        }
        short[] samples = snapshotAudioRing();
        if (samples.length < AUDIO_SAMPLE_RATE) {
            return "";
        }
        File dir = new File(getFilesDir(), "event_audio");
        if (!dir.exists() && !dir.mkdirs()) {
            return "";
        }
        File file = new File(dir, "sleep-event-" + timestamp + ".wav");
        try {
            WavFileWriter.writePcm16Mono(file, samples, AUDIO_SAMPLE_RATE);
            return file.getAbsolutePath();
        } catch (IOException ex) {
            return "";
        }
    }

    private short[] snapshotAudioRing() {
        synchronized (audioBufferLock) {
            if (audioRingCount <= 0) {
                return new short[0];
            }
            short[] copy = new short[audioRingCount];
            int start = audioRingCount == audioRing.length ? audioRingIndex : 0;
            for (int i = 0; i < audioRingCount; i++) {
                copy[i] = audioRing[(start + i) % audioRing.length];
            }
            return copy;
        }
    }

    private int estimateAudioSeconds() {
        synchronized (audioBufferLock) {
            return Math.max(1, audioRingCount / AUDIO_SAMPLE_RATE);
        }
    }

    private void openAlarm(String reason) {
        Intent alarm = new Intent(this, AlarmActivity.class);
        alarm.putExtra("reason", reason);
        alarm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreen = PendingIntent.getActivity(
                this, 3001, alarm,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, ALARM_CHANNEL)
                .setSmallIcon(getResources().getIdentifier("ic_launcher", "drawable", getPackageName()))
                .setContentTitle("请醒一下")
                .setContentText("检测到高风险睡眠异常")
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreen, true)
                .setAutoCancel(true)
                .build();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(2002, notification);

        try {
            startActivity(alarm);
        } catch (Exception ignored) {
        }
    }

    private Notification buildGuardNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 10, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, SleepMonitorService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 11, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, GUARD_CHANNEL)
                .setSmallIcon(getResources().getIdentifier("ic_launcher", "drawable", getPackageName()))
                .setContentTitle("睡了么正在守护")
                .setContentText("静音守护中，不会发出运行提示音")
                .setContentIntent(openPi)
                .addAction(0, "停止", stopPi)
                .setPriority(Notification.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_STATUS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            NotificationChannel guard = new NotificationChannel(GUARD_CHANNEL, "静音睡眠守护", NotificationManager.IMPORTANCE_LOW);
            guard.setDescription("守护运行状态，只静音显示，不发提示音、不震动");
            guard.setSound(null, null);
            guard.enableVibration(false);
            guard.setShowBadge(false);
            manager.createNotificationChannel(guard);

            NotificationChannel alarm = new NotificationChannel(ALARM_CHANNEL, "高风险强唤醒", NotificationManager.IMPORTANCE_HIGH);
            alarm.setDescription("仅在检测到高风险睡眠异常时用于全屏唤醒");
            manager.createNotificationChannel(alarm);

            NotificationChannel morning = new NotificationChannel(MORNING_CHANNEL, "早安护理静音提醒", NotificationManager.IMPORTANCE_LOW);
            morning.setDescription("清晨可能醒来时的低打扰确认，不发提示音、不震动");
            morning.setSound(null, null);
            morning.enableVibration(false);
            morning.setShowBadge(false);
            manager.createNotificationChannel(morning);
        }
    }

    private void vibrate(long millis) {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(millis);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double x = event.values[0];
            double y = event.values[1];
            double z = event.values[2];
            double magnitude = Math.sqrt(x * x + y * y + z * z);
            double delta = Math.abs(magnitude - SensorManager.GRAVITY_EARTH);
            motionScore = Math.max(motionScore, delta * 10.0);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
