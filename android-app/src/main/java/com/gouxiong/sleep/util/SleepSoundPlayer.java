package com.gouxiong.sleep.util;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import java.util.Random;

public final class SleepSoundPlayer {
    private static final int SAMPLE_RATE = 22050;
    private static final int FRAME_SAMPLES = 512;
    private final Object lock = new Object();
    private AudioTrack track;
    private Thread thread;
    private volatile boolean running;
    private volatile float volume = 0.22f;
    private volatile int fadeSerial;

    public boolean isRunning() {
        return running;
    }

    public void startRain() {
        synchronized (lock) {
            stopLocked(false);
            running = true;
            volume = 0.02f;
            track = createTrack();
            track.play();
            thread = new Thread(this::writeRainLoop, "SleepSoundPlayerRain");
            thread.start();
            restoreGentleVolume(1800L);
        }
    }

    public void fadeOutAndStop(long millis) {
        int serial = fadeTo(0f, Math.max(600L, millis), "SleepSoundPlayerFadeOut");
        new Thread(() -> {
            try {
                Thread.sleep(Math.max(600L, millis) + 80L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (serial == fadeSerial) {
                stop();
            }
        }, "SleepSoundPlayerStopAfterFade").start();
    }

    public void duckForSpeech() {
        fadeTo(0.055f, 650L, "SleepSoundPlayerDuck");
    }

    public void restoreGentleVolume(long millis) {
        fadeTo(0.20f, Math.max(600L, millis), "SleepSoundPlayerRestore");
    }

    public void stop() {
        synchronized (lock) {
            stopLocked(true);
        }
    }

    private int fadeTo(float target, long millis, String threadName) {
        int serial = ++fadeSerial;
        new Thread(() -> {
            long duration = Math.max(300L, millis);
            int steps = 24;
            float start = volume;
            float cleanTarget = clampVolume(target);
            for (int i = 0; i < steps && running && serial == fadeSerial; i++) {
                volume = start + (cleanTarget - start) * ((float) (i + 1) / steps);
                try {
                    Thread.sleep(duration / steps);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, threadName).start();
        return serial;
    }

    private float clampVolume(float value) {
        if (value < 0f) return 0f;
        if (value > 0.24f) return 0.24f;
        return value;
    }

    private void stopLocked(boolean interrupt) {
        running = false;
        fadeSerial++;
        if (interrupt && thread != null) {
            thread.interrupt();
        }
        thread = null;
        if (track != null) {
            try {
                track.pause();
                track.flush();
            } catch (Exception ignored) {
            }
            try {
                track.release();
            } catch (Exception ignored) {
            }
            track = null;
        }
    }

    private AudioTrack createTrack() {
        int minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuffer, SAMPLE_RATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferSize)
                    .build();
        }
        return new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM);
    }

    private void writeRainLoop() {
        Random random = new Random();
        short[] samples = new short[FRAME_SAMPLES];
        byte[] bytes = new byte[FRAME_SAMPLES * 2];
        double phase = 0d;
        float smooth = 0f;
        while (running) {
            for (int i = 0; i < samples.length; i++) {
                float noise = (random.nextFloat() * 2f) - 1f;
                smooth = smooth * 0.82f + noise * 0.18f;
                phase += 2d * Math.PI * 0.17d / SAMPLE_RATE;
                float slowWave = (float) Math.sin(phase) * 0.12f;
                float sample = (smooth * 0.72f + slowWave) * volume;
                int pcm = Math.max(-32768, Math.min(32767, (int) (sample * 32767f)));
                samples[i] = (short) pcm;
                bytes[i * 2] = (byte) (pcm & 0xff);
                bytes[i * 2 + 1] = (byte) ((pcm >> 8) & 0xff);
            }
            AudioTrack active;
            synchronized (lock) {
                active = track;
            }
            if (active == null) {
                return;
            }
            try {
                active.write(bytes, 0, bytes.length);
            } catch (Exception ignored) {
                stop();
                return;
            }
        }
    }
}
