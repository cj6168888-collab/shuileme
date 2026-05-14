package com.gouxiong.sleep;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;

import com.gouxiong.sleep.data.SleepDatabase;
import com.gouxiong.sleep.model.DeviceReading;
import com.gouxiong.sleep.model.SleepEvent;
import com.gouxiong.sleep.util.CompanionAssistant;
import com.gouxiong.sleep.util.DeepSeekClient;
import com.gouxiong.sleep.util.PreferenceStore;
import com.gouxiong.sleep.util.Theme;
import com.gouxiong.sleep.util.WavFileWriter;

import java.util.List;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_AUDIO = 2101;
    private static final int OWNER_PROFILE_STEP_COUNT = 6;

    private PreferenceStore prefs;
    private SleepDatabase db;
    private LinearLayout root;
    private LinearLayout content;
    private boolean pendingStartAfterPermission;
    private MediaRecorder voiceRecorder;
    private File voiceFile;
    private MediaPlayer previewPlayer;
    private Ringtone previewRingtone;
    private TextToSpeech assistantTts;
    private boolean assistantTtsReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new PreferenceStore(this);
        db = new SleepDatabase(this);
        getWindow().setStatusBarColor(Theme.WARM_WHITE);

        if (handleDebugDeepSeekIntent(getIntent())) {
            return;
        }

        if (prefs.isFirstLaunch()) {
            showOnboarding();
        } else if (shouldOpenMorningCare(getIntent())) {
            showShell("guard");
            showMorningCare();
        } else {
            showShell("guard");
            maybeShowProactiveCare();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (handleDebugDeepSeekIntent(intent)) {
            return;
        }
        if (shouldOpenMorningCare(intent)) {
            showShell("guard");
            showMorningCare();
        }
    }

    private boolean handleDebugDeepSeekIntent(Intent intent) {
        if (!isDebuggableBuild() || intent == null) {
            return false;
        }
        String key = intent.getStringExtra("debug_deepseek_key");
        String model = intent.getStringExtra("debug_deepseek_model");
        boolean injected = false;
        if (key != null && key.startsWith("sk-")) {
            if (prefs.setDeepSeekApiKey(key)) {
                prefs.setAssistantOnlineEnabled(true);
                injected = true;
            }
        }
        if (model != null && model.trim().length() > 0) {
            prefs.setDeepSeekModel(model);
            injected = true;
        }
        if (injected) {
            prefs.setFirstLaunchDone();
            Toast.makeText(this, "已写入本机 DeepSeek 测试配置", Toast.LENGTH_SHORT).show();
        }
        if (intent.getBooleanExtra("open_deepseek_chat", false)) {
            prefs.setFirstLaunchDone();
            showShell("guard");
            showCompanionChat();
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

    private void showOnboarding() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 24), safeTopPadding(28), Theme.dp(this, 24), Theme.dp(this, 28));
        box.setBackgroundColor(Theme.WARM_WHITE);
        scroll.addView(box);

        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("ic_launcher", "drawable", getPackageName()));
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(Theme.dp(this, 96), Theme.dp(this, 96));
        logoLp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(logo, logoLp);

        TextView title = Theme.text(this, "狗熊睡眠", 34, Theme.TEXT, Typeface.BOLD);
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
            showShell("guard");
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
        showShell("guard");
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
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.WARM_WHITE);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Theme.dp(this, 20), safeTopPadding(20), Theme.dp(this, 20), Theme.dp(this, 8));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 12));
        nav.setBackgroundColor(0xffffffff);
        addNav(nav, "守护", () -> showHome(), tab.equals("guard"));
        addNav(nav, "记录", () -> showRecords(), tab.equals("records"));
        addNav(nav, "设置", () -> showSettings(), tab.equals("settings"));
        root.addView(nav, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        if ("records".equals(tab)) {
            showRecords();
        } else if ("settings".equals(tab)) {
            showSettings();
        } else {
            showHome();
        }
    }

    private void showHome() {
        content.removeAllViews();
        boolean monitoring = prefs.isMonitoring();
        content.addView(Theme.text(this, monitoring ? "正在守护，安心睡吧" : "今晚准备好了吗？", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        content.addView(Theme.text(this, prefs.mode() + " · 智能分析 + 安全守护", 19, Theme.MUTED, Typeface.NORMAL), matchWrap());
        addSpace(content, 24);
        addAssistantHero("我的小助手", CompanionAssistant.homeLine(prefs.companionRole(), monitoring), true);
        addSpace(content, 8);

        Button primary = Theme.button(this, monitoring ? "停止守护" : "开始守护", monitoring ? Theme.RED : Theme.BLUE);
        primary.setOnClickListener(v -> {
            if (prefs.isMonitoring()) {
                stopMonitoring();
            } else {
                startMonitoring();
            }
        });
        content.addView(primary, matchWrap());
        addSpace(content, 20);

        addCard("今晚检查", checkText(), Theme.GREEN);
        addCard("守护完整性", guardIntegrityText(), guardIntegrityScore() >= 80 ? Theme.GREEN : Theme.ORANGE);
        addCard("检测可信度", detectionConfidenceText(), Theme.ORANGE);
        addCard("昨晚摘要", db.localReportText(), Theme.BLUE);
        addSpace(content, 12);
        Button selfCheck = Theme.button(this, "睡前自检", Theme.GREEN);
        selfCheck.setTextSize(20);
        selfCheck.setOnClickListener(v -> showPreSleepCheck());
        content.addView(selfCheck, matchWrap());
        addSpace(content, 10);
        Button detectionTest = Theme.button(this, "检测测试", Theme.BLUE);
        detectionTest.setTextSize(20);
        detectionTest.setOnClickListener(v -> showDetectionTest());
        content.addView(detectionTest, matchWrap());
        addSpace(content, 10);
        Button morning = Theme.button(this, "我醒了，早安护理", Theme.GREEN);
        morning.setTextSize(20);
        morning.setOnClickListener(v -> showMorningCare());
        content.addView(morning, matchWrap());
        addSpace(content, 10);
        Button testAlarm = Theme.button(this, "测试强唤醒", Theme.ORANGE);
        testAlarm.setTextSize(20);
        testAlarm.setOnClickListener(v -> startActivity(alarmDrillIntent("手动测试")));
        content.addView(testAlarm, matchWrap());
    }

    private void addAssistantHero(String title, String body, boolean includeChatButton) {
        String role = prefs.companionRole();
        LinearLayout card = cardContainer();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = Theme.text(this, CompanionAssistant.avatarLabel(role), 34, Color.WHITE, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Theme.rounded(CompanionAssistant.roleColor(role), 32, this));
        startAssistantMotion(avatar);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(Theme.dp(this, 72), Theme.dp(this, 72));
        avatarLp.setMargins(0, 0, Theme.dp(this, 14), 0);
        row.addView(avatar, avatarLp);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        String companionTitle = prefs.assistantPersonaConfigured() ? prefs.assistantName() : role;
        String heading = title.equals(role) ? companionTitle : title + " · " + companionTitle;
        words.addView(Theme.text(this, heading, 21, CompanionAssistant.roleColor(role), Typeface.BOLD), matchWrap());
        addSpace(words, 4);
        words.addView(Theme.text(this, body, 18, Theme.MUTED, Typeface.NORMAL), matchWrap());
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

    private void startAssistantMotion(TextView avatar) {
        avatar.postDelayed(() -> animateAssistantAvatar(avatar, true), 300);
    }

    private void animateAssistantAvatar(TextView avatar, boolean outward) {
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
            new AlertDialog.Builder(this)
                    .setTitle(prefs.companionRole() + "关心你")
                    .setMessage(proactiveCareText())
                    .setPositiveButton("和我聊聊", (d, w) -> showCompanionChat())
                    .setNegativeButton("稍后", null)
                    .show();
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
        content.removeAllViews();
        content.addView(Theme.text(this, "设置", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 12);
        addModeButton("标准模式");
        addModeButton("安静模式");
        addModeButton("敏感模式");
        addSpace(content, 12);

        addCard("紧急联系人",
                prefs.emergencySummary() + "\n" + prefs.emergencyActionSummary(),
                prefs.emergencyEnabled() ? Theme.GREEN : Theme.ORANGE);
        addSettingButton("紧急联系人电话/短信", this::showEmergencyDialog);
        addSettingButton("唤醒声音：亲人录音/本地歌曲", this::showSoundSettings);
        addSettingButton("异常证据：录音/外部设备", this::showEvidenceSettings);
        addSettingButton("早晨吃药提醒", this::showMedicationDialog);
        addSettingButton("我的小助手", this::showCompanionSettings);
        addSettingButton("主人档案", this::showOwnerProfileSettings);
        addSettingButton("睡前自检", this::showPreSleepCheck);
        addSettingButton("检测测试", this::showDetectionTest);
        addSettingButton("打开系统蓝牙设置", () -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        addSettingButton("导出证据摘要", this::shareReport);
        addSettingButton("删除全部本地数据", this::confirmDelete);
        addSettingButton("重新查看引导", this::showOnboarding);
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
        button.setOnClickListener(v -> action.run());
        content.addView(button, matchWrap());
        addSpace(content, 10);
    }

    private void showEmergencyDialog() {
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
            showSettings();
        }));
        dialog.show();
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
        addCard("手表/外部设备数据", prefs.externalDeviceEvidence(), prefs.externalDeviceEnabled() ? Theme.GREEN : Theme.ORANGE);
        addSettingButton("填写外部设备摘要", this::showExternalDeviceDialog);
        addSettingButton("添加心率/血氧/呼吸率读数", this::showDeviceReadingDialog);
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
        content.removeAllViews();
        content.addView(Theme.text(this, "睡前自检", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addAssistantHero("睡前准备", CompanionAssistant.sleepPrepLine(prefs.companionRole()), false);
        addCard("守护状态", preSleepStatusText(), Theme.GREEN);
        addCard("守护完整性", guardIntegrityText(), guardIntegrityScore() >= 80 ? Theme.GREEN : Theme.ORANGE);
        addCard("检测可信度", detectionConfidenceText(), Theme.ORANGE);
        addSettingButton("申请必要权限", this::requestEssentialPermissions);
        addSettingButton("关闭电池优化", this::requestIgnoreBatteryOptimization);
        addSettingButton("测试轻提醒震动", this::testGentleReminder);
        addSettingButton("测试强唤醒", () -> startActivity(alarmDrillIntent("睡前自检")));
        addSettingButton("打开系统蓝牙设置", () -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        addSettingButton("返回首页", () -> showShell("guard"));
    }

    private void showMorningCare() {
        content.removeAllViews();
        if (prefs.isMonitoring()) {
            stopMonitoring();
        }
        content.addView(Theme.text(this, "早安护理", 34, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 10);
        addAssistantHero("早安", CompanionAssistant.morningGreeting(prefs.companionRole()), true);
        addCard("睡眠汇报", db.localReportText() + "\n\n" + guardIntegrityText(), Theme.BLUE);
        addCard("喝水提醒", CompanionAssistant.waterLine(prefs.companionRole()), Theme.GREEN);
        if (prefs.medicationEnabled()) {
            addCard("吃药提醒",
                    CompanionAssistant.medicationLine(prefs.companionRole(), prefs.medicationName(), prefs.medicationConfirmedToday())
                            + "\n这不是医疗建议，只提醒你自己设定的事项。",
                    prefs.medicationConfirmedToday() ? Theme.GREEN : Theme.ORANGE);
            if (!prefs.medicationConfirmedToday()) {
                addSettingButton("已吃药", () -> {
                    prefs.confirmMedicationNow();
                    Toast.makeText(this, "已记录今天已吃药", Toast.LENGTH_SHORT).show();
                    showMorningCare();
                });
                addSettingButton("稍后再提醒", () -> {
                    scheduleMedicationReminder(prefs.medicationRepeatMinutes());
                    Toast.makeText(this, prefs.medicationRepeatMinutes() + " 分钟后再次提醒", Toast.LENGTH_SHORT).show();
                    showMorningCare();
                });
            }
        } else {
            addCard("吃药提醒", "尚未设置。需要的话可以在设置里添加早晨用药提醒。", Theme.ORANGE);
        }
        addCard("晨练小贴士", CompanionAssistant.exerciseLine(prefs.companionRole()), Theme.GREEN);
        addCard("生活小贴士", "小助手可结合睡眠、今天状态和主人档案给晨间建议；出门天气仍以手机天气为准。", Theme.BLUE);
        addCard("今天状态", prefs.assistantCheckInSummary(), prefs.assistantCheckInToday() ? Theme.GREEN : Theme.ORANGE);
        addSettingButton("记录今天状态", this::showAssistantCheckIn);
        addCard("今天关怀", proactiveCareText(), prefs.ownerProfileStarted() ? Theme.GREEN : Theme.ORANGE);
        addCard("小助手边界", CompanionAssistant.chatPrivacy(prefs.companionRole(), prefs.assistantOnlineEnabled()), Theme.ORANGE);
        addSettingButton("和我聊聊", this::showCompanionChat);
        addSettingButton("填写主人档案", this::showOwnerProfileSettings);
        addSettingButton("选择小助手", this::showCompanionSettings);
        addSettingButton("设置吃药提醒", this::showMedicationDialog);
        addSettingButton("返回首页", () -> showShell("guard"));
    }

    private void showCompanionSettings() {
        content.removeAllViews();
        content.addView(Theme.text(this, "我的小助手", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addAssistantHero("当前角色", CompanionAssistant.styleSummary(prefs.companionRole()), false);
        addCard("安全说明", "四个小助手只改变头像、语气和陪伴方式。\n检测阈值、强唤醒、电话短信升级规则完全一致。", Theme.BLUE);
        for (String role : CompanionAssistant.ROLES) {
            addCompanionChoice(role, CompanionAssistant.styleSummary(role));
        }
        addCard("联网陪伴",
                prefs.assistantOnlineEnabled()
                        ? "已开启。DeepSeek Key " + (prefs.deepSeekKeyConfigured() ? "已配置" : "未配置") + "。小助手会结合问题、档案摘要、今天状态和睡眠摘要。"
                        : "已暂停。基础守护仍可用，但聊天和复盘会变简单。",
                prefs.assistantOnlineEnabled() ? Theme.GREEN : Theme.ORANGE);
        addCard("小助手身份", prefs.assistantPersonaSummary(), prefs.assistantPersonaConfigured() ? Theme.GREEN : Theme.ORANGE);
        addCard("今天状态", prefs.assistantCheckInSummary(), prefs.assistantCheckInToday() ? Theme.GREEN : Theme.ORANGE);
        addCard("主人档案", prefs.ownerProfileSummary(), prefs.ownerProfileStarted() ? Theme.GREEN : Theme.ORANGE);
        addSettingButton(prefs.assistantPersonaConfigured() ? "改名字和身份" : "和小助手先认识一下", this::showAssistantPersonaDialog);
        addSettingButton("记录今天状态", this::showAssistantCheckIn);
        addSettingButton("填写主人档案", this::showOwnerProfileSettings);
        addSettingButton(prefs.assistantOnlineEnabled() ? "暂停联网陪伴" : "开启联网陪伴", () -> {
            prefs.setAssistantOnlineEnabled(!prefs.assistantOnlineEnabled());
            showCompanionSettings();
        });
        addSettingButton("设置联网 Key / 模型", this::showDeepSeekSettings);
        addSettingButton("进入小助手聊天", this::showCompanionChat);
        addSettingButton("返回设置", this::showSettings);
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
                "您可以像聊天一样说：以后叫你暖暖，像女儿一样陪我，听话一点，多哄我开心。",
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
        String identity = clean;
        if (identity.length() > 60) {
            identity = identity.substring(0, 60);
        }
        saveAssistantPersona(name, identity);
    }

    private String extractAssistantName(String text) {
        String[] markers = {"叫你做", "以后叫你", "叫你", "你叫", "名字叫", "以后叫"};
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
        return prefs.assistantPersonaConfigured() ? prefs.assistantName() : "小熊";
    }

    private void saveAssistantPersona(String name, String identity) {
        prefs.setAssistantPersona(name, identity);
        showCompanionReply("认识好了", CompanionAssistant.firstMeetingDone(prefs.assistantName(), prefs.assistantIdentity()));
    }

    private void addCompanionChoice(String role, String desc) {
        LinearLayout card = cardContainer();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = Theme.text(this, CompanionAssistant.avatarLabel(role), 30, Color.WHITE, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Theme.rounded(CompanionAssistant.roleColor(role), 28, this));
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(Theme.dp(this, 60), Theme.dp(this, 60));
        avatarLp.setMargins(0, 0, Theme.dp(this, 12), 0);
        row.addView(avatar, avatarLp);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Theme.text(this, (prefs.companionRole().equals(role) ? "当前 · " : "") + role, 21, CompanionAssistant.roleColor(role), Typeface.BOLD), matchWrap());
        addSpace(words, 4);
        words.addView(Theme.text(this, desc + "\n示例：" + CompanionAssistant.sampleLine(role), 17, Theme.MUTED, Typeface.NORMAL), matchWrap());
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
        sample.setOnClickListener(v -> speakAssistantText(CompanionAssistant.sampleLine(role)));
        card.addView(sample, matchWrap());

        content.addView(card, matchWrap());
        addSpace(content, 12);
    }

    private void showOwnerProfileSettings() {
        content.removeAllViews();
        content.addView(Theme.text(this, "主人档案", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addAssistantHero("我想更懂你", "这些信息会让小助手更懂您。可以少填，也可以随时改；不要填写身份证、银行卡等敏感信息。", false);
        addCard("当前档案", prefs.ownerProfileSummary(), prefs.ownerProfileStarted() ? Theme.GREEN : Theme.ORANGE);
        addCard("今天状态", prefs.assistantCheckInSummary(), prefs.assistantCheckInToday() ? Theme.GREEN : Theme.ORANGE);
        addCard("主动关怀",
                prefs.assistantProactiveCareEnabled()
                        ? "已开启。App 打开时，小助手每天最多主动问候一次；夜间守护时不会打扰。"
                        : "已关闭。小助手只在你点开时回应。",
                prefs.assistantProactiveCareEnabled() ? Theme.GREEN : Theme.ORANGE);
        addSettingButton("记录今天状态", this::showAssistantCheckIn);
        addSettingButton("小助手一步步建档", () -> showOwnerProfileWizard(0));
        addSettingButton("一次性编辑主人档案", this::showOwnerProfileDialog);
        addSettingButton(prefs.assistantProactiveCareEnabled() ? "关闭主动关怀" : "开启主动关怀", () -> {
            prefs.setAssistantProactiveCareEnabled(!prefs.assistantProactiveCareEnabled());
            showOwnerProfileSettings();
        });
        addSettingButton("看看今天关怀建议", () -> showCompanionReply("今天关怀建议", proactiveCareText()));
        addSettingButton("返回设置", this::showSettings);
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
        if (step == 0) return "只写你愿意告诉小助手的情况。App 不做诊断，只用来把提醒说得更合适。";
        if (step == 1) return "这里是生活提醒，不替代医生医嘱。具体药怎么吃，仍按医生或家人交代。";
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
        content.removeAllViews();
        content.addView(Theme.text(this, "小助手聊天", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addAssistantHero("陪您说说话", CompanionAssistant.chatIntro(prefs.companionRole()), false);
        addCard("今天状态", prefs.assistantCheckInSummary(), prefs.assistantCheckInToday() ? Theme.GREEN : Theme.ORANGE);
        addCard("我记得的主人信息", prefs.ownerProfileSummary(), prefs.ownerProfileStarted() ? Theme.GREEN : Theme.ORANGE);
        if (!prefs.assistantPersonaConfigured()) {
            addCard("先认识一下", CompanionAssistant.firstMeetingIntro(prefs.companionRole()), Theme.ORANGE);
            addSettingButton("告诉我怎么称呼", this::showAssistantPersonaDialog);
        } else {
            addCard("小助手身份", prefs.assistantPersonaSummary(), Theme.GREEN);
        }
        addCard("陪伴方式", CompanionAssistant.chatPrivacy(prefs.companionRole(), prefs.assistantOnlineEnabled())
                + "\n夜间紧急唤醒保留本地兜底，避免网络波动影响安全。", prefs.assistantOnlineEnabled() ? Theme.GREEN : Theme.ORANGE);
        if (prefs.assistantOnlineEnabled() && prefs.deepSeekKeyConfigured()) {
            addSettingButton("想跟我说什么都可以", this::showDeepSeekQuestionDialog);
            addAiQuestionButton("帮我看看昨晚", "请结合我的主人档案、今天状态、昨晚睡眠摘要和守护完整性，给我一份今天能听懂的睡眠复盘和生活建议。");
            addAiQuestionButton("今天怎么安排", "请根据我的身体情况、用药习惯、今天状态和睡眠记录，生成今天的喝水、用药提醒、活动和休息建议。不要诊断。");
            addAiQuestionButton("陪我聊聊天", "我想轻松聊几句。请根据我的兴趣爱好和今天状态，用温柔、简短、适合中老年人的方式陪我说话。");
        } else if (prefs.assistantOnlineEnabled()) {
            addSettingButton("设置联网陪伴", this::showDeepSeekSettings);
        } else {
            addSettingButton("开启联网陪伴", () -> {
                prefs.setAssistantOnlineEnabled(true);
                showCompanionChat();
            });
        }
        addSettingButton("记录今天状态", this::showAssistantCheckIn);
        addSettingButton("小助手一步步建档", () -> showOwnerProfileWizard(0));
        addAssistantReplyButton("今天关怀建议", proactiveCareText());
        addAssistantReplyButton("按今天状态关心我", CompanionAssistant.checkInCareLine(prefs.companionRole(), prefs.assistantCheckInSummary()));
        addAssistantReplyButton("解释昨晚报告",
                CompanionAssistant.chatSleepReport(prefs.companionRole(), db.localReportText(), guardIntegrityScore()));
        addAssistantReplyButton("陪我睡前放松", CompanionAssistant.chatRelax(prefs.companionRole()));
        addAssistantReplyButton("吃药提醒怎么用",
                CompanionAssistant.medicationLine(prefs.companionRole(), prefs.medicationName(), prefs.medicationConfirmedToday())
                        + "\n你可以在设置里修改药名和重复提醒间隔。");
        addAssistantReplyButton("什么时候该咨询医生", CompanionAssistant.chatDoctor(prefs.companionRole()));
        addAssistantReplyButton("隐私怎么保护", CompanionAssistant.chatPrivacy(prefs.companionRole(), prefs.assistantOnlineEnabled()));
        addSettingButton("填写主人档案", this::showOwnerProfileSettings);
        addSettingButton("选择小助手", this::showCompanionSettings);
        addSettingButton("返回首页", () -> showShell("guard"));
    }

    private String proactiveCareText() {
        StringBuilder b = new StringBuilder();
        b.append(CompanionAssistant.sampleLine(prefs.companionRole())).append("\n\n");
        if (prefs.assistantCheckInToday()) {
            b.append("今天状态我记着：\n").append(prefs.assistantCheckInSummary()).append("\n\n");
        } else {
            b.append(CompanionAssistant.checkInIntro(prefs.companionRole())).append("\n\n");
        }
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

    private void showDeepSeekSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));

        TextView note = Theme.text(this,
                "联网陪伴需要 Key。不要把开发者固定 Key 打进 APK；用户 Key 加密保存，可随时清除。",
                17, Theme.MUTED, Typeface.NORMAL);
        box.addView(note, matchWrap());

        EditText key = new EditText(this);
        key.setHint(prefs.deepSeekKeyConfigured() ? "已配置，留空则保持不变" : "粘贴 DeepSeek API Key");
        key.setTextSize(18);
        key.setSingleLine(true);
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(key, matchWrap());

        EditText model = new EditText(this);
        model.setHint("模型，例如 deepseek-v4-flash");
        model.setText(prefs.deepSeekModel());
        model.setTextSize(18);
        model.setSingleLine(true);
        box.addView(model, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("联网 Key / 模型")
                .setMessage("配置后，小助手可联网生成睡眠复盘、生活建议和聊天回复。夜间强唤醒仍有本地兜底。")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    String entered = key.getText().toString().trim();
                    boolean keySaved = true;
                    if (entered.length() > 0) {
                        keySaved = prefs.setDeepSeekApiKey(entered);
                    }
                    prefs.setDeepSeekModel(model.getText().toString());
                    prefs.setAssistantOnlineEnabled(true);
                    Toast.makeText(this, keySaved ? "已加密保存联网设置" : "Key 加密保存失败，请重试", Toast.LENGTH_SHORT).show();
                    showCompanionSettings();
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("清除Key", (d, w) -> {
                    prefs.clearDeepSeekApiKey();
                    Toast.makeText(this, "已清除 DeepSeek Key", Toast.LENGTH_SHORT).show();
                    showCompanionSettings();
                })
                .show();
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

    private void askDeepSeek(String question) {
        String cleanQuestion = question == null ? "" : question.trim();
        if (cleanQuestion.length() == 0) {
            Toast.makeText(this, "问题不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        showCompanionReply("小助手在想", "我听见了，正在认真想怎么回答您。\n夜间强唤醒保留本地兜底，避免网络波动影响安全。");
        new Thread(() -> {
            try {
                String answer = DeepSeekClient.chat(
                        prefs.deepSeekApiKey(),
                        prefs.deepSeekModel(),
                        deepSeekSystemPrompt(),
                        deepSeekUserPrompt(cleanQuestion));
                runOnUiThread(() -> showCompanionReply("小助手回答", answer + "\n\n说明：这是生活建议，不是医学诊断。"));
            } catch (Exception ex) {
                runOnUiThread(() -> showCompanionReply("小助手没连上",
                        "这次联网回答没成功：" + ex.getMessage() + "\n\n强唤醒和紧急联系人仍由本地兜底继续工作。"));
            }
        }, "GouXiongDeepSeek").start();
    }

    private String deepSeekSystemPrompt() {
        return "你是狗熊睡眠 App 的 AI 小助手，角色是" + prefs.companionRole()
                + "。" + CompanionAssistant.companionshipPrinciples(prefs.assistantName(), prefs.assistantIdentity())
                + "。你面向中老年用户，回答要短、清楚、温柔，适合语音朗读。"
                + "你平时用自然的人类陪伴口吻说话，不要反复强调自己是 AI 或机器人；按主人给你的名字和身份自称。"
                + "如果用户直接问到联网、隐私或你是不是程序，要诚实简短说明，不要欺骗。"
                + "你不是医生，不做诊断，不下医学结论。"
                + "你可以结合主人档案、今天状态、睡眠摘要和守护完整性，给睡眠复盘、生活建议、情绪陪伴和医生沟通准备。"
                + "睡眠记录只能称为疑似记录或提醒事件。"
                + "异常多晚重复、憋醒样声音、喘息呛咳或明显不适时，建议用户带记录咨询医生。";
    }

    private String deepSeekUserPrompt(String question) {
        return "用户问题：" + question
                + "\n\n小助手身份：\n" + prefs.assistantPersonaSummary()
                + "\n\n今天状态：\n" + prefs.assistantCheckInSummary()
                + "\n\n主人档案：\n" + prefs.ownerProfileSummary()
                + "\n\n睡眠摘要：\n" + db.localReportText()
                + "\n\n守护完整性：" + guardIntegrityScore() + "分"
                + "\n\n请只给非诊断生活建议，避免吓人，尽量三段以内。";
    }

    private void addAssistantReplyButton(String label, String reply) {
        Button button = Theme.button(this, label, CompanionAssistant.roleColor(prefs.companionRole()));
        button.setTextSize(20);
        button.setOnClickListener(v -> showCompanionReply(label, reply));
        content.addView(button, matchWrap());
        addSpace(content, 10);
    }

    private void addAiQuestionButton(String label, String question) {
        Button button = Theme.button(this, label, Theme.GREEN);
        button.setTextSize(20);
        button.setOnClickListener(v -> askDeepSeek(question));
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

    private void showMedicationDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8));

        EditText name = new EditText(this);
        name.setText(prefs.medicationEnabled() ? prefs.medicationName() : "");
        name.setHint("药名或备注，例如：降压药");
        name.setTextSize(20);
        box.addView(name, matchWrap());

        EditText repeat = new EditText(this);
        repeat.setText(String.valueOf(prefs.medicationRepeatMinutes()));
        repeat.setHint("未确认后几分钟再提醒");
        repeat.setTextSize(20);
        repeat.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        box.addView(repeat, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("早晨吃药提醒")
                .setMessage("只提醒你自己设定的事项，不替代医生医嘱。留空保存可关闭提醒。")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    int minutes = 30;
                    try {
                        minutes = Math.max(5, Math.min(180, Integer.parseInt(repeat.getText().toString().trim())));
                    } catch (Exception ignored) {
                    }
                    prefs.setMedication(name.getText().toString(), minutes);
                    Toast.makeText(this, prefs.medicationEnabled() ? "已保存吃药提醒" : "已关闭吃药提醒", Toast.LENGTH_SHORT).show();
                    showSettings();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void scheduleMedicationReminder(int minutes) {
        Intent intent = new Intent(this, MedicationReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 4401, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        long trigger = System.currentTimeMillis() + minutes * 60L * 1000L;
        if (alarmManager == null) return;
        if (Build.VERSION.SDK_INT >= 23) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
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
        b.append(prefs.externalDeviceEnabled() ? "✓ 已填写外部设备摘要\n" : "! 外部设备未接入\n");
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
        content.addView(Theme.text(this, "检测测试", 30, Theme.TEXT, Typeface.BOLD), matchWrap());
        addSpace(content, 8);
        addCard("测试说明", "这里用于验证记录、轻提醒、强唤醒和反馈流程。\n它是模拟事件，不代表真实睡眠识别能力。", Theme.ORANGE);
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
        addSettingButton("查看测试记录", this::showRecords);
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
        if (Build.VERSION.SDK_INT < 23) {
            return "✓ 电池优化无需处理";
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            return "✓ 已允许不受电池优化限制";
        }
        return "! 建议关闭电池优化，避免夜里被系统停止";
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
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ex) {
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
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            pendingStartAfterPermission = true;
            requestEssentialPermissions();
            Toast.makeText(this, "请先允许麦克风权限，再开始守护", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            requestEssentialPermissions();
        }
        beginMonitoringService();
    }

    private void beginMonitoringService() {
        prefs.setMonitoring(true);
        Intent intent = new Intent(this, SleepMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "已开始睡眠守护", Toast.LENGTH_SHORT).show();
        showHome();
    }

    private void stopMonitoring() {
        prefs.setMonitoring(false);
        stopService(new Intent(this, SleepMonitorService.class));
        Toast.makeText(this, "已停止守护", Toast.LENGTH_SHORT).show();
        showHome();
    }

    private void requestEssentialPermissions() {
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

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
        share.putExtra(Intent.EXTRA_SUBJECT, "狗熊睡眠复盘证据摘要");
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
    }

    private void speakAssistantText(String text) {
        if (text == null || text.trim().length() == 0) {
            return;
        }
        String clean = text.replace("\n", "。");
        if (assistantTts == null) {
            assistantTts = new TextToSpeech(this, status -> {
                assistantTtsReady = status == TextToSpeech.SUCCESS;
                if (assistantTtsReady && assistantTts != null) {
                    assistantTts.setLanguage(Locale.CHINA);
                    assistantTts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "gouxiong-assistant");
                } else {
                    Toast.makeText(this, clean, Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        if (assistantTtsReady) {
            assistantTts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "gouxiong-assistant");
        } else {
            Toast.makeText(this, clean, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        stopPreview();
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

    private LinearLayout cardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Theme.dp(this, 18), Theme.dp(this, 16), Theme.dp(this, 18), Theme.dp(this, 16));
        card.setBackground(Theme.card(this));
        return card;
    }

    private void addChoice(LinearLayout box, String text, Runnable action) {
        Button button = Theme.button(this, text, Theme.BLUE);
        button.setOnClickListener(v -> action.run());
        box.addView(button, matchWrap());
        addSpace(box, 12);
    }

    private void addNav(LinearLayout nav, String text, Runnable action, boolean selected) {
        Button button = Theme.button(this, text, selected ? Theme.BLUE : Theme.MUTED);
        button.setTextSize(18);
        button.setMinHeight(Theme.dp(this, 54));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(Theme.dp(this, 4), 0, Theme.dp(this, 4), 0);
        nav.addView(button, lp);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private void addSpace(LinearLayout parent, int dp) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, Theme.dp(this, dp)));
    }
}
