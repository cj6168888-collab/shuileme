package com.gouxiong.sleep.model;

import android.database.Cursor;

public class SleepEvent {
    public long id;
    public long timestamp;
    public String type;
    public String risk;
    public double confidence;
    public String action;
    public String basis;
    public String feedback;
    public String audioPath;
    public String audioSummary;
    public String motionSummary;
    public String deviceSummary;
    public String evidenceLevel;

    public static SleepEvent fromCursor(Cursor cursor) {
        SleepEvent event = new SleepEvent();
        event.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        event.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("ts"));
        event.type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
        event.risk = cursor.getString(cursor.getColumnIndexOrThrow("risk"));
        event.confidence = cursor.getDouble(cursor.getColumnIndexOrThrow("confidence"));
        event.action = cursor.getString(cursor.getColumnIndexOrThrow("action"));
        event.basis = cursor.getString(cursor.getColumnIndexOrThrow("basis"));
        event.feedback = cursor.getString(cursor.getColumnIndexOrThrow("feedback"));
        event.audioPath = optionalString(cursor, "audio_path");
        event.audioSummary = optionalString(cursor, "audio_summary");
        event.motionSummary = optionalString(cursor, "motion_summary");
        event.deviceSummary = optionalString(cursor, "device_summary");
        event.evidenceLevel = optionalString(cursor, "evidence_level");
        return event;
    }

    private static String optionalString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) {
            return "";
        }
        return cursor.getString(index);
    }
}
