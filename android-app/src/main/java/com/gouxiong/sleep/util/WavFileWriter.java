package com.gouxiong.sleep.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class WavFileWriter {
    private WavFileWriter() {}

    public static void writePcm16Mono(File file, short[] samples, int sampleRate) throws IOException {
        int dataSize = samples.length * 2;
        int byteRate = sampleRate * 2;
        try (FileOutputStream out = new FileOutputStream(file)) {
            writeAscii(out, "RIFF");
            writeIntLE(out, 36 + dataSize);
            writeAscii(out, "WAVE");
            writeAscii(out, "fmt ");
            writeIntLE(out, 16);
            writeShortLE(out, 1);
            writeShortLE(out, 1);
            writeIntLE(out, sampleRate);
            writeIntLE(out, byteRate);
            writeShortLE(out, 2);
            writeShortLE(out, 16);
            writeAscii(out, "data");
            writeIntLE(out, dataSize);
            for (short sample : samples) {
                writeShortLE(out, sample);
            }
        }
    }

    private static void writeAscii(FileOutputStream out, String value) throws IOException {
        out.write(value.getBytes("US-ASCII"));
    }

    private static void writeIntLE(FileOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private static void writeShortLE(FileOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }
}
