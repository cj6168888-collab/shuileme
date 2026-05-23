package com.gouxiong.sleep.avatar;

public enum AvatarState {
    IDLE("idle"),
    LISTENING("listening"),
    USER_SPEAKING("user_speaking"),
    THINKING("thinking"),
    SPEAKING("speaking"),
    INTERRUPTED("interrupted"),
    SEEING("seeing"),
    READING("reading"),
    FINDING("finding"),
    COMFORTING("comforting"),
    HAPPY("happy"),
    WORRIED("worried"),
    URGENT_WAKEUP("urgent_wakeup");

    private final String wireName;

    AvatarState(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static AvatarState fromMood(String mood) {
        String clean = mood == null ? "" : mood.trim().toLowerCase();
        for (AvatarState state : values()) {
            if (state.wireName.equals(clean)) {
                return state;
            }
        }
        if ("seeing_detail".equals(clean)) return READING;
        if ("vision".equals(clean)) return SEEING;
        if ("find".equals(clean)) return FINDING;
        if ("wakeup".equals(clean) || "alarm".equals(clean) || "urgent".equals(clean)) return URGENT_WAKEUP;
        return LISTENING;
    }
}
