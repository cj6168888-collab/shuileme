package com.gouxiong.sleep.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.gouxiong.sleep.model.DeviceReading;
import com.gouxiong.sleep.model.SleepEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SleepDatabase extends SQLiteOpenHelper {
    private static final String NAME = "gouxiong_sleep.db";
    private static final int VERSION = 4;
    private static final long DEVICE_MATCH_WINDOW_MS = 15L * 60L * 1000L;

    public SleepDatabase(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sleep_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ts INTEGER NOT NULL," +
                "type TEXT NOT NULL," +
                "risk TEXT NOT NULL," +
                "confidence REAL NOT NULL," +
                "action TEXT NOT NULL," +
                "basis TEXT NOT NULL," +
                "feedback TEXT DEFAULT 'unsure'," +
                "audio_path TEXT DEFAULT ''," +
                "audio_summary TEXT DEFAULT ''," +
                "motion_summary TEXT DEFAULT ''," +
                "device_summary TEXT DEFAULT ''," +
                "evidence_level TEXT DEFAULT 'limited'" +
                ")");
        db.execSQL("CREATE TABLE nightly_summary (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "start_ts INTEGER NOT NULL," +
                "end_ts INTEGER NOT NULL," +
                "event_count INTEGER NOT NULL," +
                "high_risk_count INTEGER NOT NULL," +
                "auto_cancel_count INTEGER NOT NULL," +
                "confidence TEXT NOT NULL," +
                "created_offline INTEGER NOT NULL DEFAULT 1" +
                ")");
        createDeviceReadingsTable(db);
        createObjectMemoryTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            addColumn(db, "sleep_events", "audio_path TEXT DEFAULT ''");
            addColumn(db, "sleep_events", "audio_summary TEXT DEFAULT ''");
            addColumn(db, "sleep_events", "motion_summary TEXT DEFAULT ''");
            addColumn(db, "sleep_events", "device_summary TEXT DEFAULT ''");
            addColumn(db, "sleep_events", "evidence_level TEXT DEFAULT 'limited'");
        }
        if (oldVersion < 3) {
            createDeviceReadingsTable(db);
        }
        if (oldVersion < 4) {
            createObjectMemoryTable(db);
        }
    }

    public long insertEvent(String type, String risk, double confidence, String action, String basis) {
        return insertEvent(type, risk, confidence, action, basis,
                "",
                "现场录音：未保存",
                "手机动作：未记录结构化指标",
                "可穿戴/外部设备：未接入，未参与本次判断",
                "limited");
    }

    public long insertEvent(String type, String risk, double confidence, String action, String basis,
                            String audioPath, String audioSummary, String motionSummary,
                            String deviceSummary, String evidenceLevel) {
        ContentValues values = new ContentValues();
        values.put("ts", System.currentTimeMillis());
        values.put("type", type);
        values.put("risk", risk);
        values.put("confidence", confidence);
        values.put("action", action);
        values.put("basis", basis);
        values.put("feedback", "unsure");
        values.put("audio_path", nullToEmpty(audioPath));
        values.put("audio_summary", nullToEmpty(audioSummary));
        values.put("motion_summary", nullToEmpty(motionSummary));
        values.put("device_summary", nullToEmpty(deviceSummary));
        values.put("evidence_level", nullToEmpty(evidenceLevel));
        return getWritableDatabase().insert("sleep_events", null, values);
    }

    public void updateFeedback(long id, String feedback) {
        ContentValues values = new ContentValues();
        values.put("feedback", feedback);
        getWritableDatabase().update("sleep_events", values, "id=?", new String[]{String.valueOf(id)});
    }

    public List<SleepEvent> getRecentEvents(int limit) {
        List<SleepEvent> events = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT * FROM sleep_events ORDER BY ts DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        try {
            while (cursor.moveToNext()) {
                events.add(SleepEvent.fromCursor(cursor));
            }
        } finally {
            cursor.close();
        }
        return events;
    }

    public int countEventsSince(long since) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM sleep_events WHERE ts>=?",
                new String[]{String.valueOf(since)});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public int countHighRiskSince(long since) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM sleep_events WHERE ts>=? AND risk='high'",
                new String[]{String.valueOf(since)});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public int countAudioClipsSince(long since) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM sleep_events WHERE ts>=? AND audio_path<>''",
                new String[]{String.valueOf(since)});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public int countDeviceReadingsSince(long since) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM device_readings WHERE ts>=?",
                new String[]{String.valueOf(since)});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public long insertDeviceReading(long timestamp, String source, int heartRate, int spo2, int respiratoryRate, String note) {
        ContentValues values = new ContentValues();
        values.put("ts", timestamp);
        values.put("source", cleanSource(source));
        values.put("heart_rate", clamp(heartRate, 0, 240));
        values.put("spo2", clamp(spo2, 0, 100));
        values.put("respiratory_rate", clamp(respiratoryRate, 0, 80));
        values.put("note", nullToEmpty(note));
        return getWritableDatabase().insert("device_readings", null, values);
    }

    public List<DeviceReading> getRecentDeviceReadings(int limit) {
        List<DeviceReading> readings = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT * FROM device_readings ORDER BY ts DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        try {
            while (cursor.moveToNext()) {
                readings.add(DeviceReading.fromCursor(cursor));
            }
        } finally {
            cursor.close();
        }
        return readings;
    }

    public DeviceReading findNearestDeviceReading(long timestamp) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT * FROM device_readings WHERE ts BETWEEN ? AND ? ORDER BY ABS(ts - ?) ASC LIMIT 1",
                new String[]{
                        String.valueOf(timestamp - DEVICE_MATCH_WINDOW_MS),
                        String.valueOf(timestamp + DEVICE_MATCH_WINDOW_MS),
                        String.valueOf(timestamp)
                });
        try {
            return cursor.moveToFirst() ? DeviceReading.fromCursor(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public String nearestDeviceEvidence(long timestamp, String fallback) {
        DeviceReading reading = findNearestDeviceReading(timestamp);
        if (reading == null) {
            String text = fallback == null || fallback.length() == 0
                    ? "可穿戴/外部设备：未接入，未参与本次判断"
                    : fallback;
            return text + "；同时间附近 15 分钟内未找到结构化设备读数";
        }
        long diffMinutes = Math.abs(reading.timestamp - timestamp) / 60000L;
        StringBuilder b = new StringBuilder();
        b.append("可穿戴/外部设备：").append(reading.source)
                .append("，距事件 ").append(diffMinutes).append(" 分钟；");
        b.append("心率 ").append(metricText(reading.heartRate, "次/分")).append("，");
        b.append("血氧 ").append(metricText(reading.spo2, "%")).append("，");
        b.append("呼吸率 ").append(metricText(reading.respiratoryRate, "次/分"));
        if (reading.note != null && reading.note.length() > 0) {
            b.append("；备注：").append(reading.note);
        }
        b.append("。当前为用户录入/模拟读数，尚未自动校验原始设备数据");
        return b.toString();
    }

    public void insertSummary(long start, long end, int eventCount, int highRiskCount, int autoCancelCount, String confidence) {
        ContentValues values = new ContentValues();
        values.put("start_ts", start);
        values.put("end_ts", end);
        values.put("event_count", eventCount);
        values.put("high_risk_count", highRiskCount);
        values.put("auto_cancel_count", autoCancelCount);
        values.put("confidence", confidence);
        values.put("created_offline", 1);
        getWritableDatabase().insert("nightly_summary", null, values);
    }

    public void deleteAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("sleep_events", null, null);
        db.delete("nightly_summary", null, null);
        db.delete("device_readings", null, null);
        db.delete("object_memory", null, null);
    }

    public void upsertObjectMemory(String itemName, String place, String note, String confidence) {
        String itemKey = normalizeObjectKey(itemName);
        String cleanPlace = nullToEmpty(place).trim();
        if (itemKey.length() == 0 || cleanPlace.length() == 0) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("item_key", itemKey);
        values.put("item_name", displayObjectName(itemName, itemKey));
        values.put("place", cleanPlace);
        values.put("note", nullToEmpty(note).trim());
        values.put("confidence", nullToEmpty(confidence).trim());
        values.put("last_seen_ts", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("object_memory", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String objectMemorySummary() {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT item_name, place, confidence, last_seen_ts FROM object_memory ORDER BY last_seen_ts DESC LIMIT 8",
                null);
        try {
            if (!cursor.moveToFirst()) {
                return "还没有自动记住物品位置。聊天时小助手看到钥匙、手机、药盒、眼镜等常用物品，会帮你记最近一次位置。";
            }
            StringBuilder b = new StringBuilder();
            do {
                if (b.length() > 0) b.append("\n");
                b.append(cursor.getString(0)).append("：").append(cursor.getString(1))
                        .append("（").append(friendlyTime(cursor.getLong(3))).append("看到");
                String confidence = cursor.getString(2);
                if (confidence != null && confidence.length() > 0) {
                    b.append("，").append(confidence);
                }
                b.append("）");
            } while (cursor.moveToNext());
            return b.toString();
        } finally {
            cursor.close();
        }
    }

    public String objectMemoryAnswer(String query) {
        String key = normalizeObjectKey(query);
        if (key.length() == 0) {
            return "";
        }
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT item_name, place, confidence, last_seen_ts, note FROM object_memory WHERE item_key=? LIMIT 1",
                new String[]{key});
        try {
            if (!cursor.moveToFirst()) {
                return "";
            }
            String item = cursor.getString(0);
            String place = cursor.getString(1);
            String confidence = cursor.getString(2);
            long ts = cursor.getLong(3);
            String note = cursor.getString(4);
            StringBuilder b = new StringBuilder();
            b.append("我记得").append(friendlyTime(ts)).append("看见")
                    .append(item).append("在").append(place).append("。");
            if (confidence != null && confidence.length() > 0) {
                b.append("这个位置是").append(confidence).append("把握。");
            }
            b.append("您先去那里看看还在不在。");
            if (note != null && note.length() > 0) {
                b.append("\n补充：").append(note);
            }
            return b.toString();
        } finally {
            cursor.close();
        }
    }

    public void clearObjectMemory() {
        getWritableDatabase().delete("object_memory", null, null);
    }

    public String localReportText() {
        long day = 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();
        int lastNight = countEventsSince(now - day);
        int week = countEventsSince(now - 7L * day);
        int highWeek = countHighRiskSince(now - 7L * day);
        int audioClips = countAudioClipsSince(now - day);
        int deviceReadings = countDeviceReadingsSince(now - day);
        return "昨晚记录 " + lastNight + " 次，可播放片段 " + audioClips + " 条\n设备读数 " + deviceReadings + " 条\n7 天记录 " + week + " 次，高风险 " + highWeek + " 次\n小助手可结合这些摘要生成复盘建议，本页为基础记录";
    }

    public String doctorReportText(int limit) {
        StringBuilder b = new StringBuilder();
        b.append("狗熊睡眠复盘摘要\n");
        b.append(localReportText()).append("\n\n");
        b.append("说明：以下为疑似异常提醒证据，不是医学诊断；医生仍需结合问诊、体征和专业检查判断。\n\n");
        List<SleepEvent> events = getRecentEvents(limit);
        if (events.isEmpty()) {
            b.append("暂无异常事件记录。");
            return b.toString();
        }
        for (SleepEvent event : events) {
            b.append(formatTime(event.timestamp)).append("  ");
            b.append(event.type).append(" · ").append(event.risk).append(" · ").append(event.action).append("\n");
            b.append("触发依据：").append(event.basis).append("\n");
            appendIfPresent(b, event.audioSummary);
            appendIfPresent(b, event.motionSummary);
            appendIfPresent(b, event.deviceSummary);
            if (event.deviceSummary == null || event.deviceSummary.indexOf("距事件") < 0) {
                appendIfPresent(b, nearestDeviceEvidence(event.timestamp, ""));
            }
            b.append("用户反馈：").append(event.feedback).append("\n");
            if (event.audioPath != null && event.audioPath.length() > 0) {
                b.append("现场录音：已保存在本机记录页，可在手机上播放；文本导出默认不附带音频。\n");
            }
            b.append("\n");
        }
        return b.toString();
    }

    public static String formatTime(long timestamp) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(timestamp));
    }

    public String formatDeviceReading(DeviceReading reading) {
        StringBuilder b = new StringBuilder();
        b.append(formatTime(reading.timestamp)).append("  ").append(reading.source).append("\n");
        b.append("心率 ").append(metricText(reading.heartRate, "次/分"));
        b.append(" · 血氧 ").append(metricText(reading.spo2, "%"));
        b.append(" · 呼吸率 ").append(metricText(reading.respiratoryRate, "次/分"));
        if (reading.note != null && reading.note.length() > 0) {
            b.append("\n").append(reading.note);
        }
        return b.toString();
    }

    private void createDeviceReadingsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS device_readings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ts INTEGER NOT NULL," +
                "source TEXT NOT NULL," +
                "heart_rate INTEGER NOT NULL DEFAULT 0," +
                "spo2 INTEGER NOT NULL DEFAULT 0," +
                "respiratory_rate INTEGER NOT NULL DEFAULT 0," +
                "note TEXT DEFAULT ''" +
                ")");
    }

    private void createObjectMemoryTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS object_memory (" +
                "item_key TEXT PRIMARY KEY," +
                "item_name TEXT NOT NULL," +
                "place TEXT NOT NULL," +
                "note TEXT DEFAULT ''," +
                "confidence TEXT DEFAULT ''," +
                "last_seen_ts INTEGER NOT NULL" +
                ")");
    }

    private void addColumn(SQLiteDatabase db, String table, String columnSql) {
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + columnSql);
        } catch (Exception ignored) {
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String cleanSource(String source) {
        if (source == null || source.trim().length() == 0) {
            return "外部设备";
        }
        return source.trim();
    }

    private static String normalizeObjectKey(String value) {
        if (value == null) return "";
        String v = value.trim().toLowerCase(Locale.CHINA);
        if (v.length() == 0) return "";
        if (containsAny(v, "钥匙", "key", "keys")) return "keys";
        if (containsAny(v, "手机", "电话", "mobile", "phone")) return "phone";
        if (containsAny(v, "药", "药盒", "药瓶", "medicine", "pill")) return "medicine";
        if (containsAny(v, "眼镜", "老花镜", "glasses")) return "glasses";
        if (containsAny(v, "钱包", "钱夹", "wallet")) return "wallet";
        if (containsAny(v, "身份证", "医保卡", "证件", "卡包", "card")) return "cards";
        if (containsAny(v, "遥控器", "remote")) return "remote";
        if (containsAny(v, "拐杖", "手杖", "cane")) return "cane";
        if (containsAny(v, "水杯", "杯子", "cup")) return "cup";
        return v.length() > 16 ? v.substring(0, 16) : v;
    }

    private static String displayObjectName(String value, String key) {
        if (value != null && value.trim().length() > 0) {
            return value.trim();
        }
        if ("keys".equals(key)) return "钥匙";
        if ("phone".equals(key)) return "手机";
        if ("medicine".equals(key)) return "药品";
        if ("glasses".equals(key)) return "眼镜";
        if ("wallet".equals(key)) return "钱包";
        if ("cards".equals(key)) return "证件/卡";
        if ("remote".equals(key)) return "遥控器";
        if ("cane".equals(key)) return "拐杖";
        if ("cup".equals(key)) return "水杯";
        return key;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private static String friendlyTime(long timestamp) {
        java.util.Calendar now = java.util.Calendar.getInstance(Locale.CHINA);
        java.util.Calendar then = java.util.Calendar.getInstance(Locale.CHINA);
        then.setTimeInMillis(timestamp);
        String hm = new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(timestamp));
        if (now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)) {
            return "今天 " + hm;
        }
        now.add(java.util.Calendar.DAY_OF_YEAR, -1);
        if (now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)) {
            return "昨天 " + hm;
        }
        return formatTime(timestamp);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static String metricText(int value, String unit) {
        return value > 0 ? value + unit : "未记录";
    }

    private static void appendIfPresent(StringBuilder b, String value) {
        if (value != null && value.length() > 0) {
            b.append(value).append("\n");
        }
    }
}
