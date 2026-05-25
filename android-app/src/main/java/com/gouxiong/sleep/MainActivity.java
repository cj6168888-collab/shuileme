package com.gouxiong.sleep;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;

import com.gouxiong.sleep.data.SleepDatabase;
import com.gouxiong.sleep.avatar.AvatarCommand;
import com.gouxiong.sleep.avatar.AvatarState;
import com.gouxiong.sleep.avatar.AvatarView;
import com.gouxiong.sleep.live.LiveCompanionSession;
import com.gouxiong.sleep.live.LivePcmPlayer;
import com.gouxiong.sleep.live.LivePcmRecorder;
import com.gouxiong.sleep.model.DeviceReading;
import com.gouxiong.sleep.model.SleepEvent;
import com.gouxiong.sleep.util.AudioOutputStatus;
import com.gouxiong.sleep.util.CompanionAssistant;
import com.gouxiong.sleep.util.PreferenceStore;
import com.gouxiong.sleep.util.ServerApiClient;
import com.gouxiong.sleep.util.SleepSoundPlayer;
import com.gouxiong.sleep.util.Theme;
import com.gouxiong.sleep.util.WavFileWriter;
import com.gouxiong.sleep.util.XiaozhiVoiceProfile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;

public class MainActivity extends Activity {
    private static final String TAG = "GouXiongSleep";
    public static final String ACTION_OPEN_SERVER_MESSAGES = "com.gouxiong.sleep.action.OPEN_SERVER_MESSAGES";
    public static final String ACTION_OPEN_COMPANION = "com.gouxiong.sleep.action.OPEN_COMPANION";
    public static final String ACTION_DEBUG_HYDRATION_CARE = "com.gouxiong.sleep.action.DEBUG_HYDRATION_CARE";
    public static final String ACTION_DEBUG_SERVER_MESSAGE_CARE = "com.gouxiong.sleep.action.DEBUG_SERVER_MESSAGE_CARE";
    public static final String ACTION_DEBUG_SERVER_MESSAGE_POLL = "com.gouxiong.sleep.action.DEBUG_SERVER_MESSAGE_POLL";
    public static final String ACTION_DEBUG_NIGHTMARE_WAKE = "com.gouxiong.sleep.action.DEBUG_NIGHTMARE_WAKE";
    public static final String ACTION_DEBUG_VOICE_TEXT = "com.gouxiong.sleep.action.DEBUG_VOICE_TEXT";
    public static final String ACTION_DEBUG_SLEEP_CHECK = "com.gouxiong.sleep.action.DEBUG_SLEEP_CHECK";
    public static final String ACTION_DEBUG_SLEEP_CHECK_TIMEOUT = "com.gouxiong.sleep.action.DEBUG_SLEEP_CHECK_TIMEOUT";
    public static final String ACTION_DEBUG_LIVE_ABORT = "com.gouxiong.sleep.action.DEBUG_LIVE_ABORT";
    public static final String ACTION_DEBUG_LIVE_BARGE_IN = "com.gouxiong.sleep.action.DEBUG_LIVE_BARGE_IN";
    public static final String ACTION_DEBUG_CAMERA_GLANCE = "com.gouxiong.sleep.action.DEBUG_CAMERA_GLANCE";
    public static final String ACTION_DEBUG_MICROPHONE_VERIFY = "com.gouxiong.sleep.action.DEBUG_MICROPHONE_VERIFY";
    public static final String ACTION_DEBUG_START_SLEEP_GUARD = "com.gouxiong.sleep.action.DEBUG_START_SLEEP_GUARD";
    public static final String ACTION_DEBUG_LIVE2D_PREVIEW = "com.gouxiong.sleep.action.DEBUG_LIVE2D_PREVIEW";
    private static final int REQUEST_PICK_AUDIO = 2101;
    private static final int REQUEST_CAPTURE_SCENE = 2201;
    private static final int REQUEST_CAMERA_VISION = 87;
    private static final int REQUEST_CAMERA_AUTO_VISION = 88;
    private static final int REQUEST_VOICE_CHAT = 89;
    private static final int REQUEST_CAMERA_GLANCE = 90;
    private static final int REQUEST_MICROPHONE_PROACTIVE = 91;
    private static final int REQUEST_REQUIRED_PERMISSIONS = 92;
    private static final int OWNER_PROFILE_STEP_COUNT = 6;
    private static final long AUTO_VISION_MIN_INTERVAL_MS = 2L * 60L * 1000L;
    private static final int AUTO_VISION_MAX_SIDE = 960;
    private static final int VISION_MAX_JPEG_BYTES = 220 * 1024;
    private static final int DETAIL_VISION_MAX_SIDE = 1800;
    private static final int DETAIL_VISION_MAX_JPEG_BYTES = 900 * 1024;
    private static final int AUDIO_ANALYSIS_MAX_BYTES = 420 * 1024;
    private static final float LIVE_BARGE_IN_RMS_THRESHOLD = 0.040f;
    private static final float LIVE_BARGE_IN_INITIAL_NOISE_FLOOR_RMS = 0.012f;
    private static final float LIVE_BARGE_IN_NOISE_FLOOR_ALPHA = 0.08f;
    private static final float LIVE_BARGE_IN_NOISE_MULTIPLIER = 3.2f;
    private static final float LIVE_BARGE_IN_EXIT_MULTIPLIER = 1.8f;
    private static final float LIVE_BARGE_IN_MAX_RMS_THRESHOLD = 0.140f;
    private static final int LIVE_BARGE_IN_MIN_SPEECH_MS = 240;
    private static final int LIVE_BARGE_IN_CONSECUTIVE_FRAMES = Math.max(3,
            LIVE_BARGE_IN_MIN_SPEECH_MS / LivePcmRecorder.FRAME_DURATION_MS);
    private static final long LIVE_BARGE_IN_COOLDOWN_MS = 1800L;
    private static final long LIVE_MODEL_AUDIO_ACTIVE_WINDOW_MS = 1600L;
    private static final int LIVE_DOT_ROW_HEIGHT_DP = 32;
    private static final int LIVE_DOT_WIDTH_DP = 16;
    private static final int LIVE_DOT_MAX_HEIGHT_DP = 22;
    private static final int LIVE_VOICE_METER_HEIGHT_DP = 58;
    private static final int LIVE_VOICE_METER_BAR_WIDTH_DP = 5;
    private static final int LIVE_VOICE_METER_BAR_HEIGHT_DP = 42;

    private PreferenceStore prefs;
    private SleepDatabase db;
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout navBar;
    private boolean pendingStartAfterPermission;
    private String activeScreen = "";
    private boolean pendingRefreshAfterBatterySettings;
    private MediaRecorder voiceRecorder;
    private File voiceFile;
    private MediaPlayer previewPlayer;
    private SleepSoundPlayer sleepSoundPlayer;
    private Ringtone previewRingtone;
    private TextToSpeech assistantTts;
    private boolean assistantTtsReady;
    private boolean assistantTtsListenerSet;
    private boolean assistantTtsProfileApplied;
    private String assistantTtsAppliedRole = "";
    private boolean assistantSpeaking;
    private SpeechRecognizer voiceRecognizer;
    private boolean realtimeVoiceEnabled;
    private boolean voiceListening;
    private boolean voiceRestartScheduled;
    private boolean sleepCheckPending;
    private long lastCompanionActiveAtMs;
    private String pendingSleepContinuationText = "";
    private boolean microphoneProactiveRequestedThisSession;
    private boolean requiredPermissionsRequestedThisSession;
    private boolean pendingStartLiveAfterRequiredPermissions;
    private int voiceConversationSerial;
    private int voicePageSerial;
    private int voiceListenSerial;
    private long voiceListenStartedAtMs;
    private long lastVoiceCallbackAtMs;
    private long lastVoiceSoundAtMs;
    private float voiceInputLevel;
    private LiveCompanionSession liveCompanionSession;
    private LivePcmPlayer livePcmPlayer;
    private LivePcmRecorder livePcmRecorder;
    private LivePcmRecorder microphoneProbeRecorder;
    private int microphoneProbeSerial;
    private volatile boolean liveCompanionConnected;
    private volatile boolean liveCompanionConnecting;
    private boolean liveAudioStreaming;
    private int liveAudioFrameCount;
    private long lastLiveAudioFrameAtMs;
    private int liveModelAudioFrameCount;
    private long lastLiveModelAudioFrameAtMs;
    private int liveAsrFallbackSerial;
    private long lastLiveSttHandledAtMs;
    private String lastLiveSttHandledText = "";
    private boolean handlingLiveAsrTranscript;
    private int liveBargeInCandidateFrames;
    private long lastLiveBargeInAtMs;
    private float liveBargeInNoiseFloorRms = LIVE_BARGE_IN_INITIAL_NOISE_FLOOR_RMS;
    private float lastLiveBargeInThresholdRms = LIVE_BARGE_IN_RMS_THRESHOLD;
    private final StringBuilder liveStreamingReplyBuffer = new StringBuilder();
    private final StringBuilder liveStreamingSpeechBuffer = new StringBuilder();
    private int liveStreamingSerial;
    private int liveStreamingTtsChunkCount;
    private long lastLiveStreamingUiUpdateAtMs;
    private String pendingLiveCompanionPrompt = "";
    private String pendingLiveCompanionCleanText = "";
    private int pendingLiveCompanionSerial;
    private int liveCompanionReplySerial;
    private boolean firstMeetingPromptedThisSession;
    private TextView voiceStatusLabel;
    private TextView liveStageStatusLabel;
    private TextView liveStageSpeechLabel;
    private TextView liveDigitalHumanLabel;
    private AvatarView liveStageAvatar;
    private String liveStageMood = "listening";
    private int liveStageAnimationSerial;
    private int avatarMouthSerial;
    private int livePcmMouthSerial;
    private String pendingVisionTask = "";
    private Bitmap latestVisionSnapshot;
    private Uri pendingVisionImageUri;
    private HandlerThread visionThread;
    private Handler visionHandler;
    private CameraDevice autoVisionCamera;
    private CameraCaptureSession autoVisionSession;
    private ImageReader autoVisionReader;
    private boolean autoVisionRunning;
    private boolean manualVisionCapture;
    private boolean activeVisionPreferFront;
    private String activeVisionTask = "";
    private boolean activeVisionDetailed;
    private String activeVisionReason = "";

    private static class VisionIntent {
        final boolean matched;
        final boolean explicitLook;
        final String task;
        final boolean preferFront;
        final boolean detailed;
        final String reason;

        VisionIntent(boolean matched, boolean explicitLook, String task, boolean preferFront, boolean detailed, String reason) {
            this.matched = matched;
            this.explicitLook = explicitLook;
            this.task = task == null ? "" : task;
            this.preferFront = preferFront;
            this.detailed = detailed;
            this.reason = reason == null ? "" : reason;
        }

        static VisionIntent none() {
            return new VisionIntent(false, false, "", false, false, "");
        }
    }

    private static class SleepDashboardData {
        long since;
        long sessionStart;
        long sessionEnd;
        int totalSleepMinutes;
        int eventCount;
        int highRiskCount;
        int mediumRiskCount;
        int autoCancelCount;
        int nocturiaCount;
        int audioClipCount;
        int deviceReadingCount;
        int estimatedDeepSleepMinutes;
        int waveformSampleCount;
        int hiddenSimulationCount;
        int[] waveformLevels = new int[0];
        String qualityLabel;
        String trendLine;
        String evidenceGrade;
        String evidenceLine;
    }

    private static class SleepGuardReadiness {
        final boolean micOk;
        final boolean notificationOk;
        final boolean batteryOk;
        final boolean emergencyOk;
        final java.util.ArrayList<String> missing = new java.util.ArrayList<>();

        SleepGuardReadiness(boolean micOk, boolean notificationOk, boolean batteryOk, boolean emergencyOk, boolean selfCheckOk) {
            this.micOk = micOk;
            this.notificationOk = notificationOk;
            this.batteryOk = batteryOk;
            this.emergencyOk = emergencyOk;
            if (!micOk) missing.add("麦克风");
            if (!notificationOk) missing.add("通知");
            if (!batteryOk) missing.add("电池优化");
            if (!emergencyOk) missing.add("家人电话");
            if (!selfCheckOk) missing.add("睡前自检");
        }

        boolean ready() {
            return missing.isEmpty();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new PreferenceStore(this);
        db = new SleepDatabase(this);
        getWindow().setStatusBarColor(Theme.WARM_WHITE);

        if (handleDebugDeepSeekIntent(getIntent())) {
            return;
        }
        if (handleDebugCareIntent(getIntent())) {
            return;
        }
        if (!prefs.isFirstLaunch()) {
            CareReminderScheduler.ensureCareReminders(this);
        }

        if (prefs.isFirstLaunch()) {
            showOnboarding();
            requestRequiredPermissionsProactivelySoon();
        } else if (shouldOpenServerMessages(getIntent())) {
            openServerMessagesFromIntent();
            requestRequiredPermissionsProactivelySoon();
        } else if (shouldOpenCompanion(getIntent())) {
            openCompanionFromIntent();
            requestRequiredPermissionsProactivelySoon();
        } else if (shouldOpenMorningCare(getIntent())) {
            showShell("assistant");
            showMorningCare();
            requestRequiredPermissionsProactivelySoon();
        } else {
            showShell("guard");
            maybeShowProactiveCare();
            requestRequiredPermissionsProactivelySoon();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (handleDebugDeepSeekIntent(intent)) {
            return;
        }
        if (handleDebugCareIntent(intent)) {
            return;
        }
        if (shouldOpenMorningCare(intent)) {
            showShell("assistant");
            showMorningCare();
            requestRequiredPermissionsProactivelySoon();
        } else if (shouldOpenServerMessages(intent)) {
            openServerMessagesFromIntent();
            requestRequiredPermissionsProactivelySoon();
        } else if (shouldOpenCompanion(intent)) {
            openCompanionFromIntent();
            requestRequiredPermissionsProactivelySoon();
        }
    }

    private boolean handleDebugDeepSeekIntent(Intent intent) {
        return false;
    }

    private boolean handleDebugCareIntent(Intent intent) {
        if (!isDebuggableBuild() || intent == null || intent.getAction() == null) {
            return false;
        }
        if (ACTION_DEBUG_HYDRATION_CARE.equals(intent.getAction())) {
            prefs.setFirstLaunchDone();
            Intent care = new Intent(this, ProactiveCareActivity.class);
            care.putExtra(ProactiveCareActivity.EXTRA_TYPE, ProactiveCareActivity.TYPE_HYDRATION);
            startActivity(care);
            finish();
            return true;
        }
        if (ACTION_DEBUG_SERVER_MESSAGE_CARE.equals(intent.getAction())) {
            prefs.setFirstLaunchDone();
            Intent care = new Intent(this, ProactiveCareActivity.class);
            care.putExtra(ProactiveCareActivity.EXTRA_TYPE, ProactiveCareActivity.TYPE_SERVER_MESSAGE);
            care.putExtra(ProactiveCareActivity.EXTRA_MESSAGE,
                    intent.getStringExtra(ProactiveCareActivity.EXTRA_MESSAGE) != null
                            ? intent.getStringExtra(ProactiveCareActivity.EXTRA_MESSAGE)
                            : "奶奶，家里人给您留了一句话：先别急，我慢慢说给您听。");
            care.putExtra(ProactiveCareActivity.EXTRA_SERVER_MESSAGE_ID, intent.getIntExtra(ProactiveCareActivity.EXTRA_SERVER_MESSAGE_ID, 0));
            startActivity(care);
            finish();
            return true;
        }
        if (ACTION_DEBUG_SERVER_MESSAGE_POLL.equals(intent.getAction())) {
            prefs.setFirstLaunchDone();
            Intent poll = new Intent(this, ServerMessageReceiver.class);
            poll.setAction(CareReminderScheduler.ACTION_SERVER_MESSAGE_POLL);
            sendBroadcast(poll);
            showShell("guard");
            Toast.makeText(this, "已触发服务端消息轮询", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (ACTION_DEBUG_NIGHTMARE_WAKE.equals(intent.getAction())) {
            prefs.setFirstLaunchDone();
            prefs.recordLiveVoiceState("urgent_wakeup", "debug_nightmare_wake", "urgent_wakeup");
            startActivity(alarmDrillIntent("疑似噩梦和明显挣扎声音"));
            finish();
            return true;
        }
        if (ACTION_DEBUG_VOICE_TEXT.equals(intent.getAction())) {
            prefs.setFirstLaunchDone();
            String heard = intent.getStringExtra("debug_voice_text");
            if (heard == null || heard.trim().length() == 0) {
                heard = "I woke up twice last night and feel dizzy today. Please comfort me.";
            }
            prefs.recordLiveVoiceState("heard", "debug_voice_text", heard);
            if (content == null) {
                showShell("assistant");
            }
            String finalHeard = heard;
            stopLiveAudioStreaming();
            abortLiveCompanionSpeech();
            content.postDelayed(() -> {
                realtimeVoiceEnabled = true;
                stopAssistantSpeech();
                stopLiveAudioStreaming();
                handleRealtimeVoiceText(finalHeard);
            }, 700);
            return true;
        }
        if (ACTION_DEBUG_SLEEP_CHECK.equals(intent.getAction())) {
            prefs.setFirstLaunchDone();
            showShell("assistant");
            content.postDelayed(() -> {
                realtimeVoiceEnabled = true;
                stopAssistantSpeech();
                pendingSleepContinuationText = "调试：我继续陪您，轻轻讲故事或者放雨声都可以。";
                lastCompanionActiveAtMs = System.currentTimeMillis() - 60L * 1000L;
                maybeAskIfUserAsleep();
            }, 500);
            return true;
        }
        if (ACTION_DEBUG_SLEEP_CHECK_TIMEOUT.equals(intent.getAction())) {
            prefs.setFirstLaunchDone();
            showShell("assistant");
            content.postDelayed(() -> {
                realtimeVoiceEnabled = true;
                stopAssistantSpeech();
                pendingSleepContinuationText = "调试：如果没听到回应，就转入睡眠守护。";
                lastCompanionActiveAtMs = System.currentTimeMillis() - 60L * 1000L;
                if (maybeAskIfUserAsleep()) {
                    content.postDelayed(() -> {
                        if (sleepCheckPending) {
                            enterSleepGuardFromCompanion(false);
                        }
                    }, 1200L);
                }
            }, 500);
            return true;
        }
        if (ACTION_DEBUG_LIVE_ABORT.equals(intent.getAction())) {
            prefs.setFirstLaunchDone();
            realtimeVoiceEnabled = true;
            if (content == null) {
                showShell("assistant");
            }
            prefs.recordLiveAbortState("debug_intent", false, "debug_live_abort");
            interruptForUserSpeech();
            prefs.recordLiveVoiceState("interrupting", "debug_live_abort", "好，我先不说了，继续听您讲。");
            updateVoiceStatus("好，我先不说了，继续听您讲。");
            return true;
        }
        if (ACTION_DEBUG_LIVE_BARGE_IN.equals(intent.getAction())) {
            prefs.setFirstLaunchDone();
            realtimeVoiceEnabled = true;
            if (content == null) {
                showShell("assistant");
            }
            prefs.recordLiveVoiceState("barge_in_test", "debug_live_barge_in", "模拟用户自然插话。");
            if (content != null) {
                content.postDelayed(this::debugFeedAutoBargeInFrames, 260);
            } else {
                debugFeedAutoBargeInFrames();
            }
            return true;
        }
        if (ACTION_DEBUG_CAMERA_GLANCE.equals(intent.getAction())) {
            prefs.setFirstLaunchDone();
            if (content == null) {
                showShell("assistant");
            }
            content.postDelayed(() -> {
                if (!hasPermission(Manifest.permission.CAMERA)) {
                    Toast.makeText(this, "调试抓拍需要摄像头权限", Toast.LENGTH_LONG).show();
                    return;
                }
                showCompanionVoiceWaiting("我看一眼，您别急。");
                updateLiveStageStatus("我认真看一眼", "seeing");
                startVisionFrameCapture("debug_camera_glance", true, false, false, "调试验收摄像头是否能打开并返回图像。");
            }, 350);
            return true;
        }
        if (ACTION_DEBUG_MICROPHONE_VERIFY.equals(intent.getAction())) {
            prefs.setFirstLaunchDone();
            showShell("assistant");
            content.postDelayed(this::showMicrophoneHonestCheck, 350);
            return true;
        }
        if (ACTION_DEBUG_START_SLEEP_GUARD.equals(intent.getAction())) {
            prefs.setFirstLaunchDone();
            showShell("guard");
            content.postDelayed(this::startMonitoring, 250);
            return true;
        }
        if (ACTION_DEBUG_LIVE2D_PREVIEW.equals(intent.getAction())) {
            prefs.setFirstLaunchDone();
            Intent preview = new Intent(this, Live2DPreviewActivity.class);
            preview.putExtra("auto_load", intent.getBooleanExtra("auto_load", false));
            startActivity(preview);
            finish();
            return true;
        }
        return false;
    }

    private boolean isDebuggableBuild() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private boolean shouldOpenMorningCare(Intent intent) {
        return intent != null && intent.getBooleanExtra("open_morning_care", false);
    }

    private boolean shouldOpenServerMessages(Intent intent) {
        return intent != null && (intent.hasExtra("open_server_messages") || ACTION_OPEN_SERVER_MESSAGES.equals(intent.getAction()));
    }

    private boolean shouldOpenCompanion(Intent intent) {
        return intent != null && ACTION_OPEN_COMPANION.equals(intent.getAction());
    }

    private void openCompanionFromIntent() {
        prefs.setFirstLaunchDone();
        CareReminderScheduler.ensureCareReminders(this);
        showShell("assistant");
    }

    private void openServerMessagesFromIntent() {
        prefs.setFirstLaunchDone();
        CareReminderScheduler.ensureCareReminders(this);
        showShell("assistant");
        content.postDelayed(this::fetchServerMessagesForReading, 700);
    }

    private void showOnboarding() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 24), safeTopPadding(22), Theme.dp(this, 24), Theme.dp(this, 28));
        box.setBackgroundColor(Theme.WARM_WHITE);
        scroll.addView(box);

        ImageView logo = designImage("ui_brand_logo", 250, ImageView.ScaleType.FIT_CENTER);
        box.addView(logo, imageLp(250));

        TextView title = Theme.text(this, "睡了么", 34, Theme.TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title, matchWrap());
        addSpace(box, 8);
        TextView sub = Theme.text(this, "贴心睡眠陪伴和健康建议\n夜间异常及时叫醒，不做医学诊断", 21, Theme.MUTED, Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        box.addView(sub, matchWrap());
        addSpace(box, 28);

        Button direct = Theme.button(this, "直接开始", Theme.BLUE);
        direct.setOnClickListener(v -> {
            prefs.setFirstLaunchDone();
            CareReminderScheduler.ensureCareReminders(this);
            showShell("guard");
            requestEssentialPermissions();
        });
        box.addView(direct, matchWrap());
        addSpace(box, 14);

        Button simple = Theme.button(this, "简单设置", Theme.ORANGE);
        simple.setOnClickListener(v -> showSimpleConcern());
        box.addView(simple, matchWrap());
        addSpace(box, 22);

        TextView note = Theme.text(this, "小助手会陪您说话、看记录；夜间安全守护保留本地兜底。详细设置以后再慢慢调。", 18, Theme.MUTED, Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        box.addView(note, matchWrap());
        setContentView(scroll);
    }

    private void showSimpleConcern() {
        LinearLayout box = plainPage("你最担心什么？", "选一个就行，也可以用默认。");
        addChoice(box, "打鼾/喘息", () -> {
            prefs.setMode("标准模式");
            showSimplePrivacy();
        });
        addChoice(box, "噩梦惊醒", () -> {
            prefs.setMode("敏感模式");
            showSimplePrivacy();
        });
        addChoice(box, "夜里动作大", () -> {
            prefs.setMode("标准模式");
            showSimplePrivacy();
        });
        addChoice(box, "先用默认", () -> {
            prefs.setMode("标准模式");
            showSimplePrivacy();
        });
    }

    private void showSimplePrivacy() {
        LinearLayout box = plainPage("数据只在手机里", "默认不保存整夜录音，只保存结构化记录和可选异常片段。");
        addChoice(box, "保存异常片段", () -> {
            prefs.setSaveAudioClips(true);
            showSimpleWake();
        });
        addChoice(box, "只保存时间线", () -> {
            prefs.setSaveAudioClips(false);
            showSimpleWake();
        });
    }

    private void showSimpleWake() {
        LinearLayout box = plainPage("怎么叫醒你？", "先用简单策略，后面可以细调。");
        addChoice(box, "只在高风险叫醒", () -> finishSimpleSetup(false));
        addChoice(box, "鼾声先轻提醒", () -> finishSimpleSetup(false));
        addChoice(box, "尽量少打扰", () -> {
            prefs.setMode("安静模式");
            finishSimpleSetup(false);
        });
        addChoice(box, "现在设置紧急联系人", () -> finishSimpleSetup(true));
    }

    private void finishSimpleSetup(boolean emergency) {
        prefs.setFirstLaunchDone();
        CareReminderScheduler.ensureCareReminders(this);
        showShell("guard");
        requestEssentialPermissions();
        if (emergency) {
            showEmergencyDialog();
        }
    }

    private LinearLayout plainPage(String title, String subtitle) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 24), safeTopPadding(40), Theme.dp(this, 24), Theme.dp(this, 24));
        box.setBackgroundColor(Theme.WARM_WHITE);
        scroll.addView(box);
        box.addView(Theme.text(this, title, 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(box, 10);
        box.addView(Theme.text(this, subtitle, 19, Theme.MUTED, Typeface.NORMAL), matchWrap());
        addSpace(box, 22);
        setContentView(scroll);
        return box;
    }

    private void showShell(String tab) {
        stopRealtimeVoiceChat(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.WARM_WHITE);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Theme.dp(this, 18), Theme.dp(this, 8), Theme.dp(this, 18), Theme.dp(this, 8));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        navBar = nav;
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(Theme.dp(this, 12), Theme.dp(this, 7), Theme.dp(this, 12), Theme.dp(this, 9));
        nav.setBackground(Theme.navBar(this));
        addNav(nav, "守护", () -> showShell("guard"), tab.equals("guard"));
        addNav(nav, "早安", () -> showShell("morning"), tab.equals("morning"));
        addNav(nav, "我的", () -> showShell("settings"), tab.equals("settings") || tab.equals("assistant"));
        root.addView(nav, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        if ("records".equals(tab)) {
            showRecords();
        } else if ("morning".equals(tab)) {
            showMorningCare();
        } else if ("assistant".equals(tab)) {
            showCompanionChat();
        } else if ("settings".equals(tab)) {
            showSettings();
        } else {
            showHome();
        }
    }

    private void showHome() {
        activeScreen = "home";
        content.removeAllViews();
        boolean monitoring = prefs.isMonitoring();
        int integrity = guardIntegrityScore();
        SleepGuardReadiness readiness = buildSleepGuardReadiness();
        addHomeHero(monitoring, integrity, readiness);
        addReadinessChips();
        addSpace(content, 8);
        addHomeWaveCard(monitoring);
        addSpace(content, 8);
        addHomeReportEntry();
        addSpace(content, 8);
        addHomeCareTiles();
    }

    private void addHomeHero(boolean monitoring, int integrity, SleepGuardReadiness readiness) {
        LinearLayout hero = cardContainer();
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(0, 0, Theme.dp(this, 16), 0);
        hero.setBackground(Theme.tintedCard(this, Theme.BLUE));

        ImageView scene = designImage("ui_sleep_scene_image2", 188, ImageView.ScaleType.CENTER_CROP);
        scene.setContentDescription("睡眠守护场景");
        LinearLayout.LayoutParams sceneLp = new LinearLayout.LayoutParams(Theme.dp(this, 190), Theme.dp(this, 188));
        sceneLp.setMargins(0, 0, Theme.dp(this, 16), 0);
        hero.addView(scene, sceneLp);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Theme.text(this, monitoring ? "睡眠守护中" : "睡眠守护", 34, Theme.TEXT, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = Theme.text(this, "›", 30, Theme.MUTED, Typeface.BOLD);
        arrow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        titleRow.addView(arrow, new LinearLayout.LayoutParams(Theme.dp(this, 28), -2));
        words.addView(titleRow, matchWrap());

        TextView status = Theme.text(this, monitoring ? "● 实时监测中" : (readiness != null && readiness.ready() ? "● 睡前准备已完成" : "! 睡前准备未完成"), 18,
                monitoring || (readiness != null && readiness.ready()) ? Theme.darken(Theme.GREEN, 0.18f) : Theme.darken(Theme.ORANGE, 0.22f),
                Typeface.BOLD);
        words.addView(status, matchWrap());
        addSpace(words, 18);

        Button primary = Theme.button(this, monitoring ? "停止守护  ›" : "开始守护  ›", monitoring ? Theme.RED : Theme.BLUE);
        primary.setTextSize(28);
        primary.setMinHeight(Theme.dp(this, 88));
        primary.setOnClickListener(v -> {
            if (prefs.isMonitoring()) {
                stopMonitoring();
            } else {
                startMonitoring();
            }
        });
        words.addView(primary, matchWrap());
        hero.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        if (!monitoring && readiness != null && !readiness.ready()) {
            hero.setOnClickListener(v -> showPreSleepCheck());
            hero.setClickable(true);
            hero.setFocusable(true);
        }

        content.addView(hero, matchWrap());
        addSpace(content, 8);
    }

    private void addHomeReadyBadge(LinearLayout hero, boolean monitoring, int integrity, SleepGuardReadiness readiness) {
        boolean ready = readiness == null || readiness.ready();
        int color = monitoring || ready ? Theme.GREEN : Theme.ORANGE;
        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Theme.dp(this, 16), Theme.dp(this, 10), Theme.dp(this, 16), Theme.dp(this, 10));
        badge.setBackground(Theme.rounded(Theme.mix(color, Theme.WARM_WHITE, 0.88f), 28, this));
        String state = monitoring ? "守护进行中" : (ready ? "守护已就绪" : "睡前准备未完成");
        String prefix = monitoring || ready ? "✓  " : "!  ";
        TextView label = Theme.text(this, prefix + state, 19, Theme.darken(color, 0.28f), Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        badge.addView(label, matchWrap());
        TextView sub = Theme.text(this, "完整性 " + integrity + " 分" + (ready || monitoring ? "" : " · 点这里补齐"), 15, Theme.MUTED, Typeface.BOLD);
        sub.setGravity(Gravity.CENTER);
        badge.addView(sub, matchWrap());
        if (!monitoring && !ready) {
            badge.setOnClickListener(v -> showPreSleepCheck());
            badge.setClickable(true);
            badge.setFocusable(true);
        }
        hero.addView(badge, matchWrap());
    }

    private void addHomeWaveCard(boolean monitoring) {
        SleepDashboardData data = buildSleepDashboardData();
        LinearLayout card = cardContainer();
        card.setPadding(Theme.dp(this, 16), Theme.dp(this, 11), Theme.dp(this, 16), Theme.dp(this, 12));
        card.setBackground(Theme.rounded(Color.rgb(19, 49, 101), 28, this));
        card.setOnClickListener(v -> showSleepReport());

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Theme.text(this, "睡觉波形检测", 22, Color.WHITE, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = Theme.text(this, monitoring ? "● 实时监测中" : (data.waveformSampleCount > 0 ? data.waveformSampleCount + " 点" : "未开始"), 14, Color.rgb(195, 218, 255), Typeface.BOLD);
        state.setGravity(Gravity.RIGHT);
        titleRow.addView(state, new LinearLayout.LayoutParams(Theme.dp(this, 120), -2));
        card.addView(titleRow, matchWrap());
        addSpace(card, 8);

        SleepWaveformView wave = new SleepWaveformView(this);
        wave.setCompact(true);
        wave.setDark(true);
        wave.setDashboardData(data, monitoring);
        card.addView(wave, new LinearLayout.LayoutParams(-1, Theme.dp(this, 110)));
        addHomeWaveTimes(card);
        addSpace(card, 10);
        addHomeWaveMetrics(card, data);

        content.addView(card, matchWrap());
    }

    private void addHomeWaveTimes(LinearLayout card) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"23:00", "01:00", "03:00", "05:00", "07:00"};
        for (String label : labels) {
            TextView item = Theme.text(this, label, 11, Color.rgb(195, 218, 255), Typeface.NORMAL);
            item.setGravity(Gravity.CENTER);
            row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        card.addView(row, matchWrap());
    }

    private void addHomeWaveMetrics(LinearLayout card, SleepDashboardData data) {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(Theme.dp(this, 10), Theme.dp(this, 8), Theme.dp(this, 10), Theme.dp(this, 8));
        strip.setBackground(Theme.rounded(Color.rgb(42, 82, 147), 18, this));
        addHomeMetric(strip, "◖", "呼吸平稳", data.eventCount > 0 ? "需留意" : "当前状态", Theme.GREEN);
        addHomeMetric(strip, "☾", "睡眠时长", data.totalSleepMinutes > 0 ? sleepDurationText(data.totalSleepMinutes) : "待生成", Theme.LILAC);
        addHomeMetric(strip, "♥", "心率", "待接入", Theme.PINK);
        card.addView(strip, new LinearLayout.LayoutParams(-1, Theme.dp(this, 72)));
    }

    private void addHomeMetric(LinearLayout row, String iconText, String title, String value, int color) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setGravity(Gravity.CENTER);

        TextView icon = Theme.text(this, iconText, 30, color, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        group.addView(icon, new LinearLayout.LayoutParams(Theme.dp(this, 40), -1));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Theme.text(this, title, 12, Color.WHITE, Typeface.BOLD);
        t.setGravity(Gravity.LEFT);
        box.addView(t, matchWrap());
        TextView v = Theme.text(this, value, 15, Color.rgb(235, 244, 255), Typeface.BOLD);
        v.setGravity(Gravity.LEFT);
        box.addView(v, matchWrap());
        group.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(group, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void addHomeReportEntry() {
        SleepDashboardData data = buildSleepDashboardData();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Theme.dp(this, 16), Theme.dp(this, 12), Theme.dp(this, 16), Theme.dp(this, 12));
        card.setBackground(Theme.tintedCard(this, Theme.BLUE));
        card.setOnClickListener(v -> showSleepReport());

        TextView reportIcon = Theme.text(this, "☾", 34, Color.WHITE, Typeface.BOLD);
        reportIcon.setGravity(Gravity.CENTER);
        reportIcon.setBackground(Theme.rounded(Theme.BLUE, 16, this));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(Theme.dp(this, 58), Theme.dp(this, 58));
        iconLp.setMargins(0, 0, Theme.dp(this, 16), 0);
        card.addView(reportIcon, iconLp);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = Theme.text(this, "睡眠报告", 22, Theme.TEXT, Typeface.BOLD);
        words.addView(title, matchWrap());
        String reportLine = data.waveformSampleCount > 0 ? "昨晚复盘、异常证据和真实波形" : "开始守护后生成真实睡眠报告";
        TextView sub = Theme.text(this, reportLine, 15, Theme.MUTED, Typeface.BOLD);
        words.addView(sub, matchWrap());
        card.addView(words, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = Theme.text(this, "查看  ›", 19, Theme.darken(Theme.BLUE, 0.22f), Typeface.BOLD);
        arrow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        card.addView(arrow, new LinearLayout.LayoutParams(Theme.dp(this, 72), -2));

        content.addView(card, new LinearLayout.LayoutParams(-1, Theme.dp(this, 86)));
    }

    private void addReadinessChips() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addMiniChip(row, hasPermission(Manifest.permission.RECORD_AUDIO) ? "麦克风可用" : "开麦克风", hasPermission(Manifest.permission.RECORD_AUDIO), Theme.GREEN, () -> requestEssentialPermissions());
        addMiniChip(row, prefs.emergencyEnabled() ? "家人已设" : "未设家人电话", prefs.emergencyEnabled(), Theme.ORANGE, this::showEmergencyDialog);
        content.addView(row, matchWrap());
    }

    private void addMiniChip(LinearLayout row, String label, boolean ok, int color, Runnable action) {
        Button chip = Theme.softButton(this, (ok ? "✓ " : "! ") + label, ok ? color : Theme.ORANGE);
        chip.setTextSize(18);
        chip.setMinHeight(Theme.dp(this, 64));
        chip.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(Theme.dp(this, 3), 0, Theme.dp(this, 3), 0);
        row.addView(chip, lp);
    }

    private void addSleepDashboardCard(boolean monitoring) {
        SleepDashboardData data = buildSleepDashboardData();
        LinearLayout card = cardContainer();
        card.setPadding(Theme.dp(this, 16), Theme.dp(this, 14), Theme.dp(this, 16), Theme.dp(this, 14));
        card.setBackground(Theme.tintedCard(this, monitoring ? Theme.BLUE : Theme.GREEN));
        card.setOnClickListener(v -> showSleepReport());

        TextView title = Theme.text(this, monitoring ? "实时守护波形" : "昨晚守护记录", 24, Theme.TEXT, Typeface.BOLD);
        title.setGravity(Gravity.LEFT);
        card.addView(title, matchWrap());
        addSpace(card, 4);

        TextView subtitle = Theme.text(this, monitoring ? "正在静音守护，只在异常时出声。" : data.trendLine, 16, Theme.MUTED, Typeface.NORMAL);
        subtitle.setGravity(Gravity.LEFT);
        card.addView(subtitle, matchWrap());
        addSpace(card, 8);

        SleepWaveformView wave = new SleepWaveformView(this);
        wave.setDashboardData(data, monitoring);
        card.addView(wave, new LinearLayout.LayoutParams(-1, Theme.dp(this, 78)));
        addSpace(card, 10);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        addSleepMetric(row1, "熟睡粗估", sleepDurationText(data.estimatedDeepSleepMinutes), Theme.GREEN);
        addSleepMetric(row1, "异常", data.eventCount + " 次", data.highRiskCount > 0 ? Theme.RED : Theme.ORANGE);
        card.addView(row1, matchWrap());
        addSpace(card, 8);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        addSleepMetric(row2, "起夜", data.nocturiaCount + " 次", Theme.BLUE);
        addSleepMetric(row2, "录音/设备记录", data.audioClipCount + "/" + data.deviceReadingCount, Theme.ORANGE);
        card.addView(row2, matchWrap());
        addSpace(card, 10);

        Button report = Theme.softButton(this, "查看睡眠报告", Theme.BLUE);
        report.setTextSize(18);
        report.setMinHeight(Theme.dp(this, 54));
        report.setOnClickListener(v -> showSleepReport());
        card.addView(report, matchWrap());

        content.addView(card, matchWrap());
        addSpace(content, 12);
    }

    private void addSleepMetric(LinearLayout row, String label, String value, int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));
        box.setBackground(Theme.rounded(Theme.mix(color, Theme.WARM_WHITE, 0.88f), 22, this));
        TextView top = Theme.text(this, label, 15, Theme.MUTED, Typeface.NORMAL);
        top.setGravity(Gravity.CENTER);
        box.addView(top, matchWrap());
        TextView bottom = Theme.text(this, value, 22, Theme.darken(color, 0.28f), Typeface.BOLD);
        bottom.setGravity(Gravity.CENTER);
        box.addView(bottom, matchWrap());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(Theme.dp(this, 4), 0, Theme.dp(this, 4), 0);
        row.addView(box, lp);
    }

    private void addCompactSleepTools(boolean monitoring) {
        SleepDashboardData data = buildSleepDashboardData();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout waveCard = new LinearLayout(this);
        waveCard.setOrientation(LinearLayout.VERTICAL);
        waveCard.setPadding(Theme.dp(this, 12), Theme.dp(this, 10), Theme.dp(this, 12), Theme.dp(this, 10));
        waveCard.setBackground(Theme.tintedCard(this, monitoring ? Theme.BLUE : Theme.GREEN));
        waveCard.setOnClickListener(v -> showSleepReport());
        TextView waveTitle = Theme.text(this, "睡觉波形检测", 17, Theme.TEXT, Typeface.BOLD);
        waveTitle.setGravity(Gravity.CENTER);
        waveCard.addView(waveTitle, matchWrap());
        SleepWaveformView wave = new SleepWaveformView(this);
        wave.setCompact(true);
        wave.setDashboardData(data, monitoring);
        waveCard.addView(wave, new LinearLayout.LayoutParams(-1, Theme.dp(this, 52)));

        LinearLayout.LayoutParams waveLp = new LinearLayout.LayoutParams(0, Theme.dp(this, 112), 1.35f);
        waveLp.setMargins(0, 0, Theme.dp(this, 6), 0);
        row.addView(waveCard, waveLp);

        Button report = Theme.softButton(this, "睡眠报告", Theme.BLUE);
        report.setTextSize(19);
        report.setMinHeight(Theme.dp(this, 112));
        report.setOnClickListener(v -> showSleepReport());
        LinearLayout.LayoutParams reportLp = new LinearLayout.LayoutParams(0, Theme.dp(this, 112), 1f);
        reportLp.setMargins(Theme.dp(this, 6), 0, 0, 0);
        row.addView(report, reportLp);

        content.addView(row, matchWrap());
    }

    private void addHomeCareTiles() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        addCareTile(row, "吃药提醒", medicationHomeLine(), prefs.medicationEnabled() ? Theme.ORANGE : Theme.BLUE,
                "ui_medication_icon_image2", true, this::showMedicationDialog);
        addCareTile(row, "健康习惯", healthHabitHomeLine(), healthHabitEnabled() ? Theme.GREEN : Theme.BLUE,
                "ui_health_habits_icon_image2", false, this::showHealthHabitsDialog);
        content.addView(row, matchWrap());
    }

    private void addCareTile(LinearLayout row, String title, String subtitle, int color, String iconName, boolean medication, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Theme.dp(this, 16), Theme.dp(this, 15), Theme.dp(this, 16), Theme.dp(this, 14));
        card.setBackground(Theme.tintedCard(this, color));
        card.setOnClickListener(v -> action.run());
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = designImage(iconName, 56, ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(Theme.dp(this, 58), Theme.dp(this, 58));
        iconLp.setMargins(0, 0, Theme.dp(this, 10), 0);
        titleRow.addView(icon, iconLp);
        TextView titleView = Theme.text(this, title, 22, Theme.TEXT, Typeface.BOLD);
        titleView.setGravity(Gravity.LEFT);
        titleRow.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(titleRow, matchWrap());
        addSpace(card, 4);
        TextView sub = Theme.text(this, subtitle, 16, medication ? Theme.ORANGE : Theme.MUTED, Typeface.BOLD);
        sub.setGravity(Gravity.LEFT);
        card.addView(sub, matchWrap());
        addSpace(card, 10);
        if (medication) {
            addCareMiniRow(card, formatMedicationTime(), prefs.medicationEnabled() ? shortText(prefs.medicationName(), 5) : "未设置", prefs.medicationConfirmedToday() ? "已服用" : "未服用", Theme.ORANGE);
            addCareMiniRow(card, "+", "添加药品", "设置", Theme.BLUE);
        } else {
            addCareMiniRow(card, "水", prefs.hydrationIntervalMinutes() + " 分钟/次", prefs.hydrationReminderEnabled() ? "开启" : "关闭", Theme.GREEN);
            addCareMiniRow(card, "坐", prefs.sedentaryIntervalMinutes() + " 分钟/次", prefs.sedentaryReminderEnabled() ? "开启" : "关闭", Theme.GREEN);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Theme.dp(this, 200), 1);
        lp.setMargins(Theme.dp(this, 5), 0, Theme.dp(this, 5), 0);
        row.addView(card, lp);
    }

    private void addCareMiniRow(LinearLayout card, String left, String middle, String right, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(this, 10), Theme.dp(this, 7), Theme.dp(this, 10), Theme.dp(this, 7));
        row.setBackground(Theme.rounded(Color.WHITE, 12, this));
        TextView l = Theme.text(this, left, 13, Theme.TEXT, Typeface.BOLD);
        row.addView(l, new LinearLayout.LayoutParams(Theme.dp(this, 50), -2));
        TextView m = Theme.text(this, middle, 14, Theme.MUTED, Typeface.BOLD);
        m.setSingleLine(true);
        row.addView(m, new LinearLayout.LayoutParams(0, -2, 1));
        TextView r = Theme.text(this, right, 13, Theme.darken(color, 0.25f), Typeface.BOLD);
        r.setSingleLine(true);
        r.setGravity(Gravity.RIGHT);
        row.addView(r, new LinearLayout.LayoutParams(Theme.dp(this, 50), -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Theme.dp(this, 42));
        lp.setMargins(0, 0, 0, Theme.dp(this, 7));
        card.addView(row, lp);
    }

    private String medicationHomeLine() {
        if (!prefs.medicationEnabled()) {
            return "设置药名和每日时间";
        }
        String state = prefs.medicationConfirmedToday() ? "今日已确认" : formatMedicationTime();
        return shortText(prefs.medicationName(), 8) + " · " + state;
    }

    private String healthHabitHomeLine() {
        boolean water = prefs.hydrationReminderEnabled();
        boolean sit = prefs.sedentaryReminderEnabled();
        if (water && sit) {
            return "喝水" + prefs.hydrationIntervalMinutes() + "分 · 久坐" + prefs.sedentaryIntervalMinutes() + "分";
        }
        if (water) {
            return "喝水每" + prefs.hydrationIntervalMinutes() + "分";
        }
        if (sit) {
            return "久坐每" + prefs.sedentaryIntervalMinutes() + "分";
        }
        return "设置喝水、久坐提醒";
    }

    private boolean healthHabitEnabled() {
        return prefs.hydrationReminderEnabled() || prefs.sedentaryReminderEnabled();
    }

    private String shortText(String text, int max) {
        String clean = text == null ? "" : text.trim();
        if (clean.length() <= max) return clean;
        return clean.substring(0, max) + "…";
    }

    private SleepDashboardData buildSleepDashboardData() {
        SleepDashboardData data = new SleepDashboardData();
        data.since = lastNightStartMillis();
        data.sessionStart = db.latestSummaryStartSince(data.since);
        data.sessionEnd = db.latestSummaryEndSince(data.since);
        data.autoCancelCount = db.latestSummaryAutoCancelSince(data.since);
        data.deviceReadingCount = db.countDeviceReadingsSince(data.since);
        data.waveformSampleCount = db.countSignalSamplesSince(data.since);
        data.waveformLevels = toSignalArray(db.getRecentSignalLevelsSince(data.since, 36));

        List<SleepEvent> events = db.getRecentEvents(80);
        for (SleepEvent event : events) {
            if (event.timestamp < data.since) {
                continue;
            }
            if (!isFormalSleepEvent(event)) {
                data.hiddenSimulationCount++;
                continue;
            }
            data.eventCount++;
            if ("high".equals(event.risk)) {
                data.highRiskCount++;
            } else if ("medium".equals(event.risk)) {
                data.mediumRiskCount++;
            }
            if (event.audioPath != null && event.audioPath.length() > 0) {
                data.audioClipCount++;
            }
            if (isNocturiaEvent(event)) {
                data.nocturiaCount++;
            }
        }

        if (data.sessionStart > 0 && data.sessionEnd > data.sessionStart) {
            data.totalSleepMinutes = (int) Math.max(0L, (data.sessionEnd - data.sessionStart) / 60000L);
        } else if (prefs.isMonitoring()) {
            long estimatedStart = Math.max(data.since, System.currentTimeMillis() - 8L * 60L * 60L * 1000L);
            data.sessionStart = estimatedStart;
            data.sessionEnd = System.currentTimeMillis();
            data.totalSleepMinutes = (int) Math.max(0L, (data.sessionEnd - data.sessionStart) / 60000L);
        }

        int disruptionMinutes = data.eventCount * 8 + data.nocturiaCount * 15 + data.highRiskCount * 20;
        int stableMinutes = Math.max(0, data.totalSleepMinutes - disruptionMinutes);
        data.estimatedDeepSleepMinutes = data.totalSleepMinutes <= 0 ? 0 : Math.max(0, Math.round(stableMinutes * 0.58f));

        if (data.totalSleepMinutes <= 0 && data.eventCount == 0) {
            data.qualityLabel = "待记录";
            data.trendLine = data.waveformSampleCount > 0 ? "已采集真实波形，今晚结束后生成复盘。" : "今晚守护后，明早生成守护复盘。";
        } else if (data.highRiskCount > 0) {
            data.qualityLabel = "需复盘";
            data.trendLine = "有 " + data.highRiskCount + " 次高风险提醒，建议点报告听录音复盘。";
        } else if (data.eventCount > 0) {
            data.qualityLabel = "有波动";
            data.trendLine = "昨晚记录 " + data.eventCount + " 次疑似波动，可查看证据。";
        } else {
            data.qualityLabel = "较平稳";
            data.trendLine = "昨晚记录较平稳，继续保持睡前准备。";
        }
        data.evidenceGrade = evidenceGradeFor(data);
        data.evidenceLine = evidenceLineFor(data);
        return data;
    }

    private boolean isFormalSleepEvent(SleepEvent event) {
        return !"simulation".equals(event.evidenceLevel) && !"manual_test".equals(event.evidenceLevel);
    }

    private int[] toSignalArray(List<Integer> levels) {
        if (levels == null || levels.isEmpty()) {
            return new int[0];
        }
        int[] values = new int[levels.size()];
        for (int i = 0; i < levels.size(); i++) {
            values[i] = Math.max(0, Math.min(100, levels.get(i)));
        }
        return values;
    }

    private String evidenceGradeFor(SleepDashboardData data) {
        if (data.waveformSampleCount <= 0) {
            return "待采样";
        }
        if (data.audioClipCount > 0 && data.deviceReadingCount > 0) {
            return "较强";
        }
        if (data.audioClipCount > 0 || data.deviceReadingCount > 0) {
            return "中等";
        }
        return "手机基础";
    }

    private String evidenceLineFor(SleepDashboardData data) {
        StringBuilder b = new StringBuilder();
        b.append("证据等级：").append(data.evidenceGrade).append("。");
        if (data.waveformSampleCount > 0) {
            b.append("本页波形来自守护服务真实采样，共 ").append(data.waveformSampleCount).append(" 个声音/动作采样点。");
        } else {
            b.append("还没有真实采样点，不画假波形；开始守护后每 3 秒记录一次声音和动作摘要。");
        }
        if (data.audioClipCount > 0) {
            b.append(" 有 ").append(data.audioClipCount).append(" 条异常录音可复盘。");
        }
        if (data.deviceReadingCount > 0) {
            b.append(" 有 ").append(data.deviceReadingCount).append(" 条手动或外部设备记录。");
        } else {
            b.append(" 暂未自动接入手表、血氧仪或 Health Connect。");
        }
        if (data.hiddenSimulationCount > 0) {
            b.append(" 已隐藏 ").append(data.hiddenSimulationCount).append(" 条演练记录，未计入正式报告。");
        }
        return b.toString();
    }

    private boolean isNocturiaEvent(SleepEvent event) {
        String text = (event.type + " " + event.action + " " + event.basis).toLowerCase(Locale.ROOT);
        return containsAny(text, "起夜", "夜尿", "厕所", "尿", "下床", "起床", "走动", "离床", "夜醒");
    }

    private long lastNightStartMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 18);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (System.currentTimeMillis() < calendar.getTimeInMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }
        return calendar.getTimeInMillis();
    }

    private String sleepDurationText(int minutes) {
        if (minutes <= 0) {
            return "待生成";
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours <= 0) {
            return rest + "分";
        }
        if (rest == 0) {
            return hours + "小时";
        }
        return hours + "小时" + rest + "分";
    }

    private void showSleepReport() {
        content.removeAllViews();
        SleepDashboardData data = buildSleepDashboardData();
        addSleepReportHeader(data);
        addSleepReportScoreCard(data);
        addSleepReportWaveCard(data);
        addSleepReportPhaseCard(data);
        addPreSleepSummaryCard();
        addCard("综合分析", sleepReportAnalysis(data), data.highRiskCount > 0 ? Theme.RED : Theme.GREEN);
        addSleepReportAssistantButton(data);
        addSleepEvidenceSection(data);
        addSleepDeviceSection(data);
        addSettingButton("返回首页", () -> showShell("guard"));
    }

    private void addSleepReportHeader(SleepDashboardData data) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Theme.text(this, "‹", 34, Theme.TEXT, Typeface.BOLD);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showShell("guard"));
        header.addView(back, new LinearLayout.LayoutParams(Theme.dp(this, 40), -2));
        TextView title = Theme.text(this, "睡眠报告", 24, Theme.TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView calendar = Theme.text(this, "日", 16, Theme.BLUE, Typeface.BOLD);
        calendar.setGravity(Gravity.CENTER);
        calendar.setBackground(Theme.rounded(Theme.mix(Theme.BLUE, Color.WHITE, 0.90f), 14, this));
        header.addView(calendar, new LinearLayout.LayoutParams(Theme.dp(this, 40), Theme.dp(this, 36)));
        content.addView(header, matchWrap());
        addSpace(content, 8);

        LinearLayout tabs = cardContainer();
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(Theme.dp(this, 6), Theme.dp(this, 5), Theme.dp(this, 6), Theme.dp(this, 5));
        tabs.setBackground(Theme.rounded(Theme.mix(Theme.BLUE, Color.WHITE, 0.94f), 18, this));
        addReportTab(tabs, "日", true);
        addReportTab(tabs, "周", false);
        addReportTab(tabs, "月", false);
        content.addView(tabs, matchWrap());
        addSpace(content, 8);
        TextView date = Theme.text(this, reportDateText(data), 15, Theme.MUTED, Typeface.BOLD);
        date.setGravity(Gravity.CENTER);
        content.addView(date, matchWrap());
        addSpace(content, 10);
    }

    private void addReportTab(LinearLayout tabs, String text, boolean selected) {
        TextView tab = Theme.text(this, text, 15, selected ? Theme.BLUE : Theme.MUTED, Typeface.BOLD);
        tab.setGravity(Gravity.CENTER);
        if (selected) {
            tab.setBackground(Theme.rounded(Color.WHITE, 14, this));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Theme.dp(this, 34), 1);
        lp.setMargins(Theme.dp(this, 2), 0, Theme.dp(this, 2), 0);
        tabs.addView(tab, lp);
    }

    private void addSleepReportScoreCard(SleepDashboardData data) {
        LinearLayout card = cardContainer();
        card.setPadding(0, 0, 0, Theme.dp(this, 12));

        LinearLayout scoreBand = new LinearLayout(this);
        scoreBand.setOrientation(LinearLayout.HORIZONTAL);
        scoreBand.setGravity(Gravity.CENTER_VERTICAL);
        scoreBand.setPadding(Theme.dp(this, 18), Theme.dp(this, 16), Theme.dp(this, 18), Theme.dp(this, 16));
        scoreBand.setBackground(Theme.rounded(data.highRiskCount > 0 ? Theme.RED : Color.rgb(24, 88, 214), 24, this));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Theme.text(this, "睡眠质量评分", 16, Color.WHITE, Typeface.BOLD), matchWrap());
        TextView score = Theme.text(this, sleepScore(data) + " 分", 40, Color.WHITE, Typeface.BOLD);
        words.addView(score, matchWrap());
        scoreBand.addView(words, new LinearLayout.LayoutParams(0, -2, 1));

        TextView quality = Theme.text(this, sleepQualityPhrase(data), 18, Color.WHITE, Typeface.BOLD);
        quality.setGravity(Gravity.CENTER);
        scoreBand.addView(quality, new LinearLayout.LayoutParams(Theme.dp(this, 98), -2));

        TextView shield = Theme.text(this, "☾", 38, Color.WHITE, Typeface.BOLD);
        shield.setGravity(Gravity.CENTER);
        shield.setBackground(Theme.rounded(Theme.mix(Theme.ORANGE, Color.WHITE, 0.12f), 22, this));
        scoreBand.addView(shield, new LinearLayout.LayoutParams(Theme.dp(this, 62), Theme.dp(this, 62)));
        card.addView(scoreBand, matchWrap());
        addSpace(card, 10);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(Theme.dp(this, 8), 0, Theme.dp(this, 8), 0);
        addReportSummaryMetric(metrics, "睡眠时长", sleepDurationText(data.totalSleepMinutes));
        addReportSummaryMetric(metrics, "入睡时长", sleepOnsetText(data));
        addReportSummaryMetric(metrics, "深睡时长", sleepDurationText(data.estimatedDeepSleepMinutes));
        card.addView(metrics, matchWrap());
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addReportSummaryMetric(LinearLayout row, String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView top = Theme.text(this, label, 14, Theme.MUTED, Typeface.BOLD);
        top.setGravity(Gravity.CENTER);
        box.addView(top, matchWrap());
        TextView bottom = Theme.text(this, value, 20, Theme.TEXT, Typeface.BOLD);
        bottom.setGravity(Gravity.CENTER);
        box.addView(bottom, matchWrap());
        row.addView(box, new LinearLayout.LayoutParams(0, Theme.dp(this, 58), 1));
    }

    private void addSleepReportWaveCard(SleepDashboardData data) {
        LinearLayout card = cardContainer();
        card.setPadding(Theme.dp(this, 16), Theme.dp(this, 14), Theme.dp(this, 16), Theme.dp(this, 16));
        TextView title = Theme.text(this, "睡眠波形", 21, Theme.TEXT, Typeface.BOLD);
        card.addView(title, matchWrap());
        addSpace(card, 8);
        SleepWaveformView wave = new SleepWaveformView(this);
        wave.setDashboardData(data, false);
        card.addView(wave, new LinearLayout.LayoutParams(-1, Theme.dp(this, 132)));
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addSleepReportPhaseCard(SleepDashboardData data) {
        LinearLayout card = cardContainer();
        card.setPadding(Theme.dp(this, 14), Theme.dp(this, 12), Theme.dp(this, 14), Theme.dp(this, 12));
        card.addView(Theme.text(this, "睡眠阶段", 21, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(card, 10);
        int total = Math.max(1, data.totalSleepMinutes);
        int deep = Math.max(0, Math.min(total, data.estimatedDeepSleepMinutes));
        int light = Math.max(0, total - deep - data.eventCount * 6);
        addSleepPhaseBar(card, "深睡", deep, total, Theme.BLUE);
        addSleepPhaseBar(card, "浅睡", light, total, Theme.GREEN);
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addPreSleepSummaryCard() {
        LinearLayout card = cardContainer();
        card.setPadding(Theme.dp(this, 14), Theme.dp(this, 12), Theme.dp(this, 14), Theme.dp(this, 12));
        card.addView(Theme.text(this, "睡前自检", 18, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(card, 8);
        TextView summary = Theme.text(this, preSleepSelfCheckSummary(), 15, Theme.MUTED, Typeface.BOLD);
        summary.setGravity(Gravity.LEFT);
        card.addView(summary, matchWrap());
        addSpace(card, 8);
        TextView link = Theme.text(this, "今晚重新自检 ›", 15, Theme.BLUE, Typeface.BOLD);
        link.setGravity(Gravity.RIGHT);
        link.setOnClickListener(v -> showPreSleepCheck());
        card.addView(link, matchWrap());
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addSleepPhaseBar(LinearLayout card, String label, int minutes, int total, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Theme.text(this, label, 16, Theme.TEXT, Typeface.BOLD), new LinearLayout.LayoutParams(Theme.dp(this, 58), -2));
        LinearLayout track = new LinearLayout(this);
        track.setBackground(Theme.rounded(Theme.mix(color, Color.WHITE, 0.88f), 10, this));
        LinearLayout fill = new LinearLayout(this);
        fill.setBackground(Theme.rounded(color, 10, this));
        track.addView(fill, new LinearLayout.LayoutParams(0, Theme.dp(this, 12), Math.max(1, minutes)));
        track.addView(new Space(this), new LinearLayout.LayoutParams(0, Theme.dp(this, 12), Math.max(1, total - minutes)));
        row.addView(track, new LinearLayout.LayoutParams(0, Theme.dp(this, 12), 1));
        String percent = total <= 0 ? "0%" : Math.round(minutes * 100f / Math.max(1, total)) + "%";
        TextView value = Theme.text(this, sleepDurationText(minutes) + "  " + percent, 13, Theme.TEXT, Typeface.BOLD);
        value.setGravity(Gravity.RIGHT);
        row.addView(value, new LinearLayout.LayoutParams(Theme.dp(this, 104), -2));
        card.addView(row, matchWrap());
        addSpace(card, 10);
    }

    private int sleepScore(SleepDashboardData data) {
        if (data.totalSleepMinutes <= 0 && data.eventCount == 0) return 0;
        int score = 86;
        if (data.totalSleepMinutes > 0) {
            int diff = Math.abs(data.totalSleepMinutes - 420);
            score -= Math.min(20, diff / 18);
        }
        score -= data.eventCount * 5 + data.highRiskCount * 10 + data.nocturiaCount * 4;
        if (data.waveformSampleCount <= 0) score -= 8;
        return Math.max(35, Math.min(96, score));
    }

    private String sleepQualityPhrase(SleepDashboardData data) {
        if (data.totalSleepMinutes <= 0 && data.eventCount == 0) return "今晚守护后生成报告";
        if (data.highRiskCount > 0) return "睡眠有风险，建议复盘";
        if (data.eventCount > 0) return "睡眠有波动";
        return "睡眠良好";
    }

    private String sleepOnsetText(SleepDashboardData data) {
        if (data.totalSleepMinutes <= 0) {
            return "待生成";
        }
        return "待接入";
    }

    private String reportDateText(SleepDashboardData data) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(data.since > 0 ? data.since + 12L * 60L * 60L * 1000L : System.currentTimeMillis());
        return calendar.get(Calendar.YEAR) + "年" + (calendar.get(Calendar.MONTH) + 1) + "月" + calendar.get(Calendar.DAY_OF_MONTH) + "日";
    }

    private void addSleepReportAssistantButton(SleepDashboardData data) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button read = Theme.button(this, "读报告", CompanionAssistant.roleColor(prefs.companionRole()));
        read.setTextSize(20);
        read.setMinHeight(Theme.dp(this, 62));
        read.setOnClickListener(v -> showCompanionVoiceReply("睡眠报告", sleepReportVoiceText(data)));
        LinearLayout.LayoutParams readLp = new LinearLayout.LayoutParams(0, -2, 1);
        readLp.setMargins(0, 0, Theme.dp(this, 6), 0);
        row.addView(read, readLp);

        Button ai = Theme.button(this, "帮我听听", Theme.BLUE);
        ai.setTextSize(20);
        ai.setMinHeight(Theme.dp(this, 62));
        ai.setOnClickListener(v -> askAssistantSleepAudioAnalysis(data));
        LinearLayout.LayoutParams aiLp = new LinearLayout.LayoutParams(0, -2, 1);
        aiLp.setMargins(Theme.dp(this, 6), 0, 0, 0);
        row.addView(ai, aiLp);

        content.addView(row, matchWrap());
        addSpace(content, 12);
    }

    private void askAssistantSleepAudioAnalysis(SleepDashboardData data) {
        if (!prefs.serverRegistered()) {
            showCompanionVoiceReply("先登录一下",
                    prefs.ownerAddress() + "，要让我仔细听昨晚的声波，先绑定手机号。绑定好以后，我就能帮您把昨晚的动静讲得更明白。");
            return;
        }
        showCompanionVoiceWaiting(prefs.ownerAddress() + "，您别急，我正在结合昨晚波形、异常记录和设备摘要想一想。");
        new Thread(() -> {
            try {
                String audioBase64 = sleepEvidenceAudioBase64(data);
                String answer = ServerApiClient.audio(
                        prefs.serverBaseUrl(),
                        prefs.serverAuthToken(),
                        deepSeekSystemPrompt(),
                        sleepAudioAnalysisPrompt(data),
                        "sleep_waveform_summary",
                        sleepWaveformModelSummary(data),
                        audioBase64,
                        "wav",
                        prefs.deepSeekModel());
                maybeSyncCompanionInsight("睡眠声波分析：\n" + answer, "sleep_audio");
                runOnUiThread(() -> showCompanionVoiceReply("声波分析", answer));
            } catch (Exception ex) {
                runOnUiThread(() -> showCompanionVoiceReply("这次没听清",
                        "昨晚的声波我这次没听明白。您别急，本机守护记录还在，强唤醒和家人电话也都照常守着。"));
            }
        }, "GouXiongSleepAudioAnalysis").start();
    }

    private String sleepEvidenceAudioBase64(SleepDashboardData data) {
        File file = firstFormalEvidenceAudioFile(data);
        if (file == null || !file.exists() || file.length() <= 0 || file.length() > AUDIO_ANALYSIS_MAX_BYTES) {
            return "";
        }
        byte[] bytes = readLimitedFile(file, AUDIO_ANALYSIS_MAX_BYTES);
        if (bytes.length == 0) return "";
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private File firstFormalEvidenceAudioFile(SleepDashboardData data) {
        List<SleepEvent> events = db.getRecentEvents(24);
        File fallback = null;
        for (SleepEvent event : events) {
            if (event.timestamp < data.since || !isFormalSleepEvent(event) || event.audioPath == null || event.audioPath.length() == 0) {
                continue;
            }
            File file = new File(event.audioPath);
            if (!file.exists() || file.length() <= 0 || file.length() > AUDIO_ANALYSIS_MAX_BYTES) {
                continue;
            }
            if ("high".equals(event.risk)) {
                return file;
            }
            if (fallback == null) {
                fallback = file;
            }
        }
        return fallback;
    }

    private byte[] readLimitedFile(File file, int maxBytes) {
        if (file == null || !file.exists() || file.length() <= 0 || file.length() > maxBytes) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length());
        byte[] buffer = new byte[8192];
        int total = 0;
        try (FileInputStream in = new FileInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) return new byte[0];
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    private void addSleepEvidenceSection(SleepDashboardData data) {
        addSectionTitle("异常证据", "只显示真实守护事件；开发者演练记录不计入正式报告。");
        List<SleepEvent> events = db.getRecentEvents(30);
        int shown = 0;
        for (SleepEvent event : events) {
            if (event.timestamp < data.since) {
                continue;
            }
            if (!isFormalSleepEvent(event)) {
                continue;
            }
            String body = SleepDatabase.formatTime(event.timestamp) + "\n" +
                    event.risk + " · " + event.action + " · 可信度 " + Math.round(event.confidence * 100) + "%\n" +
                    event.basis + "\n" +
                    evidenceText(event) + "\n反馈：" + event.feedback;
            addEventCard(event, body);
            shown++;
            if (shown >= 8) {
                break;
            }
        }
        if (shown == 0) {
            addCard("暂无异常记录", "最近一晚没有保存疑似异常事件。没有记录不代表没有健康问题，只说明本机没有捕捉到触发条件。", Theme.GREEN);
        }
        if (data.hiddenSimulationCount > 0) {
            addCard("演练记录已隐藏", data.hiddenSimulationCount + " 条模拟/手动测试记录未计入正式报告，避免误导家人和医生。", Theme.ORANGE);
        }
    }

    private void addSleepDeviceSection(SleepDashboardData data) {
        addSectionTitle("设备记录", "当前只展示手动录入或外部摘要；自动 Health Connect/蓝牙读取未接入。");
        List<DeviceReading> readings = db.getRecentDeviceReadings(8);
        int shown = 0;
        for (DeviceReading reading : readings) {
            if (reading.timestamp < data.since) {
                continue;
            }
            addCard("设备读数", db.formatDeviceReading(reading), Theme.BLUE);
            shown++;
        }
        if (shown == 0) {
            addCard("未找到昨晚设备记录", "现在还没有自动接入手表、血氧仪或 Health Connect。可以先手动录入设备摘要；自动读取完成前不会标成“已接入”。", Theme.ORANGE);
        }
    }

    private String sleepReportAnalysis(SleepDashboardData data) {
        StringBuilder b = new StringBuilder();
        if (data.totalSleepMinutes <= 0 && data.eventCount == 0) {
            b.append("今晚开始守护后，明早会生成手机采样波形、守护时长、熟睡粗估、异常证据、起夜线索和设备记录摘要。");
        } else {
            b.append("本次守护估算睡眠 ").append(sleepDurationText(data.totalSleepMinutes))
                    .append("，熟睡粗估约 ").append(sleepDurationText(data.estimatedDeepSleepMinutes)).append("。");
            b.append("\n疑似异常 ").append(data.eventCount).append(" 次");
            if (data.highRiskCount > 0) {
                b.append("，其中高风险 ").append(data.highRiskCount).append(" 次");
            }
            if (data.mediumRiskCount > 0) {
                b.append("，中风险 ").append(data.mediumRiskCount).append(" 次");
            }
            b.append("；起夜/离床线索 ").append(data.nocturiaCount).append(" 次。");
            b.append("\n真实波形采样 ").append(data.waveformSampleCount).append(" 点，现场录音 ").append(data.audioClipCount)
                    .append(" 条，设备记录 ").append(data.deviceReadingCount).append(" 条。");
        }
        if (data.highRiskCount > 0) {
            b.append("\n建议今天让家人一起听录音、看设备读数；如果多晚重复，或伴随憋醒、胸闷、白天困倦，请带记录咨询医生。");
        } else if (data.eventCount > 0) {
            b.append("\n建议重点看异常发生时间，睡前少饮水、保持侧睡和规律作息，观察是否连续多晚出现。");
        } else {
            b.append("\n建议继续保持固定入睡时间，睡前减少刺激性声音和强光。");
        }
        b.append("\n说明：熟睡粗估来自手机传感器和事件记录，不是专业睡眠分期；设备自动接入前，设备记录只作为人工复盘材料。");
        return b.toString();
    }

    private String sleepReportVoiceText(SleepDashboardData data) {
        return prefs.ownerAddress() + "，我给您读一下昨晚睡眠。"
                + sleepReportAnalysis(data)
                + "\n我会继续守着您，发现明显不对会叫醒您，也会把记录留下来。";
    }

    private String sleepAudioAnalysisPrompt(SleepDashboardData data) {
        return "请分析昨晚手机守护声波/动作摘要，给中老年主人一段能直接朗读的复盘。"
                + "\n要求：先安抚，再说可能的睡眠波动点，再给今天生活建议；不能诊断疾病，不能说已经确认呼吸暂停。"
                + "\n如果出现多次高风险、憋醒、胸闷、喘息、持续鼾声或白天困倦，要建议带记录问医生或让家人一起看。"
                + "\n用户称呼：" + prefs.ownerAddress()
                + "\n小助手身份：" + prefs.assistantPersonaSummary()
                + "\n主人档案：\n" + prefs.ownerProfileSummary()
                + "\n今天状态：\n" + prefs.assistantCheckInSummary()
                + "\n睡前自检：\n" + preSleepSelfCheckSummary()
                + "\n基础报告：\n" + sleepReportAnalysis(data);
    }

    private String sleepWaveformModelSummary(SleepDashboardData data) {
        StringBuilder b = new StringBuilder();
        b.append("守护时间：").append(sleepDurationText(data.totalSleepMinutes))
                .append("；熟睡粗估：").append(sleepDurationText(data.estimatedDeepSleepMinutes))
                .append("；疑似异常：").append(data.eventCount).append(" 次")
                .append("；高风险：").append(data.highRiskCount).append(" 次")
                .append("；中风险：").append(data.mediumRiskCount).append(" 次")
                .append("；起夜/离床线索：").append(data.nocturiaCount).append(" 次")
                .append("；自动取消：").append(data.autoCancelCount).append(" 次")
                .append("；现场录音：").append(data.audioClipCount).append(" 条")
                .append("；设备记录：").append(data.deviceReadingCount).append(" 条")
                .append("；证据等级：").append(data.evidenceGrade).append("。\n");
        b.append("手机真实采样统计：\n").append(db.signalSummarySince(data.since, 48)).append("\n");
        File audioFile = firstFormalEvidenceAudioFile(data);
        if (audioFile != null) {
            b.append("随请求附带一段正式异常现场 WAV 录音，大小 ").append(audioFile.length() / 1024).append("KB，用于辅助分析。\n");
        } else if (data.audioClipCount > 0) {
            b.append("本晚有录音记录，但没有找到可上传的正式 WAV 片段，或片段超过大小限制。\n");
        } else {
            b.append("本次没有可上传现场录音，仅使用波形和事件摘要。\n");
        }
        b.append("异常事件摘要：\n").append(sleepEventModelSummary(data));
        return b.toString();
    }

    private String sleepEventModelSummary(SleepDashboardData data) {
        StringBuilder b = new StringBuilder();
        List<SleepEvent> events = db.getRecentEvents(16);
        int shown = 0;
        for (SleepEvent event : events) {
            if (event.timestamp < data.since || !isFormalSleepEvent(event)) continue;
            if (shown > 0) b.append("\n");
            b.append(SleepDatabase.formatTime(event.timestamp))
                    .append("，风险 ").append(event.risk)
                    .append("，可信度 ").append(Math.round(event.confidence * 100)).append("%")
                    .append("，动作：").append(event.action)
                    .append("，依据：").append(event.basis);
            if (event.audioSummary != null && event.audioSummary.length() > 0) {
                b.append("，录音摘要：").append(event.audioSummary);
            }
            if (event.motionSummary != null && event.motionSummary.length() > 0) {
                b.append("，动作摘要：").append(event.motionSummary);
            }
            if (event.deviceSummary != null && event.deviceSummary.length() > 0) {
                b.append("，设备摘要：").append(event.deviceSummary);
            }
            shown++;
        }
        if (shown == 0) return "昨晚没有正式异常事件。";
        return b.toString();
    }

    private void addSectionTitle(String title, String subtitle) {
        TextView heading = Theme.text(this, title, 24, Theme.TEXT, Typeface.BOLD);
        content.addView(heading, matchWrap());
        if (subtitle != null && subtitle.length() > 0) {
            addSpace(content, 4);
            content.addView(Theme.text(this, subtitle, 16, Theme.MUTED, Typeface.NORMAL), matchWrap());
        }
        addSpace(content, 10);
    }

    private void addAssistantHero(String title, String body, boolean includeChatButton) {
        String role = prefs.companionRole();
        LinearLayout card = cardContainer();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView avatar = designImage(roleAvatarAssetName(role), 104, ImageView.ScaleType.FIT_CENTER);
        avatar.setContentDescription(role);
        startAssistantMotion(avatar);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(Theme.dp(this, 104), Theme.dp(this, 104));
        avatarLp.setMargins(0, 0, Theme.dp(this, 14), 0);
        row.addView(avatar, avatarLp);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        String companionTitle = prefs.assistantPersonaConfigured() ? prefs.assistantName() : role;
        String heading = title.equals(role) ? companionTitle : title + " · " + companionTitle;
        words.addView(Theme.text(this, heading, 22, CompanionAssistant.roleColor(role), Typeface.BOLD), matchWrap());
        addSpace(words, 4);
        words.addView(Theme.text(this, body, 18, Theme.MUTED, Typeface.NORMAL), matchWrap());
        addSpace(words, 6);
        words.addView(Theme.text(this, "我在听您说话", 16, CompanionAssistant.roleColor(role), Typeface.BOLD), matchWrap());
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));

        card.addView(row, matchWrap());
        if (includeChatButton) {
            addSpace(card, 12);
            Button chat = Theme.button(this, "和我聊聊", CompanionAssistant.roleColor(role));
            chat.setTextSize(20);
            chat.setMinHeight(Theme.dp(this, 56));
            chat.setOnClickListener(v -> showCompanionChat());
            card.addView(chat, matchWrap());
        }
        content.addView(card, matchWrap());
        addSpace(content, 14);
    }

    private void startAssistantMotion(View avatar) {
        avatar.postDelayed(() -> animateAssistantAvatar(avatar, true), 300);
    }

    private void animateAssistantAvatar(View avatar, boolean outward) {
        if (avatar.getWindowToken() == null) {
            return;
        }
        avatar.animate()
                .scaleX(outward ? 1.06f : 1.0f)
                .scaleY(outward ? 1.06f : 1.0f)
                .rotation(outward ? 1.5f : -1.0f)
                .alpha(outward ? 0.92f : 1.0f)
                .setDuration(950)
                .withEndAction(() -> animateAssistantAvatar(avatar, !outward))
                .start();
    }

    private void maybeShowProactiveCare() {
        if (!prefs.assistantProactiveCareEnabled()
                || prefs.isMonitoring()
                || prefs.assistantCarePromptedToday()) {
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing() || prefs.assistantCarePromptedToday()) {
                return;
            }
            prefs.markAssistantCarePromptedToday();
            showShell("assistant");
            showCompanionVoiceReply(prefs.companionRole() + "关心你", proactiveCareText());
        }, 800);
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

    private String checkText() {
        StringBuilder b = new StringBuilder();
        b.append(hasPermission(Manifest.permission.RECORD_AUDIO) ? "✓ 麦克风可用\n" : "! 开始守护时需要麦克风\n");
        if (Build.VERSION.SDK_INT >= 33) {
            b.append(hasPermission(Manifest.permission.POST_NOTIFICATIONS) ? "✓ 通知可用\n" : "! 建议开启通知\n");
        } else {
            b.append("✓ 通知可用\n");
        }
        b.append("✓ 数据本机保存\n");
        b.append(prefs.saveAudioClips() ? "✓ 异常片段用于复盘\n" : "! 只保存时间线，不保存现场录音\n");
        b.append(batteryOptimizationText()).append("\n");
        if (prefs.isMonitoring()) {
            b.append(heartbeatText()).append("\n");
        }
        b.append(prefs.emergencyEnabled() ? "✓ 紧急联系人已设置" : "! 紧急联系人未设置");
        return b.toString();
    }

    private String tonightReadinessTitle(java.util.List<String> missing) {
        return missing == null || missing.isEmpty() ? "今晚可以放心守护" : "今晚还差 " + missing.size() + " 项";
    }

    private String tonightReadinessText(SleepGuardReadiness readiness) {
        java.util.List<String> missing = readiness == null ? null : readiness.missing;
        StringBuilder b = new StringBuilder();
        if (missing == null || missing.isEmpty()) {
            b.append("今晚可以直接点开始守护。");
        } else {
            b.append("先处理橙色项：").append(joinLabels(missing)).append("。");
        }
        b.append("\n");
        b.append(readiness != null && readiness.micOk ? "麦克风已授权，能听守护声音。" : "还需要打开麦克风。");
        if (Build.VERSION.SDK_INT >= 33) {
            b.append("\n").append(readiness != null && readiness.notificationOk ? "通知已授权。" : "建议打开通知，避免提醒被错过。");
        }
        b.append("\n").append(batteryOptimizationText(readiness != null && readiness.batteryOk));
        b.append("\n").append(readiness != null && readiness.emergencyOk ? "紧急联系人已设置。" : "建议先设置家人电话。");
        b.append("\n").append(preSleepSubjectiveReady() ? "睡前自检已完成。" : "请确认情绪、身体、咖啡因、晚餐、运动和屏幕使用。");
        return b.toString();
    }

    private java.util.ArrayList<String> sleepGuardMissingItems() {
        return new java.util.ArrayList<>(buildSleepGuardReadiness().missing);
    }

    private boolean sleepGuardReady() {
        return buildSleepGuardReadiness().ready();
    }

    private SleepGuardReadiness buildSleepGuardReadiness() {
        return new SleepGuardReadiness(
                hasPermission(Manifest.permission.RECORD_AUDIO),
                Build.VERSION.SDK_INT < 33 || hasPermission(Manifest.permission.POST_NOTIFICATIONS),
                batteryOptimizationOk(),
                prefs.emergencyEnabled(),
                preSleepSubjectiveReady());
    }

    private void requestSleepGuardPermissions() {
        java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), 7);
        }
    }

    private boolean hasSleepGuardRuntimePermissionMissing() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            return true;
        }
        return Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void showStartGuardReadinessDialog(java.util.ArrayList<String> missing) {
        StringBuilder message = new StringBuilder();
        message.append("今晚守护还差：").append(joinLabels(missing)).append("。\n\n");
        message.append("建议先补齐，夜里更稳。也可以先开始，但提醒、后台运行或联系家人可能受影响。");
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("今晚还差 " + missing.size() + " 项")
                .setMessage(message.toString())
                .setPositiveButton("去睡前自检", (d, w) -> showPreSleepCheck())
                .setNegativeButton("仍然开始", (d, w) -> {
                    if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                        pendingStartAfterPermission = true;
                        requestSleepGuardPermissions();
                    } else {
                        beginMonitoringService();
                    }
                });
        if (hasSleepGuardRuntimePermissionMissing()) {
            builder.setNeutralButton("只开权限", (d, w) -> {
                pendingStartAfterPermission = false;
                requestSleepGuardPermissions();
            });
        } else if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null || !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                builder.setNeutralButton("去电池设置", (d, w) -> requestIgnoreBatteryOptimization());
            } else if (!prefs.emergencyEnabled()) {
                builder.setNeutralButton("设家人电话", (d, w) -> showEmergencyDialog());
            }
        } else if (!prefs.emergencyEnabled()) {
            builder.setNeutralButton("设家人电话", (d, w) -> showEmergencyDialog());
        }
        builder.show();
    }

    private String joinLabels(java.util.List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String value : values) {
            if (b.length() > 0) b.append("、");
            b.append(value);
        }
        return b.toString();
    }

    private int guardIntegrityScore() {
        int score = 100;
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) score -= 30;
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) score -= 15;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null || !pm.isIgnoringBatteryOptimizations(getPackageName())) score -= 20;
        }
        if (prefs.isMonitoring()) {
            long heartbeat = prefs.lastHeartbeat();
            long age = heartbeat <= 0 ? Long.MAX_VALUE : (System.currentTimeMillis() - heartbeat) / 1000L;
            if (age > 60) score -= 25;
            else if (age > 15) score -= 10;
        }
        if (!prefs.emergencyEnabled()) score -= 5;
        if (score < 0) score = 0;
        return score;
    }

    private String guardIntegrityText() {
        int score = guardIntegrityScore();
        String level = score >= 85 ? "高" : (score >= 65 ? "中" : "低");
        StringBuilder b = new StringBuilder();
        b.append("完整性 ").append(score).append(" 分 · ").append(level).append("\n");
        b.append("依据：麦克风、通知、电池优化、守护心跳、紧急联系人。\n");
        if (score < 85) {
            b.append("建议先打开睡前自检，把橙色/红色项处理掉。");
        } else {
            b.append("今晚守护条件较完整。");
        }
        return b.toString();
    }

    private String detectionConfidenceText() {
        StringBuilder b = new StringBuilder();
        int samples = prefs.signalBaselineSamples();
        String baselineState = samples >= 20 ? "个人基线已启用" : "个人基线学习中";
        b.append("当前版本：声音/动作强度 + 本机个人基线。\n");
        b.append(baselineState).append("，样本 ").append(samples).append(" 个；声音基线 ")
                .append((int) prefs.signalAudioBaselineRms()).append("，动作基线 ")
                .append(String.format(java.util.Locale.CHINA, "%.1f", prefs.signalMotionBaseline())).append("。\n");
        b.append("记录页会保留触发指标、外部设备状态和可选现场录音，供复盘判断。\n");
        b.append("不能诊断呼吸暂停，不能可靠区分同床人和环境噪声。\n");
        b.append("后续仍需接入 Health Connect/蓝牙设备原始数据和音频事件模型。");
        return b.toString();
    }

    private String heartbeatText() {
        long heartbeat = prefs.lastHeartbeat();
        if (heartbeat <= 0) {
            return "! 尚未收到守护心跳";
        }
        long age = (System.currentTimeMillis() - heartbeat) / 1000L;
        if (age <= 15) {
            return "✓ 守护服务刚刚活跃";
        }
        if (age <= 60) {
            return "! 守护服务 " + age + " 秒前活跃";
        }
        return "! 守护可能中断，最后活跃 " + (age / 60L) + " 分钟前";
    }

    private void showRecords() {
        content.removeAllViews();
        content.addView(Theme.text(this, "睡眠记录", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        content.addView(Theme.text(this, db.localReportText(), 19, Theme.MUTED, Typeface.NORMAL), matchWrap());
        addSpace(content, 16);

        List<SleepEvent> events = db.getRecentEvents(20);
        if (events.isEmpty()) {
            addCard("还没有记录", "今晚点击开始守护后，明早会看到本地时间线。", Theme.BLUE);
            return;
        }
        for (SleepEvent event : events) {
            String body = SleepDatabase.formatTime(event.timestamp) + "\n" +
                    event.type + " · " + event.risk + " · " + event.action + "\n" +
                    event.basis + "\n" +
                    evidenceText(event) + "\n反馈：" + event.feedback;
            addEventCard(event, body);
        }
    }

    private void showSettings() {
        activeScreen = "settings";
        content.removeAllViews();
        addSimplePageHeader("设置", "", null);
        addSettingsProfileCard();
        addSettingsCategoryGrid();
    }

    private void addSettingsCategoryGrid() {
        AudioOutputStatus.Snapshot audio = AudioOutputStatus.inspect(this);
        LinearLayout list = cardContainer();
        list.setPadding(Theme.dp(this, 8), Theme.dp(this, 6), Theme.dp(this, 8), Theme.dp(this, 6));
        addSettingsRow(list, "服务端与能力检查", prefs.serverRegistered() ? "账号已登录" : "未登录", "☘", Theme.GREEN, prefs.serverRegistered(), this::showServerCapabilityCheck);
        addSettingsRow(list, "主人档案", prefs.ownerProfileSummary().length() > 8 ? "已填写" : "待完善", "◆", Theme.ORANGE, prefs.ownerProfileSummary().length() > 8, this::showOwnerProfileSettings);
        addSettingsRow(list, "提醒与通知", prefs.emergencyEnabled() ? "家人电话已设置" : "待设置", "●", Theme.RED, prefs.emergencyEnabled(), this::showCareSettings);
        addSettingsRow(list, "设备管理", audio.isBluetooth() ? "蓝牙已连接" : "手机扬声器", "▣", Theme.BLUE, audio.isBluetooth(), this::showAudioOutputSettings);
        addSettingsRow(list, "数据与隐私", "本机记录与导出", "◈", Theme.BLUE, true, this::showDataSettings);
        addSettingsRow(list, "关于我们", appVersionLine(), "✿", Theme.GREEN, true, this::showAboutSettings);
        content.addView(list, matchWrap());
        addSpace(content, 12);
    }

    private void addSettingsProfileCard() {
        LinearLayout card = cardContainer();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Theme.tintedCard(this, Theme.BLUE));
        ImageView logo = designImage("ui_brand_logo", 58, ImageView.ScaleType.FIT_CENTER);
        card.addView(logo, new LinearLayout.LayoutParams(Theme.dp(this, 64), Theme.dp(this, 64)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        String name = prefs.ownerAddress();
        if (name == null || name.trim().length() == 0) {
            name = "主人";
        }
        words.addView(Theme.text(this, name, 20, Theme.TEXT, Typeface.BOLD), matchWrap());
        String days = prefs.isFirstLaunch() ? "今天开始守护" : "睡眠守护已开启";
        words.addView(Theme.text(this, days + " · " + (prefs.serverRegistered() ? "云端账号已登录" : "本机模式"), 14, Theme.MUTED, Typeface.BOLD), matchWrap());
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, -2, 1);
        wordsLp.setMargins(Theme.dp(this, 12), 0, 0, 0);
        card.addView(words, wordsLp);
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addSettingsRow(LinearLayout list, String title, String state, String iconText, int color, boolean ok, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(this, 10), Theme.dp(this, 10), Theme.dp(this, 10), Theme.dp(this, 10));
        TextView icon = Theme.text(this, iconText, 17, color, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Theme.rounded(Theme.mix(color, Color.WHITE, 0.86f), 14, this));
        row.addView(icon, new LinearLayout.LayoutParams(Theme.dp(this, 34), Theme.dp(this, 34)));
        TextView titleView = Theme.text(this, title, 17, Theme.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.setMargins(Theme.dp(this, 10), 0, Theme.dp(this, 8), 0);
        row.addView(titleView, titleLp);
        TextView stateView = Theme.text(this, compactForCard(state, 12) + " ›", 14, Theme.MUTED, Typeface.BOLD);
        stateView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(stateView, new LinearLayout.LayoutParams(Theme.dp(this, 112), -2));
        row.setOnClickListener(v -> action.run());
        list.addView(row, matchWrap());
    }

    private String appVersionLine() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return "版本 " + (version == null || version.length() == 0 ? "本机" : version);
        } catch (Exception ignored) {
            return "本机版本";
        }
    }

    private void showAboutSettings() {
        content.removeAllViews();
        addSimplePageHeader("关于我们", "", null);
        addSettingsProfileCard();
        addCard("睡了么", "面向中老年人的睡眠守护和陪伴工具。所有睡眠记录只作为生活提醒和复盘参考，不做医学诊断。", Theme.BLUE);
        addCard("当前版本", appVersionLine() + "\n服务器：" + compactForCard(prefs.serverBaseUrl(), 36), Theme.GREEN);
        addSettingButton("返回设置", this::showSettings);
    }

    private void showGuardianSettings() {
        content.removeAllViews();
        content.addView(Theme.text(this, "守护调校", 32, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addCard("当前模式", prefs.mode() + "\n" + guardIntegrityText(), guardIntegrityScore() >= 80 ? Theme.GREEN : Theme.ORANGE);
        addModeButton("标准模式");
        addModeButton("安静模式");
        addModeButton("敏感模式");
        if (isDebuggableBuild()) {
            addSettingButton("开发者演练测试", this::showDetectionTest);
        }
        addSettingButton("去电池设置", this::requestIgnoreBatteryOptimization);
        addSettingButton("异常证据", this::showEvidenceSettings);
        addSettingButton("返回设置", this::showSettings);
    }

    private void showCareSettings() {
        content.removeAllViews();
        addSimplePageHeader("提醒与通知", "", null);
        addCard("轻提醒", "吃药、喝水和久坐提醒都会按你设置的时间触发；睡眠守护中不会用喝水/久坐打扰。", Theme.GREEN);
        addSettingButton("家人电话", this::showEmergencyDialog);
        addSettingButton("吃药提醒", this::showMedicationDialog);
        addSettingButton("健康习惯", this::showHealthHabitsDialog);
        addSettingButton("唤醒声音", this::showSoundSettings);
        addSettingButton("返回设置", this::showSettings);
    }

    private void showDataSettings() {
        content.removeAllViews();
        content.addView(Theme.text(this, "记录数据", 32, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addCard("本地数据", "异常证据、物品记忆和主人档案保存在本机。睡眠记录只在“睡眠守护”页查看。", Theme.GREEN);
        addSettingButton("导出证据摘要", this::shareReport);
        addSettingButton("删除全部本地数据", this::confirmDelete);
        addSettingButton("重新查看引导", this::showOnboarding);
        addSettingButton("返回设置", this::showSettings);
    }

    private void addSettingsSafetyButtons() {
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        addSmallTile(row1, "☎\n家人电话", prefs.emergencyEnabled() ? Theme.GREEN : Theme.ORANGE, this::showEmergencyDialog);
        addSmallTile(row1, "♪\n唤醒声音", Theme.BLUE, this::showSoundSettings);
        content.addView(row1, matchWrap());
        addSpace(content, 10);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        AudioOutputStatus.Snapshot audio = AudioOutputStatus.inspect(this);
        addSmallTile(row2, "🔊\n" + (audio.isBluetooth() ? "蓝牙已连" : "蓝牙未连"), audio.isBluetooth() ? Theme.GREEN : Theme.ORANGE, this::showAudioOutputSettings);
        addSmallTile(row2, "♡\n小助手", CompanionAssistant.roleColor(prefs.companionRole()), this::showCompanionSettings);
        content.addView(row2, matchWrap());
        addSpace(content, 14);
    }

    private void showAudioOutputSettings() {
        content.removeAllViews();
        content.addView(Theme.text(this, "唤醒输出", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        AudioOutputStatus.Snapshot audio = AudioOutputStatus.inspect(this);
        addCard(audio.isBluetooth() ? "蓝牙音箱已检测" : "蓝牙音箱未检测到",
                audio.routeLine(),
                audio.isBluetooth() ? Theme.GREEN : Theme.ORANGE);
        addSettingButton("播放测试音", this::playAudioOutputTest);
        addSettingButton("刷新输出检测", this::showAudioOutputSettings);
        addSettingButton("打开系统蓝牙设置", () -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        addCard("真实边界", "这里读取的是 Android 当前音频输出路由。App 不负责配对蓝牙设备；连接或断开后请回到本页刷新。强唤醒会跟随系统音频路由，蓝牙断连时回退手机扬声器和震动。", Theme.BLUE);
        addSettingButton("返回设置", this::showSettings);
    }

    private void playAudioOutputTest() {
        try {
            AudioOutputStatus.playAlarmTestTone();
            Toast.makeText(this, "已播放闹钟通道测试音", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Toast.makeText(this, "测试音播放失败，请检查系统音量", Toast.LENGTH_LONG).show();
        }
    }

    private void addModeButton(String mode) {
        Button button = Theme.button(this, (prefs.mode().equals(mode) ? "✓ " : "") + mode, prefs.mode().equals(mode) ? Theme.GREEN : Theme.BLUE);
        button.setTextSize(20);
        button.setOnClickListener(v -> {
            prefs.setMode(mode);
            showSettings();
        });
        content.addView(button, matchWrap());
        addSpace(content, 8);
    }

    private void addSettingButton(String text, Runnable action) {
        Button button = Theme.button(this, text, Theme.BLUE);
        button.setTextSize(20);
        button.setMinHeight(Theme.dp(this, 64));
        button.setOnClickListener(v -> action.run());
        content.addView(button, matchWrap());
        addSpace(content, 8);
    }

    private void addPrimaryActionButton(String text, int color, Runnable action) {
        Button button = Theme.button(this, text, color);
        button.setTextSize(24);
        button.setMinHeight(Theme.dp(this, 76));
        button.setOnClickListener(v -> action.run());
        content.addView(button, matchWrap());
        addSpace(content, 12);
    }

    private void showEmergencyDialog() {
        String returnScreen = activeScreen;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));

        TextView note = Theme.text(this, "最多 3 位。短信会发给全部联系人，电话只拨打第 1 位，避免连续拨号卡住强唤醒。", 17, Theme.MUTED, Typeface.NORMAL);
        box.addView(note, matchWrap());
        addSpace(box, 8);

        EditText[] phones = new EditText[PreferenceStore.MAX_EMERGENCY_CONTACTS];
        for (int i = 0; i < PreferenceStore.MAX_EMERGENCY_CONTACTS; i++) {
            EditText phone = new EditText(this);
            phone.setText(prefs.emergencyPhone(i));
            phone.setHint(i == 0 ? "第 1 联系人电话（优先拨打）" : "第 " + (i + 1) + " 联系人电话（短信通知）");
            phone.setTextSize(20);
            phone.setSingleLine(true);
            phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            phones[i] = phone;
            box.addView(phone, matchWrap());
        }

        CheckBox call = new CheckBox(this);
        call.setText("唤醒失败后打电话");
        call.setTextSize(19);
        call.setChecked(prefs.emergencyCall());
        box.addView(call, matchWrap());

        CheckBox sms = new CheckBox(this);
        sms.setText("唤醒失败后发短信");
        sms.setTextSize(19);
        sms.setChecked(prefs.emergencySms());
        box.addView(sms, matchWrap());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("紧急联系人")
                .setMessage("只在高风险强唤醒无确认时使用。号码只保存在本机。")
                .setView(box)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String[] values = new String[PreferenceStore.MAX_EMERGENCY_CONTACTS];
            for (int i = 0; i < phones.length; i++) {
                values[i] = phones[i].getText().toString();
            }
            String error = PreferenceStore.emergencyPhoneValidationError(values);
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                return;
            }
            prefs.setEmergencyContacts(values, call.isChecked(), sms.isChecked());
            if (prefs.emergencyEnabled()) {
                requestEmergencyPermissions(call.isChecked(), sms.isChecked());
            }
            Toast.makeText(this, "已保存紧急联系人", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showAfterEmergencySave(returnScreen);
        }));
        dialog.show();
    }

    private void showAfterEmergencySave(String returnScreen) {
        if ("pre_sleep".equals(returnScreen)) {
            showPreSleepCheck();
        } else if ("home".equals(returnScreen)) {
            showHome();
        } else {
            showSettings();
        }
    }

    private void showEvidenceSettings() {
        content.removeAllViews();
        content.addView(Theme.text(this, "异常证据", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addCard("现场录音片段",
                prefs.saveAudioClips()
                        ? "已开启。检测到疑似异常时，只保存触发前一段现场短片段，不保存整夜录音。"
                        : "已关闭。只保存时间线、声音强度和动作评分。",
                prefs.saveAudioClips() ? Theme.GREEN : Theme.ORANGE);
        addSettingButton(prefs.saveAudioClips() ? "关闭异常录音片段" : "开启异常录音片段", () -> {
            prefs.setSaveAudioClips(!prefs.saveAudioClips());
            showEvidenceSettings();
        });
        addCard("设备记录状态", externalDeviceTruthText(), prefs.externalDeviceEnabled() ? Theme.ORANGE : Theme.BLUE);
        addSettingButton("手动填写设备摘要", this::showExternalDeviceDialog);
        addSettingButton("手动添加心率/血氧/呼吸率", this::showDeviceReadingDialog);
        addCard("最近设备读数", deviceReadingsText(), db.getRecentDeviceReadings(3).isEmpty() ? Theme.ORANGE : Theme.GREEN);
        if (prefs.externalDeviceEnabled()) {
            addSettingButton("清除外部设备摘要", () -> {
                prefs.clearExternalDevice();
                showEvidenceSettings();
            });
        }
        addCard("医生复盘边界", "记录页提供触发指标、外部设备状态和现场录音，帮助判断是否真实异常；App 仍只提示疑似风险，不做诊断。", Theme.BLUE);
        addSettingButton("返回设置", this::showSettings);
    }

    private void showExternalDeviceDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));

        EditText device = new EditText(this);
        device.setText(prefs.externalDeviceName());
        device.setHint("设备名称，例如：Apple Watch / 血氧仪");
        device.setTextSize(20);
        box.addView(device, matchWrap());

        EditText summary = new EditText(this);
        summary.setText(prefs.externalDeviceSummary());
        summary.setHint("昨晚摘要，例如：平均心率 62，最低血氧 93%，呼吸率 14");
        summary.setMinLines(3);
        summary.setTextSize(20);
        box.addView(summary, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("外部设备摘要")
                .setMessage("当前版本先保存用户录入的手表/血氧仪摘要，后续再接 Health Connect 或蓝牙设备原始数据。")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.setExternalDevice(device.getText().toString(), summary.getText().toString());
                    Toast.makeText(this, "已保存外部设备摘要", Toast.LENGTH_SHORT).show();
                    showEvidenceSettings();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String externalDeviceTruthText() {
        StringBuilder b = new StringBuilder();
        b.append("自动读取：未接入。当前 APK 尚未读取 Health Connect、手表、血氧仪或蓝牙设备原始数据。\n");
        if (prefs.externalDeviceEnabled()) {
            b.append("手动摘要：").append(prefs.externalDeviceEvidence()).append("\n");
        } else {
            b.append("手动摘要：未填写。\n");
        }
        b.append("说明：这里的设备记录只用于和异常时间线一起复盘，不能标成自动设备接入。");
        return b.toString();
    }

    private void showDeviceReadingDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));

        EditText source = new EditText(this);
        source.setText(prefs.externalDeviceName().length() > 0 ? prefs.externalDeviceName() : "手表/血氧仪");
        source.setHint("设备名称");
        source.setTextSize(20);
        box.addView(source, matchWrap());

        EditText minutesAgo = new EditText(this);
        minutesAgo.setText("0");
        minutesAgo.setHint("距现在几分钟，例如：0 或 12");
        minutesAgo.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minutesAgo.setTextSize(20);
        box.addView(minutesAgo, matchWrap());

        EditText heartRate = numberField("心率，例如：68");
        box.addView(heartRate, matchWrap());

        EditText spo2 = numberField("血氧，例如：96");
        box.addView(spo2, matchWrap());

        EditText respiratoryRate = numberField("呼吸率，例如：14");
        box.addView(respiratoryRate, matchWrap());

        EditText note = new EditText(this);
        note.setHint("备注，例如：手表夜间摘要手动录入");
        note.setMinLines(2);
        note.setTextSize(20);
        box.addView(note, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("添加设备读数")
                .setMessage("用于和异常时间线对齐。当前为手动录入/模拟读数，不等同于设备原始报告。")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    int minutes = parseInt(minutesAgo, 0, 0, 24 * 60);
                    long timestamp = System.currentTimeMillis() - minutes * 60L * 1000L;
                    db.insertDeviceReading(
                            timestamp,
                            source.getText().toString(),
                            parseInt(heartRate, 0, 0, 240),
                            parseInt(spo2, 0, 0, 100),
                            parseInt(respiratoryRate, 0, 0, 80),
                            note.getText().toString());
                    Toast.makeText(this, "已保存设备读数", Toast.LENGTH_SHORT).show();
                    showEvidenceSettings();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText numberField(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(20);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        return input;
    }

    private int parseInt(EditText input, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value < min) return min;
            if (value > max) return max;
            return value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String deviceReadingsText() {
        List<DeviceReading> readings = db.getRecentDeviceReadings(3);
        if (readings.isEmpty()) {
            return "还没有结构化读数。可以手动录入手表、手环或血氧仪的心率、血氧、呼吸率，用来和异常时间线对齐。";
        }
        StringBuilder b = new StringBuilder();
        for (DeviceReading reading : readings) {
            if (b.length() > 0) b.append("\n\n");
            b.append(db.formatDeviceReading(reading));
        }
        return b.toString();
    }

    private void showPreSleepCheck() {
        activeScreen = "pre_sleep";
        content.removeAllViews();
        SleepGuardReadiness readiness = buildSleepGuardReadiness();
        boolean ready = readiness.ready();
        addPreSleepHeader();
        addSpace(content, 8);
        addPreSleepNotice(readiness);
        addPreSleepSelfCheckRow("情绪状态", "mood", "☺", Theme.GREEN, new String[]{"平静", "有点焦虑", "心情不好"});
        addPreSleepSelfCheckRow("身体不适", "body", "♨", Theme.RED, new String[]{"无", "有点不舒服", "明显不舒服"});
        addPreSleepSelfCheckRow("咖啡因摄入", "caffeine", "▣", Theme.BLUE, new String[]{"未摄入", "下午喝过", "晚上喝过"});
        addPreSleepSelfCheckRow("晚餐时间", "dinner", "▣", Theme.ORANGE, new String[]{"2小时前", "1小时前", "刚吃不久"});
        addPreSleepSelfCheckRow("运动情况", "exercise", "➜", Theme.GREEN, new String[]{"适量", "很少活动", "运动较多"});
        addPreSleepSelfCheckRow("屏幕使用", "screen", "▤", Theme.BLUE, new String[]{"30分钟前", "刚看过", "准备放下"});
        addSpace(content, 10);
        addPrimaryActionButton("完成自检", ready ? Theme.BLUE : Theme.ORANGE, () -> {
            if (ready) {
                Toast.makeText(this, "睡前自检已完成，可以开始今晚守护。", Toast.LENGTH_SHORT).show();
                showShell("guard");
            } else if (preSleepSubjectiveReady()) {
                requestSleepGuardPermissions();
            } else {
                showPreSleepCheckIncomplete();
            }
        });
    }

    private boolean preSleepSubjectiveReady() {
        return prefs.preSleepCheckValue("mood").length() > 0
                && prefs.preSleepCheckValue("body").length() > 0
                && prefs.preSleepCheckValue("caffeine").length() > 0
                && prefs.preSleepCheckValue("dinner").length() > 0
                && prefs.preSleepCheckValue("exercise").length() > 0
                && prefs.preSleepCheckValue("screen").length() > 0;
    }

    private void showPreSleepCheckIncomplete() {
        new AlertDialog.Builder(this)
                .setTitle("睡前自检还没完成")
                .setMessage("请先确认情绪、身体、咖啡因、晚餐、运动和屏幕使用；麦克风和通知也需要打开。确认后再开始今晚守护。")
                .setPositiveButton("继续自检", null)
                .show();
    }

    private String preSleepSelfCheckSummary() {
        if (!prefs.preSleepCheckToday()) {
            return "昨晚没有完成睡前自检。";
        }
        StringBuilder b = new StringBuilder();
        appendPreSleepSummaryLine(b, "情绪", prefs.preSleepCheckValue("mood"));
        appendPreSleepSummaryLine(b, "身体", prefs.preSleepCheckValue("body"));
        appendPreSleepSummaryLine(b, "咖啡因", prefs.preSleepCheckValue("caffeine"));
        appendPreSleepSummaryLine(b, "晚餐", prefs.preSleepCheckValue("dinner"));
        appendPreSleepSummaryLine(b, "运动", prefs.preSleepCheckValue("exercise"));
        appendPreSleepSummaryLine(b, "屏幕", prefs.preSleepCheckValue("screen"));
        return b.length() == 0 ? "昨晚睡前自检未填写完整。" : b.toString();
    }

    private void appendPreSleepSummaryLine(StringBuilder b, String label, String value) {
        if (value == null || value.length() == 0) {
            return;
        }
        if (b.length() > 0) {
            b.append("；");
        }
        b.append(label).append("：").append(value);
    }

    private void addPreSleepSelfCheckRow(String title, String key, String iconText, int color, String[] choices) {
        String value = prefs.preSleepCheckValue(key);
        boolean ok = value.length() > 0;
        addPreSleepDesignRow(title, ok ? value : "待确认", iconText, ok, color, () -> showPreSleepChoiceDialog(title, key, choices));
    }

    private void showPreSleepChoiceDialog(String title, String key, String[] choices) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(choices, (dialog, which) -> {
                    if (which >= 0 && which < choices.length) {
                        prefs.setPreSleepCheckValue(key, choices[which]);
                        Toast.makeText(this, "已记录：" + choices[which], Toast.LENGTH_SHORT).show();
                        showPreSleepCheck();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addPreSleepHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Theme.text(this, "‹", 34, Theme.TEXT, Typeface.BOLD);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showShell("guard"));
        header.addView(back, new LinearLayout.LayoutParams(Theme.dp(this, 40), -2));
        TextView title = Theme.text(this, "睡前自检", 24, Theme.TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView history = Theme.text(this, "历史记录", 14, Theme.BLUE, Typeface.BOLD);
        history.setGravity(Gravity.CENTER);
        history.setOnClickListener(v -> showRecords());
        header.addView(history, new LinearLayout.LayoutParams(Theme.dp(this, 74), -2));
        content.addView(header, matchWrap());
    }

    private void addPreSleepNotice(SleepGuardReadiness readiness) {
        LinearLayout card = cardContainer();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Theme.dp(this, 14), Theme.dp(this, 12), Theme.dp(this, 14), Theme.dp(this, 12));
        card.setBackground(Theme.tintedCard(this, readiness.ready() ? Theme.BLUE : Theme.ORANGE));
        TextView moon = Theme.text(this, "☾", 28, readiness.ready() ? Theme.BLUE : Theme.ORANGE, Typeface.BOLD);
        moon.setGravity(Gravity.CENTER);
        moon.setBackground(Theme.rounded(Color.WHITE, 18, this));
        card.addView(moon, new LinearLayout.LayoutParams(Theme.dp(this, 46), Theme.dp(this, 46)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = Theme.text(this, readiness.ready() ? "睡前准备已完成" : "建议在睡前 30 分钟完成自检", 17, Theme.TEXT, Typeface.BOLD);
        words.addView(title, matchWrap());
        TextView body = Theme.text(this, readiness.ready() ? "可以开始今晚守护，手机会记录真实波形。" : tonightReadinessText(readiness), 13, Theme.MUTED, Typeface.NORMAL);
        words.addView(body, matchWrap());
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, -2, 1);
        wordsLp.setMargins(Theme.dp(this, 12), 0, 0, 0);
        card.addView(words, wordsLp);
        content.addView(card, matchWrap());
        addSpace(content, 8);
    }

    private void addPreSleepDesignRow(String title, String state, String iconText, boolean ok, int color, Runnable action) {
        LinearLayout row = cardContainer();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(this, 14), Theme.dp(this, 10), Theme.dp(this, 14), Theme.dp(this, 10));
        TextView icon = Theme.text(this, iconText, 20, color, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Theme.rounded(Theme.mix(color, Color.WHITE, 0.84f), 14, this));
        row.addView(icon, new LinearLayout.LayoutParams(Theme.dp(this, 38), Theme.dp(this, 38)));
        TextView left = Theme.text(this, title, 18, Theme.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -2, 1);
        leftLp.setMargins(Theme.dp(this, 12), 0, Theme.dp(this, 8), 0);
        row.addView(left, leftLp);
        TextView right = Theme.text(this, state + " ›", 16, ok ? Theme.darken(color, 0.25f) : Theme.ORANGE, Typeface.BOLD);
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(right, new LinearLayout.LayoutParams(Theme.dp(this, 116), -2));
        if (action != null) {
            row.setOnClickListener(v -> action.run());
            row.setClickable(true);
            row.setFocusable(true);
        }
        content.addView(row, matchWrap());
        addSpace(content, 6);
    }

    private void showPreSleepMoreChecks() {
        activeScreen = "pre_sleep_more";
        content.removeAllViews();
        content.addView(Theme.text(this, "更多检测与测试", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addCard("可选项目", "这里用于测试唤醒、输出和诊断。睡前只要主页面四项补齐，就可以开始守护。", Theme.BLUE);
        addCard("守护状态", preSleepStatusText(), Theme.GREEN);
        addCard("守护完整性", guardIntegrityText(), guardIntegrityScore() >= 80 ? Theme.GREEN : Theme.ORANGE);
        addCard("检测可信度", detectionConfidenceText(), Theme.ORANGE);
        addCard("唤醒输出", AudioOutputStatus.inspect(this).routeLine(), AudioOutputStatus.inspect(this).isBluetooth() ? Theme.GREEN : Theme.ORANGE);
        addSettingButton("去电池设置", this::requestIgnoreBatteryOptimization);
        addSettingButton("测试轻提醒震动", this::testGentleReminder);
        addSettingButton("测试强唤醒", () -> startActivity(alarmDrillIntent("睡前自检")));
        addSettingButton("唤醒输出检测", this::showAudioOutputSettings);
        addSettingButton("高级诊断", this::showServerCapabilityCheck);
        addSettingButton("返回睡前自检", this::showPreSleepCheck);
    }

    private void showMorningCare() {
        activeScreen = "morning";
        if (prefs.isMonitoring()) {
            stopMonitoring();
        }
        content.removeAllViews();
        addSimplePageHeader("早安简报", "", null);
        SleepDashboardData data = buildSleepDashboardData();
        addMorningBriefHeroCard();
        addMorningCareActionCards();
        addMorningSleepReviewCard(data);
        addTodayCareAdviceCard(data);
    }

    private void requestMorningBriefVoice() {
        if (!prefs.serverRegistered()) {
            showCompanionVoiceReply("晨间简报", morningBriefText());
            return;
        }
        showCompanionVoiceWaiting(prefs.ownerAddress() + "，我整理一下昨晚睡眠、喝水和吃药，马上说给您听。");
        new Thread(() -> {
            try {
                String answer = askAssistantModel(deepSeekUserPrompt(
                        "请你扮演我的小助手，生成今天早上的语音晨间简报。"
                                + "要有温度，像家人在身边，不要像报表。"
                                + "请结合昨晚睡眠摘要、用药习惯、身体状况、今天状态，提醒喝水、吃药、必要时建议把睡眠情况跟医生说。"
                                + "三到五句话，短句，适合直接朗读。"));
                runOnUiThread(() -> showCompanionVoiceReply("晨间简报", answer));
            } catch (Exception ex) {
                runOnUiThread(() -> showCompanionVoiceReply("晨间简报", morningBriefText()));
            }
        }, "GouXiongMorningBrief").start();
    }

    private void addMorningBriefHeroCard() {
        LinearLayout card = cardContainer();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Theme.tintedCard(this, Theme.ORANGE));
        ImageView sun = designImage("ui_morning_sun_card", 64, ImageView.ScaleType.CENTER_CROP);
        card.addView(sun, new LinearLayout.LayoutParams(Theme.dp(this, 64), Theme.dp(this, 64)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        String owner = prefs.ownerAddress();
        if (owner == null || owner.trim().length() == 0) {
            owner = "主人";
        }
        words.addView(Theme.text(this, "早上好，" + owner, 21, Theme.TEXT, Typeface.BOLD), matchWrap());
        words.addView(Theme.text(this, "昨晚睡眠和今天关怀，我已经整理好了。", 14, Theme.MUTED, Typeface.BOLD), matchWrap());
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, -2, 1);
        wordsLp.setMargins(Theme.dp(this, 12), 0, 0, 0);
        card.addView(words, wordsLp);
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addMorningSleepReviewCard(SleepDashboardData data) {
        LinearLayout card = cardContainer();
        card.setPadding(Theme.dp(this, 14), Theme.dp(this, 12), Theme.dp(this, 14), Theme.dp(this, 12));
        card.addView(Theme.text(this, "昨晚睡眠回顾", 19, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(card, 8);
        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        addSleepMetric(metrics, "睡眠时长", sleepDurationText(data.totalSleepMinutes), Theme.BLUE);
        addSleepMetric(metrics, "睡眠质量", sleepQualityPhrase(data), data.highRiskCount > 0 ? Theme.RED : Theme.GREEN);
        addSleepMetric(metrics, "熟睡粗估", sleepDurationText(data.estimatedDeepSleepMinutes), Theme.GREEN);
        card.addView(metrics, matchWrap());
        addSpace(card, 8);
        addCareAdviceLine(card, "睡前自检", preSleepSelfCheckSummary(), preSleepSubjectiveReady() ? Theme.GREEN : Theme.ORANGE);
        TextView link = Theme.text(this, "查看完整报告 ›", 15, Theme.BLUE, Typeface.BOLD);
        link.setGravity(Gravity.RIGHT);
        link.setOnClickListener(v -> showSleepReport());
        card.addView(link, matchWrap());
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addTodayCareAdviceCard(SleepDashboardData data) {
        LinearLayout card = cardContainer();
        card.setPadding(Theme.dp(this, 14), Theme.dp(this, 12), Theme.dp(this, 14), Theme.dp(this, 12));
        card.addView(Theme.text(this, "今日关怀建议", 19, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(card, 8);
        addCareAdviceLine(card, "喝水提醒", prefs.hydrationReminderEnabled()
                ? prefs.hydrationStartHour() + ":00-" + prefs.hydrationEndHour() + ":00 每 " + prefs.hydrationIntervalMinutes() + " 分钟"
                : "未开启喝水提醒", prefs.hydrationReminderEnabled() ? Theme.GREEN : Theme.ORANGE);
        addCareAdviceLine(card, "久坐提醒", prefs.sedentaryReminderEnabled()
                ? prefs.sedentaryStartHour() + ":00-" + prefs.sedentaryEndHour() + ":00 每 " + prefs.sedentaryIntervalMinutes() + " 分钟"
                : "未开启久坐提醒", prefs.sedentaryReminderEnabled() ? Theme.BLUE : Theme.ORANGE);
        String med = prefs.medicationEnabled()
                ? formatMedicationTime() + " " + prefs.medicationName() + (prefs.medicationConfirmedToday() ? " 已确认" : " 待确认")
                : "还未设置吃药提醒";
        addCareAdviceLine(card, "吃药提醒", med, prefs.medicationEnabled() ? Theme.ORANGE : Theme.BLUE);
        addCareAdviceLine(card, "活动建议", data.highRiskCount > 0 ? "今天动作慢一点，必要时联系家人" : "散步 30 分钟，睡前少看屏幕", data.highRiskCount > 0 ? Theme.ORANGE : Theme.GREEN);
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addMorningCareActionCards() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addMorningCareActionCard(row,
                prefs.hydrationAcknowledgedAt() > 0 ? "已喝水" : "我喝水了",
                prefs.hydrationReminderEnabled() ? "每 " + prefs.hydrationIntervalMinutes() + " 分钟提醒" : "喝水提醒未开",
                Theme.GREEN,
                () -> {
                    prefs.markHydrationAcknowledgedNow();
                    Toast.makeText(this, "我记下了，今天慢慢喝水。", Toast.LENGTH_SHORT).show();
                    showMorningCare();
                });
        addMorningCareActionCard(row,
                prefs.medicationConfirmedToday() ? "今日已吃药" : "已吃药",
                prefs.medicationEnabled() ? formatMedicationTime() + " " + prefs.medicationName() : "吃药提醒未设置",
                Theme.ORANGE,
                () -> {
                    prefs.confirmMedicationNow();
                    Toast.makeText(this, "已记录今天吃药", Toast.LENGTH_SHORT).show();
                    showMorningCare();
                });
        content.addView(row, matchWrap());
        addSpace(content, 10);
    }

    private void addMorningCareActionCard(LinearLayout row, String title, String body, int color, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(Theme.dp(this, 10), Theme.dp(this, 14), Theme.dp(this, 10), Theme.dp(this, 14));
        card.setBackground(Theme.tintedCard(this, color));
        TextView titleView = Theme.text(this, title, 21, Theme.darken(color, 0.26f), Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        card.addView(titleView, matchWrap());
        TextView bodyView = Theme.text(this, compactForCard(body, 14), 13, Theme.MUTED, Typeface.BOLD);
        bodyView.setGravity(Gravity.CENTER);
        card.addView(bodyView, matchWrap());
        card.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Theme.dp(this, 86), 1);
        lp.setMargins(Theme.dp(this, 4), 0, Theme.dp(this, 4), 0);
        row.addView(card, lp);
    }

    private void addCareAdviceLine(LinearLayout card, String title, String body, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = Theme.text(this, "✓", 15, color, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Theme.rounded(Theme.mix(color, Color.WHITE, 0.86f), 12, this));
        row.addView(icon, new LinearLayout.LayoutParams(Theme.dp(this, 30), Theme.dp(this, 30)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Theme.text(this, title, 15, Theme.TEXT, Typeface.BOLD), matchWrap());
        words.addView(Theme.text(this, body, 13, Theme.MUTED, Typeface.NORMAL), matchWrap());
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, -2, 1);
        wordsLp.setMargins(Theme.dp(this, 10), 0, 0, 0);
        row.addView(words, wordsLp);
        card.addView(row, matchWrap());
        addSpace(card, 8);
    }

    private void addMorningQuickActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addMorningActionButton(row, "喝水了", Theme.GREEN, () -> {
            prefs.markHydrationAcknowledgedNow();
            Toast.makeText(this, "我记下了，今天慢慢喝水。", Toast.LENGTH_SHORT).show();
            showMorningCare();
        });
        addMorningActionButton(row, "已吃药", Theme.ORANGE, () -> {
            prefs.confirmMedicationNow();
            Toast.makeText(this, "已记录今天吃药", Toast.LENGTH_SHORT).show();
            showMorningCare();
        });
        content.addView(row, matchWrap());
        addSpace(content, 10);
        addSettingButton("小助手读给我听", this::requestMorningBriefVoice);
        addSettingButton("记录今天状态", this::showAssistantCheckIn);
        addSettingButton("返回首页", () -> showShell("guard"));
    }

    private void addMorningActionButton(LinearLayout row, String text, int color, Runnable action) {
        Button button = Theme.softButton(this, text, color);
        button.setTextSize(20);
        button.setMinHeight(Theme.dp(this, 64));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(Theme.dp(this, 4), 0, Theme.dp(this, 4), 0);
        row.addView(button, lp);
    }

    private String morningBriefText() {
        String owner = prefs.ownerAddress();
        StringBuilder b = new StringBuilder();
        b.append(owner).append("，早安。我把昨晚和今天要注意的事整理好了。");
        b.append("\n\n昨晚睡眠：").append(db.localReportText());
        if (prefs.medicationEnabled()) {
            b.append("\n\n吃药：").append(CompanionAssistant.medicationLine(prefs.companionRole(), prefs.medicationName(), prefs.medicationConfirmedToday()));
        } else {
            b.append("\n\n吃药：您还没告诉我要提醒什么药。等会儿直接说“帮我记一下每天早上吃什么药”，我会慢慢问清楚。");
        }
        b.append("\n\n喝水：白天我会直接叫您喝水，不让您自己找按钮。");
        b.append("\n\n睡前自检：").append(preSleepSelfCheckSummary());
        if (prefs.healthProfile().length() > 0) {
            b.append("\n\n身体情况我记着：").append(prefs.healthProfile()).append("。睡眠里反复憋醒、胸闷或明显不舒服时，请跟家人或医生说。");
        }
        b.append("\n\n这不是诊断，是我帮您整理的生活提醒。");
        return b.toString();
    }

    private void addMorningActionGrid() {
        addSectionTitle("接下来", null);
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        addSmallTile(row1, "♡\n聊几句", CompanionAssistant.roleColor(prefs.companionRole()), this::showCompanionChat);
        addSmallTile(row1, "☑\n今天状态", Theme.GREEN, this::showAssistantCheckIn);
        content.addView(row1, matchWrap());
        addSpace(content, 10);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        addSmallTile(row2, "💊\n吃药设置", Theme.ORANGE, this::showMedicationDialog);
        addSmallTile(row2, "▮\n睡眠记录", Theme.BLUE, this::showRecords);
        content.addView(row2, matchWrap());
        addSpace(content, 14);
    }

    private void addMorningHero() {
        LinearLayout hero = cardContainer();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(Theme.tintedCard(this, Theme.ORANGE));
        ImageView scene = designImage("ui_morning_scene_v2", 230, ImageView.ScaleType.CENTER_CROP);
        scene.setContentDescription("早安护理场景");
        hero.addView(scene, new LinearLayout.LayoutParams(-1, Theme.dp(this, 230)));
        addSpace(hero, 8);
        TextView title = Theme.text(this, "☀  早安护理", 32, Theme.ORANGE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        hero.addView(title, matchWrap());
        addSpace(hero, 8);
        TextView subtitle = Theme.text(this, "先喝水、确认吃药，再慢慢看昨晚报告。", 20, Theme.MUTED, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        hero.addView(subtitle, matchWrap());
        content.addView(hero, matchWrap());
        addSpace(content, 14);
    }

    private void showCompanionSettings() {
        content.removeAllViews();
        content.addView(Theme.text(this, "我的小助手", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addAssistantHero("当前角色", CompanionAssistant.styleSummary(prefs.companionRole()), false);
        addCompanionRoleGrid();
        addSettingButton(prefs.assistantPersonaConfigured() ? "打开聊天" : "打开聊天自动认识", this::showCompanionChat);
        addCard("我记住了", compactAssistantMemory(), prefs.assistantPersonaConfigured() ? Theme.GREEN : Theme.ORANGE);
        addSettingButton(prefs.serverRegistered() ? "联网账号已登录" : "手机号登录", this::showServerAccountSettings);
        addSettingButton("主人档案", this::showOwnerProfileSettings);
        addSettingButton("返回设置", this::showSettings);
    }

    private void addCompanionRoleGrid() {
        addSectionTitle("选择形象", "点一下就换，守护规则不变");
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        addCompanionRoleTile(row1, CompanionAssistant.ROLES[0]);
        addCompanionRoleTile(row1, CompanionAssistant.ROLES[1]);
        content.addView(row1, matchWrap());
        addSpace(content, 10);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        addCompanionRoleTile(row2, CompanionAssistant.ROLES[2]);
        addCompanionRoleTile(row2, CompanionAssistant.ROLES[3]);
        content.addView(row2, matchWrap());
        addSpace(content, 14);
    }

    private void addCompanionRoleTile(LinearLayout row, String role) {
        boolean selected = prefs.companionRole().equals(role);
        int color = selected ? Theme.GREEN : CompanionAssistant.roleColor(role);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(Theme.dp(this, 8), Theme.dp(this, 10), Theme.dp(this, 8), Theme.dp(this, 10));
        card.setBackground(Theme.tintedCard(this, color));
        card.setOnClickListener(v -> {
            prefs.setCompanionRole(role);
            Toast.makeText(this, "已选择" + role, Toast.LENGTH_SHORT).show();
            showCompanionSettings();
        });

        ImageView avatar = designImage(roleAvatarAssetName(role), 78, ImageView.ScaleType.FIT_CENTER);
        avatar.setContentDescription(role);
        LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(-1, Theme.dp(this, 78));
        imageLp.setMargins(Theme.dp(this, 4), 0, Theme.dp(this, 4), Theme.dp(this, 8));
        card.addView(avatar, imageLp);

        TextView label = Theme.text(this, role, 19, Theme.darken(color, 0.30f), Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        card.addView(label, matchWrap());
        if (selected) {
            TextView selectedText = Theme.text(this, "已选", 16, Theme.GREEN, Typeface.BOLD);
            selectedText.setGravity(Gravity.CENTER);
            card.addView(selectedText, matchWrap());
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(Theme.dp(this, 4), 0, Theme.dp(this, 4), 0);
        row.addView(card, lp);
    }

    private String compactAssistantMemory() {
        StringBuilder b = new StringBuilder();
        if (prefs.assistantPersonaConfigured()) {
            b.append("名字：").append(prefs.assistantName())
                    .append("；称呼您：").append(prefs.ownerAddress());
        } else {
            b.append("第一次聊天时，我会问名字和怎么称呼您。");
        }
        if (prefs.assistantCheckInToday()) {
            b.append("\n今天：").append(prefs.assistantCheckInSummary().replace("\n", "；"));
        }
        if (prefs.ownerProfileStarted()) {
            b.append("\n档案：已开始");
        }
        b.append("\n联网：必需");
        if (prefs.serverRegistered()) {
            b.append("，手机号已注册；真实模型状态由服务端返回");
        } else {
            b.append("，等待手机号注册");
        }
        return b.toString();
    }

    private void showAssistantCheckIn() {
        content.removeAllViews();
        content.addView(Theme.text(this, "今天状态", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addAssistantHero("我想了解你", CompanionAssistant.checkInIntro(prefs.companionRole()), false);
        addCard("当前记录", prefs.assistantCheckInSummary(), prefs.assistantCheckInToday() ? Theme.GREEN : Theme.ORANGE);
        addSettingButton("还不错，精神可以", () -> saveAssistantCheckIn("还不错", "精神可以", ""));
        addSettingButton("有点累，想安静点", () -> saveAssistantCheckIn("有点累", "偏低", "今天提醒轻一点"));
        addSettingButton("心情不好，想聊聊", () -> saveAssistantCheckIn("心情不好", "一般", "想多聊几句"));
        addSettingButton("身体不舒服，先记一下", () -> saveAssistantCheckIn("担心身体", "偏低", "身体不舒服，必要时联系家人或医生"));
        addSettingButton("手动填写一句", this::showAssistantCheckInDialog);
        addSettingButton("返回聊天", this::showCompanionChat);
        addSettingButton("返回首页", () -> showShell("guard"));
    }

    private void saveAssistantCheckIn(String mood, String energy, String note) {
        prefs.setAssistantCheckIn(mood, energy, note);
        maybeSyncCompanionInsight("今天状态：" + mood + "；精力：" + energy + "；补充：" + note, "daily_checkin");
        showCompanionReply("我记下了",
                CompanionAssistant.checkInReply(prefs.companionRole(), mood, energy, note));
    }

    private void showAssistantCheckInDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));

        EditText mood = new EditText(this);
        mood.setHint("心情，例如：还不错、有点烦、担心身体");
        mood.setText(prefs.assistantCheckInToday() ? prefs.assistantCheckInMood() : "");
        mood.setTextSize(20);
        box.addView(mood, matchWrap());

        EditText energy = new EditText(this);
        energy.setHint("精力，例如：精神可以、有点累、很困");
        energy.setText(prefs.assistantCheckInToday() ? prefs.assistantCheckInEnergy() : "");
        energy.setTextSize(20);
        box.addView(energy, matchWrap());

        EditText note = new EditText(this);
        note.setHint("补充一句，例如：今天头晕、想安静、想多聊聊");
        note.setText(prefs.assistantCheckInToday() ? prefs.assistantCheckInNote() : "");
        note.setTextSize(20);
        note.setMinLines(2);
        box.addView(note, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("记录今天状态")
                .setMessage("用于小助手今天给出更贴近你的建议。不要填写身份证、银行卡等敏感信息。")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> saveAssistantCheckIn(
                        mood.getText().toString(),
                        energy.getText().toString(),
                        note.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAssistantPersonaDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));

        TextView hint = Theme.text(this,
                "您可以像聊天一样说：以后叫你暖暖，像女儿一样陪我，你叫我奶奶，听话一点，多哄我开心。",
                18, Theme.MUTED, Typeface.NORMAL);
        box.addView(hint, matchWrap());

        EditText message = new EditText(this);
        message.setHint("直接说一句就行");
        if (prefs.assistantPersonaConfigured()) {
            message.setText("以后叫你" + prefs.assistantName() + "，" + prefs.assistantIdentity());
        }
        message.setTextSize(20);
        message.setMinLines(3);
        message.setSingleLine(false);
        message.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(message, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("和小助手说一句")
                .setMessage("小助手会从这句话里记住名字和身份。")
                .setView(box)
                .setPositiveButton("说好了", (d, w) -> saveAssistantPersonaFromMessage(message.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveAssistantPersonaFromMessage(String message) {
        String clean = message == null ? "" : message.trim();
        if (clean.length() == 0) {
            Toast.makeText(this, "可以先说一句想怎么称呼我", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = extractAssistantName(clean);
        String ownerAddress = extractOwnerAddress(clean);
        String identity = clean;
        if (identity.length() > 60) {
            identity = identity.substring(0, 60);
        }
        saveAssistantPersona(name, identity, ownerAddress);
    }

    private String extractAssistantName(String text) {
        String[] markers = {"叫你做", "以后叫你", "叫你", "你叫", "名字叫", "以后叫"};
        for (String marker : markers) {
            int start = text.indexOf(marker);
            if (start >= 0) {
                String tail = text.substring(start + marker.length()).trim();
                if ("你叫".equals(marker) && tail.startsWith("我")) {
                    continue;
                }
                StringBuilder name = new StringBuilder();
                for (int i = 0; i < tail.length() && name.length() < 8; i++) {
                    char ch = tail.charAt(i);
                    if (ch == '，' || ch == ',' || ch == '。' || ch == '；' || ch == ';'
                            || ch == ' ' || ch == '\n' || ch == '像' || ch == '当' || ch == '做') {
                        break;
                    }
                    name.append(ch);
                }
                if (name.length() > 0) return name.toString();
            }
        }
        return prefs.assistantPersonaConfigured() ? prefs.assistantName() : "小熊";
    }

    private void saveAssistantPersona(String name, String identity) {
        saveAssistantPersona(name, identity, prefs.ownerAddress());
    }

    private String extractOwnerAddress(String text) {
        String[] markers = {"以后叫我", "你叫我", "叫我做", "称呼我为", "你称呼我", "称呼我", "叫我", "喊我", "我叫", "叫主人"};
        for (String marker : markers) {
            int start = text.indexOf(marker);
            if (start >= 0) {
                String tail = text.substring(start + marker.length()).trim();
                StringBuilder name = new StringBuilder();
                for (int i = 0; i < tail.length() && name.length() < 8; i++) {
                    char ch = tail.charAt(i);
                    if (ch == '，' || ch == ',' || ch == '。' || ch == '；' || ch == ';'
                            || ch == ' ' || ch == '\n' || ch == '像' || ch == '当' || ch == '做') {
                        break;
                    }
                    name.append(ch);
                }
                if (name.length() > 0) return name.toString();
            }
        }
        return prefs.assistantPersonaConfigured() ? prefs.ownerAddress() : "主人";
    }

    private void saveAssistantPersona(String name, String identity, String ownerAddress) {
        prefs.setAssistantPersona(name, identity, ownerAddress);
        firstMeetingPromptedThisSession = true;
        syncOwnerProfileQuiet("persona");
        showCompanionVoiceReply("认识好了",
                CompanionAssistant.firstMeetingDone(prefs.assistantName(), prefs.assistantIdentity(), prefs.ownerAddress()));
    }

    private void addCompanionChoice(String role, String desc) {
        LinearLayout card = cardContainer();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView avatar = designImage(roleAssetName(role), 92, ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(Theme.dp(this, 112), Theme.dp(this, 92));
        avatarLp.setMargins(0, 0, Theme.dp(this, 12), 0);
        row.addView(avatar, avatarLp);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Theme.text(this, (prefs.companionRole().equals(role) ? "当前 · " : "") + role, 21, CompanionAssistant.roleColor(role), Typeface.BOLD), matchWrap());
        addSpace(words, 4);
        words.addView(Theme.text(this, desc + "\n声音：" + CompanionAssistant.voiceSummary(role), 17, Theme.MUTED, Typeface.NORMAL), matchWrap());
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row, matchWrap());
        addSpace(card, 10);

        Button choose = Theme.button(this, prefs.companionRole().equals(role) ? "已选择" : "设为我的助手", prefs.companionRole().equals(role) ? Theme.GREEN : Theme.BLUE);
        choose.setTextSize(18);
        choose.setMinHeight(Theme.dp(this, 52));
        choose.setOnClickListener(v -> {
            prefs.setCompanionRole(role);
            Toast.makeText(this, "已选择" + role, Toast.LENGTH_SHORT).show();
            showCompanionSettings();
        });
        card.addView(choose, matchWrap());
        addSpace(card, 8);

        Button sample = Theme.button(this, "听一句", CompanionAssistant.roleColor(role));
        sample.setTextSize(18);
        sample.setMinHeight(Theme.dp(this, 52));
        sample.setOnClickListener(v -> speakAssistantTextForRole(CompanionAssistant.sampleLine(role), role));
        card.addView(sample, matchWrap());

        content.addView(card, matchWrap());
        addSpace(content, 12);
    }

    private void showOwnerProfileSettings() {
        content.removeAllViews();
        addSimplePageHeader("主人档案", "编辑", this::showOwnerProfileDialog);
        addOwnerProfileHeroCard();
        addOwnerMemoryList();
    }

    private void addOwnerProfileHeroCard() {
        LinearLayout card = cardContainer();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Theme.tintedCard(this, CompanionAssistant.roleColor(prefs.companionRole())));
        ImageView avatar = designImage("ui_owner_profile_avatar", 76, ImageView.ScaleType.CENTER_CROP);
        card.addView(avatar, new LinearLayout.LayoutParams(Theme.dp(this, 78), Theme.dp(this, 78)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        String name = prefs.ownerAddress();
        if (name == null || name.trim().length() == 0) {
            name = "主人";
        }
        words.addView(Theme.text(this, name, 22, Theme.TEXT, Typeface.BOLD), matchWrap());
        words.addView(Theme.text(this, "档案：" + (prefs.ownerProfileStarted() ? "已建立" : "待完善"), 15, prefs.ownerProfileStarted() ? Theme.GREEN : Theme.ORANGE, Typeface.BOLD), matchWrap());
        words.addView(Theme.text(this, "记忆：" + (prefs.serverRegistered() ? "云端已开启" : "本机保存"), 14, Theme.MUTED, Typeface.BOLD), matchWrap());
        words.addView(Theme.text(this, prefs.assistantCheckInToday() ? "今日状态已记录" : "今日状态待记录", 14, Theme.MUTED, Typeface.BOLD), matchWrap());
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, -2, 1);
        wordsLp.setMargins(Theme.dp(this, 12), 0, 0, 0);
        card.addView(words, wordsLp);
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addOwnerMemoryList() {
        LinearLayout card = cardContainer();
        card.setPadding(Theme.dp(this, 14), Theme.dp(this, 12), Theme.dp(this, 14), Theme.dp(this, 12));
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Theme.text(this, "我的记忆", 19, Theme.TEXT, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
        TextView all = Theme.text(this, "全部 ›", 14, Theme.MUTED, Typeface.BOLD);
        all.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        all.setOnClickListener(v -> showOwnerProfileDialog());
        header.addView(all, new LinearLayout.LayoutParams(Theme.dp(this, 72), -2));
        card.addView(header, matchWrap());
        addSpace(card, 10);
        addMemoryLine(card, "健康与用药", ownerMemoryBody(prefs.healthProfile(), prefs.medicationHabits(), "还没有填写健康和用药习惯"), prefs.healthProfile().length() + prefs.medicationHabits().length() > 0 ? Theme.GREEN : Theme.ORANGE, () -> showOwnerProfileWizard(0));
        addMemoryLine(card, "睡眠与关怀", ownerMemoryBody(prefs.sleepSituation(), prefs.carePreference(), "还没有填写睡眠偏好和关怀方式"), prefs.sleepSituation().length() + prefs.carePreference().length() > 0 ? Theme.BLUE : Theme.ORANGE, () -> showOwnerProfileWizard(2));
        addMemoryLine(card, "今天状态", prefs.assistantCheckInSummary(), prefs.assistantCheckInToday() ? Theme.GREEN : Theme.ORANGE, this::showAssistantCheckIn);
        String objectMemory = prefs.importantObjectMemory();
        addMemoryLine(card, "常用物品", objectMemory.length() > 0 ? objectMemory : "还没有记录物品位置", objectMemory.length() > 0 ? Theme.BLUE : Theme.ORANGE, this::showCompanionVision);
        addMemoryLine(card, "主动关怀", prefs.assistantProactiveCareEnabled() ? "已开启，每天最多主动问候一次" : "已关闭，只在点开时回应", prefs.assistantProactiveCareEnabled() ? Theme.GREEN : Theme.ORANGE, () -> {
            prefs.setAssistantProactiveCareEnabled(!prefs.assistantProactiveCareEnabled());
            showOwnerProfileSettings();
        });
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private String ownerMemoryBody(String first, String second, String fallback) {
        String cleanFirst = first == null ? "" : first.trim();
        String cleanSecond = second == null ? "" : second.trim();
        if (cleanFirst.length() > 0 && cleanSecond.length() > 0) {
            return cleanFirst + "；" + cleanSecond;
        }
        if (cleanFirst.length() > 0) {
            return cleanFirst;
        }
        if (cleanSecond.length() > 0) {
            return cleanSecond;
        }
        return fallback;
    }

    private void addMemoryLine(LinearLayout card, String title, String body, int color, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Theme.dp(this, 2), 0, Theme.dp(this, 8));
        TextView dot = Theme.text(this, "✓", 15, color, Typeface.BOLD);
        dot.setGravity(Gravity.CENTER);
        dot.setBackground(Theme.rounded(Theme.mix(color, Color.WHITE, 0.86f), 12, this));
        row.addView(dot, new LinearLayout.LayoutParams(Theme.dp(this, 30), Theme.dp(this, 30)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Theme.text(this, title, 15, Theme.TEXT, Typeface.BOLD), matchWrap());
        words.addView(Theme.text(this, compactForCard(body, 34), 13, Theme.MUTED, Typeface.NORMAL), matchWrap());
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, -2, 1);
        wordsLp.setMargins(Theme.dp(this, 10), 0, 0, 0);
        row.addView(words, wordsLp);
        TextView arrow = Theme.text(this, "›", 18, Theme.MUTED, Typeface.BOLD);
        arrow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(arrow, new LinearLayout.LayoutParams(Theme.dp(this, 22), -2));
        row.setOnClickListener(v -> action.run());
        card.addView(row, matchWrap());
    }

    private void showOwnerProfileWizard(int step) {
        int safeStep = Math.max(0, Math.min(OWNER_PROFILE_STEP_COUNT - 1, step));
        String label = ownerProfileStepTitle(safeStep);
        content.removeAllViews();
        content.addView(Theme.text(this, "小助手建档", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addAssistantHero("第 " + (safeStep + 1) + " 项", CompanionAssistant.profileWizardIntro(prefs.companionRole(), label), false);
        addCard("进度", (safeStep + 1) + "/" + OWNER_PROFILE_STEP_COUNT + " · " + label
                + "\n可以少填、跳过，也可以以后再改。小助手会用这些摘要给出更贴近你的建议。", Theme.BLUE);
        addCard("怎么填", ownerProfileStepExample(safeStep), Theme.GREEN);

        EditText input = profileEditText(ownerProfileStepHint(safeStep));
        input.setText(ownerProfileStepValue(safeStep));
        content.addView(input, matchWrap());
        addSpace(content, 10);

        addSettingButton(safeStep == OWNER_PROFILE_STEP_COUNT - 1 ? "保存并完成" : "保存，下一项",
                () -> saveOwnerProfileWizardStep(safeStep, input.getText().toString(), safeStep + 1));
        addSettingButton(safeStep == OWNER_PROFILE_STEP_COUNT - 1 ? "跳过并完成" : "跳过，下一项",
                () -> {
                    if (safeStep + 1 >= OWNER_PROFILE_STEP_COUNT) {
                        showOwnerProfileWizardDone();
                    } else {
                        showOwnerProfileWizard(safeStep + 1);
                    }
                });
        if (safeStep > 0) {
            addSettingButton("上一项", () -> showOwnerProfileWizard(safeStep - 1));
        }
        addSettingButton("返回档案页", this::showOwnerProfileSettings);
    }

    private void saveOwnerProfileWizardStep(int step, String value, int nextStep) {
        saveOwnerProfileField(step, value);
        if (nextStep >= OWNER_PROFILE_STEP_COUNT) {
            showOwnerProfileWizardDone();
        } else {
            showOwnerProfileWizard(nextStep);
        }
    }

    private void showOwnerProfileWizardDone() {
        content.removeAllViews();
        content.addView(Theme.text(this, "建档完成", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addAssistantHero("我更懂你了", CompanionAssistant.profileWizardDone(prefs.companionRole()), false);
        addCard("当前档案", prefs.ownerProfileSummary(), prefs.ownerProfileStarted() ? Theme.GREEN : Theme.ORANGE);
        addCard("今天关怀", proactiveCareText(), prefs.ownerProfileStarted() ? Theme.GREEN : Theme.ORANGE);
        addSettingButton("看看今天关怀建议", () -> showCompanionReply("今天关怀建议", proactiveCareText()));
        addSettingButton("继续聊天", this::showCompanionChat);
        addSettingButton("返回档案页", this::showOwnerProfileSettings);
    }

    private String ownerProfileStepTitle(int step) {
        if (step == 0) return "身体状况";
        if (step == 1) return "用药习惯";
        if (step == 2) return "睡眠情况";
        if (step == 3) return "家庭情况";
        if (step == 4) return "兴趣爱好";
        return "关怀偏好";
    }

    private String ownerProfileStepHint(int step) {
        if (step == 0) return "例如：高血压、糖尿病、容易头晕；没有可写“目前没有特别不舒服”";
        if (step == 1) return "例如：早上降压药，饭后吃；没有可写“暂时没有固定用药”";
        if (step == 2) return "例如：打鼾、夜醒、午睡、怕吵、容易做噩梦";
        if (step == 3) return "例如：独居、和老伴住、孩子住附近、紧急时先联系谁";
        if (step == 4) return "例如：散步、听戏、象棋、养花、听轻音乐";
        return "例如：提醒轻一点、少说话、早晨多鼓励、难受时先联系家人";
    }

    private String ownerProfileStepExample(int step) {
        if (step == 0) return "只写你愿意告诉小助手的情况，我会把提醒说得更贴心。";
        if (step == 1) return "这里是生活提醒。把您平时固定吃的药和时间写上，我会按点轻轻提醒。";
        if (step == 2) return "可以写睡眠习惯和困扰。比如怕吵、常夜醒、午睡多、经常打鼾。";
        if (step == 3) return "这能帮助小助手在高风险无确认时，提醒你按设置联系家人。电话仍以紧急联系人页为准。";
        if (step == 4) return "兴趣会用于白天陪伴和情绪关怀，比如建议听一会儿喜欢的戏曲或慢慢散步。";
        return "偏好会影响小助手说话方式，比如更安静、更简短、更多鼓励。";
    }

    private String ownerProfileStepValue(int step) {
        if (step == 0) return prefs.healthProfile();
        if (step == 1) return prefs.medicationHabits();
        if (step == 2) return prefs.sleepSituation();
        if (step == 3) return prefs.familySituation();
        if (step == 4) return prefs.hobbies();
        return prefs.carePreference();
    }

    private void saveOwnerProfileField(int step, String value) {
        String health = prefs.healthProfile();
        String medication = prefs.medicationHabits();
        String sleep = prefs.sleepSituation();
        String family = prefs.familySituation();
        String hobbies = prefs.hobbies();
        String care = prefs.carePreference();
        if (step == 0) health = value;
        else if (step == 1) medication = value;
        else if (step == 2) sleep = value;
        else if (step == 3) family = value;
        else if (step == 4) hobbies = value;
        else care = value;
        prefs.setOwnerProfile(health, medication, sleep, family, hobbies, care);
        syncOwnerProfileQuiet("profile_wizard");
        Toast.makeText(this, "已保存" + ownerProfileStepTitle(step), Toast.LENGTH_SHORT).show();
    }

    private void showOwnerProfileDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));

        EditText health = profileEditText("身体状况，例如：高血压、糖尿病、容易头晕；可留空");
        health.setText(prefs.healthProfile());
        box.addView(health, matchWrap());

        EditText medication = profileEditText("用药习惯，例如：早上降压药，饭后吃；可留空");
        medication.setText(prefs.medicationHabits());
        box.addView(medication, matchWrap());

        EditText sleep = profileEditText("睡眠情况，例如：打鼾、夜醒、午睡、怕吵；可留空");
        sleep.setText(prefs.sleepSituation());
        box.addView(sleep, matchWrap());

        EditText family = profileEditText("家庭情况，例如：独居、和老伴住、孩子电话；可留空");
        family.setText(prefs.familySituation());
        box.addView(family, matchWrap());

        EditText hobbies = profileEditText("兴趣爱好，例如：散步、听戏、象棋、养花；可留空");
        hobbies.setText(prefs.hobbies());
        box.addView(hobbies, matchWrap());

        EditText care = profileEditText("关怀偏好，例如：提醒轻一点、少说话、早晨多鼓励；可留空");
        care.setText(prefs.carePreference());
        box.addView(care, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("主人档案")
                .setMessage("这些信息会用于小助手的生活建议，不做诊断。不要填写身份证、银行卡等敏感信息。")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.setOwnerProfile(
                            health.getText().toString(),
                            medication.getText().toString(),
                            sleep.getText().toString(),
                            family.getText().toString(),
                            hobbies.getText().toString(),
                            care.getText().toString());
                    syncOwnerProfileQuiet("profile_edit");
                    Toast.makeText(this, "已保存主人档案", Toast.LENGTH_SHORT).show();
                    showOwnerProfileSettings();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText profileEditText(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(18);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setSingleLine(false);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return input;
    }

    private void showCompanionChat() {
        stopRealtimeVoiceChat(false);
        int pageSerial = ++voicePageSerial;
        hideBottomNavForLiveCompanion();
        content.removeAllViews();
        String opening = prefs.assistantPersonaConfigured()
                ? CompanionAssistant.chatIntro(prefs.companionRole())
                : CompanionAssistant.firstMeetingIntro(prefs.companionRole());
        addLiveCompanionStage("我在这里，您直接说", opening, "listening");
        if (requestRequiredPermissionsForLiveChatIfNeeded()) {
            return;
        }
        if (!prefs.debugCompanionUiTestMode()) {
            content.postDelayed(() -> {
                if (pageSerial == voicePageSerial) startRealtimeVoiceChat();
            }, 350);
            content.postDelayed(this::maybeStartAutoVisionScan, 500);
            content.postDelayed(this::fetchServerMessagesForReadingSilently, 900);
        }
    }

    private void hideBottomNavForLiveCompanion() {
        if (navBar != null) {
            navBar.setVisibility(View.GONE);
        }
    }

    private void addLiveCompanionStage(String status, String speech, String mood) {
        String role = prefs.companionRole();
        String name = assistantDisplayName();
        liveStageMood = mood == null ? "listening" : mood;
        liveStageStatusLabel = null;
        liveStageSpeechLabel = null;
        liveDigitalHumanLabel = null;
        voiceStatusLabel = null;
        liveStageAvatar = null;
        int animationSerial = ++liveStageAnimationSerial;
        LinearLayout stage = cardContainer();
        stage.setPadding(Theme.dp(this, 12), Theme.dp(this, 10), Theme.dp(this, 12), Theme.dp(this, 14));
        stage.setBackground(Theme.tintedCard(this, CompanionAssistant.roleColor(role)));
        stage.setOnClickListener(v -> interruptForUserSpeech());

        addLiveCompanionTopBar(stage);

        View avatarStage = createLiveAvatarStage(role, name, liveStageMood, animationSerial);
        stage.addView(avatarStage, imageLp(318));
        addSpace(stage, 8);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(Theme.dp(this, 18), Theme.dp(this, 13), Theme.dp(this, 18), Theme.dp(this, 13));
        bubble.setBackground(Theme.rounded(Theme.mix(CompanionAssistant.roleColor(role), Theme.WARM_WHITE, 0.90f), 22, this));
        TextView line = Theme.text(this, compactLiveBubbleText(speech), 22, Theme.TEXT, Typeface.BOLD);
        line.setGravity(Gravity.CENTER);
        line.setMinHeight(Theme.dp(this, 82));
        liveStageSpeechLabel = line;
        bubble.addView(line, matchWrap());
        stage.addView(bubble, matchWrap());
        addSpace(stage, 12);

        addXiaozhiVoiceMeter(stage, CompanionAssistant.roleColor(role));
        addSpace(stage, 10);

        addLiveConversationControls(stage);
        addSpace(stage, 8);
        voiceStatusLabel = Theme.text(this, status, 18, Theme.MUTED, Typeface.BOLD);
        voiceStatusLabel.setGravity(Gravity.CENTER);
        voiceStatusLabel.setMinHeight(Theme.dp(this, 28));
        stage.addView(voiceStatusLabel, matchWrap());

        content.addView(stage, matchWrap());
    }

    private void addLiveCompanionTopBar(LinearLayout stage) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView menu = Theme.text(this, "☰", 22, Theme.TEXT, Typeface.BOLD);
        menu.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(menu, new LinearLayout.LayoutParams(Theme.dp(this, 42), Theme.dp(this, 38)));
        TextView title = Theme.text(this, assistantDisplayName(), 20, Theme.TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        row.addView(title, new LinearLayout.LayoutParams(0, Theme.dp(this, 38), 1));
        TextView sound = Theme.text(this, "●", 18, Color.WHITE, Typeface.BOLD);
        sound.setGravity(Gravity.CENTER);
        sound.setBackground(Theme.rounded(Theme.MUTED, 20, this));
        row.addView(sound, new LinearLayout.LayoutParams(Theme.dp(this, 38), Theme.dp(this, 38)));
        stage.addView(row, matchWrap());
        addSpace(stage, 4);
    }

    private void addLiveConversationControls(LinearLayout stage) {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button talk = Theme.button(this, "🎙  按住说话", Theme.GREEN);
        talk.setTextSize(24);
        talk.setMinHeight(Theme.dp(this, 70));
        talk.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                interruptForUserSpeech();
                updateVoiceStatus("我在听，您慢慢说。");
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                restartRealtimeListeningSoon(120);
                return true;
            }
            return true;
        });
        LinearLayout.LayoutParams talkLp = new LinearLayout.LayoutParams(0, -2, 1);
        talkLp.setMargins(0, 0, Theme.dp(this, 6), 0);
        actions.addView(talk, talkLp);

        Button end = Theme.softButton(this, "☎  结束对话", Theme.RED);
        end.setTextSize(24);
        end.setMinHeight(Theme.dp(this, 70));
        end.setOnClickListener(v -> {
            stopRealtimeVoiceChat(true);
            stopAssistantSpeech();
            showShell("guard");
        });
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(0, -2, 1);
        endLp.setMargins(Theme.dp(this, 6), 0, 0, 0);
        actions.addView(end, endLp);
        stage.addView(actions, matchWrap());
    }

    private void refreshLiveDigitalHumanLabel(int serial) {
        new Thread(() -> {
            try {
                ServerApiClient.ServerHealth health = ServerApiClient.health(prefs.serverBaseUrl());
                String line;
                if (health.linlyDigitalHumanConfigured) {
                    line = "Linly-Talker 已接入 · " + health.linlyDigitalHumanAvatarEngine + " · 动画同步";
                } else if (health.local2dAvatarView && health.avatarStateMachine) {
                    line = "本机 2D 数字人动画 · Linly-Talker 未配置";
                } else {
                    line = "数字人动画检测未完成";
                }
                runOnUiThread(() -> {
                    if (serial == liveStageAnimationSerial && liveDigitalHumanLabel != null) {
                        liveDigitalHumanLabel.setText(line);
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (serial == liveStageAnimationSerial && liveDigitalHumanLabel != null) {
                        liveDigitalHumanLabel.setText("本机 2D 数字人动画 · 服务端稍后重连");
                    }
                });
            }
        }, "GouXiongLiveDigitalHumanBadge").start();
    }

    private void addAssistantChatModeSwitch(LinearLayout stage, boolean videoMode, String name) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button mode = Theme.softButton(this, videoMode ? "文" : "视频", videoMode ? Theme.BLUE : CompanionAssistant.roleColor(prefs.companionRole()));
        mode.setTextSize(videoMode ? 22 : 18);
        mode.setMinHeight(Theme.dp(this, 50));
        mode.setContentDescription(videoMode ? "切换到文字聊天" : "切换到视频聊天");
        mode.setOnClickListener(v -> toggleAssistantChatMode());
        mode.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                toggleAssistantChatMode();
                return true;
            }
            return event.getAction() == MotionEvent.ACTION_DOWN;
        });
        if (videoMode) {
            row.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(Theme.dp(this, 76), -2);
            row.addView(mode, modeLp);
        } else {
            TextView nameView = Theme.text(this, name, 30, Theme.TEXT, Typeface.BOLD);
            nameView.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, -2, 1);
            row.addView(nameView, nameLp);
            LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(Theme.dp(this, 132), -2);
            modeLp.setMargins(Theme.dp(this, 10), 0, 0, 0);
            row.addView(mode, modeLp);
        }
        stage.addView(row, matchWrap());
        addSpace(stage, 8);
    }

    private void toggleAssistantChatMode() {
        prefs.setAssistantVideoChatMode(!prefs.assistantVideoChatMode());
        stopAssistantSpeech();
        showCompanionChat();
    }

    private String assistantDisplayName() {
        if (prefs.assistantPersonaConfigured()) {
            return prefs.assistantName();
        }
        return prefs.companionRole();
    }

    private String liveMoodText(String mood) {
        if ("idle".equals(mood)) return "安静陪着您";
        if ("thinking".equals(mood)) return "正在想一想";
        if ("speaking".equals(mood)) return "正在慢慢说";
        if ("interrupted".equals(mood)) return "刚刚停下来听您";
        if ("user_speaking".equals(mood)) return "正在认真听您说";
        if ("seeing".equals(mood)) return "正在看一眼";
        if ("reading".equals(mood)) return "正在认真读";
        if ("finding".equals(mood)) return "正在帮您找";
        if ("comforting".equals(mood)) return "正在安抚您";
        if ("happy".equals(mood)) return "正在开心陪您";
        if ("worried".equals(mood)) return "有点担心您";
        if ("urgent_wakeup".equals(mood)) return "正在紧急叫醒";
        return "正在听您说";
    }

    private void updateLiveStageStatus(String status, String mood) {
        liveStageMood = mood == null ? "listening" : mood;
        if (liveStageStatusLabel != null) {
            liveStageStatusLabel.setText(status == null ? "" : status);
        }
        applyLiveAvatarState(liveStageMood);
        if (liveStageAvatar != null) {
            liveStageAvatar.setContentDescription(assistantDisplayName() + "，" + liveMoodText(liveStageMood));
        }
    }

    private void updateLiveStageSpeech(String speech) {
        if (liveStageSpeechLabel != null) {
            liveStageSpeechLabel.setText(speech == null ? "" : speech);
        }
    }

    private View createLiveAvatarStage(String role, String name, String mood, int serial) {
        return createBitmapAvatarFallbackStage(role, name, mood);
    }

    private View createBitmapAvatarFallbackStage(String role, String name, String mood) {
        liveStageAvatar = new AvatarView(this);
        liveStageAvatar.setRole(role);
        liveStageAvatar.setCharacterResource(liveAvatarResourceId(role));
        liveStageAvatar.setCharacterBitmapMode(true);
        liveStageAvatar.setAnimationEnabled(!prefs.debugCompanionUiTestMode());
        liveStageAvatar.applyCommand(AvatarCommand.setState(avatarStateForMood(mood)));
        liveStageAvatar.setContentDescription(name + "，" + liveMoodText(mood));
        liveStageAvatar.setOnClickListener(v -> interruptForUserSpeech());
        return liveStageAvatar;
    }

    private AvatarState avatarStateForMood(String mood) {
        return AvatarState.fromMood(mood);
    }

    private void applyLiveAvatarState(String mood) {
        if (liveStageAvatar == null) return;
        liveStageAvatar.applyCommand(AvatarCommand.setState(avatarStateForMood(mood)));
    }

    private void startAvatarSpeaking() {
        if (liveStageAvatar != null) {
            liveStageAvatar.applyCommand(AvatarCommand.startSpeaking());
            int serial = ++avatarMouthSerial;
            liveStageAvatar.postDelayed(() -> driveTtsMouth(serial, 0), 60L);
        }
    }

    private void driveTtsMouth(int serial, int tick) {
        if (serial != avatarMouthSerial || liveStageAvatar == null || !assistantSpeaking) {
            return;
        }
        float wave = (float) Math.abs(Math.sin((System.currentTimeMillis() + tick * 47L) / 96d));
        float level = 0.18f + wave * 0.54f;
        liveStageAvatar.applyCommand(AvatarCommand.mouthLevel(level));
        liveStageAvatar.postDelayed(() -> driveTtsMouth(serial, tick + 1), 82L);
    }

    private void stopAvatarSpeaking() {
        avatarMouthSerial++;
        if (liveStageAvatar == null) return;
        liveStageAvatar.applyCommand(AvatarCommand.stopSpeaking());
        liveStageAvatar.applyCommand(AvatarCommand.mouthLevel(0f));
    }

    private void updateAvatarMouthFromPcm(byte[] pcmFrame) {
        if (pcmFrame == null || pcmFrame.length < 2) return;
        float level = pcm16Level(pcmFrame);
        int serial = ++livePcmMouthSerial;
        if (liveStageAvatar == null) return;
        liveStageAvatar.applyCommand(AvatarCommand.startSpeaking());
        liveStageAvatar.applyCommand(AvatarCommand.mouthLevel(level));
        liveStageAvatar.postDelayed(() -> {
            if (serial == livePcmMouthSerial && liveStageAvatar != null
                    && System.currentTimeMillis() - lastLiveModelAudioFrameAtMs > 320L
                    && !assistantSpeaking) {
                liveStageAvatar.applyCommand(AvatarCommand.stopSpeaking());
                liveStageAvatar.applyCommand(AvatarCommand.setState(avatarStateForMood(liveStageMood)));
            }
        }, 380L);
    }

    private float pcm16Level(byte[] pcmFrame) {
        if (pcmFrame == null || pcmFrame.length < 2) return 0f;
        long sum = 0L;
        int count = 0;
        for (int i = 0; i + 1 < pcmFrame.length; i += 2) {
            int low = pcmFrame[i] & 0xff;
            int high = pcmFrame[i + 1];
            short sample = (short) ((high << 8) | low);
            sum += (long) sample * (long) sample;
            count++;
        }
        if (count == 0) return 0f;
        double rms = Math.sqrt(sum / (double) count) / 32768d;
        return Math.max(0f, Math.min(1f, (float) (rms * 5.2d)));
    }

    private void addLiveStateDots(LinearLayout stage, int color, int serial) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setMinimumHeight(Theme.dp(this, LIVE_DOT_ROW_HEIGHT_DP));
        for (int i = 0; i < 3; i++) {
            View dot = new View(this);
            dot.setBackground(Theme.rounded(Theme.mix(color, Theme.WARM_WHITE, 0.25f), 12, this));
            dot.setPivotY(Theme.dp(this, LIVE_DOT_MAX_HEIGHT_DP));
            dot.setScaleY(0.58f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Theme.dp(this, LIVE_DOT_WIDTH_DP),
                    Theme.dp(this, LIVE_DOT_MAX_HEIGHT_DP));
            lp.setMargins(Theme.dp(this, 5), 0, Theme.dp(this, 5), 0);
            row.addView(dot, lp);
        }
        stage.addView(row, new LinearLayout.LayoutParams(-1, Theme.dp(this, LIVE_DOT_ROW_HEIGHT_DP)));
        if (!prefs.debugCompanionUiTestMode()) {
            row.postDelayed(() -> animateLiveStateDots(row, color, serial, 0), 60);
        }
    }

    private void animateLiveStateDots(LinearLayout row, int color, int serial, int tick) {
        if (row.getWindowToken() == null || serial != liveStageAnimationSerial) {
            return;
        }
        String mood = liveStageMood == null ? "listening" : liveStageMood;
        int childCount = row.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View dot = row.getChildAt(i);
            boolean speaking = "speaking".equals(mood);
            boolean thinking = "thinking".equals(mood);
            int phase = (tick + i) % childCount;
            float scale = speaking
                    ? 0.58f + phase * 0.18f
                    : (thinking ? 0.5f + (2 - phase) * 0.16f : 0.56f + (phase == 0 ? 0.22f : 0.0f));
            dot.setScaleY(Math.min(1.0f, scale));
            dot.setAlpha(0.42f + phase * 0.22f);
            dot.setBackground(Theme.rounded(Theme.mix(color, Theme.WARM_WHITE, phase / 4f), 12, this));
        }
        row.postDelayed(() -> animateLiveStateDots(row, color, serial, tick + 1),
                "speaking".equals(mood) ? 170 : ("thinking".equals(mood) ? 260 : 520));
    }

    private String liveBubbleText(String text) {
        String clean = text == null ? "" : text.replace("\n", " ").trim();
        if (clean.length() <= 86) {
            return clean;
        }
        return clean.substring(0, 86) + "……";
    }

    private String compactLiveBubbleText(String text) {
        String clean = text == null ? "" : text.replace("\n", " ").trim();
        if (clean.length() <= 34) {
            return clean;
        }
        return clean.substring(0, 34) + "……";
    }

    private void addRealtimeVoicePanel() {
        LinearLayout card = cardContainer();
        int roleColor = CompanionAssistant.roleColor(prefs.companionRole());
        card.setBackground(Theme.tintedCard(this, roleColor));
        card.setPadding(Theme.dp(this, 16), Theme.dp(this, 14), Theme.dp(this, 16), Theme.dp(this, 16));
        card.addView(Theme.text(this, "实时陪伴", 21, roleColor, Typeface.BOLD), matchWrap());
        addSpace(card, 4);
        voiceStatusLabel = Theme.text(this, "我在听您说话，不用按发送。", 18, Theme.MUTED, Typeface.NORMAL);
        voiceStatusLabel.setMinHeight(Theme.dp(this, 30));
        card.addView(voiceStatusLabel, matchWrap());
        addSpace(card, 8);
        addXiaozhiVoiceMeter(card, roleColor);
        addSpace(card, 10);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        addLiveActionButton(actions, "暂停", Theme.ORANGE, () -> {
            stopRealtimeVoiceChat(true);
            stopAssistantSpeech();
            updateVoiceStatus("我先安静等您。要继续时点小助手。");
        }, false);
        addLiveActionButton(actions, "看一眼", Theme.GREEN, this::startQuickVisionGlance, false);
        addLiveActionButton(actions, "简报", Theme.BLUE, this::showMorningCare, false);
        card.addView(actions, matchWrap());

        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addLiveActionButton(LinearLayout row, String text, int color, Runnable action, boolean primary) {
        Button button = primary ? Theme.button(this, text, color) : Theme.softButton(this, text, color);
        button.setTextSize(18);
        button.setMinHeight(Theme.dp(this, 56));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(Theme.dp(this, 3), 0, Theme.dp(this, 3), 0);
        row.addView(button, lp);
    }

    private void openLive2DPreview() {
        try {
            startActivity(new Intent(this, Live2DPreviewActivity.class));
        } catch (Exception e) {
            Toast.makeText(this, "Live2D preview cannot open", Toast.LENGTH_SHORT).show();
        }
    }

    private void startQuickVisionGlance() {
        pendingVisionTask = "quick_glance";
        activeVisionTask = "quick_glance";
        activeVisionPreferFront = true;
        activeVisionDetailed = false;
        activeVisionReason = "用户点了聊天页的看一眼，希望小助手快速观察主人和周围。";
        if (!hasPermission(Manifest.permission.CAMERA)) {
            updateVoiceStatus("需要允许摄像头，小助手才能看一眼。");
            updateLiveStageStatus("需要摄像头权限", "comforting");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_GLANCE);
            return;
        }
        if (!prefs.deepSeekKeyConfigured()) {
            showCompanionVoiceReply("我还不能看",
                    "奶奶，摄像头已经准备好了，但我现在还没学会仔细看图。先去“我的”里把联网陪伴打开，回头您说一句“帮我看看这个”，我就能帮您看。");
            return;
        }
        showCompanionVoiceWaiting("我看一眼，您别急。");
        updateLiveStageStatus("我认真看一眼", "seeing");
        startVisionFrameCapture("quick_glance", true, false, true, activeVisionReason);
    }

    private void addXiaozhiVoiceMeter(LinearLayout parent, int color) {
        LinearLayout meter = new LinearLayout(this);
        meter.setOrientation(LinearLayout.HORIZONTAL);
        meter.setGravity(Gravity.CENTER);
        meter.setPadding(Theme.dp(this, 8), Theme.dp(this, 6), Theme.dp(this, 8), Theme.dp(this, 6));
        meter.setBackground(Theme.rounded(Theme.mix(color, Theme.WARM_WHITE, 0.88f), 18, this));
        meter.setMinimumHeight(Theme.dp(this, LIVE_VOICE_METER_HEIGHT_DP));
        for (int i = 0; i < 18; i++) {
            View bar = new View(this);
            bar.setBackground(Theme.rounded(Theme.mix(color, Theme.BLUE, i / 24f), 4, this));
            bar.setPivotY(Theme.dp(this, LIVE_VOICE_METER_BAR_HEIGHT_DP));
            bar.setScaleY(0.28f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Theme.dp(this, LIVE_VOICE_METER_BAR_WIDTH_DP),
                    Theme.dp(this, LIVE_VOICE_METER_BAR_HEIGHT_DP));
            lp.setMargins(Theme.dp(this, 2), 0, Theme.dp(this, 2), 0);
            meter.addView(bar, lp);
        }
        parent.addView(meter, new LinearLayout.LayoutParams(-1, Theme.dp(this, LIVE_VOICE_METER_HEIGHT_DP)));
        int serial = voicePageSerial;
        if (!prefs.debugCompanionUiTestMode()) {
            meter.postDelayed(() -> animateXiaozhiVoiceMeter(meter, color, serial, 0), 80);
        }
    }

    private void animateXiaozhiVoiceMeter(LinearLayout meter, int color, int serial, int tick) {
        if (meter.getWindowToken() == null || serial != voicePageSerial) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean recentMicSound = voiceListening && now - lastVoiceSoundAtMs < 900;
        boolean active = realtimeVoiceEnabled || assistantSpeaking || voiceListening;
        float inputLevel = recentMicSound ? voiceInputLevel : (assistantSpeaking ? 0.55f : 0.06f);
        int childCount = meter.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View bar = meter.getChildAt(i);
            int wave = (int) (Math.abs(Math.sin((tick + i) * 0.58d)) * (14 + inputLevel * 30));
            int heightDp = active ? 10 + Math.round(inputLevel * 24) + wave / 2 : 10 + (i % 4) * 3;
            float scale = Math.max(0.2f, Math.min(1.0f, Math.min(LIVE_VOICE_METER_BAR_HEIGHT_DP, heightDp) / (float) LIVE_VOICE_METER_BAR_HEIGHT_DP));
            bar.setScaleY(scale);
            bar.setAlpha(recentMicSound ? 1.0f : (active ? 0.62f : 0.38f));
        }
        meter.postDelayed(() -> animateXiaozhiVoiceMeter(meter, color, serial, tick + 1), 120);
    }

    private void interruptForUserSpeech() {
        realtimeVoiceEnabled = true;
        voiceConversationSerial++;
        stopAssistantSpeech();
        abortLiveCompanionSpeech();
        updateVoiceStatus("我在听，您直接说。");
        updateLiveStageStatus("我先停下，听您说", "interrupted");
        if (content != null) {
            content.postDelayed(() -> updateLiveStageStatus("我在听，您直接说", "listening"), 180L);
        }
        restartRealtimeListeningSoon(120);
    }

    private void addLiveMemoryStrip() {
        StringBuilder text = new StringBuilder();
        if (!prefs.assistantPersonaConfigured()) {
            text.append("第一次见面时，我会直接问您：我叫什么、像什么身份陪您、该怎么称呼您。");
        } else {
            text.append("我记住了：").append(prefs.assistantPersonaSummary().replace("\n", "；"));
        }
        if (db.objectMemorySummary().length() > 0) {
            text.append("\n最近看到：").append(db.objectMemorySummary());
        }
        addCard("我记得", text.toString(), prefs.assistantPersonaConfigured() ? Theme.GREEN : Theme.ORANGE);
    }

    private void addLiveCompanionShortcuts() {
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        addLiveActionButton(row1, "昨晚", Theme.BLUE, () -> askLiveShortcut("昨晚",
                "请结合我的主人档案、今天状态、昨晚睡眠摘要和守护完整性，给我一份今天能听懂的睡眠复盘和生活建议。"), false);
        addLiveActionButton(row1, "今天", Theme.GREEN, () -> askLiveShortcut("今天",
                "请根据我的身体情况、用药习惯、今天状态和睡眠记录，生成今天的喝水、用药提醒、活动和休息建议。不要诊断。"), false);
        addLiveActionButton(row1, "设置", Theme.ORANGE, this::showCompanionSettings, false);
        content.addView(row1, matchWrap());
        addSpace(content, 10);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        addLiveActionButton(row2, "记状态", Theme.GREEN, this::showAssistantCheckIn, false);
        addLiveActionButton(row2, "建档", Theme.BLUE, () -> showOwnerProfileWizard(0), false);
        addLiveActionButton(row2, "首页", Theme.ORANGE, () -> showShell("guard"), false);
        content.addView(row2, matchWrap());
        addSpace(content, 12);
    }

    private void askLiveShortcut(String label, String question) {
        if (prefs.deepSeekKeyConfigured()) {
            realtimeVoiceEnabled = true;
            handleRealtimeVoiceText(question);
        } else {
            showCompanionVoiceReply(label, localVoiceFallback(question));
        }
    }

    private void askBedtimeStory() {
        String prompt = "请给我讲一个适合中老年人睡前听的温柔小故事。"
                + "要求：不要惊吓，不要复杂剧情，不讲医疗诊断；分成很短的段落，语气慢一点。"
                + "结尾不要催我立刻睡，只轻轻陪着我安静下来。";
        if (prefs.deepSeekKeyConfigured()) {
            realtimeVoiceEnabled = true;
            startCompanionModelAnswer("睡前小故事", prompt, "story");
        } else {
            showCompanionVoiceReply("睡前小故事", localBedtimeStory());
        }
    }

    private String localBedtimeStory() {
        return prefs.ownerAddress() + "，我先给您讲一个很轻的小故事。\n\n"
                + "从前有个小院子，院里有一盏暖灯。每天晚上，灯都会慢慢亮起来，照着窗边的花，也照着回家的人。\n\n"
                + "有一天，院子里的老人坐在藤椅上，听见风把树叶吹得沙沙响。那声音不急，也不吵，像有人轻轻说：今天已经很好了，剩下的事明天再想。\n\n"
                + "老人把杯子放稳，把肩膀放松，慢慢吸一口气，又慢慢呼出来。暖灯还在，院子也安静。故事就停在这里，我陪您慢慢安静下来。";
    }

    private void toggleSleepSound() {
        ensureSleepSoundPlayer();
        if (sleepSoundPlayer.isRunning()) {
            stopSleepSound();
            return;
        }
        startSleepSound();
    }

    private void startSleepSound() {
        ensureSleepSoundPlayer();
        if (sleepSoundPlayer.isRunning()) {
            showCompanionVoiceReply("本地助眠音",
                    "本地轻雨声已经在很轻地放着。我会在问“您睡了么？”前先把声音压低，不会吵您。");
            return;
        }
        stopAssistantSpeech();
        sleepSoundPlayer.startRain();
        pendingSleepContinuationText = "我给您放着本地轻雨声，音量会保持很轻。您如果困了，我会轻轻问“"
                + prefs.ownerAddress() + "，您睡了么？”，没回应就慢慢转入睡眠守护。";
        showCompanionVoiceReply("本地助眠音",
                pendingSleepContinuationText + "\n\n这是手机本地生成的声音，不联网、不下载音乐，也不冒充版权歌曲。");
    }

    private void stopSleepSound() {
        ensureSleepSoundPlayer();
        if (!sleepSoundPlayer.isRunning()) {
            showCompanionVoiceReply("助眠音未播放",
                    "现在没有助眠音在播放。您想听的时候，说“放点轻雨声”就行。");
            return;
        }
        sleepSoundPlayer.fadeOutAndStop(1200L);
        showCompanionVoiceReply("助眠音已淡出",
                "我把本地轻雨声慢慢收起来了。当前只是离线合成助眠音，不是版权音乐平台。");
    }

    private void ensureSleepSoundPlayer() {
        if (sleepSoundPlayer == null) {
            sleepSoundPlayer = new SleepSoundPlayer();
        }
    }

    private void showNewsCapabilityStatus() {
        if (!prefs.serverRegistered()) {
            showCompanionVoiceReply("新闻源还没接入",
                    "我记着您想听新闻。当前还没有登录服务端，也没有接入真实新闻源，所以我不能凭空编今天的新闻。");
            return;
        }
        showCompanionVoiceWaiting("我先看新闻源有没有接上，没接上就不编。");
        new Thread(() -> {
            try {
                ServerApiClient.NewsBriefResult result = ServerApiClient.newsBrief(prefs.serverBaseUrl(), prefs.serverAuthToken());
                runOnUiThread(() -> {
                    if (!result.configured) {
                        showCompanionVoiceReply("新闻源还没接入",
                                "我查过服务端了，新闻源还没接入，所以我不能凭空编今天的新闻。\n\n"
                                        + "等新闻来源接好，我再给您读带来源和时间的简报。");
                    } else {
                        showCompanionVoiceReply("新闻简报", result.body);
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> showCompanionVoiceReply("新闻源检查失败",
                        "这次没查到新闻源状态：" + ex.getMessage() + "\n\n我不会编新闻，等服务端连上真实来源后再读给您听。"));
            }
        }, "ShuilemeNewsBrief").start();
    }

    private String autoVisionStatusText() {
        if (!prefs.assistantAutoVisionEnabled()) {
            return "已暂停。开启后，进入聊天时小助手会自动看一眼，把药盒、手机、钥匙、眼镜等常用东西的位置记下来。";
        }
        StringBuilder b = new StringBuilder();
        b.append("已开启。进入聊天时会自动低清采样一张图，压缩到约 ")
                .append(VISION_MAX_JPEG_BYTES / 1024)
                .append("KB 内再分析，减少等待。");
        b.append("\n只在聊天页运行，不在夜间守护和后台偷拍。");
        if (!hasPermission(Manifest.permission.CAMERA)) {
            b.append("\n需要先允许摄像头。");
        } else if (!prefs.deepSeekKeyConfigured()) {
            b.append("\n联网陪伴未打开时，我先帮您记文字位置。");
        } else {
            b.append("\n最近物品记忆：\n").append(db.objectMemorySummary());
        }
        return b.toString();
    }

    private void showCompanionVision() {
        content.removeAllViews();
        content.addView(Theme.text(this, "小助手看看", 32, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addAssistantHero("您直接说", "比如：帮我看看这个药瓶、看看这朵花叫啥、帮我找手机、帮我读这个小字儿。我会自己判断看哪里、用前摄还是后摄。", false);
        addVisionVoiceExamples();
        addVisionPrimaryButton("打开小助手，直接说", Theme.GREEN, this::showCompanionChat);
        String memory = db.objectMemorySummary() + "\n" + prefs.visualMemorySummary();
        if (memory.trim().length() > 0 && !memory.startsWith("还没有")) {
            addCard("我记得", memory.trim(), Theme.GREEN);
        }
        addSettingButton("清除位置记忆", () -> {
            prefs.clearVisualMemory();
            db.clearObjectMemory();
            Toast.makeText(this, "已清除位置记忆", Toast.LENGTH_SHORT).show();
            showCompanionVision();
        });
        addSettingButton("返回聊天", this::showCompanionChat);
        addSettingButton("返回首页", () -> showShell("guard"));
    }

    private void addVisionVoiceExamples() {
        LinearLayout card = cardContainer();
        card.setPadding(Theme.dp(this, 16), Theme.dp(this, 14), Theme.dp(this, 16), Theme.dp(this, 14));
        card.setBackground(Theme.tintedCard(this, Theme.BLUE));
        card.addView(Theme.text(this, "能听懂这些说法", 20, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(card, 8);
        addVisionExampleLine(card, "药瓶小字", "帮我看看这个药瓶");
        addVisionExampleLine(card, "读字识物", "帮我看看这个小字儿");
        addVisionExampleLine(card, "花草物品", "你看看这朵花叫啥");
        addVisionExampleLine(card, "找东西", "帮我看看我手机放哪儿了");
        addVisionExampleLine(card, "照看自己", "帮我看看我眼角变化明显不明显");
        content.addView(card, matchWrap());
        addSpace(content, 12);
    }

    private void addVisionExampleLine(LinearLayout card, String title, String body) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = Theme.text(this, title, 15, Theme.TEXT, Typeface.BOLD);
        row.addView(left, new LinearLayout.LayoutParams(Theme.dp(this, 82), -2));
        TextView right = Theme.text(this, body, 15, Theme.MUTED, Typeface.BOLD);
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row, matchWrap());
        addSpace(card, 6);
    }

    private void addVisionPrimaryButton(String text, int color, Runnable action) {
        Button button = Theme.button(this, text, color);
        button.setTextSize(26);
        button.setMinHeight(Theme.dp(this, 96));
        button.setOnClickListener(v -> action.run());
        content.addView(button, matchWrap());
        addSpace(content, 12);
    }

    private void requestCameraForVision(String task) {
        pendingVisionTask = task == null ? "" : task;
        if (!hasPermission(Manifest.permission.CAMERA)) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_VISION);
            Toast.makeText(this, "请允许摄像头，小助手才能看一眼", Toast.LENGTH_LONG).show();
            return;
        }
        if (!prefs.deepSeekKeyConfigured()) {
            showCompanionVoiceReply("我还不能细看",
                    "奶奶，摄像头已经准备好了，但我现在还不能把小字和东西看清楚。先去“我的”里打开联网陪伴，回头我就能帮您念药瓶、找手机、认花草。");
            return;
        }
        String reason = visionReasonForTask(pendingVisionTask);
        showCompanionVoiceWaiting(reason.length() > 0 ? reason : "您别急，我仔细看。");
        updateLiveStageStatus(reason.length() > 0 ? reason : "我认真看", avatarMoodForVisionTask(pendingVisionTask));
        startVisionFrameCapture(pendingVisionTask, preferFrontForVisionTask(pendingVisionTask), true, true, reason);
    }

    private void launchVisionCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        pendingVisionImageUri = VisionImageProvider.newImageUri(this);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingVisionImageUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_CAPTURE_SCENE);
        } catch (Exception ex) {
            Toast.makeText(this, "没有可用相机应用", Toast.LENGTH_LONG).show();
        }
    }

    private void handleVisionSnapshot(Bitmap bitmap) {
        latestVisionSnapshot = bitmap;
        String task = pendingVisionTask == null ? "" : pendingVisionTask;
        if (!prefs.deepSeekKeyConfigured()) {
            showCompanionVoiceReply("我还不能细看",
                    "奶奶，这张照片我现在还看不清楚。到“我的”里打开联网陪伴后，我就能帮您念字、看药瓶、认花草。");
            return;
        }
        askDeepSeekIntentVision(task, bitmap, true, visionReasonForTask(task));
    }

    private String visionTaskTitle(String task) {
        if ("quick_glance".equals(task)) return "快速看一眼";
        if ("face".equals(task)) return "看看气色";
        if ("face_detail".equals(task)) return "看看脸部细节";
        if ("medicine_text".equals(task)) return "看药瓶小字";
        if ("medication".equals(task)) return "看看吃药";
        if ("report".equals(task)) return "看体检报告";
        if ("finance".equals(task)) return "看投资理财";
        if ("plant".equals(task)) return "识别花草";
        if ("object".equals(task)) return "识别东西";
        if ("read".equals(task)) return "帮我读字";
        if ("find".equals(task)) return "帮我找东西";
        if ("outside".equals(task)) return "看看外面";
        return "小助手看看";
    }

    private boolean preferFrontForVisionTask(String task) {
        return "face".equals(task) || "face_detail".equals(task) || "quick_glance".equals(task);
    }

    private String avatarMoodForVisionTask(String task) {
        if ("read".equals(task) || "report".equals(task) || "medicine_text".equals(task) || "medication".equals(task)) {
            return "reading";
        }
        if ("find".equals(task)) {
            return "finding";
        }
        return "seeing";
    }

    private String visionReasonForTask(String task) {
        if ("face".equals(task)) return "我看看您的气色，您别急。";
        if ("face_detail".equals(task)) return "我看看您说的脸部细节，顺便哄您开心。";
        if ("medicine_text".equals(task) || "medication".equals(task)) return "我仔细看看药瓶上的字，慢慢念给您听。";
        if ("report".equals(task)) return "我仔细看看报告，先帮您读清楚重点。";
        if ("finance".equals(task)) return "我帮您看看这是不是理财、投资或转账风险。";
        if ("plant".equals(task)) return "我帮您看看这朵花或这盆植物。";
        if ("read".equals(task)) return "我仔细看看上面的字，读给您听。";
        if ("find".equals(task)) return "您别急，我帮您看看周围。";
        if ("object".equals(task)) return "我仔细看看这个东西。";
        if ("outside".equals(task)) return "我帮您看看外面。";
        return "我看一眼，您别急。";
    }

    private void showObjectMemoryDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));

        EditText item = new EditText(this);
        item.setHint("东西名称，例如：钥匙、眼镜、药盒");
        item.setTextSize(20);
        box.addView(item, matchWrap());

        EditText place = new EditText(this);
        place.setHint("放在哪里，例如：床头柜第二层抽屉");
        place.setTextSize(20);
        place.setSingleLine(false);
        place.setMinLines(2);
        box.addView(place, matchWrap());

        EditText note = new EditText(this);
        note.setHint("补充备注，可不填");
        note.setTextSize(18);
        note.setSingleLine(false);
        note.setMinLines(2);
        box.addView(note, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("帮我记住")
                .setMessage("只保存成文字位置记忆，方便以后找东西。")
                .setView(box)
                .setPositiveButton("记住", (d, w) -> {
                    prefs.setVisualMemory(item.getText().toString(), place.getText().toString(), note.getText().toString());
                    db.upsertObjectMemory(item.getText().toString(), place.getText().toString(), note.getText().toString(), "主人确认");
                    showCompanionReply("我记住了", CompanionAssistant.visualMemorySaved(item.getText().toString(), place.getText().toString()));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showFindObjectDialog() {
        EditText object = new EditText(this);
        object.setHint("想找什么？例如：眼镜、钥匙、药盒");
        object.setTextSize(20);
        object.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("帮我找东西")
                .setMessage("我会先查自己记下的位置，也可以再打开摄像头看周围。")
                .setView(object)
                .setPositiveButton("查记忆", (d, w) -> showFindObjectResult(object.getText().toString()))
                .setNegativeButton("取消", null)
                .setNeutralButton("打开摄像头", (d, w) -> requestCameraForVision("find"))
                .show();
    }

    private void showFindObjectResult(String objectName) {
        content.removeAllViews();
        content.addView(Theme.text(this, "帮我找东西", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        String memory = db.objectMemoryAnswer(objectName);
        if (memory.length() == 0) {
            memory = CompanionAssistant.findObjectLine(objectName, db.objectMemorySummary() + "\n" + prefs.visualMemorySummary());
        }
        addAssistantHero("我帮你想想", memory, false);
        addSettingButton("打开摄像头找一找", () -> requestCameraForVision("find"));
        addSettingButton("记住东西放哪里", this::showObjectMemoryDialog);
        addSettingButton("返回小助手看看", this::showCompanionVision);
    }

    private void askDeepSeekVision(String task, Bitmap bitmap) {
        askDeepSeekIntentVision(task, bitmap, true, visionReasonForTask(task));
    }

    private String askAssistantModel(String userPrompt) throws Exception {
        if (prefs.serverRegistered()) {
            return ServerApiClient.chat(
                    prefs.serverBaseUrl(),
                    prefs.serverAuthToken(),
                    deepSeekSystemPrompt(),
                    userPrompt,
                    prefs.deepSeekModel());
        }
        throw new IllegalStateException("请先手机号登录，登录后我才能通过服务端联网回答。");
    }

    private String askAssistantVisionModel(String task, String prompt, String jpegBase64) throws Exception {
        if (prefs.serverRegistered()) {
            return ServerApiClient.vision(
                    prefs.serverBaseUrl(),
                    prefs.serverAuthToken(),
                    deepSeekSystemPrompt(),
                    prompt,
                    task,
                    jpegBase64,
                    prefs.deepSeekModel());
        }
        throw new IllegalStateException("请先手机号登录，登录后我才能通过服务端帮您看东西。");
    }

    private void askDeepSeekIntentVision(String task, Bitmap bitmap, boolean detailed, String userIntent) {
        if (bitmap == null) {
            Toast.makeText(this, "没有可分析的照片", Toast.LENGTH_SHORT).show();
            return;
        }
        showCompanionVoiceWaiting(CompanionAssistant.thinkingComfortLine(prefs.companionRole(), "vision"));
        updateLiveStageStatus(detailed ? visionReasonForTask(task) : "我认真看一眼", avatarMoodForVisionTask(task));
        new Thread(() -> {
            try {
                String prompt = deepSeekVisionPrompt(task, userIntent, detailed);
                String answer = askAssistantVisionModel(task, prompt, detailed ? bitmapToDetailedJpegBase64(bitmap) : bitmapToJpegBase64(bitmap));
                maybeSyncCompanionInsight(prompt + "\n\n看图回答：" + answer, "vision");
                runOnUiThread(() -> {
                    int saved = storeAutoVisionMemory(answer);
                    String clean = stripVisionMemoryJson(answer);
                    if (saved > 0) {
                        clean = clean + "\n\n我也帮您记住了 " + saved + " 个常用东西的位置。";
                    }
                    showCompanionVoiceReply("我看完了", clean);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> showCompanionVoiceReply("这次没看清",
                        "奶奶，这次看图没成功：" + ex.getMessage()
                                + "\n\n您别急，可以把手机拿稳一点再说“你帮我看看这个”。"));
            }
        }, detailed ? "GouXiongDeepSeekDetailedVision" : "GouXiongDeepSeekGlanceVision").start();
    }

    private String deepSeekVisionPrompt(String task) {
        return deepSeekVisionPrompt(task, visionReasonForTask(task), true);
    }

    private String deepSeekVisionPrompt(String task, String userIntent, boolean detailed) {
        return "视觉任务：" + visionTaskTitle(task)
                + "\n用户让你看的原意/场景：" + (userIntent == null ? "" : userIntent)
                + "\n清晰度：" + (detailed ? "高清仔细看，适合药瓶小字、文字、报告、物品识别。" : "低清快速看，只做粗略观察，不能细读小字。")
                + "\n请用适合中老年人听的短句回答，像亲近的孩子在身边帮忙。"
                + "\n先听懂主人真正想要什么：念药瓶小字、读纸上的字、找手机钥匙、看看脸色哄开心、认花草、看报告或看一件东西。"
                + "\n回答时先直接帮忙，不要先讲免责声明，不要摆架子，不要像客服。"
                + "\n看药瓶、药盒、说明书时，把能看清的药名、字、用法提示、日期慢慢念出来；如果看不清，就温柔地请主人拿近一点、稳一点、换个亮处。"
                + "\n看脸、眼角、皱纹、气色时，用温柔的话描述你看见的样子，多哄主人开心；没有历史对比就别硬说“加深了”，可以说“这张里看着怎样”。"
                + "\n看花草植物时，先说它像什么花、叶子和花朵有什么特点，再给一点简单养护建议，比如浇水、晒太阳、通风。"
                + "\n找手机、钥匙、眼镜等东西时，直接说你在画面里看到的可能位置，再给下一步找法，先安抚主人别急。"
                + "\n看报告时，先帮主人读出看得清的项目和数字，再用大白话解释，语气放松。"
                + "\n只有遇到转账、验证码、贷款、陌生投资、夸张收益承诺时，才温和提醒先别急着给钱，找家人核实。"
                + "\n如果看到了钥匙、手机、药盒/药瓶、眼镜、钱包、证件/医保卡、遥控器、拐杖、水杯等常忘物品，请在回答最后另起一行追加：MEMORY_JSON:{\"objects\":[{\"item\":\"钥匙\",\"place\":\"冰箱门上的挂钩\",\"confidence\":\"中\",\"note\":\"可选\"}]}。看不清就 objects 为空。"
                + "\n不要把 MEMORY_JSON 解释给用户听。"
                + "\n\n小助手身份：\n" + prefs.assistantPersonaSummary()
                + "\n\n你称呼用户为：" + prefs.ownerAddress()
                + "\n\n主人档案：\n" + prefs.ownerProfileSummary()
                + "\n\n今天状态：\n" + prefs.assistantCheckInSummary()
                + "\n\n自动位置记忆：\n" + db.objectMemorySummary()
                + "\n\n手动位置备注：\n" + prefs.visualMemorySummary();
    }

    private String bitmapToJpegBase64(Bitmap bitmap) {
        return Base64.encodeToString(bitmapToLimitedJpeg(bitmap), Base64.NO_WRAP);
    }

    private String bitmapToDetailedJpegBase64(Bitmap bitmap) {
        return Base64.encodeToString(bitmapToLimitedJpeg(bitmap, DETAIL_VISION_MAX_SIDE, DETAIL_VISION_MAX_JPEG_BYTES, 84, 55), Base64.NO_WRAP);
    }

    private void maybeStartAutoVisionScan() {
        if (!prefs.assistantAutoVisionEnabled() || autoVisionRunning || isFinishing()) {
            return;
        }
        if (!autoVisionForegroundReady()) {
            return;
        }
        long last = prefs.assistantAutoVisionLastAt();
        if (last > 0 && System.currentTimeMillis() - last < AUTO_VISION_MIN_INTERVAL_MS) {
            return;
        }
        if (!hasPermission(Manifest.permission.CAMERA)) {
            return;
        }
        if (!prefs.deepSeekKeyConfigured()) {
            return;
        }
        startAutoVisionCapture();
    }

    private void startAutoVisionCapture() {
        startVisionFrameCapture("auto_glance", true, false, false, "聊天页自动低清看一眼，只保存重要物品位置和轻微关怀。");
    }

    private void startVisionFrameCapture(String task, boolean preferFront, boolean detailed, boolean manual, String reason) {
        if (autoVisionRunning) {
            if (manual) {
                showCompanionVoiceReply("我正在看", "奶奶，我正在处理上一张画面。您别急，等我说完再让我看下一样东西。");
            }
            return;
        }
        activeVisionTask = task == null ? "" : task;
        activeVisionPreferFront = preferFront;
        activeVisionDetailed = detailed;
        activeVisionReason = reason == null ? "" : reason;
        manualVisionCapture = manual;
        autoVisionRunning = true;
        ensureVisionThread();
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (manager == null) {
                autoVisionRunning = false;
                handleVisionCaptureFailure(manual);
                return;
            }
            String cameraId = findVisionCameraId(manager, preferFront);
            if (cameraId.length() == 0) {
                autoVisionRunning = false;
                handleVisionCaptureFailure(manual);
                return;
            }
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Size size = chooseVisionSize(characteristics, detailed ? DETAIL_VISION_MAX_SIDE : AUTO_VISION_MAX_SIDE);
            autoVisionReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 1);
            autoVisionReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        byte[] jpeg = imageToBytes(image);
                        runOnUiThread(() -> processAutoVisionJpeg(jpeg));
                    }
                } catch (Exception ignored) {
                    closeAutoVisionCamera();
                } finally {
                    if (image != null) image.close();
                }
            }, visionHandler);
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    autoVisionCamera = camera;
                    captureAutoVisionFrame();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    closeAutoVisionCamera();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    boolean wasManual = manualVisionCapture;
                    closeAutoVisionCamera();
                    handleVisionCaptureFailure(wasManual);
                }
            }, visionHandler);
        } catch (Exception ex) {
            boolean wasManual = manualVisionCapture;
            closeAutoVisionCamera();
            handleVisionCaptureFailure(wasManual);
        }
    }

    private void handleVisionCaptureFailure(boolean manual) {
        if (!manual || isFinishing()) {
            return;
        }
        runOnUiThread(() -> showCompanionVoiceReply("这次没打开镜头",
                "奶奶，这次摄像头没打开成功。您别急，确认没有别的 App 正在用摄像头，再说“你帮我看看这个”。"));
    }

    private void captureAutoVisionFrame() {
        if (autoVisionCamera == null || autoVisionReader == null) {
            closeAutoVisionCamera();
            return;
        }
        try {
            Surface surface = autoVisionReader.getSurface();
            CaptureRequest.Builder builder = autoVisionCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(surface);
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            autoVisionCamera.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    autoVisionSession = session;
                    try {
                        session.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
                        }, visionHandler);
                    } catch (CameraAccessException ex) {
                        closeAutoVisionCamera();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    closeAutoVisionCamera();
                }
            }, visionHandler);
        } catch (Exception ex) {
            closeAutoVisionCamera();
        }
    }

    private void processAutoVisionJpeg(byte[] jpeg) {
        boolean wasManual = manualVisionCapture;
        boolean wasDetailed = activeVisionDetailed;
        String task = activeVisionTask;
        String reason = activeVisionReason;
        closeAutoVisionCamera();
        manualVisionCapture = false;
        activeVisionDetailed = false;
        activeVisionTask = "";
        activeVisionReason = "";
        if (jpeg == null || jpeg.length == 0 || isFinishing()) {
            if (wasManual) {
                showCompanionVoiceReply("这次没看清", "奶奶，这次没有拍到画面。您别急，把手机拿稳一点，再说“你帮我看看这个”。");
            }
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        if (bitmap == null) {
            if (wasManual) {
                showCompanionVoiceReply("这次没看清", "奶奶，这次照片没有解出来。您别急，再试一次就行。");
            }
            return;
        }
        prefs.recordVisionCaptureState(task, jpeg.length, bitmap.getWidth(), bitmap.getHeight());
        prefs.markAssistantAutoVisionNow();
        latestVisionSnapshot = bitmap;
        if (wasManual) {
            askDeepSeekIntentVision(task, bitmap, wasDetailed, reason);
        } else {
            askDeepSeekAutoVision(bitmap);
        }
    }

    private boolean autoVisionForegroundReady() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return (pm == null || pm.isInteractive()) && hasWindowFocus();
    }

    private void askDeepSeekAutoVision(Bitmap bitmap) {
        new Thread(() -> {
            try {
                String answer = askAssistantVisionModel("auto_glance", deepSeekAutoVisionPrompt(), bitmapToJpegBase64(bitmap));
                maybeSyncCompanionInsight("自动低清看一眼：" + answer, "auto_vision");
                runOnUiThread(() -> handleAutoVisionAnswer(answer));
            } catch (Exception ignored) {
                runOnUiThread(() -> Toast.makeText(this, "小助手这次没有看清", Toast.LENGTH_SHORT).show());
            }
        }, "GouXiongAutoVision").start();
    }

    private String deepSeekAutoVisionPrompt() {
        return "你正在帮一位中老年主人聊天陪伴时自动看一眼。"
                + "\n请只返回 JSON，不要写解释，不要 Markdown。格式："
                + "{\"scene_note\":\"一句很短的生活观察\",\"objects\":[{\"item\":\"钥匙\",\"place\":\"冰箱门上的挂钩\",\"confidence\":\"中\",\"note\":\"可选\"}],\"care\":\"可选的一句关心\"}"
                + "\n重点识别并记录老人常忘的东西：钥匙、手机、药盒/药瓶、眼镜、钱包、证件/医保卡、遥控器、拐杖、水杯。"
                + "\n如果能看到主人，给一句温柔生活观察，例如光线不足、看起来有些累、精神还不错。"
                + "\nplace 要尽量具体，例如“冰箱门上挂钩”“床头柜第二层抽屉”“沙发左边扶手旁”。"
                + "\n如果看不清，不要编造位置，objects 返回空数组。"
                + "\n看到药盒药瓶时，只记录它在哪里，别编造看不清的字。";
    }

    private void handleAutoVisionAnswer(String answer) {
        int saved = storeAutoVisionMemory(answer);
        String care = autoVisionCareText(answer);
        if (saved > 0 || care.length() > 0) {
            String status = care.length() > 0 ? care : "我帮您记住了 " + saved + " 个东西的位置。";
            if (saved > 0 && care.length() > 0) {
                status = status + " 我也记住了 " + saved + " 个东西的位置。";
            }
            updateVoiceStatus(status);
        }
    }

    private String autoVisionCareText(String answer) {
        try {
            String json = extractVisionMemoryJson(answer);
            if (json.length() == 0) return "";
            JSONObject root = new JSONObject(json);
            String care = root.optString("care", "").trim();
            if (care.length() > 0) return care;
            return root.optString("scene_note", "").trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private int storeAutoVisionMemory(String answer) {
        try {
            String json = extractVisionMemoryJson(answer);
            if (json.length() == 0) return 0;
            JSONObject root = new JSONObject(json);
            JSONArray objects = root.optJSONArray("objects");
            if (objects == null) return 0;
            int saved = 0;
            for (int i = 0; i < objects.length(); i++) {
                JSONObject obj = objects.optJSONObject(i);
                if (obj == null) continue;
                String item = obj.optString("item", "").trim();
                String place = obj.optString("place", "").trim();
                String confidence = obj.optString("confidence", "").trim();
                String note = obj.optString("note", "").trim();
                if (item.length() == 0 || place.length() == 0) continue;
                db.upsertObjectMemory(item, place, note.length() > 0 ? note : "聊天页自动看见", confidence);
                if (item.contains("药")) {
                    prefs.markMedicationSeen("自动视觉看见药品位置：" + place);
                }
                saved++;
            }
            return saved;
        } catch (Exception ex) {
            return 0;
        }
    }

    private String extractVisionMemoryJson(String answer) {
        if (answer == null) return "";
        int marker = answer.indexOf("MEMORY_JSON:");
        if (marker >= 0) {
            return extractJsonObject(answer.substring(marker + "MEMORY_JSON:".length()));
        }
        return extractJsonObject(answer);
    }

    private String stripVisionMemoryJson(String answer) {
        if (answer == null) return "";
        int marker = answer.indexOf("MEMORY_JSON:");
        String clean = marker >= 0 ? answer.substring(0, marker) : answer;
        clean = clean.trim();
        if (clean.startsWith("{") && clean.endsWith("}")) {
            String care = autoVisionCareText(clean);
            return care.length() > 0 ? care : "我看到了，但需要您再靠近一点，我才能说得更准。";
        }
        return clean;
    }

    private String extractJsonObject(String answer) {
        if (answer == null) return "";
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) return "";
        return answer.substring(start, end + 1);
    }

    private void ensureVisionThread() {
        if (visionThread != null && visionThread.isAlive()) {
            return;
        }
        visionThread = new HandlerThread("GouXiongVisionCamera");
        visionThread.start();
        visionHandler = new Handler(visionThread.getLooper());
    }

    private String findAutoVisionCameraId(CameraManager manager) throws CameraAccessException {
        return findVisionCameraId(manager, false);
    }

    private String findVisionCameraId(CameraManager manager, boolean preferFront) throws CameraAccessException {
        String fallback = "";
        int preferredFacing = preferFront ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (fallback.length() == 0) fallback = id;
            if (facing != null && facing == preferredFacing) {
                return id;
            }
        }
        return fallback;
    }

    private Size chooseVisionSize(CameraCharacteristics characteristics) {
        return chooseVisionSize(characteristics, AUTO_VISION_MAX_SIDE);
    }

    private Size chooseVisionSize(CameraCharacteristics characteristics, int maxSideLimit) {
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size fallback = new Size(640, 480);
        if (map == null) return fallback;
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        if (sizes == null || sizes.length == 0) return fallback;
        Arrays.sort(sizes, (a, b) -> (a.getWidth() * a.getHeight()) - (b.getWidth() * b.getHeight()));
        Size best = sizes[0];
        for (Size size : sizes) {
            int max = Math.max(size.getWidth(), size.getHeight());
            if (max <= maxSideLimit) {
                best = size;
            }
        }
        return best;
    }

    private byte[] imageToBytes(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private byte[] bitmapToLimitedJpeg(Bitmap bitmap) {
        return bitmapToLimitedJpeg(bitmap, AUTO_VISION_MAX_SIDE, VISION_MAX_JPEG_BYTES, 72, 45);
    }

    private byte[] bitmapToLimitedJpeg(Bitmap bitmap, int maxSide, int maxBytes, int initialQuality, int minQuality) {
        Bitmap scaled = scaleBitmapForVision(bitmap, maxSide);
        int quality = initialQuality;
        byte[] bytes = compressJpeg(scaled, quality);
        while (bytes.length > maxBytes && quality > minQuality) {
            quality -= 7;
            bytes = compressJpeg(scaled, quality);
        }
        return bytes;
    }

    private Bitmap scaleBitmapForVision(Bitmap bitmap) {
        return scaleBitmapForVision(bitmap, AUTO_VISION_MAX_SIDE);
    }

    private Bitmap scaleBitmapForVision(Bitmap bitmap, int maxSideLimit) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int max = Math.max(width, height);
        if (max <= maxSideLimit) {
            return bitmap;
        }
        float ratio = maxSideLimit / (float) max;
        int targetWidth = Math.max(1, Math.round(width * ratio));
        int targetHeight = Math.max(1, Math.round(height * ratio));
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
    }

    private byte[] compressJpeg(Bitmap bitmap, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
    }

    private void closeAutoVisionCamera() {
        autoVisionRunning = false;
        try {
            if (autoVisionSession != null) autoVisionSession.close();
        } catch (Exception ignored) {
        }
        try {
            if (autoVisionCamera != null) autoVisionCamera.close();
        } catch (Exception ignored) {
        }
        try {
            if (autoVisionReader != null) autoVisionReader.close();
        } catch (Exception ignored) {
        }
        autoVisionSession = null;
        autoVisionCamera = null;
        autoVisionReader = null;
    }

    private String proactiveCareText() {
        StringBuilder b = new StringBuilder();
        b.append(CompanionAssistant.sampleLine(prefs.companionRole())).append("\n\n");
        if (prefs.assistantCheckInToday()) {
            b.append("今天状态我记着：\n").append(prefs.assistantCheckInSummary()).append("\n\n");
        } else {
            b.append(CompanionAssistant.checkInIntro(prefs.companionRole())).append("\n\n");
        }
        b.append("昨晚睡前自检：\n").append(preSleepSelfCheckSummary()).append("\n\n");
        if (prefs.ownerProfileStarted()) {
            if (prefs.healthProfile().length() > 0) {
                b.append("身体情况我记着：").append(prefs.healthProfile()).append("。今天如果不舒服，先休息，必要时联系家人或医生。\n");
            }
            if (prefs.medicationHabits().length() > 0 || prefs.medicationEnabled()) {
                String med = prefs.medicationHabits().length() > 0 ? prefs.medicationHabits() : prefs.medicationName();
                b.append("用药提醒：").append(med).append("。按你自己的医嘱或家人交代处理，吃过可在早安护理里确认。\n");
            }
            if (prefs.sleepSituation().length() > 0) {
                b.append("睡眠情况：").append(prefs.sleepSituation()).append("。今晚继续观察记录，不把 App 记录当诊断。\n");
            }
            b.append("\n今日小妙招：")
                    .append(CompanionAssistant.wellnessTipLine(
                            prefs.companionRole(),
                            prefs.ownerAddress(),
                            prefs.healthProfile(),
                            prefs.medicationHabits(),
                            prefs.sleepSituation(),
                            prefs.hobbies()))
                    .append("\n");
            if (prefs.familySituation().length() > 0) {
                b.append("家庭关怀：").append(prefs.familySituation()).append("。如果高风险无确认，会按你设置的联系人策略处理。\n");
            }
            if (prefs.hobbies().length() > 0) {
                b.append("今天可以留一点时间给喜欢的事：").append(prefs.hobbies()).append("。\n");
            }
            if (prefs.carePreference().length() > 0) {
                b.append("我会按你的偏好来：").append(prefs.carePreference()).append("。");
            }
        } else {
            b.append("你还没填写主人档案。填一点身体、用药、睡眠、家庭和兴趣，小助手会更会关心你。");
        }
        return b.toString();
    }

    private void showWellnessTip() {
        if (!prefs.ownerProfileStarted()) {
            showCompanionVoiceReply("今日小妙招",
                    CompanionAssistant.wellnessTipLine(prefs.companionRole(), prefs.ownerAddress(), "", "", "", "")
                            + "\n\n您先补一点主人档案，我以后就能按身体状况、用药和睡眠来找更合适的小妙招。");
            return;
        }
        String fallback = CompanionAssistant.wellnessTipLine(
                prefs.companionRole(),
                prefs.ownerAddress(),
                prefs.healthProfile(),
                prefs.medicationHabits(),
                prefs.sleepSituation(),
                prefs.hobbies());
        if (!prefs.serverRegistered()) {
            showCompanionVoiceReply("今日小妙招", fallback);
            return;
        }
        showCompanionVoiceWaiting(prefs.ownerAddress() + "，我根据您的身体状况找一个今天能试的小妙招，马上说给您听。");
        new Thread(() -> {
            try {
                JSONObject context = new JSONObject()
                        .put("owner_address", prefs.ownerAddress())
                        .put("assistant_role", prefs.companionRole())
                        .put("owner_profile", prefs.ownerProfileSummary())
                        .put("today_check_in", prefs.assistantCheckInSummary())
                        .put("sleep_summary", db.localReportText())
                        .put("wellness_topic", "根据主人身体状况，自动整理安全的养生小妙招、食补食疗方向；先主动汇报一个，主人感兴趣时再详细教怎么做。");
                ServerApiClient.CareBriefResult result = ServerApiClient.careBrief(
                        prefs.serverBaseUrl(),
                        prefs.serverAuthToken(),
                        "wellness_tip",
                        context,
                        false,
                        prefs.deepSeekModel());
                String answer = result.body == null ? "" : result.body.trim();
                runOnUiThread(() -> showCompanionVoiceReply("今日小妙招",
                        answer.length() > 0 ? answer : fallback));
            } catch (Exception ex) {
                runOnUiThread(() -> showCompanionVoiceReply("今日小妙招",
                        fallback + "\n\n这次联网搜集没成功，我先按本机档案给您一个稳妥建议。"));
            }
        }, "GouXiongWellnessTip").start();
    }

    private void showServerAccountSettings() {
        content.removeAllViews();
        content.addView(Theme.text(this, "联网账号", 32, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addCheckRow("账号", prefs.serverRegistered() ? "已登录" : "未登录", prefs.serverRegistered(), null);
        addCheckRow("服务端", compactForCard(prefs.serverBaseUrl(), 24), true, this::showServerUrlDialog);
        addSettingButton("睡前自检", this::showPreSleepCheck);
        addSettingButton("高级诊断", this::showServerCapabilityCheck);
        if (prefs.serverRegistered()) {
            content.postDelayed(this::syncOwnerProfileQuietAfterAccountOpen, 450);
            addSettingButton("导出服务端资料", this::exportServerAccountData);
            addSettingButton("删除服务端账号", this::confirmDeleteServerAccount);
            addSettingButton("重新登录", this::showPhoneLoginDialog);
            addSettingButton("退出登录", () -> {
                prefs.clearServerAuth();
                CareReminderScheduler.ensureCareReminders(this);
                Toast.makeText(this, "已退出服务端账号", Toast.LENGTH_SHORT).show();
                showServerAccountSettings();
            });
        } else {
            addSettingButton("手机号验证码登录", this::showPhoneLoginDialog);
            addSettingButton("连接服务设置", this::showServerUrlDialog);
        }
        addSettingButton("返回设置", this::showSettings);
    }

    private void exportServerAccountData() {
        if (!prefs.serverRegistered()) {
            Toast.makeText(this, "请先手机号注册", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在导出服务端资料", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                JSONObject data = ServerApiClient.exportMe(prefs.serverBaseUrl(), prefs.serverAuthToken());
                String exported = data.toString(2);
                runOnUiThread(() -> {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/json");
                    share.putExtra(Intent.EXTRA_SUBJECT, "睡了么服务端资料导出");
                    share.putExtra(Intent.EXTRA_TEXT, exported);
                    startActivity(Intent.createChooser(share, "导出服务端资料"));
                });
            } catch (Exception ex) {
                runOnUiThread(() -> showCompanionReply("导出没成功",
                        "服务端资料暂时没导出来：" + ex.getMessage() + "\n\n您别急，本机睡眠记录还在手机里。"));
            }
        }, "GouXiongServerAccountExport").start();
    }

    private void confirmDeleteServerAccount() {
        if (!prefs.serverRegistered()) {
            Toast.makeText(this, "请先手机号注册", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除服务端账号？")
                .setMessage("会删除服务端手机号账号、健康档案、长期记忆、聊天记录和待读消息。本机睡眠记录不会被删除。")
                .setPositiveButton("删除服务端资料", (d, w) -> deleteServerAccountData())
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteServerAccountData() {
        Toast.makeText(this, "正在删除服务端资料", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                ServerApiClient.deleteMe(prefs.serverBaseUrl(), prefs.serverAuthToken());
                runOnUiThread(() -> {
                    prefs.clearServerAuth();
                    CareReminderScheduler.ensureCareReminders(this);
                    Toast.makeText(this, "服务端账号已删除，本机记录仍保留", Toast.LENGTH_LONG).show();
                    showServerAccountSettings();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> showCompanionReply("删除没成功",
                        "服务端账号暂时没有删掉：" + ex.getMessage() + "\n\n请稍后再试，或让家人检查服务端是否在线。"));
            }
        }, "GouXiongServerAccountDelete").start();
    }

    private void showServerCapabilityCheck() {
        content.removeAllViews();
        addSimplePageHeader("服务端与能力检查", "", null);
        addSpace(content, 8);
        addServerStatusHero("正在检查", "正在连接 " + compactForCard(prefs.serverBaseUrl(), 28), Theme.ORANGE);
        new Thread(() -> {
            try {
                ServerApiClient.ServerHealth status = ServerApiClient.health(prefs.serverBaseUrl());
                runOnUiThread(() -> showServerCapabilityResult(status));
            } catch (Exception ex) {
                runOnUiThread(() -> showServerCapabilityError(ex.getMessage()));
            }
        }, "GouXiongServerCapabilityCheck").start();
    }

    private void showServerCapabilityResult(ServerApiClient.ServerHealth status) {
        content.removeAllViews();
        addSimplePageHeader("服务端与能力检查", "", null);
        addSpace(content, 8);
        addServerStatusHero(status.ok ? "服务端连接正常" : "服务端连接异常",
                "最后检查：" + timeNowShort() + " · " + compactForCard(prefs.serverBaseUrl(), 28),
                status.ok ? Theme.GREEN : Theme.RED);
        boolean realtimePathReady = status.realtimeConfigured && status.modelAudioOutputStreaming && status.apkLowLatencyAudioPlayback;
        boolean avatarReady = status.linlyDigitalHumanConfigured || (status.local2dAvatarView && status.avatarStateMachine);
        boolean companionReady = realtimePathReady || avatarReady || status.textChat;
        boolean sleepAnalysisReady = status.modelReady() && status.textChat;
        boolean guardReady = prefs.sleepGuardAudioPassed();
        LinearLayout list = cardContainer();
        list.setOrientation(LinearLayout.VERTICAL);
        addServerCapabilityRow(list, "账号状态", prefs.serverRegistered() ? "已登录" : "未登录", "人", Theme.GREEN, prefs.serverRegistered(), this::showServerAccountSettings);
        addServerCapabilityRow(list, "云端同步", status.ok ? "正常" : "异常", "云", Theme.BLUE, status.ok, null);
        addServerCapabilityRow(list, "睡眠分析服务", sleepAnalysisReady ? "正常" : "未完整证明", "眠", Theme.ORANGE, sleepAnalysisReady, null);
        addServerCapabilityRow(list, "独居守护服务", guardReady ? "正常" : prefs.sleepGuardAudioShortState(), "护", Theme.GREEN, guardReady, () -> showShell("guard"));
        addServerCapabilityRow(list, "数字人助手服务", companionStateLabel(status, realtimePathReady, avatarReady), "数", Theme.BLUE, companionReady, this::showCompanionChat);
        addServerCapabilityRow(list, "敏感噪声服务", guardReady ? "正常" : prefs.sleepGuardAudioShortState(), "声", Theme.RED, guardReady, () -> showShell("guard"));
        content.addView(list, matchWrap());
        addSpace(content, 14);
        addPrimaryActionButton("重新检查", Theme.BLUE, this::showServerCapabilityCheck);
    }

    private void showLinlyAvatarPreview() {
        startActivity(new Intent(this, LinlyAvatarPreviewActivity.class));
    }

    private void showServerCapabilityError(String message) {
        content.removeAllViews();
        addSimplePageHeader("服务端与能力检查", "", null);
        addSpace(content, 8);
        addServerStatusHero("服务端连接失败", compactForCard(message, 70), Theme.RED);
        addCard("错误", compactForCard(message, 80), Theme.RED);
        addSettingButton("连接服务设置", this::showServerUrlDialog);
        addSettingButton("重新检查", this::showServerCapabilityCheck);
        addSettingButton("返回设置", this::showSettings);
    }

    private String companionStateLabel(ServerApiClient.ServerHealth status, boolean realtimePathReady, boolean avatarReady) {
        if (status.linlyDigitalHumanConfigured) {
            return "Linly";
        }
        if (realtimePathReady) {
            return "实时语音";
        }
        if (avatarReady) {
            return status.live2dSdk ? "Live2D" : "本机2D";
        }
        return status.textChat ? "文字可用" : "未完整证明";
    }

    private void addServerStatusHero(String title, String body, int color) {
        LinearLayout card = cardContainer();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Theme.tintedCard(this, color));
        TextView dot = Theme.text(this, color == Theme.RED ? "!" : "✓", 24, color, Typeface.BOLD);
        dot.setGravity(Gravity.CENTER);
        dot.setBackground(Theme.rounded(Color.WHITE, 18, this));
        card.addView(dot, new LinearLayout.LayoutParams(Theme.dp(this, 48), Theme.dp(this, 48)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Theme.text(this, title, 19, Theme.TEXT, Typeface.BOLD), matchWrap());
        words.addView(Theme.text(this, body == null ? "" : body, 13, Theme.MUTED, Typeface.BOLD), matchWrap());
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, -2, 1);
        wordsLp.setMargins(Theme.dp(this, 12), 0, 0, 0);
        card.addView(words, wordsLp);
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addServerCapabilityRow(LinearLayout list, String title, String state, String iconText, int color, boolean ok, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(this, 12), Theme.dp(this, 11), Theme.dp(this, 12), Theme.dp(this, 11));
        int iconColor = ok ? color : Theme.ORANGE;
        TextView icon = Theme.text(this, iconText, 16, iconColor, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Theme.rounded(Theme.mix(iconColor, Color.WHITE, 0.86f), 14, this));
        row.addView(icon, new LinearLayout.LayoutParams(Theme.dp(this, 34), Theme.dp(this, 34)));
        TextView titleView = Theme.text(this, title, 16, Theme.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.setMargins(Theme.dp(this, 10), 0, Theme.dp(this, 8), 0);
        row.addView(titleView, titleLp);
        TextView stateView = Theme.text(this, compactForCard(state, 12) + (action == null ? "" : " ›"), 14, ok ? Theme.darken(Theme.GREEN, 0.25f) : Theme.ORANGE, Typeface.BOLD);
        stateView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(stateView, new LinearLayout.LayoutParams(Theme.dp(this, 116), -2));
        if (action != null) {
            row.setOnClickListener(v -> action.run());
        }
        list.addView(row, matchWrap());
    }

    private String timeNowShort() {
        Calendar calendar = Calendar.getInstance();
        return twoDigits(calendar.get(Calendar.HOUR_OF_DAY)) + ":" + twoDigits(calendar.get(Calendar.MINUTE));
    }

    private void showMicrophoneHonestCheck() {
        stopRealtimeVoiceChat(false);
        content.removeAllViews();
        content.addView(Theme.text(this, "麦克风拾音验证", 32, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            prefs.recordMicrophoneProbeState(false, 0, 0f, 0f, "未授权 RECORD_AUDIO");
            addCheckRow("权限", "未授权", false, null);
            addCheckRow("结论", "未实现到可用", false, null);
            addSettingButton("授权麦克风", () -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_VOICE_CHAT));
            addSettingButton("返回小助手", this::showCompanionChat);
            return;
        }
        addCheckRow("验证中", "说句话或轻拍桌面，2.5秒", false, null);
        addSettingButton("返回小助手", this::showCompanionChat);
        startMicrophoneHonestProbe(++microphoneProbeSerial);
    }

    private void startMicrophoneHonestProbe(final int serial) {
        stopMicrophoneHonestProbe();
        new Thread(() -> {
            final int[] frames = {0};
            final float[] maxRms = {0f};
            final float[] minRms = {Float.MAX_VALUE};
            final String[] error = {""};
            final boolean[] started = {false};
            try {
                LivePcmRecorder recorder = new LivePcmRecorder();
                microphoneProbeRecorder = recorder;
                recorder.start(new LivePcmRecorder.Listener() {
                    @Override
                    public void onPcmFrame(short[] samples, float rms, long captureTimeMs) {
                        frames[0]++;
                        if (rms > maxRms[0]) maxRms[0] = rms;
                        if (rms < minRms[0]) minRms[0] = rms;
                    }

                    @Override
                    public void onRecorderError(Throwable ex) {
                        error[0] = ex == null ? "未知录音错误" : ex.getMessage();
                    }
                });
                started[0] = true;
                Thread.sleep(2500L);
            } catch (Throwable ex) {
                error[0] = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            } finally {
                stopMicrophoneHonestProbe();
            }
            float min = minRms[0] == Float.MAX_VALUE ? 0f : minRms[0];
            prefs.recordMicrophoneProbeState(started[0], frames[0], maxRms[0], min, error[0]);
            if (serial == microphoneProbeSerial) {
                runOnUiThread(() -> showMicrophoneHonestResult(started[0], frames[0], maxRms[0], min, error[0]));
            }
        }, "GouXiongMicrophoneHonestProbe").start();
    }

    private void stopMicrophoneHonestProbe() {
        LivePcmRecorder recorder = microphoneProbeRecorder;
        microphoneProbeRecorder = null;
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (Exception ignored) {
            }
        }
    }

    private void showMicrophoneHonestResult(boolean started, int frames, float maxRms, float minRms, String error) {
        content.removeAllViews();
        content.addView(Theme.text(this, "麦克风拾音验证", 32, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        boolean frameOk = frames >= 12;
        boolean soundOk = maxRms >= 0.010f && maxRms - minRms >= 0.004f;
        addCheckRow("权限", hasPermission(Manifest.permission.RECORD_AUDIO) ? "已授权" : "未授权", hasPermission(Manifest.permission.RECORD_AUDIO), null);
        addCheckRow("AudioRecord", started ? "已启动" : "失败", started, null);
        addCheckRow("PCM帧", frames + " 帧", frameOk, null);
        addCheckRow("声音变化", formatRms(minRms) + "-" + formatRms(maxRms), soundOk, null);
        if (error != null && error.length() > 0) {
            addCard("错误", compactForCard(error, 80), Theme.RED);
        }
        addCheckRow("结论", prefs.microphoneProbePassed() ? "拾音通过" : "未证明真实拾音", prefs.microphoneProbePassed(), null);
        addSettingButton("重新验证", this::showMicrophoneHonestCheck);
        addSettingButton("返回小助手", this::showCompanionChat);
    }

    private String formatRms(float value) {
        return String.format(Locale.US, "%.4f", Math.max(0f, value));
    }

    private String emptyText(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private void syncOwnerProfileQuietAfterAccountOpen() {
        syncOwnerProfileQuiet("account_open");
    }

    private void showServerUrlDialog() {
        EditText input = new EditText(this);
        input.setText(prefs.serverBaseUrl());
        input.setHint("例如 http://10.0.2.2:8787");
        input.setTextSize(18);
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("服务端地址")
                .setMessage("模拟器访问电脑本机用 http://10.0.2.2:8787；真机要填局域网或公网 HTTPS 地址。")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.setServerBaseUrl(input.getText().toString());
                    Toast.makeText(this, "已保存服务端地址", Toast.LENGTH_SHORT).show();
                    showServerAccountSettings();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showPhoneLoginDialog() {
        EditText phone = new EditText(this);
        phone.setHint("输入手机号");
        phone.setText(prefs.serverPhone());
        phone.setTextSize(22);
        phone.setSingleLine(true);
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        new AlertDialog.Builder(this)
                .setTitle("手机号注册")
                .setMessage("我们会向这个手机号发送验证码，用于绑定您的健康档案和小助手长期记忆。")
                .setView(phone)
                .setPositiveButton("获取验证码", (d, w) -> requestServerCode(phone.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void requestServerCode(String phone) {
        String cleanPhone = phone == null ? "" : phone.trim();
        if (cleanPhone.length() < 6) {
            Toast.makeText(this, "手机号不正确", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在获取验证码", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                ServerApiClient.CodeResult result = ServerApiClient.requestCode(prefs.serverBaseUrl(), cleanPhone);
                runOnUiThread(() -> showPhoneVerifyDialog(cleanPhone, result.devCode, result.smsProvider));
            } catch (Exception ex) {
                runOnUiThread(() -> showCompanionReply("注册没成功",
                        "服务端验证码请求失败：" + ex.getMessage() + "\n\n请检查服务端地址是否可访问。"));
            }
        }, "GouXiongRequestCode").start();
    }

    private void showPhoneVerifyDialog(String phone, String devCode, String smsProvider) {
        EditText code = new EditText(this);
        boolean devMode = devCode != null && devCode.length() > 0;
        code.setHint(devMode ? "测试验证码：" + devCode : "输入短信验证码");
        code.setText(devMode ? devCode : "");
        code.setTextSize(24);
        code.setSingleLine(true);
        code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
                .setTitle("输入验证码")
                .setMessage(devMode
                        ? "验证码会在 10 分钟内有效。当前服务端是开发短信模式，已自动填入测试验证码。"
                        : "验证码会在 10 分钟内有效。请查看手机短信，不会在 App 里显示验证码。")
                .setView(code)
                .setPositiveButton("登录", (d, w) -> verifyServerCode(phone, code.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void verifyServerCode(String phone, String code) {
        Toast.makeText(this, "正在登录服务端", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                ServerApiClient.AuthResult result = ServerApiClient.verifyCode(
                        prefs.serverBaseUrl(),
                        phone,
                        code,
                        androidDeviceId());
                prefs.setServerAuth(result.phone, result.token, result.userId);
                CareReminderScheduler.ensureCareReminders(this);
                syncOwnerProfileBlocking();
                runOnUiThread(() -> {
                    Toast.makeText(this, "手机号已注册，服务端记忆已开启", Toast.LENGTH_SHORT).show();
                    showServerAccountSettings();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> showCompanionReply("登录没成功",
                        "验证码登录失败：" + ex.getMessage() + "\n\n请重新获取验证码。"));
            }
        }, "GouXiongVerifyCode").start();
    }

    private String androidDeviceId() {
        try {
            String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            return id == null ? "android" : id;
        } catch (Exception ex) {
            return "android";
        }
    }

    private JSONObject ownerProfileJson() throws Exception {
        return new JSONObject()
                .put("assistant_name", prefs.assistantName())
                .put("assistant_identity", prefs.assistantIdentity())
                .put("owner_address", prefs.ownerAddress())
                .put("health", prefs.healthProfile())
                .put("medication", prefs.medicationHabits())
                .put("sleep", prefs.sleepSituation())
                .put("family", prefs.familySituation())
                .put("hobbies", prefs.hobbies())
                .put("care_preference", prefs.carePreference());
    }

    private void syncOwnerProfileBlocking() throws Exception {
        if (!prefs.serverRegistered()) return;
        ServerApiClient.syncProfile(prefs.serverBaseUrl(), prefs.serverAuthToken(), ownerProfileJson());
    }

    private void syncOwnerProfileAsync(String source) {
        if (!prefs.serverRegistered()) {
            Toast.makeText(this, "请先手机号注册", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                syncOwnerProfileBlocking();
                ServerApiClient.uploadInsight(
                        prefs.serverBaseUrl(),
                        prefs.serverAuthToken(),
                        source == null ? "profile" : source,
                        "profile",
                        prefs.ownerProfileSummary(),
                        1,
                        ownerProfileJson());
                runOnUiThread(() -> Toast.makeText(this, "已同步服务端档案", Toast.LENGTH_SHORT).show());
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(this, "档案同步失败：" + ex.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "GouXiongSyncProfile").start();
    }

    private void syncOwnerProfileQuiet(String source) {
        if (!prefs.serverRegistered()) return;
        new Thread(() -> {
            try {
                syncOwnerProfileBlocking();
                ServerApiClient.uploadInsight(
                        prefs.serverBaseUrl(),
                        prefs.serverAuthToken(),
                        source == null ? "profile" : source,
                        "profile",
                        prefs.ownerProfileSummary(),
                        1,
                        ownerProfileJson());
            } catch (Exception ignored) {
            }
        }, "GouXiongSyncProfileQuiet").start();
    }

    private void maybeSyncCompanionInsight(String text, String source) {
        if (!prefs.serverRegistered() || text == null || text.trim().length() == 0) {
            return;
        }
        String category = localInsightCategory(text);
        if ("general".equals(category) && text.trim().length() < 16) {
            return;
        }
        int severity = localInsightSeverity(category, text);
        new Thread(() -> {
            try {
                ServerApiClient.uploadInsight(
                        prefs.serverBaseUrl(),
                        prefs.serverAuthToken(),
                        source == null ? "chat" : source,
                        category,
                        text,
                        severity,
                        new JSONObject()
                                .put("assistant_role", prefs.companionRole())
                                .put("owner_address", prefs.ownerAddress()));
            } catch (Exception ignored) {
            }
        }, "GouXiongSyncInsight").start();
    }

    private String localInsightCategory(String text) {
        String q = text == null ? "" : text;
        if (containsAny(q, "药", "吃过", "漏吃", "药瓶", "药盒", "降压", "胰岛素")) return "medication";
        if (containsAny(q, "头晕", "胸闷", "不舒服", "疼", "血压", "血糖", "气色", "体检", "报告", "养生", "食补", "食疗", "饮食", "妙招")) return "health";
        if (containsAny(q, "投资", "理财", "转账", "验证码", "保险", "贷款", "收益", "养老项目", "扫码")) return "economy";
        if (containsAny(q, "喘", "憋", "噩梦", "呼吸", "摔", "救命", "异常", "惊醒", "呛咳")) return "abnormal";
        if (containsAny(q, "睡", "打鼾", "午睡", "夜醒", "起床")) return "sleep";
        if (containsAny(q, "孩子", "老伴", "家人", "独居", "女儿", "儿子")) return "family";
        if (containsAny(q, "难过", "孤单", "烦", "开心", "害怕", "心情")) return "mood";
        return "general";
    }

    private int localInsightSeverity(String category, String text) {
        String q = text == null ? "" : text;
        if ("economy".equals(category) && containsAny(q, "转账", "验证码", "贷款", "扫码付款")) return 4;
        if ("abnormal".equals(category) && containsAny(q, "呼吸", "憋", "救命", "摔", "胸闷")) return 4;
        if ("health".equals(category) || "medication".equals(category)) return 2;
        return 1;
    }

    private void fetchServerMessagesForReading() {
        fetchServerMessagesForReading(false);
    }

    private void fetchServerMessagesForReadingSilently() {
        fetchServerMessagesForReading(true);
    }

    private void fetchServerMessagesForReading(boolean silent) {
        if (!prefs.serverRegistered()) {
            if (!silent) Toast.makeText(this, "请先手机号注册", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                JSONArray messages = ServerApiClient.pendingMessages(prefs.serverBaseUrl(), prefs.serverAuthToken());
                if (messages.length() == 0) {
                    if (!silent) runOnUiThread(() -> Toast.makeText(this, "暂时没有新消息", Toast.LENGTH_SHORT).show());
                    return;
                }
                JSONObject first = messages.getJSONObject(0);
                runOnUiThread(() -> readAndMarkServerMessage(first));
            } catch (Exception ex) {
                if (!silent) runOnUiThread(() -> Toast.makeText(this, "服务器消息读取失败：" + ex.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "GouXiongFetchMessages").start();
    }

    private void readAndMarkServerMessage(JSONObject message) {
        int id = message.optInt("id", 0);
        String title = message.optString("title", "小助手提醒");
        String body = message.optString("body", "");
        showCompanionVoiceReply(title, body);
        if (id > 0 && prefs.serverRegistered()) {
            new Thread(() -> {
                try {
                    ServerApiClient.markMessageRead(prefs.serverBaseUrl(), prefs.serverAuthToken(), id);
                } catch (Exception ignored) {
                }
            }, "GouXiongMarkMessageRead").start();
        }
    }

    private void showDeepSeekQuestionDialog() {
        EditText question = new EditText(this);
        question.setHint("想跟小助手说什么？");
        question.setMinLines(4);
        question.setTextSize(20);
        question.setSingleLine(false);
        question.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        new AlertDialog.Builder(this)
                .setTitle("跟小助手说一句")
                .setMessage("会发送你的问题、主人档案摘要、今天状态和睡眠摘要。不要输入银行卡、身份证等敏感信息。")
                .setView(question)
                .setPositiveButton("发送", (d, w) -> askDeepSeek(question.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void requestRequiredPermissionsProactivelySoon() {
        if (requiredRuntimePermissions().isEmpty() || requiredPermissionsRequestedThisSession) {
            return;
        }
        requiredPermissionsRequestedThisSession = true;
        View anchor = content != null ? content : getWindow().getDecorView();
        anchor.postDelayed(() -> {
            java.util.ArrayList<String> permissions = requiredRuntimePermissions();
            if (isFinishing() || permissions.isEmpty() || realtimeVoiceEnabled) {
                return;
            }
            Toast.makeText(this, "请允许麦克风、摄像头、媒体、电话和短信权限，小助手才能听、看、读文件并紧急通知家人。", Toast.LENGTH_LONG).show();
            requestPermissions(permissions.toArray(new String[0]), REQUEST_REQUIRED_PERMISSIONS);
        }, 650);
    }

    private boolean requestMicrophoneForLiveChatIfNeeded() {
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            return false;
        }
        realtimeVoiceEnabled = true;
        updateVoiceStatus("需要允许麦克风，小助手才能直接听您说话。");
        updateLiveStageStatus("需要麦克风权限", "comforting");
        Toast.makeText(this, "请点允许麦克风，我才能听见您。", Toast.LENGTH_LONG).show();
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_VOICE_CHAT);
        return true;
    }

    private boolean requestRequiredPermissionsForLiveChatIfNeeded() {
        java.util.ArrayList<String> permissions = requiredRuntimePermissions();
        if (permissions.isEmpty()) {
            return false;
        }
        realtimeVoiceEnabled = true;
        pendingStartLiveAfterRequiredPermissions = true;
        updateVoiceStatus("需要先允许麦克风、摄像头和媒体权限，我才能听您说话、帮您看东西。");
        updateLiveStageStatus("需要必要权限", "comforting");
        Toast.makeText(this, "请允许必要权限，小助手才能正常听、看、读取文件和紧急通知。", Toast.LENGTH_LONG).show();
        requestPermissions(permissions.toArray(new String[0]), REQUEST_REQUIRED_PERMISSIONS);
        return true;
    }

    private void startRealtimeVoiceChat() {
        realtimeVoiceEnabled = true;
        lastCompanionActiveAtMs = System.currentTimeMillis();
        sleepCheckPending = false;
        updateLiveStageStatus("我在这里，您直接说", "listening");
        XiaozhiVoiceProfile.configureRealtimeAudio(this);
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            requestMicrophoneForLiveChatIfNeeded();
            return;
        }
        if (!prefs.assistantPersonaConfigured() && !firstMeetingPromptedThisSession) {
            firstMeetingPromptedThisSession = true;
            String intro = CompanionAssistant.firstMeetingIntro(prefs.companionRole());
            updateVoiceStatus("我先问您一句，您直接回答就行。");
            updateLiveStageStatus("我先问您一句", "speaking");
            speakAssistantText(intro);
            return;
        }
        if (prefs.serverRegistered()) {
            ensureLiveCompanionSession();
            updateVoiceStatus("正在连接实时语音，连上后您直接说。");
            updateLiveStageStatus("正在连接实时语音", "listening");
            scheduleLiveAsrFallback(++liveAsrFallbackSerial);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateVoiceStatus("这台设备暂时不支持系统语音识别，可以先用下方快捷按钮。");
            updateLiveStageStatus("这台设备暂时不能听", "comforting");
            return;
        }
        ensureVoiceRecognizer();
        updateVoiceStatus("我在听，您直接说，不用点发送。");
        updateLiveStageStatus("我在听，您直接说", "listening");
        restartRealtimeListeningSoon(120);
    }

    private void scheduleLiveAsrFallback(int serial) {
        if (content == null) return;
        content.postDelayed(() -> {
            if (!realtimeVoiceEnabled || serial != liveAsrFallbackSerial) {
                return;
            }
            if (liveAudioStreaming && (liveAudioFrameCount > 0 || liveCompanionConnected)) {
                return;
            }
            prefs.recordLiveAudioFrameState(liveAudioFrameCount, "cloud_asr_fallback_to_system", 0f);
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                updateVoiceStatus("实时语音没有连通，这台设备也没有可用的系统语音识别。");
                updateLiveStageStatus("语音入口不可用", "comforting");
                return;
            }
            ensureVoiceRecognizer();
            updateVoiceStatus("实时语音连接慢，我先用手机系统识别听您说。");
            updateLiveStageStatus("改用系统语音识别", "listening");
            restartRealtimeListeningSoon(120);
        }, 6500);
    }

    private void ensureLiveCompanionSession() {
        if (!prefs.serverRegistered() || liveCompanionConnected || liveCompanionConnecting) {
            Log.i(TAG, "Live WS skip ensure registered=" + prefs.serverRegistered()
                    + " connected=" + liveCompanionConnected
                    + " connecting=" + liveCompanionConnecting);
            return;
        }
        try {
            liveCompanionConnecting = true;
            LiveCompanionSession session = new LiveCompanionSession();
            liveCompanionSession = session;
            Log.i(TAG, "Live WS connect " + liveCompanionEndpoint());
            session.connect(liveCompanionEndpoint(), prefs.serverAuthToken(), "gouxiong-android", "gouxiong-sleep", new LiveCompanionSession.Listener() {
                @Override
                public void onConnected() {
                    Log.i(TAG, "Live WS connected");
                    liveCompanionConnected = true;
                    liveCompanionConnecting = false;
                    try {
                        LiveCompanionSession active = liveCompanionSession;
                        if (active != null) active.startListening("auto");
                    } catch (Exception ignored) {
                    }
                    runOnUiThread(() -> {
                        updateVoiceStatus("实时陪伴已连上，我在听您说。");
                        updateLiveStageStatus("实时陪伴已连上", "listening");
                        startLiveAudioStreamingIfPossible();
                        flushPendingLiveCompanionText();
                    });
                }

                @Override
                public void onTts(String state, String text) {
                    if ("interrupted".equals(state) && text != null && text.length() > 0) {
                        runOnUiThread(() -> {
                            prefs.recordLiveVoiceState("interrupted", "live_abort", text);
                            updateVoiceStatus(text);
                        });
                    }
                }

                @Override
                public void onStt(String text) {
                    runOnUiThread(() -> handleLiveSttTranscript(text));
                }

                @Override
                public void onEmotion(String emotion, float intensity, String gesture, String safetyLevel, String speechText, JSONObject event) {
                    runOnUiThread(() -> {
                        prefs.recordLiveEmotionTagState(emotion, intensity, gesture, safetyLevel, speechText,
                                event == null ? "" : event.optString("source", ""));
                        if (liveStageAvatar != null) {
                            liveStageAvatar.applyCommand(AvatarCommand.setEmotion(emotion, intensity));
                            applyLiveAvatarGesture(gesture);
                        }
                        updateLiveStageStatus(liveEmotionLabel(emotion), liveEmotionMood(emotion, safetyLevel));
                    });
                }

                @Override
                public void onEvent(String type, JSONObject event) {
                    Log.i(TAG, "Live WS event " + type);
                    if ("audio_received".equals(type) && event != null) {
                        int frames = event.optInt("frames", liveAudioFrameCount);
                        prefs.recordLiveAudioFrameState(frames, "server_audio_received", 0f);
                        return;
                    }
                    if ("event".equals(type) && event != null && "realtime_bridge_abort".equals(event.optString("name", ""))) {
                        prefs.recordLiveAbortState("server_realtime_bridge_abort",
                                event.optBoolean("realtime_aborted", false),
                                event.optString("reason", ""));
                        return;
                    }
                    if (!"tts".equals(type) || event == null) {
                        return;
                    }
                    String state = event.optString("state", "");
                    String text = event.optString("text", "");
                    int serial = liveCompanionReplySerial;
                    if ("interrupted".equals(state)) {
                        prefs.recordLiveAbortState("server_tts_interrupted",
                                event.optBoolean("realtime_aborted", false),
                                text);
                        return;
                    }
                    if ("sentence_delta".equals(state)) {
                        runOnUiThread(() -> handleLiveTtsDelta(text, serial));
                        return;
                    }
                    if ("sentence_end".equals(state)) {
                        runOnUiThread(() -> finishLiveTtsReply(text, serial));
                        return;
                    }
                    if ("sentence_start".equals(state) && text.trim().length() > 0) {
                        runOnUiThread(() -> updateVoiceStatus(text));
                    }
                }

                @Override
                public void onAudio(byte[] pcmFrame) {
                    playLiveModelAudioFrame(pcmFrame);
                }

                @Override
                public void onClosed() {
                    Log.i(TAG, "Live WS closed");
                    liveCompanionConnected = false;
                    liveCompanionConnecting = false;
                    stopLiveAudioStreaming();
                    stopLiveModelAudioPlayback();
                }

                @Override
                public void onError(String message, Throwable error) {
                    Log.w(TAG, "Live WS error: " + message, error);
                    liveCompanionConnected = false;
                    liveCompanionConnecting = false;
                    stopLiveAudioStreaming();
                    stopLiveModelAudioPlayback();
                    runOnUiThread(() -> {
                        updateVoiceStatus("实时陪伴连接慢，我先用普通联网回答。");
                        fallbackPendingLiveCompanionText();
                    });
                }
            });
        } catch (Exception ex) {
            Log.w(TAG, "Live WS connect setup failed", ex);
            liveCompanionConnected = false;
            liveCompanionConnecting = false;
        }
    }

    private String liveCompanionEndpoint() {
        String base = prefs.serverBaseUrl();
        if (base == null || base.trim().length() == 0) {
            base = BuildSettings.DEFAULT_SERVER_BASE_URL;
        }
        base = base.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.startsWith("https://")) {
            return "wss://" + base.substring("https://".length()) + "/api/live/session";
        }
        if (base.startsWith("http://")) {
            return "ws://" + base.substring("http://".length()) + "/api/live/session";
        }
        return "ws://" + base + "/api/live/session";
    }

    private boolean trySendLiveCompanionText(String clean, String prompt, int serial) {
        if (!prefs.serverRegistered()) return false;
        Log.i(TAG, "Live WS try text serial=" + serial + " cleanLength=" + (clean == null ? 0 : clean.length()));
        pendingLiveCompanionCleanText = clean == null ? "" : clean;
        pendingLiveCompanionPrompt = prompt == null ? "" : prompt;
        pendingLiveCompanionSerial = serial;
        liveCompanionReplySerial = serial;
        resetLiveStreamingReply(serial);
        ensureLiveCompanionSession();
        if (flushPendingLiveCompanionText()) {
            return true;
        }
        content.postDelayed(() -> {
            if (pendingLiveCompanionSerial == serial && pendingLiveCompanionPrompt.length() > 0) {
                fallbackPendingLiveCompanionText();
            }
        }, 6500);
        return true;
    }

    private boolean flushPendingLiveCompanionText() {
        if (!liveCompanionConnected || liveCompanionSession == null || pendingLiveCompanionPrompt.length() == 0) {
            Log.i(TAG, "Live WS flush blocked connected=" + liveCompanionConnected
                    + " session=" + (liveCompanionSession != null)
                    + " promptLength=" + pendingLiveCompanionPrompt.length());
            return false;
        }
        String prompt = pendingLiveCompanionPrompt;
        LiveCompanionSession active = liveCompanionSession;
        int serial = pendingLiveCompanionSerial;
        pendingLiveCompanionPrompt = "";
        new Thread(() -> {
            try {
                active.sendTextInput(prompt);
                Log.i(TAG, "Live WS text sent promptLength=" + prompt.length());
            } catch (Exception ex) {
                Log.w(TAG, "Live WS send failed", ex);
                runOnUiThread(() -> {
                    if (pendingLiveCompanionSerial == serial && pendingLiveCompanionPrompt.length() == 0) {
                        pendingLiveCompanionPrompt = prompt;
                    }
                    liveCompanionConnected = false;
                    liveCompanionConnecting = false;
                    fallbackPendingLiveCompanionText();
                });
            }
        }, "GouXiongLiveWebSocketSend").start();
        runOnUiThread(() -> {
            updateVoiceStatus("我听懂了，正在实时陪伴里想。");
            updateLiveStageStatus("您别急，我想想", "thinking");
        });
        return true;
    }

    private void startLiveAudioStreamingIfPossible() {
        if (!realtimeVoiceEnabled || !liveCompanionConnected || liveCompanionSession == null) {
            return;
        }
        if (voiceRecognizer != null) {
            try {
                voiceRecognizer.cancel();
            } catch (Exception ignored) {
            }
            voiceListening = false;
        }
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            return;
        }
        if (livePcmRecorder != null && livePcmRecorder.isRunning()) {
            return;
        }
        try {
            liveAudioStreaming = true;
            liveAudioFrameCount = 0;
            lastLiveAudioFrameAtMs = 0L;
            LivePcmRecorder recorder = new LivePcmRecorder();
            livePcmRecorder = recorder;
            recorder.start(new LivePcmRecorder.Listener() {
                @Override
                public void onPcmFrame(short[] samples, float rms, long captureTimeMs) {
                    sendLiveAudioFrame(samples, rms, captureTimeMs);
                }

                @Override
                public void onRecorderError(Throwable error) {
                    Log.w(TAG, "Live PCM recorder error", error);
                    liveAudioStreaming = false;
                    prefs.recordLiveAudioFrameState(liveAudioFrameCount, "recorder_error", 0f);
                    runOnUiThread(() -> updateVoiceStatus("实时麦克风采样暂时不可用，文字识别仍在。"));
                }
            });
            prefs.recordLiveAudioFrameState(0, "recorder_started", 0f);
            Log.i(TAG, "Live PCM recorder started");
        } catch (Exception ex) {
            liveAudioStreaming = false;
            livePcmRecorder = null;
            Log.w(TAG, "Live PCM recorder start failed", ex);
            prefs.recordLiveAudioFrameState(0, "recorder_start_failed", 0f);
        }
    }

    private void handleLiveSttTranscript(String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.length() == 0 || !realtimeVoiceEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (clean.equals(lastLiveSttHandledText) && now - lastLiveSttHandledAtMs < 2500L) {
            return;
        }
        lastLiveSttHandledText = clean;
        lastLiveSttHandledAtMs = now;
        prefs.recordSpeechRecognitionState("server_stt", clean, 0);
        updateVoiceStatus("我听到了，正在想。");
        updateLiveStageStatus("听到了，我正在想", "thinking");
        handlingLiveAsrTranscript = true;
        try {
            handleRealtimeVoiceText(clean);
        } finally {
            handlingLiveAsrTranscript = false;
        }
    }

    private void sendLiveAudioFrame(short[] samples, float rms, long captureTimeMs) {
        LiveCompanionSession active = liveCompanionSession;
        if (!liveAudioStreaming || active == null || !liveCompanionConnected) {
            return;
        }
        try {
            active.sendPcmFrame(LivePcmRecorder.shortsToLittleEndianPcm(samples));
            liveAudioFrameCount++;
            lastLiveAudioFrameAtMs = captureTimeMs;
            maybeAutoBargeInFromPcm(rms, captureTimeMs);
            if (liveAudioFrameCount == 1 || liveAudioFrameCount % 20 == 0) {
                Log.i(TAG, "Live PCM frame sent count=" + liveAudioFrameCount + " rms=" + rms);
                prefs.recordLiveAudioFrameState(liveAudioFrameCount, "client_pcm_sent", rms);
            }
        } catch (Exception ex) {
            Log.w(TAG, "Live PCM frame send failed", ex);
            liveAudioStreaming = false;
            prefs.recordLiveAudioFrameState(liveAudioFrameCount, "client_pcm_send_failed", rms);
        }
    }

    private void stopLiveAudioStreaming() {
        liveAudioStreaming = false;
        LivePcmRecorder recorder = livePcmRecorder;
        livePcmRecorder = null;
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (Exception ignored) {
            }
        }
        if (liveAudioFrameCount > 0) {
            prefs.recordLiveAudioFrameState(liveAudioFrameCount, "client_pcm_stopped", 0f);
        }
        liveBargeInCandidateFrames = 0;
    }

    private void playLiveModelAudioFrame(byte[] pcmFrame) {
        if (pcmFrame == null || pcmFrame.length == 0) {
            return;
        }
        try {
            if (assistantTts != null) {
                try {
                    assistantTts.stop();
                } catch (Exception ignored) {
                }
            }
            if (livePcmPlayer == null) {
                livePcmPlayer = new LivePcmPlayer();
                liveModelAudioFrameCount = 0;
            }
            livePcmPlayer.play(pcmFrame);
            liveModelAudioFrameCount++;
            lastLiveModelAudioFrameAtMs = System.currentTimeMillis();
            prefs.recordLiveModelAudioFrameState(liveModelAudioFrameCount, pcmFrame.length, "played");
            runOnUiThread(() -> {
                updateLiveStageStatus("我慢慢说给您听", "speaking");
                updateAvatarMouthFromPcm(pcmFrame);
                updateVoiceStatus("我正在说，您可以随时插话。");
            });
        } catch (Exception ex) {
            prefs.recordLiveModelAudioFrameState(liveModelAudioFrameCount, pcmFrame.length, "play_failed");
            runOnUiThread(() -> updateVoiceStatus("我准备好声音了，但这台设备暂时没放出来。"));
        }
    }

    private void stopLiveModelAudioPlayback() {
        liveModelAudioFrameCount = 0;
        lastLiveModelAudioFrameAtMs = 0L;
        if (liveStageAvatar != null && !assistantSpeaking) {
            liveStageAvatar.applyCommand(AvatarCommand.stopSpeaking());
            liveStageAvatar.applyCommand(AvatarCommand.setState(avatarStateForMood(liveStageMood)));
        }
        LivePcmPlayer player = livePcmPlayer;
        livePcmPlayer = null;
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
        }
    }

    private void maybeAutoBargeInFromPcm(float rms, long captureTimeMs) {
        long now = captureTimeMs > 0L ? captureTimeMs : System.currentTimeMillis();
        boolean outputActive = liveOutputActiveForBargeIn(now);
        updateLiveBargeInNoiseFloor(rms, outputActive);
        if (!realtimeVoiceEnabled || !liveCompanionConnected || !outputActive) {
            liveBargeInCandidateFrames = 0;
            return;
        }
        if (now - lastLiveBargeInAtMs < LIVE_BARGE_IN_COOLDOWN_MS) {
            return;
        }
        float threshold = liveBargeInSpeechThresholdRms();
        float exitThreshold = liveBargeInExitThresholdRms(threshold);
        lastLiveBargeInThresholdRms = threshold;
        if (rms >= threshold) {
            liveBargeInCandidateFrames++;
        } else if (rms <= exitThreshold) {
            liveBargeInCandidateFrames = 0;
        } else {
            return;
        }
        if (liveBargeInCandidateFrames < LIVE_BARGE_IN_CONSECUTIVE_FRAMES) {
            return;
        }
        lastLiveBargeInAtMs = now;
        liveBargeInCandidateFrames = 0;
        final float finalThreshold = threshold;
        final float finalNoiseFloor = liveBargeInNoiseFloorRms;
        runOnUiThread(() -> triggerAutoLiveBargeIn(rms, finalThreshold, finalNoiseFloor, LIVE_BARGE_IN_CONSECUTIVE_FRAMES));
    }

    private boolean liveOutputActiveForBargeIn(long now) {
        if (assistantSpeaking) {
            return true;
        }
        return lastLiveModelAudioFrameAtMs > 0L && now - lastLiveModelAudioFrameAtMs <= LIVE_MODEL_AUDIO_ACTIVE_WINDOW_MS;
    }

    private void updateLiveBargeInNoiseFloor(float rms, boolean outputActive) {
        if (rms <= 0f) {
            return;
        }
        float threshold = liveBargeInSpeechThresholdRms();
        if (outputActive && rms >= threshold) {
            return;
        }
        float clamped = Math.max(0.004f, Math.min(0.080f, rms));
        liveBargeInNoiseFloorRms = liveBargeInNoiseFloorRms <= 0f
                ? clamped
                : liveBargeInNoiseFloorRms * (1f - LIVE_BARGE_IN_NOISE_FLOOR_ALPHA)
                + clamped * LIVE_BARGE_IN_NOISE_FLOOR_ALPHA;
    }

    private float liveBargeInSpeechThresholdRms() {
        float adaptive = Math.max(LIVE_BARGE_IN_RMS_THRESHOLD,
                liveBargeInNoiseFloorRms * LIVE_BARGE_IN_NOISE_MULTIPLIER);
        return Math.min(LIVE_BARGE_IN_MAX_RMS_THRESHOLD, adaptive);
    }

    private float liveBargeInExitThresholdRms(float threshold) {
        float adaptiveExit = liveBargeInNoiseFloorRms * LIVE_BARGE_IN_EXIT_MULTIPLIER;
        return Math.max(0.020f, Math.min(threshold * 0.72f, adaptiveExit));
    }

    private void debugFeedAutoBargeInFrames() {
        long base = System.currentTimeMillis();
        lastLiveModelAudioFrameAtMs = base;
        liveBargeInCandidateFrames = 0;
        float simulatedRms = Math.max(0.12f, liveBargeInSpeechThresholdRms() + 0.035f);
        for (int i = 0; i < LIVE_BARGE_IN_CONSECUTIVE_FRAMES; i++) {
            maybeAutoBargeInFromPcm(simulatedRms, base + (long) i * LivePcmRecorder.FRAME_DURATION_MS);
        }
    }

    private void triggerAutoLiveBargeIn(float rms, float thresholdRms, float noiseFloorRms, int frames) {
        if (!realtimeVoiceEnabled) {
            return;
        }
        prefs.recordLiveAutoBargeInState(rms, frames, thresholdRms, noiseFloorRms,
                frames * LivePcmRecorder.FRAME_DURATION_MS);
        voiceConversationSerial++;
        stopAssistantSpeech();
        abortLiveCompanionSpeech();
        updateVoiceStatus("我听到您插话了，先停下，您说。");
        updateLiveStageStatus("我先停下，听您说", "interrupted");
        if (content != null) {
            content.postDelayed(() -> updateLiveStageStatus("我听到您了", "listening"), 180L);
        }
        restartRealtimeListeningSoon(120);
    }

    private void fallbackPendingLiveCompanionText() {
        if (pendingLiveCompanionPrompt.length() == 0) return;
        Log.w(TAG, "Live WS fallback to HTTP serial=" + pendingLiveCompanionSerial
                + " connected=" + liveCompanionConnected
                + " connecting=" + liveCompanionConnecting);
        String clean = pendingLiveCompanionCleanText;
        int serial = pendingLiveCompanionSerial;
        pendingLiveCompanionPrompt = "";
        pendingLiveCompanionCleanText = "";
        pendingLiveCompanionSerial = 0;
        startHttpVoiceAnswer(clean, serial);
    }

    private void abortLiveCompanionSpeech() {
        LiveCompanionSession active = liveCompanionSession;
        String stateDetail = "connected=" + liveCompanionConnected
                + ", connecting=" + liveCompanionConnecting
                + ", session=" + (active != null);
        if (active != null && liveCompanionConnected) {
            prefs.recordLiveAbortState("client_abort_sent", false, stateDetail);
            new Thread(() -> {
                try {
                    active.abortCurrentSpeech();
                    prefs.recordLiveAbortState("client_abort_sent_ok", false, stateDetail);
                    Log.i(TAG, "Live WS abort sent");
                } catch (Exception ex) {
                    prefs.recordLiveAbortState("client_abort_send_failed", false, ex.getMessage());
                    Log.w(TAG, "Live WS abort failed", ex);
                }
            }, "GouXiongLiveWebSocketAbort").start();
        } else {
            prefs.recordLiveAbortState("client_abort_skipped", false, stateDetail);
        }
    }

    private String liveEmotionLabel(String emotion) {
        if ("thinking".equals(emotion)) return "您别急，我想想";
        if ("speaking".equals(emotion)) return "我慢慢说给您听";
        if ("listening".equals(emotion)) return "我在听您说话";
        if ("happy".equals(emotion)) return "我也很开心";
        if ("worried".equals(emotion)) return "我有点担心您";
        if ("seeing".equals(emotion)) return "我认真看一下";
        if ("reading".equals(emotion)) return "我帮您读清楚";
        if ("finding".equals(emotion)) return "您别急，我帮您想想";
        if ("comforting".equals(emotion)) return "我陪着您";
        if ("urgent_wakeup".equals(emotion)) return "快醒醒，我在这里";
        return "我在这里";
    }

    private void applyLiveAvatarGesture(String gesture) {
        if (liveStageAvatar == null || gesture == null) {
            return;
        }
        if ("wave".equals(gesture)) {
            liveStageAvatar.applyCommand(AvatarCommand.wave());
        } else if ("nod".equals(gesture)) {
            liveStageAvatar.applyCommand(AvatarCommand.nod());
        } else if ("lookDown".equals(gesture)) {
            liveStageAvatar.applyCommand(AvatarCommand.lookDown());
        } else if ("lookAtUser".equals(gesture)) {
            liveStageAvatar.applyCommand(AvatarCommand.lookAtUser());
        } else if ("urgentWake".equals(gesture)) {
            liveStageAvatar.applyCommand(AvatarCommand.urgentWake());
        }
    }

    private String liveEmotionMood(String emotion, String safetyLevel) {
        if ("urgent".equals(safetyLevel)) return "urgent_wakeup";
        return liveEmotionMood(emotion);
    }

    private String liveEmotionMood(String emotion) {
        if ("thinking".equals(emotion)) return "thinking";
        if ("speaking".equals(emotion)) return "speaking";
        if ("listening".equals(emotion)) return "listening";
        if ("happy".equals(emotion)) return "happy";
        if ("worried".equals(emotion)) return "worried";
        if ("seeing".equals(emotion)) return "seeing";
        if ("reading".equals(emotion)) return "reading";
        if ("finding".equals(emotion)) return "finding";
        if ("comforting".equals(emotion)) return "comforting";
        if ("urgent_wakeup".equals(emotion)) return "urgent_wakeup";
        return "comforting";
    }

    private void resetLiveStreamingReply(int serial) {
        liveStreamingSerial = serial;
        liveStreamingReplyBuffer.setLength(0);
        liveStreamingSpeechBuffer.setLength(0);
        liveStreamingTtsChunkCount = 0;
        lastLiveStreamingUiUpdateAtMs = 0L;
        prefs.resetLiveTtsDeltaState(serial);
    }

    private void handleLiveTtsDelta(String delta, int serial) {
        String cleanDelta = delta == null ? "" : delta;
        if (serial != voiceConversationSerial || cleanDelta.trim().length() == 0) {
            return;
        }
        if (liveStreamingSerial != serial) {
            resetLiveStreamingReply(serial);
        }
        liveStreamingReplyBuffer.append(cleanDelta);
        liveStreamingSpeechBuffer.append(cleanDelta);
        String visible = liveStreamingReplyBuffer.toString().trim();
        prefs.recordLiveTtsDeltaState(serial, visible);
        long now = System.currentTimeMillis();
        if (lastLiveStreamingUiUpdateAtMs == 0L || now - lastLiveStreamingUiUpdateAtMs >= 450L) {
            lastLiveStreamingUiUpdateAtMs = now;
            prefs.recordLiveVoiceState("streaming", "我正在说", visible);
            updateLiveStageStatus("我边想边说", "speaking");
            updateLiveStageSpeech(liveBubbleText(visible));
            updateVoiceStatus("我正在说，您可以随时插话。");
        }
        if (!shouldPreferRealtimeModelAudio()) {
            speakLiveStreamingChunk(false);
        }
    }

    private void finishLiveTtsReply(String answer, int serial) {
        if (serial != voiceConversationSerial) {
            return;
        }
        String finalAnswer = answer == null ? "" : answer.trim();
        if (liveStreamingSerial != serial) {
            resetLiveStreamingReply(serial);
        }
        if (liveStreamingReplyBuffer.length() == 0 && finalAnswer.length() == 0) {
            return;
        }
        String streamed = liveStreamingReplyBuffer.toString();
        if (finalAnswer.length() > streamed.length() && finalAnswer.startsWith(streamed)) {
            String rest = finalAnswer.substring(streamed.length());
            liveStreamingReplyBuffer.append(rest);
            liveStreamingSpeechBuffer.append(rest);
        } else if (streamed.length() == 0 && finalAnswer.length() > 0) {
            liveStreamingReplyBuffer.append(finalAnswer);
            liveStreamingSpeechBuffer.append(finalAnswer);
        }
        String core = liveStreamingReplyBuffer.length() > 0 ? liveStreamingReplyBuffer.toString().trim() : finalAnswer;
        String reply = core;
        maybeSyncCompanionInsight("实时陪伴回复：" + core, "live_voice_reply");
        prefs.recordLiveVoiceState("reply", "我听懂了", reply);
        updateLiveStageStatus("我慢慢说给您听", "speaking");
        updateLiveStageSpeech(liveBubbleText(reply));
        updateVoiceStatus("我听懂了。您可以随时插话。");
        if (shouldPreferRealtimeModelAudio() || liveModelAudioFrameCount > 0) {
            liveStreamingSpeechBuffer.setLength(0);
            if (liveModelAudioFrameCount > 0) {
                updateVoiceStatus("我正在说，您可以随时插话。");
            } else {
                updateVoiceStatus("我正在准备声音；如果一会儿没出声，我就用手机声音说给您听。");
                content.postDelayed(() -> {
                    if (realtimeVoiceEnabled && liveModelAudioFrameCount == 0 && liveStreamingSerial == serial) {
                        speakAssistantText(reply);
                    }
                }, 1800L);
            }
        } else if (liveStreamingSpeechBuffer.length() > 0) {
            liveStreamingSpeechBuffer.append("。");
            speakLiveStreamingChunk(true);
        } else if (liveStreamingTtsChunkCount > 0) {
            speakAssistantTextQueued("我说完啦，您慢慢来。", prefs.companionRole(),
                    TextToSpeech.QUEUE_ADD, "gouxiong-live-stream-" + serial + "-final");
        } else {
            speakAssistantText(reply);
        }
    }

    private void speakLiveStreamingChunk(boolean finalFlush) {
        String pending = liveStreamingSpeechBuffer.toString().trim();
        if (pending.length() == 0) {
            return;
        }
        if (!finalFlush && !isLiveStreamingChunkReady(pending)) {
            return;
        }
        liveStreamingSpeechBuffer.setLength(0);
        int chunk = ++liveStreamingTtsChunkCount;
        int queueMode = chunk == 1 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
        String suffix = finalFlush ? "-final" : "";
        speakAssistantTextQueued(pending, prefs.companionRole(), queueMode,
                "gouxiong-live-stream-" + liveStreamingSerial + "-" + chunk + suffix);
    }

    private boolean shouldPreferRealtimeModelAudio() {
        return realtimeVoiceEnabled && liveCompanionConnected;
    }

    private boolean isLiveStreamingChunkReady(String pending) {
        if (pending.length() >= 60) {
            return true;
        }
        char last = pending.charAt(pending.length() - 1);
        return last == '。' || last == '！' || last == '？' || last == '；'
                || last == '!' || last == '?' || last == ';';
    }

    private void ensureVoiceRecognizer() {
        if (voiceRecognizer != null) return;
        voiceRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        voiceRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                lastVoiceCallbackAtMs = System.currentTimeMillis();
                prefs.recordSpeechRecognitionState("ready", "", 0);
                updateVoiceStatus("我在听，您直接说。");
                updateLiveStageStatus("我在听您说话", "listening");
            }

            @Override
            public void onBeginningOfSpeech() {
                lastVoiceCallbackAtMs = System.currentTimeMillis();
                lastCompanionActiveAtMs = lastVoiceCallbackAtMs;
                prefs.recordSpeechRecognitionState("beginning", "", 0);
                stopAssistantSpeech();
                updateVoiceStatus("听到了，您慢慢说。");
                updateLiveStageStatus("听到了，您慢慢说", "user_speaking");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                handleVoiceRms(rmsdB);
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                lastVoiceCallbackAtMs = System.currentTimeMillis();
                lastCompanionActiveAtMs = lastVoiceCallbackAtMs;
                prefs.recordSpeechRecognitionState("end", "", 0);
                updateVoiceStatus("我听完了，正在想。");
                updateLiveStageStatus("我听完了，正在想", "thinking");
            }

            @Override
            public void onError(int error) {
                voiceListening = false;
                prefs.recordSpeechRecognitionState("error", "", error);
                if (!realtimeVoiceEnabled) return;
                if (sleepCheckPending && error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    enterSleepGuardFromCompanion(false);
                    return;
                }
                if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT && maybeAskIfUserAsleep()) {
                    return;
                }
                updateVoiceStatus(voiceErrorText(error));
                updateLiveStageStatus("刚才没听清，再说一遍", "comforting");
                restartRealtimeListeningSoon(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 900 : 500);
            }

            @Override
            public void onResults(Bundle results) {
                voiceListening = false;
                String text = bestRecognitionText(results);
                prefs.recordSpeechRecognitionState("result", text, 0);
                handleRealtimeVoiceText(text);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                String text = bestRecognitionText(partialResults);
                if (text.length() > 0) {
                    prefs.recordSpeechRecognitionState("partial", text, 0);
                    stopAssistantSpeech();
                    updateVoiceStatus("听到了，您继续说。");
                    updateLiveStageStatus("听到了，您继续说", "user_speaking");
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    private Intent realtimeVoiceIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700);
        return intent;
    }

    private void restartRealtimeListeningSoon(long delayMs) {
        if (!realtimeVoiceEnabled || voiceRestartScheduled) return;
        if (content == null) return;
        voiceRestartScheduled = true;
        content.postDelayed(() -> {
            voiceRestartScheduled = false;
            startRealtimeListening();
        }, delayMs);
    }

    private void startRealtimeListening() {
        if (!realtimeVoiceEnabled || assistantSpeaking || voiceListening || voiceRecognizer == null || !hasPermission(Manifest.permission.RECORD_AUDIO)) {
            return;
        }
        try {
            int listenSerial = ++voiceListenSerial;
            voiceListenStartedAtMs = System.currentTimeMillis();
            lastVoiceCallbackAtMs = 0L;
            lastVoiceSoundAtMs = 0L;
            voiceInputLevel = 0f;
            updateVoiceStatus("正在打开麦克风，您可以直接说。");
            updateLiveStageStatus("正在打开麦克风", "listening");
            stopLiveAudioStreaming();
            prefs.recordSpeechRecognitionState("starting", "", 0);
            voiceRecognizer.cancel();
            voiceRecognizer.startListening(realtimeVoiceIntent());
            voiceListening = true;
            scheduleVoiceListenWatchdog(listenSerial);
        } catch (Exception ex) {
            voiceListening = false;
            prefs.recordSpeechRecognitionState("start_failed", ex.getMessage(), -1);
            updateVoiceStatus("语音入口暂时不可用，请稍后再试。");
            updateLiveStageStatus("语音入口暂时不可用", "comforting");
        }
    }

    private void handleVoiceRms(float rmsdB) {
        long now = System.currentTimeMillis();
        lastVoiceCallbackAtMs = now;
        float level = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f));
        voiceInputLevel = level;
        if (level > 0.18f) {
            lastVoiceSoundAtMs = now;
            lastCompanionActiveAtMs = now;
            updateVoiceStatus("麦克风有声音，我在听。");
            updateLiveStageStatus("听到了，您继续说", "user_speaking");
        }
    }

    private void scheduleVoiceListenWatchdog(int listenSerial) {
        content.postDelayed(() -> {
            if (!realtimeVoiceEnabled || !voiceListening || listenSerial != voiceListenSerial) {
                return;
            }
            long started = voiceListenStartedAtMs;
            if (lastVoiceCallbackAtMs < started) {
                updateVoiceStatus("系统语音入口还没响应，请确认手机语音识别服务可用。");
                updateLiveStageStatus("麦克风入口没响应", "comforting");
            } else if (lastVoiceSoundAtMs < started) {
                if (!maybeAskIfUserAsleep()) {
                    updateVoiceStatus("麦克风已打开，但还没检测到声音。请靠近手机说一句。");
                    updateLiveStageStatus("我在等您说话", "listening");
                }
            }
        }, 1800);
    }

    private String voiceErrorText(int error) {
        if (error == SpeechRecognizer.ERROR_AUDIO) {
            return "麦克风录音出错，请确认没有被别的 App 占用。";
        }
        if (error == SpeechRecognizer.ERROR_CLIENT) {
            return "系统语音识别临时出错，我再试一次。";
        }
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            return "麦克风权限没有打开，请允许后再说。";
        }
        if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
            return "语音识别网络慢，我再听一次。";
        }
        if (error == SpeechRecognizer.ERROR_NO_MATCH) {
            return "麦克风开着，但没听清人声，请靠近一点再说。";
        }
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            return "语音入口正忙，我马上重新听。";
        }
        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            return "我没听到声音，请直接说一句。";
        }
        return "刚才没听清，您可以再说一遍。";
    }

    private void stopRealtimeVoiceChat(boolean clearStatus) {
        voicePageSerial++;
        realtimeVoiceEnabled = false;
        voiceListening = false;
        voiceRestartScheduled = false;
        voiceInputLevel = 0f;
        liveCompanionConnected = false;
        liveCompanionConnecting = false;
        stopLiveAudioStreaming();
        stopLiveModelAudioPlayback();
        pendingLiveCompanionPrompt = "";
        pendingLiveCompanionCleanText = "";
        pendingLiveCompanionSerial = 0;
        if (liveCompanionSession != null) {
            try {
                liveCompanionSession.close();
            } catch (Exception ignored) {
            }
            liveCompanionSession = null;
        }
        XiaozhiVoiceProfile.restoreRealtimeAudio(this);
        if (voiceRecognizer != null) {
            try {
                voiceRecognizer.cancel();
            } catch (Exception ignored) {
            }
        }
        if (clearStatus) {
            updateVoiceStatus("实时语音已暂停。");
        }
    }

    private void stopAssistantSpeech() {
        assistantSpeaking = false;
        stopLiveModelAudioPlayback();
        stopAvatarSpeaking();
        if (assistantTts != null) {
            try {
                assistantTts.stop();
            } catch (Exception ignored) {
            }
        }
    }

    private void updateVoiceStatus(String text) {
        if (voiceStatusLabel != null) {
            voiceStatusLabel.setText(text == null ? "" : text);
        }
    }

    private String bestRecognitionText(Bundle bundle) {
        if (bundle == null) return "";
        java.util.ArrayList<String> list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty() || list.get(0) == null) return "";
        return list.get(0).trim();
    }

    private void handleRealtimeVoiceText(String heard) {
        String clean = heard == null ? "" : heard.trim();
        if (!realtimeVoiceEnabled) return;
        if (clean.length() == 0) {
            updateVoiceStatus("没听清，您再说一遍。");
            restartRealtimeListeningSoon(500);
            return;
        }
        lastCompanionActiveAtMs = System.currentTimeMillis();
        if (sleepCheckPending || recentPersistedSleepCheckPending()) {
            if (looksLikeStillAwakeReply(clean)) {
                sleepCheckPending = false;
                prefs.recordSleepCheckPending(false);
                restoreSleepSoundAfterAwakeReply();
                showCompanionVoiceReply("我继续陪您", sleepCheckContinueLine());
                return;
            }
            if (looksLikeReadyToSleepReply(clean)) {
                prefs.recordSleepCheckPending(false);
                enterSleepGuardFromCompanion(true);
                return;
            }
            updateVoiceStatus("我听到了。您要是还没睡，可以直接说“没睡”或“继续”。");
            updateLiveStageStatus("等您确认是否睡着", "comforting");
            return;
        }
        if (!prefs.assistantPersonaConfigured()) {
            saveAssistantPersonaFromMessage(clean);
            return;
        }
        if (looksLikeBedtimeStoryRequest(clean)) {
            prefs.recordCompanionShortcutRoute("bedtime_story", clean);
            askBedtimeStory();
            return;
        }
        if (looksLikeNewsRequest(clean)) {
            prefs.recordCompanionShortcutRoute("news_briefing", clean);
            showNewsCapabilityStatus();
            return;
        }
        if (looksLikeSleepSoundStopRequest(clean)) {
            prefs.recordCompanionShortcutRoute("music_playback_stop", clean);
            stopSleepSound();
            return;
        }
        if (looksLikeSleepSoundStartRequest(clean)) {
            prefs.recordCompanionShortcutRoute("music_playback", clean);
            startSleepSound();
            return;
        }
        int serial = ++voiceConversationSerial;
        boolean findQuestion = looksLikeFindObjectQuestion(clean);
        maybeSyncCompanionInsight(clean, "voice_chat");
        String objectAnswer = db.objectMemoryAnswer(clean);
        VisionIntent visionIntent = analyzeVisionIntent(clean);
        if (objectAnswer.length() > 0 && findQuestion && !visionIntent.explicitLook) {
            showCompanionVoiceReply("我帮你找", objectAnswer);
            return;
        }
        if (visionIntent.matched) {
            handleRealtimeVisionIntent(visionIntent);
            return;
        }
        if (looksLikeWellnessTipQuestion(clean)) {
            showWellnessTip();
            return;
        }
        if (!prefs.deepSeekKeyConfigured()) {
            showCompanionVoiceReply("我先陪您说", localVoiceFallback(clean));
            return;
        }
        String thinking = CompanionAssistant.thinkingComfortLine(prefs.companionRole(), findQuestion ? "find" : "chat");
        if (handlingLiveAsrTranscript && liveCompanionConnected) {
            prefs.recordLiveVoiceState("heard", "server_stt", clean);
            hideBottomNavForLiveCompanion();
            content.removeAllViews();
            addLiveCompanionStage("我听懂了", "我正在想怎么跟您说。", "thinking");
            updateVoiceStatus("我听懂了，正在想。");
            updateLiveStageStatus("我听懂了，正在回答", "thinking");
            return;
        }
        showCompanionVoiceWaiting(thinking);
        if (trySendLiveCompanionText(clean, deepSeekUserPrompt(clean), serial)) {
            return;
        }
        startHttpVoiceAnswer(clean, serial);
    }

    private boolean recentPersistedSleepCheckPending() {
        long at = prefs.sleepCheckPendingAt();
        return prefs.sleepCheckPending()
                && at > 0L
                && System.currentTimeMillis() - at < 5L * 60L * 1000L;
    }

    private void startHttpVoiceAnswer(String clean, int serial) {
        new Thread(() -> {
            try {
                String answer = askAssistantModel(deepSeekUserPrompt(clean));
                maybeSyncCompanionInsight("用户：" + clean + "\n小助手：" + answer, "voice_reply");
                runOnUiThread(() -> {
                    if (serial == voiceConversationSerial) {
                        showCompanionVoiceReply("我听懂了", answer);
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (serial == voiceConversationSerial) {
                        showCompanionVoiceReply("这次没连上",
                                "刚才我没接上，话没想完整。您别急，我还在，您可以再说一遍。");
                    }
                });
            }
        }, "GouXiongRealtimeVoiceChat").start();
    }

    private void startCompanionModelAnswer(String topic, String prompt, String syncSource) {
        int serial = ++voiceConversationSerial;
        String cleanTopic = topic == null || topic.trim().length() == 0 ? "我听懂了" : topic.trim();
        String cleanPrompt = prompt == null ? "" : prompt.trim();
        if (cleanPrompt.length() == 0) {
            showCompanionVoiceReply(cleanTopic, localVoiceFallback(""));
            return;
        }
        showCompanionVoiceWaiting(CompanionAssistant.thinkingComfortLine(prefs.companionRole(), syncSource));
        if (trySendLiveCompanionText(cleanTopic, cleanPrompt, serial)) {
            return;
        }
        startHttpVoiceAnswerWithPrompt(cleanTopic, cleanPrompt, serial, syncSource);
    }

    private void startHttpVoiceAnswerWithPrompt(String topic, String prompt, int serial, String syncSource) {
        new Thread(() -> {
            try {
                String answer = askAssistantModel(prompt);
                maybeSyncCompanionInsight("任务：" + topic + "\n小助手：" + answer,
                        syncSource == null || syncSource.length() == 0 ? "voice_reply" : syncSource);
                runOnUiThread(() -> {
                    if (serial == voiceConversationSerial) {
                        showCompanionVoiceReply(topic, answer);
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (serial == voiceConversationSerial) {
                        showCompanionVoiceReply("这次没连上",
                                "刚才我没接上，没能好好回答。您别急，我先陪着您。");
                    }
                });
            }
        }, "GouXiongCompanionModelTask").start();
    }

    private String localVoiceFallback(String question) {
        if (looksLikeFindObjectQuestion(question)) {
            return CompanionAssistant.findObjectLine(question, db.objectMemorySummary() + "\n" + prefs.visualMemorySummary());
        }
        if (looksLikeWellnessTipQuestion(question)) {
            return CompanionAssistant.wellnessTipLine(
                    prefs.companionRole(),
                    prefs.ownerAddress(),
                    prefs.healthProfile(),
                    prefs.medicationHabits(),
                    prefs.sleepSituation(),
                    prefs.hobbies())
                    + "\n\n我先按本机记着的情况陪您想一想。";
        }
        return "我听到了。现在我还不能长篇回答，我先按手机里记着的情况陪您说两句。\n\n"
                + proactiveCareText()
                + "\n\n想让我聊得更自然，可以到“我的”里打开联网陪伴。";
    }

    private void showCompanionVoiceWaiting(String line) {
        prefs.recordLiveVoiceState("waiting", "您别急，我想想", line);
        hideBottomNavForLiveCompanion();
        content.removeAllViews();
        addLiveCompanionStage("您别急，我想想", line, "thinking");
        updateVoiceStatus("我先陪着您，正在想。");
        speakAssistantText(line);
    }

    private void showCompanionVoiceReply(String topic, String reply) {
        prefs.recordLiveVoiceState("reply", topic, reply);
        pendingSleepContinuationText = reply == null ? "" : reply.trim();
        lastCompanionActiveAtMs = System.currentTimeMillis();
        hideBottomNavForLiveCompanion();
        content.removeAllViews();
        addLiveCompanionStage("我慢慢说给您听", liveBubbleText(reply), "speaking");
        updateVoiceStatus(topic + "。您可以随时插话。");
        speakAssistantText(reply);
    }

    private boolean maybeAskIfUserAsleep() {
        if (!realtimeVoiceEnabled || assistantSpeaking || sleepCheckPending) {
            return false;
        }
        long now = System.currentTimeMillis();
        long activeAt = lastCompanionActiveAtMs > 0L ? lastCompanionActiveAtMs : voiceListenStartedAtMs;
        if (activeAt > 0L && now - activeAt < 45L * 1000L) {
            return false;
        }
        sleepCheckPending = true;
        prefs.recordSleepCheckPending(true);
        voiceConversationSerial++;
        String line = prefs.ownerAddress() + "，您睡了么？";
        prefs.recordLiveVoiceState("sleep_check", "可能睡着确认", line);
        updateVoiceStatus("我轻轻问一句，确认您是不是睡着了。");
        updateLiveStageStatus("我轻轻问一句", "comforting");
        if (sleepSoundPlayer != null && sleepSoundPlayer.isRunning()) {
            sleepSoundPlayer.duckForSpeech();
        }
        speakAssistantTextQueued(line, prefs.companionRole(), TextToSpeech.QUEUE_FLUSH, "sleep-check");
        return true;
    }

    private boolean looksLikeStillAwakeReply(String text) {
        String q = text == null ? "" : text.trim();
        return containsAny(q, "没睡", "没有睡", "还没睡", "没睡着", "睡不着", "没呢", "没有呢",
                "还没", "没有", "没", "别停", "继续", "接着", "再讲", "再说", "放着", "听着",
                "not_asleep", "still_awake", "continue_companion");
    }

    private boolean looksLikeReadyToSleepReply(String text) {
        String q = text == null ? "" : text.trim();
        return containsAny(q, "睡了", "要睡", "想睡", "困了", "睡着", "晚安", "不用了", "停吧", "关了", "守护",
                "asleep_now", "start_sleep_guard");
    }

    private String sleepCheckContinueLine() {
        String last = pendingSleepContinuationText == null ? "" : pendingSleepContinuationText.trim();
        if (last.length() == 0) {
            return "好的，那我继续陪您。您想聊天、听故事，还是听点轻音乐，都可以慢慢说。";
        }
        return "好的，那我继续。\n\n" + last;
    }

    private void restoreSleepSoundAfterAwakeReply() {
        if (sleepSoundPlayer == null || !sleepSoundPlayer.isRunning() || content == null) {
            return;
        }
        content.postDelayed(() -> {
            if (sleepSoundPlayer != null && sleepSoundPlayer.isRunning()) {
                sleepSoundPlayer.restoreGentleVolume(1800L);
            }
        }, 1800L);
    }

    private void enterSleepGuardFromCompanion(boolean confirmedByUser) {
        sleepCheckPending = false;
        prefs.recordSleepCheckPending(false);
        pendingSleepContinuationText = "";
        String line = confirmedByUser
                ? "好的，您安心睡。我把声音收起来，开始安静守护。"
                : "我没听到您回答，就先当您可能睡着了。我把声音收起来，开始安静守护。";
        prefs.recordLiveVoiceState("sleep_guard", "转入睡眠守护", line);
        updateVoiceStatus("准备转入睡眠守护。");
        updateLiveStageStatus("我安静守着您", "comforting");
        if (sleepSoundPlayer != null && sleepSoundPlayer.isRunning()) {
            sleepSoundPlayer.fadeOutAndStop(confirmedByUser ? 2200L : 4200L);
        }
        if (confirmedByUser) {
            speakAssistantTextQueued(line, prefs.companionRole(), TextToSpeech.QUEUE_FLUSH, "sleep-guard-start");
            if (content != null) {
                content.postDelayed(() -> {
                    stopRealtimeVoiceChat(false);
                    startMonitoring();
                }, 3200L);
            }
        } else {
            stopRealtimeVoiceChat(false);
            startMonitoring();
        }
    }

    private void askDeepSeek(String question) {
        String cleanQuestion = question == null ? "" : question.trim();
        if (cleanQuestion.length() == 0) {
            Toast.makeText(this, "问题不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (looksLikeBedtimeStoryRequest(cleanQuestion)) {
            prefs.recordCompanionShortcutRoute("bedtime_story", cleanQuestion);
            askBedtimeStory();
            return;
        }
        if (looksLikeNewsRequest(cleanQuestion)) {
            prefs.recordCompanionShortcutRoute("news_briefing", cleanQuestion);
            showNewsCapabilityStatus();
            return;
        }
        if (looksLikeSleepSoundStopRequest(cleanQuestion)) {
            prefs.recordCompanionShortcutRoute("music_playback_stop", cleanQuestion);
            stopSleepSound();
            return;
        }
        if (looksLikeSleepSoundStartRequest(cleanQuestion)) {
            prefs.recordCompanionShortcutRoute("music_playback", cleanQuestion);
            startSleepSound();
            return;
        }
        boolean findQuestion = looksLikeFindObjectQuestion(cleanQuestion);
        maybeSyncCompanionInsight(cleanQuestion, "typed_chat");
        String objectAnswer = db.objectMemoryAnswer(cleanQuestion);
        if (objectAnswer.length() > 0 && findQuestion) {
            showCompanionReply("我帮你找", objectAnswer);
            return;
        }
        if (looksLikeWellnessTipQuestion(cleanQuestion)) {
            showWellnessTip();
            return;
        }
        showCompanionWaiting("小助手在想",
                CompanionAssistant.thinkingComfortLine(prefs.companionRole(), findQuestion ? "find" : "chat")
                        + "\n\n夜间强唤醒保留本地兜底，避免网络波动影响安全。");
        new Thread(() -> {
            try {
                String answer = askAssistantModel(deepSeekUserPrompt(cleanQuestion));
                maybeSyncCompanionInsight("用户：" + cleanQuestion + "\n小助手：" + answer, "typed_reply");
                runOnUiThread(() -> showCompanionReply("小助手回答", answer));
            } catch (Exception ex) {
                runOnUiThread(() -> showCompanionReply("小助手没连上",
                        "刚才我没接上，没能好好回答。您别急，可以再问我一遍。"));
            }
        }, "GouXiongDeepSeek").start();
    }

    private boolean looksLikeFindObjectQuestion(String question) {
        String q = question == null ? "" : question;
        return q.contains("在哪") || q.contains("哪里") || q.contains("哪儿") || q.contains("找不到")
                || q.contains("丢") || q.contains("放哪") || q.contains("不见");
    }

    private boolean looksLikeWellnessTipQuestion(String question) {
        String q = question == null ? "" : question;
        return containsAny(q, "妙招", "养生", "食补", "食疗", "饮食建议", "今天吃什么", "怎么吃",
                "喝什么汤", "睡前怎么做", "降压吃", "血糖怎么吃", "清淡一点");
    }

    private boolean looksLikeBedtimeStoryRequest(String question) {
        String q = question == null ? "" : question.trim();
        return containsAny(q, "讲故事", "说故事", "小故事", "睡前故事", "讲个故事", "说个故事",
                "哄我睡", "陪我睡", "睡不着讲点", "tell_bedtime_story", "bedtime_story");
    }

    private boolean looksLikeNewsRequest(String question) {
        String q = question == null ? "" : question.trim();
        return containsAny(q, "说新闻", "讲新闻", "听新闻", "新闻简报", "今天新闻", "有什么新闻",
                "读新闻", "播新闻", "read_news", "news_brief");
    }

    private boolean looksLikeSleepSoundStartRequest(String question) {
        String q = question == null ? "" : question.trim();
        return containsAny(q, "放音乐", "放点音乐", "轻音乐", "助眠音", "白噪声", "雨声",
                "轻雨声", "催眠声", "放点声音", "睡眠音乐", "play_rain_sound", "play_sleep_sound");
    }

    private boolean looksLikeSleepSoundStopRequest(String question) {
        String q = question == null ? "" : question.trim();
        return containsAny(q, "停音乐", "关音乐", "别放音乐", "停助眠音", "关助眠音", "停雨声",
                "关雨声", "别放了", "安静点", "声音关掉", "stop_rain_sound", "stop_sleep_sound");
    }

    private VisionIntent analyzeVisionIntent(String text) {
        String q = text == null ? "" : text.trim();
        if (q.length() == 0) return VisionIntent.none();
        boolean explicitLook = containsAny(q, "帮我看看", "帮我看", "你看看", "你看下", "看一下", "看一眼",
                "看看", "瞧瞧", "拍一下", "识别", "读读", "念念", "帮我读", "帮我念", "分析一下");
        boolean findAsk = containsAny(q, "找钥匙", "找手机", "找眼镜", "找钱包", "找药", "找遥控器", "找医保卡",
                "找证件", "丢哪", "放哪", "不见了", "找不到");
        boolean identifyAsk = containsAny(q, "叫啥", "叫什么", "是什么", "啥东西", "啥花", "什么花", "什么植物",
                "认一下", "识别一下", "看得出来吗", "帮我认");
        boolean face = containsAny(q, "脸", "脸色", "气色", "眼睛", "眼角", "皱纹", "法令纹", "舌头", "皮肤", "伤口", "表情", "精神", "脸上");
        boolean plant = containsAny(q, "花", "植物", "盆栽", "叶子", "叶片", "草", "树", "多肉", "兰花", "玫瑰", "菊花", "绿植");
        if (!explicitLook && !findAsk && !(identifyAsk && plant) && !(face && containsAny(q, "眼角", "皱纹", "加深", "明显", "变化"))) {
            return VisionIntent.none();
        }

        boolean report = containsAny(q, "体检", "报告", "化验", "检查单", "检验单", "血常规", "尿常规", "ct", "CT",
                "B超", "b超", "心电图", "血压单", "血糖", "血脂", "肝功", "肾功");
        boolean medicine = containsAny(q, "药瓶", "药盒", "药品", "药片", "药名", "吃药", "保健品", "说明书",
                "剂量", "用法", "禁忌", "有效期");
        boolean finance = containsAny(q, "投资", "理财", "股票", "基金", "保险", "养老项目", "分红", "收益",
                "贷款", "借钱", "转账", "扫码付款", "验证码", "签字");
        boolean read = containsAny(q, "读", "念", "文字", "小字", "这封信", "信上", "合同", "纸上", "通知",
                "票据", "短信", "公告", "说明");
        boolean find = findAsk || containsAny(q, "钥匙", "手机", "眼镜", "钱包", "遥控器", "医保卡", "证件",
                "拐杖", "水杯", "药盒在哪", "药瓶在哪");
        boolean outside = containsAny(q, "外面", "门口", "窗外", "厨房", "客厅", "床边", "桌上", "地上");
        boolean faceDetail = face && containsAny(q, "脸上", "眼角", "皱纹", "法令纹", "伤口", "红", "痣", "肿", "斑", "疼", "疹", "破", "加深", "变化", "明显");

        String task;
        if (report) task = "report";
        else if (finance) task = "finance";
        else if (medicine) task = "medicine_text";
        else if (read) task = "read";
        else if (find) task = "find";
        else if (faceDetail) task = "face_detail";
        else if (face) task = "face";
        else if (plant) task = "plant";
        else if (outside) task = "outside";
        else task = "object";

        boolean preferFront = "face".equals(task) || "face_detail".equals(task);
        boolean detailed = faceDetail || (!"face".equals(task) && !"quick_glance".equals(task));
        String reason = "用户刚才说：" + q + "。请自动判断要看的目标和摄像头方向：脸部/眼角用前摄，药瓶、小字、花草、手机位置和周围物品用后摄。回答要像亲近的孩子，不要机械列清单。";
        return new VisionIntent(true, explicitLook, task, preferFront, detailed, reason);
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String needle : needles) {
            if (needle != null && needle.length() > 0 && text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private void handleRealtimeVisionIntent(VisionIntent intent) {
        if (intent == null || !intent.matched) return;
        pendingVisionTask = intent.task;
        activeVisionTask = intent.task;
        activeVisionPreferFront = intent.preferFront;
        activeVisionDetailed = intent.detailed;
        activeVisionReason = intent.reason;
        if (!hasPermission(Manifest.permission.CAMERA)) {
            updateVoiceStatus("需要允许摄像头，小助手才能帮您看。");
            updateLiveStageStatus("需要摄像头权限", "comforting");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_GLANCE);
            return;
        }
        if (!prefs.deepSeekKeyConfigured()) {
            showCompanionVoiceReply("我还不能看",
                    "奶奶，我听懂了，您是想让我帮您看。摄像头已经准备好了，但我现在还没学会仔细看图。到“我的”里打开联网陪伴后，您再说“帮我看看这个”，我就能帮您看。");
            return;
        }
        String waiting = intent.detailed ? visionReasonForTask(intent.task) : "我看看您，您别急。";
        showCompanionVoiceWaiting(waiting);
        updateLiveStageStatus(waiting, avatarMoodForVisionTask(intent.task));
        startVisionFrameCapture(intent.task, intent.preferFront, intent.detailed, true, intent.reason);
    }

    private String deepSeekSystemPrompt() {
        return "你是睡了么 App 的小助手，角色是" + prefs.companionRole()
                + "。" + CompanionAssistant.companionshipPrinciples(prefs.assistantName(), prefs.assistantIdentity(), prefs.ownerAddress())
                + "。你面向中老年用户，回答要短、清楚、温柔，适合语音朗读。"
                + "你平时用自然的人类陪伴口吻说话，不要反复强调自己是程序；按主人给你的名字和身份自称。"
                + "你要智能判断情绪和场景：孤单时共情陪伴，拖延喝水吃药时可以温柔撒娇或着急，睡眠异常时要坚定叫醒，开心时跟着开心，难过时先陪着。"
                + "你可以有情绪，但不能辱骂、吓唬、羞辱或操控用户；所有情绪都服务于关心和安全。"
                + "你要像懂事的孩子或亲近家人一样，先理解奶奶、爷爷、爸爸妈妈为什么让你看这个东西，再给贴心建议。"
                + "如果用户直接问到联网、隐私或你是不是程序，要诚实简短说明，不要欺骗。"
                + "看图时先帮主人把字念清、把东西找着、把花草认一认、把心情哄好。"
                + "遇到药品、保健品就先读清楚包装和说明；遇到投资、理财、转账、贷款、保险、养老项目等明显可能吃亏的内容，再温和提醒主人先别急着付款或给验证码，问问家人。"
                + "你可以结合主人档案、今天状态、睡眠摘要和守护完整性，给睡眠复盘、生活建议、养生小妙招、食补食疗做法、情绪陪伴和医生沟通准备。"
                + "讲养生和食疗时，只说安全、常见、容易执行的日常食养办法，不承诺疗效；先主动汇报一个方向，主人感兴趣时再一步一步教怎么做。"
                + "睡眠记录只能称为疑似记录或提醒事件。"
                + "异常多晚重复、憋醒样声音、喘息呛咳或明显不适时，建议用户带记录咨询医生。";
    }

    private String deepSeekUserPrompt(String question) {
        return "用户问题：" + question
                + "\n\n小助手身份：\n" + prefs.assistantPersonaSummary()
                + "\n\n你称呼用户为：" + prefs.ownerAddress()
                + "\n\n今天状态：\n" + prefs.assistantCheckInSummary()
                + "\n\n睡前自检：\n" + preSleepSelfCheckSummary()
                + "\n\n主人档案：\n" + prefs.ownerProfileSummary()
                + "\n\n小助手自动位置记忆：\n" + db.objectMemorySummary()
                + "\n\n小助手手动位置备注：\n" + prefs.visualMemorySummary()
                + "\n\n吃药看见记录：\n" + prefs.medicationVisionSummary()
                + "\n\n睡眠摘要：\n" + db.localReportText()
                + "\n\n守护完整性：" + guardIntegrityScore() + "分"
                + "\n\n请只给非诊断生活建议，避免吓人，尽量三段以内。";
    }

    private void addAssistantReplyButton(String label, String reply) {
        Button button = Theme.button(this, label, CompanionAssistant.roleColor(prefs.companionRole()));
        button.setTextSize(20);
        button.setOnClickListener(v -> {
            if (realtimeVoiceEnabled) showCompanionVoiceReply(label, reply);
            else showCompanionReply(label, reply);
        });
        content.addView(button, matchWrap());
        addSpace(content, 10);
    }

    private void addAiQuestionButton(String label, String question) {
        Button button = Theme.button(this, label, Theme.GREEN);
        button.setTextSize(20);
        button.setOnClickListener(v -> {
            if (realtimeVoiceEnabled) handleRealtimeVoiceText(question);
            else askDeepSeek(question);
        });
        content.addView(button, matchWrap());
        addSpace(content, 10);
    }

    private void showCompanionReply(String topic, String reply) {
        content.removeAllViews();
        content.addView(Theme.text(this, topic, 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addAssistantHero(prefs.companionRole(), reply, false);
        addSettingButton("读给我听", () -> speakAssistantText(reply));
        addSettingButton("继续聊", this::showCompanionChat);
        addSettingButton("返回首页", () -> showShell("guard"));
    }

    private void showCompanionWaiting(String topic, String reply) {
        showCompanionReply(topic, reply);
        content.postDelayed(() -> speakAssistantText(reply), 250);
    }

    private void showMedicationDialog() {
        activeScreen = "medication";
        content.removeAllViews();
        addSimplePageHeader("吃药提醒", "+", this::showMedicationDialog);

        CheckBox enabled = new CheckBox(this);
        enabled.setText("吃药提醒总开关");
        enabled.setTextSize(20);
        enabled.setTextColor(Theme.TEXT);
        enabled.setTypeface(Typeface.DEFAULT_BOLD);
        enabled.setChecked(prefs.medicationEnabled());

        EditText name = new EditText(this);
        name.setText(prefs.medicationEnabled() ? prefs.medicationName() : "");
        name.setHint("每天吃的药，例如：降压药、维生素");
        stylePageInput(name);

        EditText time = new EditText(this);
        time.setText(formatMedicationTime());
        time.setHint("每天提醒时间，例如 07:30");
        time.setSingleLine(true);
        stylePageInput(time);

        EditText repeat = new EditText(this);
        repeat.setText(String.valueOf(prefs.medicationRepeatMinutes()));
        repeat.setHint("未确认后几分钟再提醒");
        repeat.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        stylePageInput(repeat);

        addMedicationSummaryCard();
        addMedicationTodayConfirmCard();
        addMedicationFormCard(enabled, name, time, repeat);
        addPrimaryActionButton("保存吃药提醒", Theme.BLUE, () -> {
            int minutes = 30;
            try {
                minutes = Math.max(5, Math.min(180, Integer.parseInt(repeat.getText().toString().trim())));
            } catch (Exception ignored) {
            }
            int[] hm = parseHourMinute(time.getText().toString(), prefs.medicationHour(), prefs.medicationMinute());
            String medicationName = enabled.isChecked() ? name.getText().toString() : "";
            prefs.setMedication(medicationName, hm[0], hm[1], minutes);
            CareReminderScheduler.ensureCareReminders(this);
            Toast.makeText(this, prefs.medicationEnabled() ? "已保存吃药提醒" : "已关闭吃药提醒", Toast.LENGTH_SHORT).show();
            showHome();
        });
        addSettingButton("关闭提醒", () -> {
            prefs.setMedication("", prefs.medicationHour(), prefs.medicationMinute(), prefs.medicationRepeatMinutes());
            CareReminderScheduler.ensureCareReminders(this);
            Toast.makeText(this, "已关闭吃药提醒", Toast.LENGTH_SHORT).show();
            showHome();
        });
        addSettingButton("返回首页", () -> showShell("guard"));
    }

    private void showHealthHabitsDialog() {
        activeScreen = "health_habits";
        content.removeAllViews();
        addSimplePageHeader("健康习惯", "", null);

        CheckBox water = new CheckBox(this);
        water.setText("喝水提醒");
        water.setTextSize(20);
        water.setChecked(prefs.hydrationReminderEnabled());

        EditText waterInterval = new EditText(this);
        waterInterval.setText(String.valueOf(prefs.hydrationIntervalMinutes()));
        waterInterval.setHint("喝水间隔分钟，例如 60");
        waterInterval.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        stylePageInput(waterInterval);

        EditText waterStart = new EditText(this);
        waterStart.setText(twoDigits(prefs.hydrationStartHour()) + ":00");
        waterStart.setHint("开始时间，例如 07:00");
        waterStart.setSingleLine(true);
        stylePageInput(waterStart);

        EditText waterEnd = new EditText(this);
        waterEnd.setText(twoDigits(prefs.hydrationEndHour()) + ":00");
        waterEnd.setHint("结束时间，例如 22:00");
        waterEnd.setSingleLine(true);
        stylePageInput(waterEnd);

        CheckBox sedentary = new CheckBox(this);
        sedentary.setText("久坐提醒");
        sedentary.setTextSize(20);
        sedentary.setChecked(prefs.sedentaryReminderEnabled());

        EditText sedentaryInterval = new EditText(this);
        sedentaryInterval.setText(String.valueOf(prefs.sedentaryIntervalMinutes()));
        sedentaryInterval.setHint("久坐间隔分钟，例如 60");
        sedentaryInterval.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        stylePageInput(sedentaryInterval);

        EditText sedentaryStart = new EditText(this);
        sedentaryStart.setText(twoDigits(prefs.sedentaryStartHour()) + ":00");
        sedentaryStart.setHint("开始时间，例如 08:00");
        sedentaryStart.setSingleLine(true);
        stylePageInput(sedentaryStart);

        EditText sedentaryEnd = new EditText(this);
        sedentaryEnd.setText(twoDigits(prefs.sedentaryEndHour()) + ":00");
        sedentaryEnd.setHint("结束时间，例如 22:00");
        sedentaryEnd.setSingleLine(true);
        stylePageInput(sedentaryEnd);

        addHealthHabitSummaryCard();
        addHabitCard("喝水提醒", "💧", Theme.BLUE, water, "时间间隔（分钟）", waterInterval, waterStart, waterEnd,
                "建议 45-90 分钟；睡眠守护时不会打扰。");
        addHabitCard("久坐提醒", "▥", Theme.GREEN, sedentary, "久坐间隔（分钟）", sedentaryInterval, sedentaryStart, sedentaryEnd,
                "适合白天提醒起身活动；夜间自动安静。");
        addPrimaryActionButton("保存健康习惯", Theme.GREEN, () -> {
            int waterMinutes = parseIntInRange(waterInterval.getText().toString(), prefs.hydrationIntervalMinutes(), 30, 180);
            int sitMinutes = parseIntInRange(sedentaryInterval.getText().toString(), prefs.sedentaryIntervalMinutes(), 30, 240);
            int waterStartHour = parseHourOnly(waterStart.getText().toString(), prefs.hydrationStartHour());
            int waterEndHour = parseHourOnly(waterEnd.getText().toString(), prefs.hydrationEndHour());
            int sitStartHour = parseHourOnly(sedentaryStart.getText().toString(), prefs.sedentaryStartHour());
            int sitEndHour = parseHourOnly(sedentaryEnd.getText().toString(), prefs.sedentaryEndHour());
            prefs.setHydrationReminder(water.isChecked(), waterMinutes, waterStartHour, waterEndHour);
            prefs.setSedentaryReminder(sedentary.isChecked(), sitMinutes, sitStartHour, sitEndHour);
            CareReminderScheduler.ensureCareReminders(this);
            Toast.makeText(this, "已保存健康习惯提醒", Toast.LENGTH_SHORT).show();
            showHome();
        });
        addSettingButton("返回首页", () -> showShell("guard"));
    }

    private void addSimplePageHeader(String title, String rightText, Runnable rightAction) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Theme.text(this, "‹", 34, Theme.TEXT, Typeface.BOLD);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showShell("guard"));
        header.addView(back, new LinearLayout.LayoutParams(Theme.dp(this, 40), -2));
        TextView titleView = Theme.text(this, title, 24, Theme.TEXT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));
        TextView right = Theme.text(this, rightText == null ? "" : rightText, 24, Theme.BLUE, Typeface.BOLD);
        right.setGravity(Gravity.CENTER);
        if (rightAction != null) {
            right.setOnClickListener(v -> rightAction.run());
        }
        header.addView(right, new LinearLayout.LayoutParams(Theme.dp(this, 40), -2));
        content.addView(header, matchWrap());
        addSpace(content, 10);
    }

    private void stylePageInput(EditText input) {
        input.setTextSize(19);
        input.setSingleLine(true);
        input.setPadding(Theme.dp(this, 14), Theme.dp(this, 8), Theme.dp(this, 14), Theme.dp(this, 8));
        input.setBackground(Theme.rounded(Color.WHITE, 16, this));
    }

    private void addMedicationSummaryCard() {
        LinearLayout card = cardContainer();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Theme.tintedCard(this, Theme.ORANGE));
        ImageView icon = designImage("ui_medication_icon_image2", 56, ImageView.ScaleType.FIT_CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(Theme.dp(this, 62), Theme.dp(this, 62)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Theme.text(this, "今日提醒", 18, Theme.TEXT, Typeface.BOLD), matchWrap());
        String state = prefs.medicationEnabled()
                ? formatMedicationTime() + "  " + prefs.medicationName() + (prefs.medicationConfirmedToday() ? " · 已确认" : " · 未服用")
                : "填写药名和时间后自动提醒";
        words.addView(Theme.text(this, state, 14, Theme.MUTED, Typeface.BOLD), matchWrap());
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, -2, 1);
        wordsLp.setMargins(Theme.dp(this, 12), 0, 0, 0);
        card.addView(words, wordsLp);
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addMedicationTodayConfirmCard() {
        if (!prefs.medicationEnabled()) {
            return;
        }
        LinearLayout card = cardContainer();
        card.setPadding(Theme.dp(this, 14), Theme.dp(this, 12), Theme.dp(this, 14), Theme.dp(this, 14));
        card.setBackground(Theme.tintedCard(this, prefs.medicationConfirmedToday() ? Theme.GREEN : Theme.ORANGE));
        card.addView(Theme.text(this, "今日服药确认", 18, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(card, 6);
        String line = prefs.medicationConfirmedToday()
                ? "今天已确认服用：" + prefs.medicationName()
                : "今天 " + formatMedicationTime() + " 的 " + prefs.medicationName() + " 还未确认。";
        card.addView(Theme.text(this, line, 14, Theme.MUTED, Typeface.BOLD), matchWrap());
        addSpace(card, 10);
        Button confirm = Theme.button(this, prefs.medicationConfirmedToday() ? "重新确认已吃药" : "确认已吃药", prefs.medicationConfirmedToday() ? Theme.GREEN : Theme.ORANGE);
        confirm.setTextSize(19);
        confirm.setMinHeight(Theme.dp(this, 54));
        confirm.setOnClickListener(v -> {
            prefs.confirmMedicationNow();
            CareReminderScheduler.ensureCareReminders(this);
            Toast.makeText(this, "已记录今天吃药", Toast.LENGTH_SHORT).show();
            showMedicationDialog();
        });
        card.addView(confirm, matchWrap());
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addMedicationFormCard(CheckBox enabled, EditText name, EditText time, EditText repeat) {
        LinearLayout card = cardContainer();
        card.setPadding(Theme.dp(this, 14), Theme.dp(this, 12), Theme.dp(this, 14), Theme.dp(this, 14));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = designImage("ui_medication_icon_image2", 44, ImageView.ScaleType.FIT_CENTER);
        titleRow.addView(icon, new LinearLayout.LayoutParams(Theme.dp(this, 48), Theme.dp(this, 48)));
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(Theme.text(this, prefs.medicationEnabled() ? prefs.medicationName() : "药品提醒", 20, Theme.TEXT, Typeface.BOLD), matchWrap());
        titleBox.addView(Theme.text(this, "每天 " + formatMedicationTime(), 14, Theme.MUTED, Typeface.BOLD), matchWrap());
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.setMargins(Theme.dp(this, 10), 0, Theme.dp(this, 8), 0);
        titleRow.addView(titleBox, titleLp);
        enabled.setText("");
        enabled.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        titleRow.addView(enabled, new LinearLayout.LayoutParams(Theme.dp(this, 54), -2));
        card.addView(titleRow, matchWrap());
        addSpace(card, 12);

        card.addView(Theme.text(this, "每天吃的药", 16, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(card, 6);
        card.addView(name, matchWrap());
        addSpace(card, 10);

        LinearLayout scheduleRow = new LinearLayout(this);
        scheduleRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout timeBox = formColumn("提醒时间", time);
        LinearLayout repeatBox = formColumn("再次提醒", repeat);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(0, -2, 1);
        timeLp.setMargins(0, 0, Theme.dp(this, 6), 0);
        scheduleRow.addView(timeBox, timeLp);
        LinearLayout.LayoutParams repeatLp = new LinearLayout.LayoutParams(0, -2, 1);
        repeatLp.setMargins(Theme.dp(this, 6), 0, 0, 0);
        scheduleRow.addView(repeatBox, repeatLp);
        card.addView(scheduleRow, matchWrap());

        addSpace(card, 8);
        card.addView(Theme.text(this, "只提醒您自己设定的事项。留空保存会关闭提醒。", 13, Theme.MUTED, Typeface.NORMAL), matchWrap());
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addHealthHabitSummaryCard() {
        LinearLayout card = cardContainer();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Theme.tintedCard(this, Theme.GREEN));
        ImageView icon = designImage("ui_health_habits_icon_image2", 56, ImageView.ScaleType.FIT_CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(Theme.dp(this, 62), Theme.dp(this, 62)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Theme.text(this, "白天健康提醒", 18, Theme.TEXT, Typeface.BOLD), matchWrap());
        words.addView(Theme.text(this, healthHabitHomeLine(), 14, Theme.MUTED, Typeface.BOLD), matchWrap());
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, -2, 1);
        wordsLp.setMargins(Theme.dp(this, 12), 0, 0, 0);
        card.addView(words, wordsLp);
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private void addHabitCard(String title, String iconText, int color, CheckBox toggle, String label,
                              EditText input, EditText start, EditText end, String note) {
        LinearLayout card = cardContainer();
        card.setPadding(Theme.dp(this, 14), Theme.dp(this, 12), Theme.dp(this, 14), Theme.dp(this, 14));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = Theme.text(this, iconText, 26, color, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Theme.rounded(Theme.mix(color, Color.WHITE, 0.84f), 16, this));
        titleRow.addView(icon, new LinearLayout.LayoutParams(Theme.dp(this, 42), Theme.dp(this, 42)));
        TextView titleView = Theme.text(this, title, 20, Theme.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.setMargins(Theme.dp(this, 10), 0, Theme.dp(this, 8), 0);
        titleRow.addView(titleView, titleLp);
        toggle.setText("");
        toggle.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        titleRow.addView(toggle, new LinearLayout.LayoutParams(Theme.dp(this, 54), -2));
        card.addView(titleRow, matchWrap());
        addSpace(card, 8);
        card.addView(Theme.text(this, label, 15, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(card, 6);
        card.addView(input, matchWrap());
        addSpace(card, 8);
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout startBox = formColumn("开始时间", start);
        LinearLayout endBox = formColumn("结束时间", end);
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(0, -2, 1);
        startLp.setMargins(0, 0, Theme.dp(this, 6), 0);
        timeRow.addView(startBox, startLp);
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(0, -2, 1);
        endLp.setMargins(Theme.dp(this, 6), 0, 0, 0);
        timeRow.addView(endBox, endLp);
        card.addView(timeRow, matchWrap());
        addSpace(card, 4);
        card.addView(Theme.text(this, note, 13, Theme.MUTED, Typeface.NORMAL), matchWrap());
        content.addView(card, matchWrap());
        addSpace(content, 10);
    }

    private LinearLayout formColumn(String label, EditText input) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Theme.text(this, label, 14, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(box, 4);
        box.addView(input, matchWrap());
        return box;
    }

    private String formatMedicationTime() {
        return twoDigits(prefs.medicationHour()) + ":" + twoDigits(prefs.medicationMinute());
    }

    private String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private int[] parseHourMinute(String value, int fallbackHour, int fallbackMinute) {
        String clean = value == null ? "" : value.trim();
        try {
            String[] parts = clean.split(":");
            if (parts.length >= 2) {
                int h = Math.max(0, Math.min(23, Integer.parseInt(parts[0].trim())));
                int m = Math.max(0, Math.min(59, Integer.parseInt(parts[1].trim())));
                return new int[]{h, m};
            }
        } catch (Exception ignored) {
        }
        return new int[]{fallbackHour, fallbackMinute};
    }

    private int parseHourOnly(String value, int fallbackHour) {
        return parseHourMinute(value, fallbackHour, 0)[0];
    }

    private int parseIntInRange(String value, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void scheduleMedicationReminder(int minutes) {
        CareReminderScheduler.scheduleMedicationLater(this, minutes);
    }

    private String preSleepStatusText() {
        StringBuilder b = new StringBuilder();
        b.append(hasPermission(Manifest.permission.RECORD_AUDIO) ? "✓ 麦克风可用\n" : "! 麦克风未授权\n");
        if (Build.VERSION.SDK_INT >= 33) {
            b.append(hasPermission(Manifest.permission.POST_NOTIFICATIONS) ? "✓ 通知可用\n" : "! 通知未授权\n");
        } else {
            b.append("✓ 通知可用\n");
        }
        b.append(prefs.isMonitoring() ? heartbeatText() + "\n" : "✓ 尚未开始守护，可先测试\n");
        b.append(batteryOptimizationText()).append("\n");
        b.append("✓ 守护通知静音，不会发运行提示音\n");
        b.append(prefs.saveAudioClips() ? "✓ 异常现场录音片段已开启\n" : "! 异常现场录音片段已关闭\n");
        b.append(AudioOutputStatus.inspect(this).preSleepLine()).append("\n");
        b.append(prefs.externalDeviceEnabled() ? "! 已填写手动设备摘要，未自动接入\n" : "! 外部设备未自动接入\n");
        b.append("唤醒声音：").append(prefs.alarmLabel()).append("\n");
        b.append(prefs.emergencyEnabled() ? "✓ 紧急联系人已设置" : "! 紧急联系人未设置");
        return b.toString();
    }

    private void testGentleReminder() {
        vibrateOnce(500);
        db.insertEvent("睡前自检轻提醒", "medium", 1.0, "auto_cancel", "用户手动测试轻提醒震动",
                "",
                "现场录音：睡前自检为手动测试，未采集真实异常录音",
                "手机加速度计：手动测试，不代表夜间动作异常",
                db.nearestDeviceEvidence(System.currentTimeMillis(), prefs.externalDeviceEvidence()),
                "manual_test");
        Toast.makeText(this, "已测试轻提醒，并写入本地记录", Toast.LENGTH_SHORT).show();
    }

    private Intent alarmDrillIntent(String reason) {
        return new Intent(this, AlarmActivity.class)
                .putExtra("reason", reason)
                .putExtra("drill_mode", true);
    }

    private void showDetectionTest() {
        content.removeAllViews();
        content.addView(Theme.text(this, "开发者演练测试", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addCard("测试说明", "这里用于验证记录、轻提醒、强唤醒和反馈流程。\n它是模拟事件，不代表真实睡眠识别能力，也不会计入正式睡眠守护报告。", Theme.ORANGE);
        addSettingButton("模拟鼾声：只记录", () -> simulateEvent("模拟鼾声", "low", "record", "测试音频集：鼾声样本模拟，只记录不唤醒"));
        addSettingButton("模拟咳嗽/轻喘：轻提醒并自动取消", () -> {
            simulateEvent("模拟咳嗽/轻喘", "medium", "auto_cancel", "测试音频集：中风险样本模拟，轻提醒后自动取消");
            vibrateOnce(500);
        });
        addSettingButton("模拟尖叫/强动作：强唤醒", () -> {
            simulateEvent("模拟尖叫/强动作", "high", "alarm", "测试音频集：高风险样本模拟，触发强唤醒");
            startActivity(alarmDrillIntent("检测测试：模拟尖叫/强动作"));
        });
        addSettingButton("填入模拟手表摘要", () -> {
            prefs.setExternalDevice("模拟手表", "最低血氧 92%，平均心率 68，夜间呼吸率 14；供测试展示，不代表真实设备数据");
            db.insertDeviceReading(System.currentTimeMillis(), "模拟手表", 68, 92, 14, "检测测试生成的结构化读数");
            Toast.makeText(this, "已填入模拟手表摘要", Toast.LENGTH_SHORT).show();
            showDetectionTest();
        });
        addSettingButton("查看全部记录（含演练）", this::showRecords);
        addSettingButton("返回首页", () -> showShell("guard"));
        addCard("真实测试建议", "模拟事件会生成一段测试 WAV，方便验收播放按钮。\n真机验收时仍要用另一台设备播放鼾声、咳嗽、喘息、尖叫和环境噪声样本，观察是否触发相应记录。", Theme.BLUE);
    }

    private void simulateEvent(String type, String risk, String action, String basis) {
        double confidence = "high".equals(risk) ? 0.86 : ("medium".equals(risk) ? 0.68 : 0.55);
        long now = System.currentTimeMillis();
        String audioPath = createDemoEvidenceClip(type, risk);
        String audioSummary = audioPath.length() > 0
                ? "现场录音：模拟测试 WAV，可用于验收播放流程；不代表真实睡眠现场"
                : "现场录音：模拟事件未生成录音";
        String motionSummary = "手机加速度计：模拟动作评分 " + demoMotionScore(risk) + "；用于验收证据展示";
        db.insertEvent(type, risk, confidence, action, basis,
                audioPath,
                audioSummary,
                motionSummary,
                db.nearestDeviceEvidence(now, prefs.externalDeviceEvidence()),
                "simulation");
        Toast.makeText(this, "已写入：" + type, Toast.LENGTH_SHORT).show();
    }

    private void vibrateOnce(long millis) {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(millis);
        }
    }

    private int demoMotionScore(String risk) {
        if ("high".equals(risk)) return 28;
        if ("medium".equals(risk)) return 16;
        return 8;
    }

    private String createDemoEvidenceClip(String type, String risk) {
        if (!prefs.saveAudioClips()) {
            return "";
        }
        File dir = new File(getFilesDir(), "event_audio");
        if (!dir.exists() && !dir.mkdirs()) {
            return "";
        }
        File file = new File(dir, "demo-event-" + System.currentTimeMillis() + ".wav");
        short[] samples = demoEvidenceSamples(type, risk);
        try {
            WavFileWriter.writePcm16Mono(file, samples, 8000);
            return file.getAbsolutePath();
        } catch (IOException ex) {
            return "";
        }
    }

    private short[] demoEvidenceSamples(String type, String risk) {
        int rate = 8000;
        int seconds = "high".equals(risk) ? 4 : 3;
        short[] samples = new short[rate * seconds];
        double baseHz = type.contains("鼾") ? 90.0 : (type.contains("咳") || type.contains("喘") ? 180.0 : 420.0);
        int amplitude = "high".equals(risk) ? 11000 : ("medium".equals(risk) ? 7500 : 4200);
        for (int i = 0; i < samples.length; i++) {
            double t = i / (double) rate;
            double envelope = 0.45 + 0.55 * Math.abs(Math.sin(2.0 * Math.PI * 0.8 * t));
            if (type.contains("咳") || type.contains("喘")) {
                envelope = (i % rate < rate / 5) ? 1.0 : 0.2;
            }
            double wave = Math.sin(2.0 * Math.PI * baseHz * t) + 0.35 * Math.sin(2.0 * Math.PI * baseHz * 2.1 * t);
            samples[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, (int) (wave * amplitude * envelope)));
        }
        return samples;
    }

    private String batteryOptimizationText() {
        return batteryOptimizationText(batteryOptimizationOk());
    }

    private String batteryOptimizationText(boolean batteryOkSnapshot) {
        if (Build.VERSION.SDK_INT < 23) {
            return "✓ 电池优化无需处理";
        }
        if (batteryOkSnapshot) {
            return "✓ 已允许不受电池优化限制";
        }
        return "! 建议关闭电池优化，避免夜里被系统停止";
    }

    private boolean batteryOptimizationOk() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT < 23) {
            Toast.makeText(this, "当前系统无需设置电池优化", Toast.LENGTH_SHORT).show();
            return;
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "已经允许不受电池优化限制", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            pendingRefreshAfterBatterySettings = true;
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ex) {
            pendingRefreshAfterBatterySettings = true;
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(intent);
        }
    }

    private void showSoundSettings() {
        content.removeAllViews();
        content.addView(Theme.text(this, "唤醒声音", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addCard("当前声音", prefs.alarmLabel() + "\n强唤醒会优先播放它；失败时自动回退系统闹钟。", Theme.ORANGE);

        if (voiceRecorder == null) {
            addSettingButton("录一段亲人声音", this::startVoiceRecording);
        } else {
            addSettingButton("停止并保存录音", this::stopVoiceRecording);
        }
        addSettingButton("选择手机里的歌曲", this::pickLocalSong);
        addSettingButton("试听当前唤醒声音", this::previewAlarmSound);
        addSettingButton("恢复系统闹钟", () -> {
            stopPreview();
            prefs.useSystemAlarm();
            Toast.makeText(this, "已恢复系统闹钟", Toast.LENGTH_SHORT).show();
            showSoundSettings();
        });
        addSettingButton("返回设置", this::showSettings);

        addCard("建议录音文案", "醒一下，确认你没事。\n慢慢呼吸，我在这里。\n听到了就说“我没事”。", Theme.GREEN);
    }

    private void startVoiceRecording() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 77);
            Toast.makeText(this, "请先允许麦克风，再录亲人声音", Toast.LENGTH_LONG).show();
            return;
        }
        stopPreview();
        try {
            voiceFile = new File(getFilesDir(), "family_wakeup_voice.3gp");
            voiceRecorder = new MediaRecorder();
            voiceRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            voiceRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            voiceRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            voiceRecorder.setMaxDuration(15000);
            voiceRecorder.setOutputFile(voiceFile.getAbsolutePath());
            voiceRecorder.prepare();
            voiceRecorder.start();
            Toast.makeText(this, "正在录音，建议 3-15 秒", Toast.LENGTH_LONG).show();
            showSoundSettings();
        } catch (Exception ex) {
            voiceRecorder = null;
            Toast.makeText(this, "录音启动失败，请确认麦克风未被占用", Toast.LENGTH_LONG).show();
        }
    }

    private void stopVoiceRecording() {
        if (voiceRecorder == null) return;
        try {
            voiceRecorder.stop();
        } catch (Exception ignored) {
        }
        try {
            voiceRecorder.release();
        } catch (Exception ignored) {
        }
        voiceRecorder = null;
        if (voiceFile != null && voiceFile.exists() && voiceFile.length() > 0) {
            prefs.useVoice(voiceFile.getAbsolutePath());
            Toast.makeText(this, "已保存亲人录音为唤醒声", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "录音太短，未保存", Toast.LENGTH_LONG).show();
        }
        showSoundSettings();
    }

    private void pickLocalSong() {
        stopPreview();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_AUDIO);
    }

    private void previewAlarmSound() {
        stopPreview();
        String source = prefs.alarmSource();
        try {
            if ("voice".equals(source) && prefs.voicePath().length() > 0) {
                previewPlayer = new MediaPlayer();
                previewPlayer.setDataSource(prefs.voicePath());
                previewPlayer.prepare();
                previewPlayer.start();
                Toast.makeText(this, "正在试听亲人录音", Toast.LENGTH_SHORT).show();
                return;
            }
            if ("song".equals(source) && prefs.songUri().length() > 0) {
                previewPlayer = new MediaPlayer();
                previewPlayer.setDataSource(this, Uri.parse(prefs.songUri()));
                previewPlayer.prepare();
                previewPlayer.start();
                Toast.makeText(this, "正在试听本地歌曲", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception ex) {
            Toast.makeText(this, "自定义声音不可用，回退系统闹钟", Toast.LENGTH_LONG).show();
        }

        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        previewRingtone = RingtoneManager.getRingtone(this, uri);
        if (previewRingtone != null) previewRingtone.play();
    }

    private void stopPreview() {
        if (previewPlayer != null) {
            try {
                previewPlayer.stop();
            } catch (Exception ignored) {
            }
            previewPlayer.release();
            previewPlayer = null;
        }
        if (previewRingtone != null && previewRingtone.isPlaying()) {
            previewRingtone.stop();
        }
        previewRingtone = null;
        if (voiceRecorder != null) {
            try {
                voiceRecorder.stop();
            } catch (Exception ignored) {
            }
            try {
                voiceRecorder.release();
            } catch (Exception ignored) {
            }
            voiceRecorder = null;
        }
    }

    private void requestEmergencyPermissions(boolean call, boolean sms) {
        java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
        if (call && !hasPermission(Manifest.permission.CALL_PHONE)) {
            permissions.add(Manifest.permission.CALL_PHONE);
        }
        if (sms && !hasPermission(Manifest.permission.SEND_SMS)) {
            permissions.add(Manifest.permission.SEND_SMS);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), 42);
        }
    }

    private void startMonitoring() {
        java.util.ArrayList<String> missing = sleepGuardMissingItems();
        if (!missing.isEmpty()) {
            pendingStartAfterPermission = false;
            showStartGuardReadinessDialog(missing);
            return;
        }
        beginMonitoringService();
    }

    private void beginMonitoringService() {
        try {
            prefs.recordSleepGuardAudioState(false, 0, 0, 0, "服务启动中");
            prefs.setMonitoring(true);
            Intent intent = new Intent(this, SleepMonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception ex) {
            prefs.setMonitoring(false);
            prefs.recordSleepGuardAudioState(false, 0, 0, 0,
                    "服务启动失败：" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            Toast.makeText(this, "睡眠守护启动失败，请看睡前自检", Toast.LENGTH_LONG).show();
            showHome();
            return;
        }
        Toast.makeText(this, "已开始睡眠守护", Toast.LENGTH_SHORT).show();
        content.postDelayed(() -> {
            if (prefs.isMonitoring() && !prefs.sleepGuardAudioPassed()) {
                Toast.makeText(this, "守护已启动，但真机拾音未证明，请看睡前自检", Toast.LENGTH_LONG).show();
            }
        }, 8000L);
        showHome();
    }

    private void stopMonitoring() {
        prefs.setMonitoring(false);
        stopService(new Intent(this, SleepMonitorService.class));
        CareReminderScheduler.ensureCareReminders(this);
        Toast.makeText(this, "已停止守护", Toast.LENGTH_SHORT).show();
        showHome();
    }

    private void requestEssentialPermissions() {
        java.util.ArrayList<String> permissions = requiredRuntimePermissions();
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), 7);
        }
    }

    private java.util.ArrayList<String> requiredRuntimePermissions() {
        java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!hasPermission(Manifest.permission.CAMERA)) {
            permissions.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasPermission(Manifest.permission.READ_MEDIA_IMAGES)) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (!hasPermission(Manifest.permission.READ_MEDIA_VIDEO)) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (!hasPermission(Manifest.permission.READ_MEDIA_AUDIO)) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else if (Build.VERSION.SDK_INT >= 23 && !hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT <= 28 && !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= 31 && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            permissions.add(Manifest.permission.CALL_PHONE);
        }
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            permissions.add(Manifest.permission.SEND_SMS);
        }
        return permissions;
    }

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_VISION) {
            if (hasPermission(Manifest.permission.CAMERA)) {
                requestCameraForVision(pendingVisionTask);
            } else {
                Toast.makeText(this, "未授权摄像头，小助手暂时不能看一眼", Toast.LENGTH_LONG).show();
                showCompanionVision();
            }
            return;
        }
        if (requestCode == REQUEST_CAMERA_GLANCE) {
            if (hasPermission(Manifest.permission.CAMERA)) {
                String task = activeVisionTask.length() > 0 ? activeVisionTask : pendingVisionTask;
                if (!prefs.deepSeekKeyConfigured()) {
                    showCompanionVoiceReply("我还不能看",
                            "奶奶，摄像头已经允许了，但我现在还没学会仔细看图。到“我的”里打开联网陪伴后，我就能帮您看药瓶、读小字、找手机。");
                    return;
                }
                boolean detailed = activeVisionDetailed;
                boolean preferFront = activeVisionPreferFront || preferFrontForVisionTask(task);
                String reason = activeVisionReason.length() > 0 ? activeVisionReason : visionReasonForTask(task);
                showCompanionVoiceWaiting(detailed ? visionReasonForTask(task) : "我看一眼，您别急。");
                updateLiveStageStatus(detailed ? visionReasonForTask(task) : "我认真看一眼", avatarMoodForVisionTask(task));
                startVisionFrameCapture(task, preferFront, detailed, true, reason);
            } else {
                updateVoiceStatus("未授权摄像头，不能帮您看。");
                showCompanionVoiceReply("我还不能看", "奶奶，摄像头没有允许，我现在还不能帮您看东西。");
            }
            return;
        }
        if (requestCode == REQUEST_CAMERA_AUTO_VISION) {
            if (hasPermission(Manifest.permission.CAMERA)) {
                maybeStartAutoVisionScan();
            } else {
                Toast.makeText(this, "未授权摄像头，小助手暂时不能自动看见", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQUEST_VOICE_CHAT) {
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                startRealtimeVoiceChat();
            } else {
                updateVoiceStatus("未授权麦克风，不能打开实时语音聊天。");
                Toast.makeText(this, "未授权麦克风，小助手暂时不能直接听您说话", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQUEST_MICROPHONE_PROACTIVE) {
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "麦克风已打开，小助手可以听您说话了。", Toast.LENGTH_SHORT).show();
                if (realtimeVoiceEnabled) {
                    startRealtimeVoiceChat();
                }
            } else {
                Toast.makeText(this, "未授权麦克风，小助手听不见您，也不能做睡眠声音守护。", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQUEST_REQUIRED_PERMISSIONS) {
            java.util.ArrayList<String> missing = requiredRuntimePermissions();
            if (missing.isEmpty()) {
                Toast.makeText(this, "必要权限已打开，小助手可以听、看和读取文件了。", Toast.LENGTH_SHORT).show();
                if (pendingStartLiveAfterRequiredPermissions || realtimeVoiceEnabled) {
                    pendingStartLiveAfterRequiredPermissions = false;
                    startRealtimeVoiceChat();
                    maybeStartAutoVisionScan();
                }
            } else {
                pendingStartLiveAfterRequiredPermissions = false;
                updateVoiceStatus("必要权限没开全，我还不能完整听您说话、看东西或读文件。");
                updateLiveStageStatus("必要权限未完成", "comforting");
                Toast.makeText(this, "必要权限未开全，小助手功能会受限。", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == 7) {
            if (pendingStartAfterPermission) {
                pendingStartAfterPermission = false;
                if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    beginMonitoringService();
                } else {
                    Toast.makeText(this, "未授权麦克风，无法开始睡眠守护", Toast.LENGTH_LONG).show();
                    showPreSleepCheck();
                }
            } else {
                Toast.makeText(this, hasSleepGuardRuntimePermissionMissing() ? "权限还没开全，可以稍后再试" : "睡前权限已打开", Toast.LENGTH_SHORT).show();
                showPreSleepCheck();
            }
            return;
        }
        if (pendingStartAfterPermission) {
            pendingStartAfterPermission = false;
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                beginMonitoringService();
            } else {
                Toast.makeText(this, "未授权麦克风，无法开始睡眠守护", Toast.LENGTH_LONG).show();
                showHome();
            }
        }
    }

    private void shareReport() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "睡了么复盘证据摘要");
        share.putExtra(Intent.EXTRA_TEXT, db.doctorReportText(20));
        startActivity(Intent.createChooser(share, "导出给医生/自己复盘"));
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("删除本地数据？")
                .setMessage("会删除事件和汇总记录。亲人录音和本地歌曲授权仍可在唤醒声音里单独调整。")
                .setPositiveButton("删除", (d, w) -> {
                    db.deleteAll();
                    deleteEvidenceAudioFiles();
                    Toast.makeText(this, "已删除本地记录", Toast.LENGTH_SHORT).show();
                    showSettings();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteEvidenceAudioFiles() {
        deleteFileTree(new File(getFilesDir(), "event_audio"));
    }

    private void deleteFileTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFileTree(child);
                }
            }
        }
        file.delete();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_AUDIO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            prefs.useSong(uri.toString());
            Toast.makeText(this, "已设为本地歌曲唤醒", Toast.LENGTH_SHORT).show();
            showSoundSettings();
        }
        if (requestCode == REQUEST_CAPTURE_SCENE && resultCode == RESULT_OK && data != null && data.getExtras() != null) {
            Bitmap bitmap = decodeVisionBitmap(pendingVisionImageUri);
            if (bitmap == null) {
                Object raw = data.getExtras().get("data");
                if (raw instanceof Bitmap) {
                    bitmap = (Bitmap) raw;
                }
            }
            if (bitmap != null) handleVisionSnapshot(bitmap);
            else {
                Toast.makeText(this, "没有拿到照片，请重拍一次", Toast.LENGTH_LONG).show();
                showCompanionVision();
            }
        } else if (requestCode == REQUEST_CAPTURE_SCENE && resultCode == RESULT_OK) {
            Bitmap bitmap = decodeVisionBitmap(pendingVisionImageUri);
            if (bitmap != null) handleVisionSnapshot(bitmap);
            else {
                Toast.makeText(this, "没有拿到照片，请重拍一次", Toast.LENGTH_LONG).show();
                showCompanionVision();
            }
        }
    }

    @Override
    protected void onPause() {
        if (isFinishing()) {
            stopRealtimeVoiceChat(false);
        }
        closeAutoVisionCamera();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!pendingRefreshAfterBatterySettings) {
            return;
        }
        pendingRefreshAfterBatterySettings = false;
        if ("pre_sleep".equals(activeScreen)) {
            showPreSleepCheck();
        } else if ("pre_sleep_more".equals(activeScreen)) {
            showPreSleepMoreChecks();
        } else if ("settings".equals(activeScreen)) {
            showSettings();
        } else if ("home".equals(activeScreen)) {
            showHome();
        }
    }

    private Bitmap decodeVisionBitmap(Uri uri) {
        if (uri == null) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream first = getContentResolver().openInputStream(uri);
            if (first != null) {
                BitmapFactory.decodeStream(first, null, bounds);
                first.close();
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 1600);
            InputStream second = getContentResolver().openInputStream(uri);
            if (second == null) return null;
            Bitmap bitmap = BitmapFactory.decodeStream(second, null, options);
            second.close();
            return bitmap;
        } catch (Exception ex) {
            return null;
        }
    }

    private int sampleSize(int width, int height, int target) {
        int sample = 1;
        while (width / sample > target || height / sample > target) {
            sample *= 2;
        }
        return sample;
    }

    private void speakAssistantText(String text) {
        speakAssistantTextForRole(text, prefs.companionRole());
    }

    private void speakAssistantTextForRole(String text, String role) {
        speakAssistantTextQueued(text, role, TextToSpeech.QUEUE_FLUSH, "gouxiong-assistant");
    }

    private void speakAssistantTextQueued(String text, String role, int queueMode, String utteranceId) {
        if (text == null || text.trim().length() == 0) {
            return;
        }
        String clean = text.replace("\n", "。");
        String speakRole = CompanionAssistant.normalize(role);
        if (realtimeVoiceEnabled && voiceRecognizer != null) {
            try {
                voiceRecognizer.cancel();
            } catch (Exception ignored) {
            }
            voiceListening = false;
        }
        if (assistantTts == null) {
            assistantTtsProfileApplied = false;
            assistantTtsAppliedRole = "";
            assistantTts = new TextToSpeech(this, status -> {
                assistantTtsReady = status == TextToSpeech.SUCCESS;
                if (assistantTtsReady && assistantTts != null) {
                    applyAssistantTtsProfileIfNeeded(speakRole);
                    installAssistantTtsListener();
                    assistantSpeaking = true;
                    startAvatarSpeaking();
                    assistantTts.speak(clean, queueMode, null, utteranceId);
                } else {
                    showAssistantSpeechFallback(clean);
                    if (realtimeVoiceEnabled) restartRealtimeListeningSoon(500);
                }
            });
            return;
        }
        if (assistantTtsReady) {
            applyAssistantTtsProfileIfNeeded(speakRole);
            installAssistantTtsListener();
            assistantSpeaking = true;
            startAvatarSpeaking();
            assistantTts.speak(clean, queueMode, null, utteranceId);
        } else {
            showAssistantSpeechFallback(clean);
            if (realtimeVoiceEnabled) restartRealtimeListeningSoon(500);
        }
    }

    private void showAssistantSpeechFallback(String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.length() == 0) {
            return;
        }
        updateLiveStageSpeech(prefs.assistantVideoChatMode() ? compactLiveBubbleText(clean) : liveBubbleText(clean));
        updateVoiceStatus("我在这里。语音暂时没放出来，您可以直接说。");
        updateLiveStageStatus("我在这里", "comforting");
    }

    private void applyAssistantTtsProfileIfNeeded(String speakRole) {
        if (assistantTts == null) return;
        String cleanRole = CompanionAssistant.normalize(speakRole);
        if (assistantTtsProfileApplied && cleanRole.equals(assistantTtsAppliedRole)) {
            return;
        }
        XiaozhiVoiceProfile.applyTo(assistantTts, cleanRole);
        assistantTtsAppliedRole = cleanRole;
        assistantTtsProfileApplied = true;
    }

    private void installAssistantTtsListener() {
        if (assistantTts == null || assistantTtsListenerSet) return;
        assistantTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                assistantSpeaking = true;
                runOnUiThread(() -> {
                    updateLiveStageStatus("我慢慢说给您听", "speaking");
                    startAvatarSpeaking();
                });
            }

            @Override
            public void onDone(String utteranceId) {
                runOnUiThread(() -> {
                    if (isLiveStreamingInterimUtterance(utteranceId)) {
                        return;
                    }
                    assistantSpeaking = false;
                    stopAvatarSpeaking();
                    if (realtimeVoiceEnabled) {
                        updateVoiceStatus("我说完了，继续听您说。");
                        updateLiveStageStatus("我说完了，继续听您说", "listening");
                        restartRealtimeListeningSoon(250);
                    } else {
                        settleCompanionAvatarAfterSpeech();
                    }
                });
            }

            @Override
            public void onError(String utteranceId) {
                runOnUiThread(() -> {
                    if (isLiveStreamingInterimUtterance(utteranceId)) {
                        return;
                    }
                    assistantSpeaking = false;
                    stopAvatarSpeaking();
                    if (realtimeVoiceEnabled) {
                        updateLiveStageStatus("声音没放出来，我还在听", "comforting");
                        restartRealtimeListeningSoon(250);
                    } else {
                        updateLiveStageStatus("声音没放出来，我还在这里", "comforting");
                    }
                });
            }
        });
        assistantTtsListenerSet = true;
    }

    private void settleCompanionAvatarAfterSpeech() {
        if (sleepCheckPending) {
            updateLiveStageStatus("我等您回一句", "listening");
        } else if (sleepSoundPlayer != null && sleepSoundPlayer.isRunning()) {
            updateLiveStageStatus("本地轻雨声放着，我轻轻陪您", "comforting");
        } else {
            updateLiveStageStatus("我在这里陪您", "comforting");
        }
    }

    private boolean isLiveStreamingInterimUtterance(String utteranceId) {
        return utteranceId != null
                && utteranceId.startsWith("gouxiong-live-stream-")
                && !utteranceId.endsWith("-final");
    }

    @Override
    protected void onDestroy() {
        stopPreview();
        if (sleepSoundPlayer != null) {
            sleepSoundPlayer.stop();
            sleepSoundPlayer = null;
        }
        stopMicrophoneHonestProbe();
        stopRealtimeVoiceChat(false);
        if (voiceRecognizer != null) {
            voiceRecognizer.destroy();
            voiceRecognizer = null;
        }
        closeAutoVisionCamera();
        if (visionThread != null) {
            visionThread.quitSafely();
            visionThread = null;
            visionHandler = null;
        }
        if (assistantTts != null) {
            assistantTts.stop();
            assistantTts.shutdown();
            assistantTts = null;
        }
        super.onDestroy();
    }

    private String evidenceText(SleepEvent event) {
        StringBuilder b = new StringBuilder();
        b.append("证据完整度：").append(evidenceLabel(event.evidenceLevel)).append("\n");
        appendEvidenceLine(b, event.audioSummary);
        appendEvidenceLine(b, event.motionSummary);
        appendEvidenceLine(b, event.deviceSummary);
        return b.toString().trim();
    }

    private String evidenceLabel(String value) {
        if ("phone_audio_motion".equals(value)) {
            return "手机录音 + 动作指标";
        }
        if ("phone_metrics_only".equals(value)) {
            return "手机声音/动作指标，无录音";
        }
        if ("simulation".equals(value)) {
            return "模拟测试";
        }
        if ("manual_test".equals(value)) {
            return "手动自检";
        }
        return "有限证据";
    }

    private void appendEvidenceLine(StringBuilder b, String value) {
        if (value != null && value.length() > 0) {
            b.append(value).append("\n");
        }
    }

    private boolean hasAudioClip(SleepEvent event) {
        return event.audioPath != null && event.audioPath.length() > 0 && new File(event.audioPath).exists();
    }

    private void playEvidenceAudio(String path) {
        stopPreview();
        if (path == null || path.length() == 0 || !new File(path).exists()) {
            Toast.makeText(this, "现场录音文件不存在", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            previewPlayer = new MediaPlayer();
            previewPlayer.setDataSource(path);
            previewPlayer.prepare();
            previewPlayer.start();
            Toast.makeText(this, "正在播放现场录音", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            stopPreview();
            Toast.makeText(this, "现场录音播放失败", Toast.LENGTH_LONG).show();
        }
    }

    private void showEventReviewDialog(SleepEvent event) {
        new AlertDialog.Builder(this)
                .setTitle("复盘判断")
                .setMessage(reviewText(event))
                .setPositiveButton("标记真实", (d, w) -> {
                    db.updateFeedback(event.id, "true_positive");
                    Toast.makeText(this, "已标记为真实异常", Toast.LENGTH_SHORT).show();
                    showRecords();
                })
                .setNegativeButton("标记误报", (d, w) -> {
                    db.updateFeedback(event.id, "false_positive");
                    Toast.makeText(this, "已标记为误报", Toast.LENGTH_SHORT).show();
                    showRecords();
                })
                .setNeutralButton("不确定", (d, w) -> {
                    db.updateFeedback(event.id, "unsure");
                    Toast.makeText(this, "已标记为不确定", Toast.LENGTH_SHORT).show();
                    showRecords();
                })
                .show();
    }

    private String reviewText(SleepEvent event) {
        StringBuilder b = new StringBuilder();
        b.append("这条记录只能说明 App 发现疑似异常，不能直接证明疾病。\n\n");
        b.append("建议按下面顺序判断：\n");
        b.append("1. 先听现场录音：是否像本人鼾声、喘息、呛咳、惊恐喊声，还是环境噪声/同床人声音。\n");
        b.append("2. 再看动作评分：是否同时有明显翻动、撞击或起身动作。\n");
        b.append("3. 再看手表/血氧仪：同一时间是否有心率、血氧、呼吸率异常。\n");
        b.append("4. 多晚重复、或伴随憋醒/白天困倦，应带记录咨询医生。\n\n");
        b.append(evidenceText(event));
        if (!hasAudioClip(event)) {
            b.append("\n\n这条记录没有可播放现场录音，判断可信度较低。");
        }
        return b.toString();
    }

    private void addEventCard(SleepEvent event, String body) {
        LinearLayout card = cardContainer();
        card.addView(Theme.text(this, event.type, 22, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(card, 4);
        card.addView(Theme.text(this, body, 17, Theme.MUTED, Typeface.NORMAL), matchWrap());
        addSpace(card, 10);
        Button review = Theme.button(this, "复盘判断", Theme.ORANGE);
        review.setTextSize(18);
        review.setMinHeight(Theme.dp(this, 48));
        review.setOnClickListener(v -> showEventReviewDialog(event));
        card.addView(review, matchWrap());
        addSpace(card, 8);
        if (hasAudioClip(event)) {
            Button play = Theme.button(this, "播放现场录音", Theme.GREEN);
            play.setTextSize(18);
            play.setMinHeight(Theme.dp(this, 48));
            play.setOnClickListener(v -> playEvidenceAudio(event.audioPath));
            card.addView(play, matchWrap());
            addSpace(card, 8);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addFeedback(row, event.id, "真实", "true_positive");
        addFeedback(row, event.id, "误报", "false_positive");
        addFeedback(row, event.id, "不确定", "unsure");
        card.addView(row, matchWrap());
        content.addView(card, matchWrap());
        addSpace(content, 12);
    }

    private void addFeedback(LinearLayout row, long id, String label, String value) {
        Button button = Theme.button(this, label, Theme.BLUE);
        button.setTextSize(16);
        button.setMinHeight(Theme.dp(this, 48));
        button.setOnClickListener(v -> {
            db.updateFeedback(id, value);
            Toast.makeText(this, "已记录反馈", Toast.LENGTH_SHORT).show();
            showRecords();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(Theme.dp(this, 3), 0, Theme.dp(this, 3), 0);
        row.addView(button, lp);
    }

    private void addCard(String title, String body, int color) {
        LinearLayout card = cardContainer();
        TextView t = Theme.text(this, title, 22, color, Typeface.BOLD);
        card.addView(t, matchWrap());
        addSpace(card, 8);
        card.addView(Theme.text(this, body, 18, Theme.MUTED, Typeface.NORMAL), matchWrap());
        content.addView(card, matchWrap());
        addSpace(content, 14);
    }

    private void addCheckRow(String title, String state, boolean ok, Runnable action) {
        LinearLayout card = cardContainer();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Theme.dp(this, 14), Theme.dp(this, 8), Theme.dp(this, 14), Theme.dp(this, 8));
        int color = ok ? Theme.GREEN : Theme.ORANGE;
        TextView left = Theme.text(this, (ok ? "✓ " : "! ") + title, 20, ok ? Theme.darken(color, 0.28f) : Theme.RED, Typeface.BOLD);
        left.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        TextView right = Theme.text(this, compactForCard(state, 22), 18, Theme.MUTED, Typeface.BOLD);
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        card.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            card.setOnClickListener(v -> action.run());
            card.setClickable(true);
            card.setFocusable(true);
        }
        content.addView(card, matchWrap());
        addSpace(content, 6);
    }

    private String compactForCard(String text, int max) {
        String clean = text == null ? "" : text.replace("\n", " ").trim();
        if (clean.length() <= max) return clean;
        return clean.substring(0, Math.max(0, max - 1)) + "…";
    }

    private LinearLayout cardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Theme.dp(this, 18), Theme.dp(this, 16), Theme.dp(this, 18), Theme.dp(this, 16));
        card.setBackground(Theme.card(this));
        return card;
    }

    private ImageView designImage(String drawableName, int heightDp, ImageView.ScaleType scaleType) {
        ImageView image = new ImageView(this);
        int id = getResources().getIdentifier(drawableName, "drawable", getPackageName());
        if (id != 0) {
            image.setImageResource(id);
        } else {
            image.setImageResource(getResources().getIdentifier("ic_launcher", "drawable", getPackageName()));
        }
        image.setScaleType(scaleType);
        image.setAdjustViewBounds(false);
        image.setMinimumHeight(Theme.dp(this, heightDp));
        return image;
    }

    private String roleAssetName(String role) {
        return roleAvatarAssetName(role);
    }

    private int roleAvatarResourceId(String role) {
        int id = getResources().getIdentifier(roleAvatarAssetName(role), "drawable", getPackageName());
        if (id == 0) {
            id = getResources().getIdentifier("ic_launcher", "drawable", getPackageName());
        }
        return id;
    }

    private int liveAvatarResourceId(String role) {
        if (CompanionAssistant.ROLE_GENTLE_WOMAN.equals(role)) {
            int image2Id = getResources().getIdentifier("ui_digital_human_assistant_image2", "drawable", getPackageName());
            if (image2Id != 0) {
                return image2Id;
            }
        }
        return roleAvatarResourceId(role);
    }

    private String roleAvatarAssetName(String role) {
        if (CompanionAssistant.ROLE_SISTER.equals(role)) return "avatar_2d_sister";
        if (CompanionAssistant.ROLE_BROTHER.equals(role)) return "avatar_2d_brother";
        if (CompanionAssistant.ROLE_YOUNG_MAN.equals(role)) return "avatar_2d_young_man";
        return "avatar_2d_gentle_woman";
    }

    private void addStatusPill(String text, int color) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER);
        card.setPadding(Theme.dp(this, 18), Theme.dp(this, 12), Theme.dp(this, 18), Theme.dp(this, 12));
        card.setBackground(Theme.tintedCard(this, color));
        TextView label = Theme.text(this, "✓  " + text, 21, Theme.darken(color, 0.30f), Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        card.addView(label, matchWrap());
        content.addView(card, matchWrap());
        addSpace(content, 12);
    }

    private void addHomeTileGrid(SleepGuardReadiness readiness) {
        boolean ready = readiness == null || readiness.ready();
        boolean emergencyOk = readiness != null && readiness.emergencyOk;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addSmallTile(row, (ready ? "✓" : "!") + "\n睡前自检", ready ? Theme.GREEN : Theme.ORANGE, this::showPreSleepCheck);
        addSmallTile(row, "▮\n睡眠报告", Theme.BLUE, this::showSleepReport);
        content.addView(row, matchWrap());
        addSpace(content, 10);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        addSmallTile(row2, "♡\n小助手", CompanionAssistant.roleColor(prefs.companionRole()), this::showCompanionChat);
        addSmallTile(row2, (emergencyOk ? "☎" : "!") + "\n家人电话", emergencyOk ? Theme.GREEN : Theme.ORANGE, this::showEmergencyDialog);
        content.addView(row2, matchWrap());
    }

    private void addMorningTiles() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        Button water = Theme.softButton(this, "💧  我喝水了", Theme.GREEN);
        water.setTextSize(26);
        water.setMinHeight(Theme.dp(this, 104));
        water.setOnClickListener(v -> Toast.makeText(this, "我记下了，今天慢慢喝水。", Toast.LENGTH_SHORT).show());
        row.addView(water, matchWrap());
        addSpace(row, 12);
        Button med = Theme.softButton(this, "💊  已吃药", Theme.ORANGE);
        med.setTextSize(26);
        med.setMinHeight(Theme.dp(this, 104));
        med.setOnClickListener(v -> {
            prefs.confirmMedicationNow();
            Toast.makeText(this, "已记录今天已吃药", Toast.LENGTH_SHORT).show();
            showMorningCare();
        });
        row.addView(med, matchWrap());
        content.addView(row, matchWrap());
        addSpace(content, 14);
    }

    private void addSmallTile(LinearLayout row, String text, int color, Runnable action) {
        Button tile = Theme.softButton(this, text, color);
        tile.setTextSize(17);
        tile.setMinHeight(Theme.dp(this, 68));
        tile.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(Theme.dp(this, 4), 0, Theme.dp(this, 4), 0);
        row.addView(tile, lp);
    }

    private void addChoice(LinearLayout box, String text, Runnable action) {
        Button button = Theme.button(this, text, Theme.BLUE);
        button.setOnClickListener(v -> action.run());
        box.addView(button, matchWrap());
        addSpace(box, 12);
    }

    private void addNav(LinearLayout nav, String text, Runnable action, boolean selected) {
        int color = "守护".equals(text) ? Theme.BLUE : ("早安".equals(text) ? Theme.ORANGE : Theme.MUTED);
        Button button = selected ? Theme.button(this, navLabel(text), color) : Theme.softButton(this, navLabel(text), color);
        button.setTextSize(20);
        button.setMinHeight(Theme.dp(this, 64));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(Theme.dp(this, 4), 0, Theme.dp(this, 4), 0);
        nav.addView(button, lp);
    }

    private String navLabel(String text) {
        return text;
    }

    private static class SleepWaveformView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private SleepDashboardData data;
        private boolean monitoring;
        private boolean compact;
        private boolean dark;

        SleepWaveformView(android.content.Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setDashboardData(SleepDashboardData data, boolean monitoring) {
            this.data = data;
            this.monitoring = monitoring;
            invalidate();
        }

        void setCompact(boolean compact) {
            this.compact = compact;
            invalidate();
        }

        void setDark(boolean dark) {
            this.dark = dark;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            int events = data == null ? 0 : data.eventCount;
            int high = data == null ? 0 : data.highRiskCount;
            int[] levels = data == null || data.waveformLevels == null ? new int[0] : data.waveformLevels;

            RectF bounds = new RectF(0, 0, w, h);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(dark ? Color.rgb(27, 65, 128) : Theme.mix(monitoring ? Theme.BLUE : Theme.GREEN, Theme.WARM_WHITE, 0.88f));
            canvas.drawRoundRect(bounds, Theme.dp(getContext(), 18), Theme.dp(getContext(), 18), paint);

            float centerY = h * 0.56f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, Theme.dp(getContext(), 1)));
            paint.setColor(dark ? Color.rgb(112, 160, 242) : Theme.mix(Theme.BLUE, Theme.WARM_WHITE, 0.52f));
            canvas.drawLine(Theme.dp(getContext(), 12), centerY, w - Theme.dp(getContext(), 12), centerY, paint);

            if (levels.length == 0) {
                drawPreviewWave(canvas, w, h, centerY);
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(Theme.dp(getContext(), compact ? 12 : 14));
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setColor(dark ? Color.rgb(255, 214, 114) : Theme.ORANGE);
                canvas.drawText(monitoring ? "等待采样" : "未开始", Theme.dp(getContext(), 14), Theme.dp(getContext(), 22), paint);
                if (compact) {
                    return;
                }
                paint.setTextSize(Theme.dp(getContext(), 11));
                paint.setTypeface(Typeface.DEFAULT);
                paint.setColor(dark ? Color.rgb(195, 218, 255) : Theme.MUTED);
                canvas.drawText("开始守护后每 3 秒记录声音/动作摘要", Theme.dp(getContext(), 14), h * 0.72f, paint);
                return;
            }

            int bars = levels.length;
            float side = Theme.dp(getContext(), 14);
            float usable = Math.max(1f, w - side * 2f);
            float step = usable / bars;
            float barWidth = Math.max(Theme.dp(getContext(), 4), step * 0.56f);
            for (int i = 0; i < bars; i++) {
                float x = side + i * step + (step - barWidth) / 2f;
                int level = Math.max(0, Math.min(100, levels[i]));
                float amp = h * (0.08f + level / 100f * 0.42f);
                int color = monitoring ? Theme.BLUE : (dark ? Color.rgb(112, 216, 181) : Theme.GREEN);
                boolean highSpike = level >= 88;
                boolean mediumSpike = level >= 58 && level < 88;
                if (highSpike) {
                    color = Theme.RED;
                } else if (mediumSpike) {
                    color = Theme.ORANGE;
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(dark ? Theme.lighten(color, highSpike || mediumSpike ? 0.10f : 0.22f)
                        : Theme.mix(color, Theme.WARM_WHITE, highSpike || mediumSpike ? 0.08f : 0.18f));
                RectF bar = new RectF(x, centerY - amp, x + barWidth, centerY + amp * 0.55f);
                canvas.drawRoundRect(bar, barWidth, barWidth, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(Theme.dp(getContext(), 12));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setColor(dark ? Color.rgb(195, 218, 255)
                    : (high > 0 ? Theme.RED : (events > 0 ? Theme.ORANGE : Theme.darken(Theme.GREEN, 0.20f))));
            String label = "真实采样 " + (data == null ? levels.length : data.waveformSampleCount) + " 点";
            canvas.drawText(label, Theme.dp(getContext(), 14), h - Theme.dp(getContext(), 10), paint);
        }

        private void drawPreviewWave(Canvas canvas, int w, int h, float centerY) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            float side = Theme.dp(getContext(), 14);
            float usable = Math.max(1f, w - side * 2f);
            android.graphics.Path path = new android.graphics.Path();
            for (int i = 0; i <= 72; i++) {
                float x = side + usable * i / 72f;
                double t = i / 72d;
                float amp = (float) ((Math.sin(t * Math.PI * 10) * 0.35d
                        + Math.sin(t * Math.PI * 23) * 0.20d
                        + Math.sin(t * Math.PI * 37) * 0.10d) * h * 0.32d);
                float y = centerY - amp;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            paint.setStrokeWidth(Theme.dp(getContext(), 6));
            paint.setColor(dark ? Color.argb(70, 122, 209, 255) : Color.argb(70, 43, 104, 226));
            canvas.drawPath(path, paint);
            paint.setStrokeWidth(Theme.dp(getContext(), 2));
            paint.setColor(dark ? Color.rgb(139, 220, 255) : Theme.BLUE);
            canvas.drawPath(path, paint);
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams imageLp(int heightDp) {
        return new LinearLayout.LayoutParams(-1, Theme.dp(this, heightDp));
    }

    private void addSpace(LinearLayout parent, int dp) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, Theme.dp(this, dp)));
    }
}
