package com.gouxiong.sleep.model;

import android.database.Cursor;

public class DeviceReading {
    public long id;
    public long timestamp;
    public String source;
    public int heartRate;
    public int spo2;
    public int respiratoryRate;
    public String note;

    public static DeviceReading fromCursor(Cursor cursor) {
        DeviceReading reading = new DeviceReading();
        reading.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        reading.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("ts"));
        reading.source = cursor.getString(cursor.getColumnIndexOrThrow("source"));
        reading.heartRate = cursor.getInt(cursor.getColumnIndexOrThrow("heart_rate"));
        reading.spo2 = cursor.getInt(cursor.getColumnIndexOrThrow("spo2"));
        reading.respiratoryRate = cursor.getInt(cursor.getColumnIndexOrThrow("respiratory_rate"));
        reading.note = cursor.getString(cursor.getColumnIndexOrThrow("note"));
        return reading;
    }
}
