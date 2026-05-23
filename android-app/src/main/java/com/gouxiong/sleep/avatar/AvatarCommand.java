package com.gouxiong.sleep.avatar;

public final class AvatarCommand {
    public static final String SET_STATE = "setState";
    public static final String SET_EMOTION = "setEmotion";
    public static final String START_SPEAKING = "startSpeaking";
    public static final String STOP_SPEAKING = "stopSpeaking";
    public static final String MOUTH_LEVEL = "mouthLevel";
    public static final String BLINK = "blink";
    public static final String NOD = "nod";
    public static final String LOOK_AT_USER = "lookAtUser";
    public static final String LOOK_DOWN = "lookDown";
    public static final String WAVE = "wave";
    public static final String URGENT_WAKE = "urgentWake";

    public final String type;
    public final AvatarState state;
    public final String emotion;
    public final float intensity;
    public final float mouthLevel;

    private AvatarCommand(String type, AvatarState state, String emotion, float intensity, float mouthLevel) {
        this.type = type;
        this.state = state;
        this.emotion = emotion == null ? "" : emotion;
        this.intensity = clamp(intensity);
        this.mouthLevel = clamp(mouthLevel);
    }

    public static AvatarCommand setState(AvatarState state) {
        return new AvatarCommand(SET_STATE, state == null ? AvatarState.LISTENING : state, "", 1f, 0f);
    }

    public static AvatarCommand setEmotion(String emotion, float intensity) {
        return new AvatarCommand(SET_EMOTION, null, emotion, intensity, 0f);
    }

    public static AvatarCommand startSpeaking() {
        return new AvatarCommand(START_SPEAKING, AvatarState.SPEAKING, "", 1f, 0.45f);
    }

    public static AvatarCommand stopSpeaking() {
        return new AvatarCommand(STOP_SPEAKING, null, "", 0f, 0f);
    }

    public static AvatarCommand mouthLevel(float level) {
        return new AvatarCommand(MOUTH_LEVEL, null, "", 0f, level);
    }

    public static AvatarCommand blink() {
        return new AvatarCommand(BLINK, null, "", 1f, 0f);
    }

    public static AvatarCommand nod() {
        return new AvatarCommand(NOD, null, "", 1f, 0f);
    }

    public static AvatarCommand lookAtUser() {
        return new AvatarCommand(LOOK_AT_USER, null, "", 1f, 0f);
    }

    public static AvatarCommand lookDown() {
        return new AvatarCommand(LOOK_DOWN, null, "", 1f, 0f);
    }

    public static AvatarCommand wave() {
        return new AvatarCommand(WAVE, null, "", 1f, 0f);
    }

    public static AvatarCommand urgentWake() {
        return new AvatarCommand(URGENT_WAKE, AvatarState.URGENT_WAKEUP, "urgent", 1f, 0.7f);
    }

    private static float clamp(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }
}
