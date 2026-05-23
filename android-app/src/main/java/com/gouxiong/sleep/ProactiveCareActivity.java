package com.gouxiong.sleep;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gouxiong.sleep.util.CompanionAssistant;
import com.gouxiong.sleep.util.PreferenceStore;
import com.gouxiong.sleep.util.ServerApiClient;
import com.gouxiong.sleep.util.Theme;
import com.gouxiong.sleep.util.XiaozhiVoiceProfile;

import org.json.JSONObject;

public class ProactiveCareActivity extends Activity {
    public static final String TYPE_HYDRATION = "hydration";
    public static final String TYPE_MEDICATION = "medication";
    public static final String TYPE_MORNING = "morning";
    public static final String TYPE_SERVER_MESSAGE = "server_message";
    public static final String EXTRA_TYPE = "care_type";
    public static final String EXTRA_MESSAGE = "care_message";
    public static final String EXTRA_SERVER_MESSAGE_ID = "server_message_id";

    private PreferenceStore prefs;
    private TextToSpeech tts;
    private TextView bodyView;
    private String type;
    private String message;
    private int serverMessageId;
    private boolean modelLineRequested;

    public static void open(ContextStarter starter, String type) {
        Intent intent = new Intent(starter.context(), ProactiveCareActivity.class);
        intent.putExtra(EXTRA_TYPE, type);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        starter.context().startActivity(intent);
    }

    public interface ContextStarter {
        android.content.Context context();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new PreferenceStore(this);
        type = getIntent().getStringExtra(EXTRA_TYPE);
        if (type == null || type.length() == 0) type = TYPE_HYDRATION;
        message = getIntent().getStringExtra(EXTRA_MESSAGE);
        if (message == null || message.length() == 0) {
            message = defaultMessage(type);
        }
        serverMessageId = getIntent().getIntExtra(EXTRA_SERVER_MESSAGE_ID, 0);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) {
            setTurnScreenOn(true);
        }

