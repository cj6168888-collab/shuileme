package com.gouxiong.sleep.live;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;

public final class LivePcmRecorder {
    public static final int SAMPLE_RATE = LiveCompanionProtocol.PCM_SAMPLE_RATE;
    public static final int CHANNELS = LiveCompanionProtocol.PCM_CHANNELS;
    public static final int FRAME_DURATION_MS = LiveCompanionProtocol.PCM_FRAME_DURATION_MS;
    public static final int FRAME_SAMPLES = SAMPLE_RATE * FRAME_DURATION_MS / 1000;

    private volatile boolean running;
    private AudioRecord recorder;
    private Thread worker;
    private AcousticEchoCanceler echoCanceler;
    private NoiseSuppressor noiseSuppressor;
    private AutomaticGainControl gainControl;

    public interface Listener {
        void onPcmFrame(short[] samples, float rms, long captureTimeMs);

        void onRecorderError(Throwable error);
    }

    public synchronized void start(final Listener listener) {
        if (running) {
            return;
        }
        int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) {
            throw new IllegalStateException("手机暂不支持实时录音参数");
        }
        int bufferBytes = Math.max(minBuffer * 2, FRAME_SAMPLES * 2 * 4);
        recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes);
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            releaseRecorder();
            throw new IllegalStateException("实时录音初始化失败");
        }
        enableVoiceEffects(recorder.getAudioSessionId());
        recorder.startRecording();
        running = true;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                captureLoop(listener == null ? new EmptyListener() : listener);
            }
        }, "GouXiongLivePcmRecorder");
        worker.start();
    }

    public synchronized void stop() {
        running = false;
        AudioRecord active = recorder;
        if (active != null) {
            try {
                active.stop();
            } catch (RuntimeException ignored) {
            }
        }
        Thread activeWorker = worker;
        if (activeWorker != null && Thread.currentThread() != activeWorker) {
            try {
                activeWorker.join(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        releaseRecorder();
    }

    public boolean isRunning() {
        return running;
    }

    public static byte[] shortsToLittleEndianPcm(short[] samples) {
        if (samples == null) {
            return new byte[0];
        }
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            bytes[i * 2] = (byte) (samples[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        return bytes;
    }

    public static float rms(short[] samples) {
        if (samples == null || samples.length == 0) {
            return 0f;
        }
        double sum = 0;
        for (short sample : samples) {
            double normal = sample / 32768.0;
            sum += normal * normal;
        }
        return (float) Math.sqrt(sum / samples.length);
    }

    private void captureLoop(Listener listener) {
        short[] frame = new short[FRAME_SAMPLES];
        try {
            while (running) {
                int offset = 0;
                while (running && offset < frame.length) {
                    int read = recorder.read(frame, offset, frame.length - offset);
                    if (read > 0) {
                        offset += read;
                    } else if (read < 0) {
                        throw new IllegalStateException("实时录音读取失败：" + read);
                    }
                }
                if (running && offset == frame.length) {
                    short[] copy = new short[frame.length];
                    System.arraycopy(frame, 0, copy, 0, frame.length);
                    listener.onPcmFrame(copy, rms(copy), System.currentTimeMillis());
                }
            }
        } catch (Throwable ex) {
            if (running) {
                listener.onRecorderError(ex);
            }
        }
    }

    private void enableVoiceEffects(int audioSessionId) {
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(audioSessionId);
            if (echoCanceler != null) echoCanceler.setEnabled(true);
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioSessionId);
            if (noiseSuppressor != null) noiseSuppressor.setEnabled(true);
        }
        if (AutomaticGainControl.isAvailable()) {
            gainControl = AutomaticGainControl.create(audioSessionId);
            if (gainControl != null) gainControl.setEnabled(true);
        }
    }

    private void releaseRecorder() {
        releaseEffects();
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
        worker = null;
    }

    private void releaseEffects() {
        if (echoCanceler != null) {
            echoCanceler.release();
            echoCanceler = null;
        }
        if (noiseSuppressor != null) {
            noiseSuppressor.release();
            noiseSuppressor = null;
        }
        if (gainControl != null) {
            gainControl.release();
            gainControl = null;
        }
    }

    private static final class EmptyListener implements Listener {
        @Override
        public void onPcmFrame(short[] samples, float rms, long captureTimeMs) {
        }

        @Override
        public void onRecorderError(Throwable error) {
        }
    }
}
