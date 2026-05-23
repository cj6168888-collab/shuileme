package com.gouxiong.sleep.live;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

public final class LivePcmPlayer {
    private AudioTrack track;

    public synchronized void play(byte[] pcmFrame) {
        if (pcmFrame == null || pcmFrame.length == 0) {
            return;
        }
        ensureTrack();
        track.write(pcmFrame, 0, pcmFrame.length);
    }

    public synchronized void stop() {
        if (track == null) return;
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

    private void ensureTrack() {
        if (track != null) {
            return;
        }
        int minBuffer = AudioTrack.getMinBufferSize(
                LiveCompanionProtocol.PCM_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuffer, LiveCompanionProtocol.PCM_SAMPLE_RATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(LiveCompanionProtocol.PCM_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferSize)
                    .build();
        } else {
            track = new AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    LiveCompanionProtocol.PCM_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM);
        }
        track.play();
    }
}
