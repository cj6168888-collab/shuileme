package com.gouxiong.sleep.live;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

public final class NativeWebSocketClient {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_HTTP_HEADER_BYTES = 64 * 1024;
    private static final int OPCODE_CONTINUATION = 0x0;
    private static final int OPCODE_TEXT = 0x1;
    private static final int OPCODE_BINARY = 0x2;
    private static final int OPCODE_CLOSE = 0x8;
    private static final int OPCODE_PING = 0x9;
    private static final int OPCODE_PONG = 0xA;

    private final SecureRandom random = new SecureRandom();
    private final Object writeLock = new Object();
    private volatile boolean running;
    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private Thread worker;

    public interface Listener {
        void onOpen();

        void onText(String text);

        void onBinary(byte[] data);

        void onClosed();

        void onError(Throwable error);
    }

    public synchronized void connect(final URI uri, final Map<String, String> headers, final Listener listener) {
        if (worker != null && worker.isAlive()) {
            throw new IllegalStateException("WebSocket is already running");
        }
        running = true;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runSocket(uri, headers, listener);
            }
        }, "GouXiongNativeWebSocket");
        worker.start();
    }

    public boolean isOpen() {
        Socket active = socket;
        return running && active != null && active.isConnected() && !active.isClosed();
    }

    public void sendText(String text) throws IOException {
        sendFrame(OPCODE_TEXT, text == null ? new byte[0] : text.getBytes(UTF_8));
    }

    public void sendBinary(byte[] data) throws IOException {
        sendFrame(OPCODE_BINARY, data == null ? new byte[0] : data);
    }

    public void close() {
        running = false;
        try {
            sendFrame(OPCODE_CLOSE, new byte[0]);
        } catch (IOException ignored) {
        }
        closeQuietly();
    }

    private void runSocket(URI uri, Map<String, String> headers, Listener listener) {
        Listener activeListener = listener == null ? new EmptyListener() : listener;
        boolean opened = false;
        try {
            openSocket(uri);
            String key = createWebSocketKey();
            writeHandshake(uri, headers, key);
            validateHandshake(readHttpHeader(), key);
            opened = true;
            activeListener.onOpen();
            receiveLoop(activeListener);
        } catch (Throwable ex) {
            if (running || !opened) {
                activeListener.onError(ex);
            }
        } finally {
            running = false;
            closeQuietly();
            if (opened) {
                activeListener.onClosed();
            }
        }
    }

    private void openSocket(URI uri) throws IOException {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            throw new IOException("Invalid WebSocket URI: " + uri);
        }
        int port = uri.getPort();
        if ("wss".equalsIgnoreCase(scheme)) {
            if (port < 0) port = 443;
            socket = SSLSocketFactory.getDefault().createSocket(host, port);
        } else if ("ws".equalsIgnoreCase(scheme)) {
            if (port < 0) port = 80;
            socket = new Socket(host, port);
        } else {
            throw new IOException("Unsupported WebSocket scheme: " + scheme);
        }
        socket.setTcpNoDelay(true);
        input = socket.getInputStream();
        output = socket.getOutputStream();
    }

    private void writeHandshake(URI uri, Map<String, String> headers, String key) throws IOException {
        StringBuilder request = new StringBuilder();
        request.append("GET ").append(requestTarget(uri)).append(" HTTP/1.1\r\n");
        request.append("Host: ").append(hostHeader(uri)).append("\r\n");
        request.append("Upgrade: websocket\r\n");
        request.append("Connection: Upgrade\r\n");
        request.append("Sec-WebSocket-Key: ").append(key).append("\r\n");
        request.append("Sec-WebSocket-Version: 13\r\n");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();
                if (name != null && value != null && name.trim().length() > 0) {
                    request.append(name.trim()).append(": ").append(value.trim()).append("\r\n");
                }
            }
        }
        request.append("\r\n");
        output.write(request.toString().getBytes(UTF_8));
        output.flush();
    }

    private String readHttpHeader() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b1 = -1;
        int b2 = -1;
        int b3 = -1;
        int b4 = -1;
        while (true) {
            int next = input.read();
            if (next < 0) {
                throw new EOFException("WebSocket handshake ended early");
            }
            buffer.write(next);
            if (buffer.size() > MAX_HTTP_HEADER_BYTES) {
                throw new IOException("WebSocket handshake header is too large");
            }
            b1 = b2;
            b2 = b3;
            b3 = b4;
            b4 = next;
            if (b1 == '\r' && b2 == '\n' && b3 == '\r' && b4 == '\n') {
                return new String(buffer.toByteArray(), UTF_8);
            }
        }
    }

    private void validateHandshake(String header, String key) throws Exception {
        String[] lines = header.split("\r\n");
        if (lines.length == 0 || !lines[0].contains(" 101 ")) {
            throw new IOException("WebSocket upgrade failed: " + (lines.length > 0 ? lines[0] : ""));
        }
        Map<String, String> responseHeaders = new LinkedHashMap<String, String>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                responseHeaders.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.US),
                        lines[i].substring(colon + 1).trim());
            }
        }
        String expected = acceptKey(key);
        String actual = responseHeaders.get("sec-websocket-accept");
        if (!expected.equals(actual)) {
            throw new IOException("Invalid Sec-WebSocket-Accept");
        }
    }

    private void receiveLoop(Listener listener) throws IOException {
        ByteArrayOutputStream fragmented = null;
        int fragmentedOpcode = -1;
        while (running) {
            Frame frame = readFrame();
            if (frame.opcode == OPCODE_CLOSE) {
                sendFrame(OPCODE_CLOSE, new byte[0]);
                return;
            } else if (frame.opcode == OPCODE_PING) {
                sendFrame(OPCODE_PONG, frame.payload);
            } else if (frame.opcode == OPCODE_PONG) {
                continue;
            } else if (frame.opcode == OPCODE_CONTINUATION) {
                if (fragmented == null) {
                    throw new IOException("Unexpected continuation frame");
                }
                fragmented.write(frame.payload);
                if (frame.fin) {
                    dispatch(listener, fragmentedOpcode, fragmented.toByteArray());
                    fragmented = null;
                    fragmentedOpcode = -1;
                }
            } else if (frame.opcode == OPCODE_TEXT || frame.opcode == OPCODE_BINARY) {
                if (frame.fin) {
                    dispatch(listener, frame.opcode, frame.payload);
                } else {
                    fragmented = new ByteArrayOutputStream();
                    fragmented.write(frame.payload);
                    fragmentedOpcode = frame.opcode;
                }
            }
        }
    }

    private Frame readFrame() throws IOException {
        int first = readByteRequired();
        int second = readByteRequired();
        boolean fin = (first & 0x80) != 0;
        int opcode = first & 0x0F;
        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7F;
        if (length == 126) {
            length = readUnsignedShort();
        } else if (length == 127) {
            length = readUnsignedLong();
        }
        if (length > Integer.MAX_VALUE) {
            throw new IOException("WebSocket frame is too large");
        }
        byte[] mask = masked ? readBytes(4) : null;
        byte[] payload = readBytes((int) length);
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }
        return new Frame(fin, opcode, payload);
    }

    private void sendFrame(int opcode, byte[] payload) throws IOException {
        byte[] data = payload == null ? new byte[0] : payload;
        ByteArrayOutputStream frame = new ByteArrayOutputStream(data.length + 16);
        frame.write(0x80 | (opcode & 0x0F));
        if (data.length <= 125) {
            frame.write(0x80 | data.length);
        } else if (data.length <= 65535) {
            frame.write(0x80 | 126);
            frame.write((data.length >> 8) & 0xFF);
            frame.write(data.length & 0xFF);
        } else {
            frame.write(0x80 | 127);
            long length = data.length;
            for (int i = 7; i >= 0; i--) {
                frame.write((int) ((length >> (8 * i)) & 0xFF));
            }
        }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        frame.write(mask);
        for (int i = 0; i < data.length; i++) {
            frame.write(data[i] ^ mask[i % 4]);
        }
        synchronized (writeLock) {
            if (output == null) {
                throw new IOException("WebSocket is not connected");
            }
            output.write(frame.toByteArray());
            output.flush();
        }
    }

    private void dispatch(Listener listener, int opcode, byte[] payload) {
        if (opcode == OPCODE_TEXT) {
            listener.onText(new String(payload, UTF_8));
        } else if (opcode == OPCODE_BINARY) {
            listener.onBinary(payload);
        }
    }

    private int readByteRequired() throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new EOFException("WebSocket connection closed");
        }
        return value;
    }

    private int readUnsignedShort() throws IOException {
        return ((readByteRequired() & 0xFF) << 8) | (readByteRequired() & 0xFF);
    }

    private long readUnsignedLong() throws IOException {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (readByteRequired() & 0xFFL);
        }
        if (value < 0) {
            throw new IOException("Invalid WebSocket frame length");
        }
        return value;
    }

    private byte[] readBytes(int length) throws IOException {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(bytes, offset, length - offset);
            if (read < 0) {
                throw new EOFException("WebSocket payload ended early");
            }
            offset += read;
        }
        return bytes;
    }

    private String createWebSocketKey() {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        return Base64.encodeToString(nonce, Base64.NO_WRAP);
    }

    private String acceptKey(String key) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest((key + WS_GUID).getBytes(UTF_8));
        return Base64.encodeToString(hash, Base64.NO_WRAP);
    }

    private String requestTarget(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.length() == 0) {
            path = "/";
        }
        String query = uri.getRawQuery();
        return query == null || query.length() == 0 ? path : path + "?" + query;
    }

    private String hostHeader(URI uri) {
        int port = uri.getPort();
        if (port < 0 || (port == 80 && "ws".equalsIgnoreCase(uri.getScheme()))
                || (port == 443 && "wss".equalsIgnoreCase(uri.getScheme()))) {
            return uri.getHost();
        }
        return uri.getHost() + ":" + port;
    }

    private void closeQuietly() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
        input = null;
        output = null;
    }

    private static final class Frame {
        final boolean fin;
        final int opcode;
        final byte[] payload;

        Frame(boolean fin, int opcode, byte[] payload) {
            this.fin = fin;
            this.opcode = opcode;
            this.payload = payload;
        }
    }

    private static final class EmptyListener implements Listener {
        @Override
        public void onOpen() {
        }

        @Override
        public void onText(String text) {
        }

        @Override
        public void onBinary(byte[] data) {
        }

        @Override
        public void onClosed() {
        }

        @Override
        public void onError(Throwable error) {
        }
    }
}