        buildUi();
        speak(message);
        requestModelCareLine();
    }

    private void buildUi() {
        String role = prefs.companionRole();
        int color = CompanionAssistant.roleColor(role);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Theme.dp(this, 22), safeTopPadding(30), Theme.dp(this, 22), Theme.dp(this, 22));
        root.setBackgroundColor(Theme.WARM_WHITE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(Theme.dp(this, 18), Theme.dp(this, 18), Theme.dp(this, 18), Theme.dp(this, 18));
        card.setBackground(Theme.tintedCard(this, color));

        TextView title = Theme.text(this, headlineText(), 34, headlineColor(), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title, matchWrap());
        addSpace(card, 6);

        TextView status = Theme.text(this, assistantTitle(role), 20, color, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        card.addView(status, matchWrap());
        addSpace(card, 10);

        ImageView avatar = avatarImage(role);
        card.addView(avatar, new LinearLayout.LayoutParams(-1, Theme.dp(this, 224)));
        addSpace(card, 12);

        bodyView = Theme.text(this, message, 24, Theme.TEXT, Typeface.BOLD);
        bodyView.setGravity(Gravity.CENTER);
        bodyView.setPadding(Theme.dp(this, 16), Theme.dp(this, 14), Theme.dp(this, 16), Theme.dp(this, 14));
        bodyView.setBackground(Theme.rounded(Theme.mix(color, Theme.WARM_WHITE, 0.86f), 24, this));
        card.addView(bodyView, matchWrap());
        addSpace(card, 16);

        Button primary = Theme.button(this, primaryText(), color);
        primary.setTextSize(28);
        primary.setMinHeight(Theme.dp(this, 78));
        primary.setOnClickListener(v -> confirmDone());
        card.addView(primary, matchWrap());
        addSpace(card, 10);

        Button later = Theme.softButton(this, "等会儿", Theme.ORANGE);
        later.setTextSize(22);
        later.setMinHeight(Theme.dp(this, 64));
        later.setOnClickListener(v -> remindLater());
        card.addView(later, matchWrap());
        addSpace(card, 10);

        Button chat = Theme.softButton(this, "陪我说说", Theme.BLUE);
        chat.setTextSize(22);
        chat.setMinHeight(Theme.dp(this, 62));
        chat.setOnClickListener(v -> openCompanion());
        card.addView(chat, matchWrap());

        root.addView(card, matchWrap());
        setContentView(root);
    }

    private ImageView avatarImage(String role) {
        ImageView image = new ImageView(this);
        int id = getResources().getIdentifier(avatarAssetName(role), "drawable", getPackageName());
        if (id != 0) image.setImageResource(id);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(role);
        return image;
    }

    private String avatarAssetName(String role) {
        role = CompanionAssistant.normalize(role);
        if (CompanionAssistant.ROLE_SISTER.equals(role)) return "ui_avatar_sister";
        if (CompanionAssistant.ROLE_BROTHER.equals(role)) return "ui_avatar_brother";
        if (CompanionAssistant.ROLE_YOUNG_MAN.equals(role)) return "ui_avatar_young_man";
        return "ui_avatar_gentle_woman";
    }

    private String headlineText() {
        if (TYPE_MEDICATION.equals(type)) return "该吃药啦！";
        if (TYPE_MORNING.equals(type)) return "起床啦！";
        if (TYPE_SERVER_MESSAGE.equals(type)) return "小助手来啦！";
        return "该喝水啦！";
    }

    private int headlineColor() {
        if (TYPE_HYDRATION.equals(type)) return Theme.GREEN;
        if (TYPE_MEDICATION.equals(type)) return Theme.ORANGE;
        if (TYPE_SERVER_MESSAGE.equals(type)) return Theme.BLUE;
        return Theme.RED;
    }

    private String assistantTitle(String role) {
        String name = prefs.assistantPersonaConfigured() ? prefs.assistantName() : role;
        return name + "在叫您";
    }

    private String statusText() {
        if (TYPE_MEDICATION.equals(type)) return "我有点着急啦";
        if (TYPE_MORNING.equals(type)) return "早安，我给您说重点";
        return "我来提醒您一下";
    }

    private String primaryText() {
        if (TYPE_MEDICATION.equals(type)) return "我吃过了";
        if (TYPE_MORNING.equals(type)) return "我知道了";
        if (TYPE_SERVER_MESSAGE.equals(type)) return "我听到了";
        return "我喝了";
    }

    private String defaultMessage(String careType) {
        String owner = prefs.ownerAddress();
        String role = prefs.companionRole();
        if (TYPE_MEDICATION.equals(careType)) {
            String med = prefs.medicationName();
            if (med == null || med.length() == 0) med = "早晨的药";
            return CompanionAssistant.proactiveMedicationLine(role, owner, med, false);
        }
        if (TYPE_MORNING.equals(careType)) {
            return owner + "，早安呀。我把昨晚睡眠、喝水和吃药整理好了，您慢慢听。";
        }
        if (TYPE_SERVER_MESSAGE.equals(careType)) {
            return owner + "，我有一条重要的话想慢慢说给您听。";
        }
        long last = prefs.hydrationAcknowledgedAt();
        boolean overdue = last <= 0 || System.currentTimeMillis() - last >= 2L * 60L * 60L * 1000L;
        return CompanionAssistant.proactiveHydrationLine(role, owner, overdue);
    }

    private void requestModelCareLine() {
        if (modelLineRequested || !prefs.serverRegistered() || TYPE_SERVER_MESSAGE.equals(type)) {
            return;
        }
        modelLineRequested = true;
        new Thread(() -> {
            try {
                ServerApiClient.CareBriefResult result = ServerApiClient.careBrief(
                        prefs.serverBaseUrl(),
                        prefs.serverAuthToken(),
                        type,
                        modelCareContext(),
                        false,
                        prefs.deepSeekModel());
                String answer = result.body;
                if (answer == null || answer.trim().length() == 0) return;
                String clean = answer.trim();
                runOnUiThread(() -> {
                    message = clean;
                    if (bodyView != null) bodyView.setText(clean);
                    speak(clean);
                });
            } catch (Exception ignored) {
            }
        }, "GouXiongModelCareLine").start();
    }

    private JSONObject modelCareContext() {
        JSONObject context = new JSONObject();
        try {
            long lastDrinkAt = prefs.hydrationAcknowledgedAt();
            long elapsedMinutes = lastDrinkAt <= 0 ? -1 : Math.max(0, (System.currentTimeMillis() - lastDrinkAt) / 60000L);
            context.put("owner_address", prefs.ownerAddress());
            context.put("assistant_role", prefs.companionRole());
            context.put("medication_name", prefs.medicationName());
            context.put("medication_confirmed_today", prefs.medicationConfirmedToday());
            context.put("hydration_elapsed_minutes", elapsedMinutes);
            context.put("owner_profile", prefs.ownerProfileSummary());
            if (TYPE_MORNING.equals(type)) {
                context.put("sleep_summary", "请结合服务器长期记忆和昨夜本机睡眠记录，整理成一句晨间关怀。");
            }
        } catch (Exception ignored) {
        }
        return context;
    }

    private void confirmDone() {
        if (TYPE_MEDICATION.equals(type)) {
            prefs.confirmMedicationNow();
            CareReminderScheduler.scheduleNextMedicationMorning(this);
            Toast.makeText(this, "我记下了，今天不再催您吃药。", Toast.LENGTH_SHORT).show();
        } else if (TYPE_HYDRATION.equals(type)) {
            prefs.markHydrationAcknowledgedNow();
            CareReminderScheduler.scheduleNextHydration(this);
            Toast.makeText(this, "我记下了，等会儿再提醒喝水。", Toast.LENGTH_SHORT).show();
        } else if (TYPE_SERVER_MESSAGE.equals(type)) {
            markServerMessageReadAsync();
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(CareReminderScheduler.NOTIFICATION_SERVER_MESSAGE);
            Toast.makeText(this, "我记下了。", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void remindLater() {
        if (TYPE_MEDICATION.equals(type)) {
            CareReminderScheduler.scheduleMedicationLater(this, prefs.medicationRepeatMinutes());
        } else if (TYPE_SERVER_MESSAGE.equals(type)) {
            CareReminderScheduler.scheduleServerMessagePollLater(this, 15);
        } else {
            CareReminderScheduler.scheduleHydrationLater(this, 30);
        }
        Toast.makeText(this, "好，我等会儿再来叫您。", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void markServerMessageReadAsync() {
        if (serverMessageId <= 0 || !prefs.serverRegistered()) {
            return;
        }
        new Thread(() -> {
            try {
                ServerApiClient.markMessageRead(prefs.serverBaseUrl(), prefs.serverAuthToken(), serverMessageId);
            } catch (Exception ignored) {
            }
        }, "GouXiongCareMarkServerMessage").start();
    }

    private void openCompanion() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(MainActivity.ACTION_OPEN_COMPANION);
        startActivity(intent);
        finish();
    }

    private void speak(String text) {
        String clean = text == null ? "" : text.replace("\n", "。").trim();
        if (clean.length() == 0) return;
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) {
                Toast.makeText(this, clean, Toast.LENGTH_LONG).show();
                return;
            }
            XiaozhiVoiceProfile.applyTo(tts, prefs.companionRole());
            if (Build.VERSION.SDK_INT >= 21) {
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
            }
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "gouxiong-care");
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    private int safeTopPadding(int baseDp) {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int top = id > 0 ? getResources().getDimensionPixelSize(id) : Theme.dp(this, 24);
        return Theme.dp(this, baseDp) + top;
    }

    private void addAction(LinearLayout row, String text, int color, Runnable action) {
        Button button = Theme.button(this, text, color);
        button.setTextSize(22);
        button.setMinHeight(Theme.dp(this, 74));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(Theme.dp(this, 4), 0, Theme.dp(this, 4), 0);
        row.addView(button, lp);
    }

    private void addSpace(LinearLayout box, int dp) {
        android.view.View view = new android.view.View(this);
        box.addView(view, new LinearLayout.LayoutParams(1, Theme.dp(this, dp)));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }
}
