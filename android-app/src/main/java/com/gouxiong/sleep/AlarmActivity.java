package com.gouxiong.sleep;

import android.Manifest;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.content.Intent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gouxiong.sleep.util.EmergencyNotifier;
import com.gouxiong.sleep.util.CompanionAssistant;
import com.gouxiong.sleep.util.PreferenceStore;
import com.gouxiong.sleep.util.Theme;

import java.io.File;
import java.util.ArrayList;

public class AlarmActivity extends Activity {
    private Handler handler;
    private Ringtone ringtone;
    private MediaPlayer customPlayer;
    private Vibrator vibrator;
    private SpeechRecognizer speechRecognizer;
    private PowerManager.WakeLock wakeLock;
    private TextView countdown;
    private int secondsLeft = 75;
    private boolean confirmed;
    private boolean emergencySent;
    private boolean drillMode;
    private String reason;
    private String assistantRole;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (confirmed) return;
            secondsLeft--;
            if (drillMode && secondsLeft <= 15 && !emergencySent) {
                countdown.setText("演练模式：" + secondsLeft + " 秒后只显示演练结果，不会通知联系人");
            } else if (secondsLeft <= 15 && !emergencySent) {
                countdown.setText("还没有确认，" + secondsLeft + " 秒后通知紧急联系人");
            } else {
                countdown.setText(CompanionAssistant.confirmLine(assistantRole) + "\n" + secondsLeft + " 秒后升级提醒");
            }
            if (secondsLeft <= 0 && !emergencySent) {
                emergencySent = true;
                if (drillMode) {
                    countdown.setText("演练完成：没有通知紧急联系人。请按“我没事”结束。");
                } else {
                    countdown.setText("已尝试通知紧急联系人，仍在本机强唤醒");
                    EmergencyNotifier.trigger(AlarmActivity.this, reason);
                }
            }
            if (!drillMode || !emergencySent) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        reason = getIntent().getStringExtra("reason");
        if (reason == null) reason = "高风险睡眠异常";
        drillMode = getIntent().getBooleanExtra("drill_mode", false);
        assistantRole = new PreferenceStore(this).companionRole();
        handler = new Handler(Looper.getMainLooper());

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        acquireWakeLock();
        buildUi();
        startAlarmSound();
        startSpeechStop();
        handler.post(tick);
    }

    private void buildUi() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Theme.dp(this, 24), safeTopPadding(44), Theme.dp(this, 24), Theme.dp(this, 24));
        box.setBackgroundColor(Theme.WARM_WHITE);

        ImageView alarm = designImage("ui_alarm_scene");
        box.addView(alarm, new LinearLayout.LayoutParams(-1, Theme.dp(this, 280)));
        addSpace(box, 10);

        TextView sub = Theme.text(this, "检测到高风险睡眠异常", 22, Theme.RED, Typeface.BOLD);
        sub.setGravity(Gravity.CENTER);
        box.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 6);

        TextView assistant = Theme.text(this, assistantRole + "： " + CompanionAssistant.wakeLine(assistantRole), 19, Theme.MUTED, Typeface.BOLD);
        assistant.setGravity(Gravity.CENTER);
        box.addView(assistant, new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 6);

        TextView basis = Theme.text(this, reason, 16, Theme.MUTED, Typeface.NORMAL);
        basis.setGravity(Gravity.CENTER);
        box.addView(basis, new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 8);

        if (drillMode) {
            TextView drill = Theme.text(this, "演练模式：不打电话或发短信。", 16, Theme.ORANGE, Typeface.BOLD);
            drill.setGravity(Gravity.CENTER);
            box.addView(drill, new LinearLayout.LayoutParams(-1, -2));
            addSpace(box, 8);
        }

        countdown = Theme.text(this, CompanionAssistant.confirmLine(assistantRole), 20, Theme.TEXT, Typeface.BOLD);
        countdown.setGravity(Gravity.CENTER);
        box.addView(countdown, new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 14);

        Button ok = Theme.button(this, "❤  我醒了", Theme.RED);
        ok.setTextSize(28);
        ok.setMinHeight(Theme.dp(this, 88));
        ok.setOnClickListener(v -> confirmSafe());
        box.addView(ok, new LinearLayout.LayoutParams(-1, -2));
        addSpace(box, 10);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button pause = Theme.softButton(this, "🛡  我没事", Theme.RED);
        pause.setTextSize(18);
        pause.setMinHeight(Theme.dp(this, 70));
        pause.setOnClickListener(v -> confirmSafe());
        Button end = Theme.softButton(this, "结束本晚", Theme.ORANGE);
        end.setTextSize(18);
        end.setMinHeight(Theme.dp(this, 70));
        end.setOnClickListener(v -> {
            stopService(new Intent(this, SleepMonitorService.class));
            confirmSafe();
        });
        row.addView(pause, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(end, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));

        setContentView(box);
    }

    private ImageView designImage(String drawableName) {
        ImageView image = new ImageView(this);
        int id = getResources().getIdentifier(drawableName, "drawable", getPackageName());
        if (id != 0) image.setImageResource(id);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return image;
    }

    private int safeTopPadding(int baseDp) {
        return Theme.dp(this, baseDp) + statusBarHeight();
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            return getResources().getDimensionPixelSize(id);
        }
        return Theme.dp(this, 24);
    }

    private void startAlarmSound() {
        if (startCustomAlarmSound()) {
            startVibration();
            return;
        }
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(this, uri);
            if (Build.VERSION.SDK_INT >= 21) {
                ringtone.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }
            ringtone.play();
        } catch (Exception ignored) {
        }
        startVibration();
    }

    private boolean startCustomAlarmSound() {
        PreferenceStore store = new PreferenceStore(this);
        String source = store.alarmSource();
        try {
            customPlayer = new MediaPlayer();
            if ("voice".equals(source) && store.voicePath().length() > 0) {
                File file = new File(store.voicePath());
                if (!file.exists() || file.length() == 0) return false;
                customPlayer.setDataSource(store.voicePath());
            } else if ("song".equals(source) && store.songUri().length() > 0) {
                customPlayer.setDataSource(this, Uri.parse(store.songUri()));
            } else {
                return false;
            }
            if (Build.VERSION.SDK_INT >= 21) {
                customPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            }
            customPlayer.setLooping(true);
            customPlayer.prepare();
            customPlayer.start();
            return true;
        } catch (Exception ex) {
            if (customPlayer != null) {
                customPlayer.release();
                customPlayer = null;
            }
            return false;
        }
    }

    private void startVibration() {
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) {
            long[] pattern = new long[]{0, 600, 250, 600, 250, 900};
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private void startSpeechStop() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                if (!confirmed) handler.postDelayed(() -> startListening(), 1200);
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> words = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (words != null) {
                    for (String word : words) {
                        if (word.contains("我没事") || word.contains("停止") || word.contains("不用") || word.contains("没事")) {
                            confirmSafe();
                            return;
                        }
                    }
                }
                if (!confirmed) startListening();
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
        startListening();
    }

    private void startListening() {
        if (speechRecognizer == null || confirmed) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        try {
            speechRecognizer.startListening(intent);
        } catch (Exception ignored) {
        }
    }

    private void confirmSafe() {
        confirmed = true;
        stopAlarm();
        Toast.makeText(this, "已确认，继续守护", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void stopAlarm() {
        handler.removeCallbacksAndMessages(null);
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        if (customPlayer != null) {
            try {
                customPlayer.stop();
            } catch (Exception ignored) {
            }
            customPlayer.release();
            customPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "GouXiongSleep:alarm");
            wakeLock.acquire(2 * 60 * 1000L);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_POWER) {
            confirmSafe();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }

    private void addSpace(LinearLayout parent, int dp) {
        android.view.View space = new android.view.View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, Theme.dp(this, dp)));
    }
}
