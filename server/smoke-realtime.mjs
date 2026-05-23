import net from 'node:net';
import tls from 'node:tls';
import { createHash, randomBytes } from 'node:crypto';
import { env, loadServerEnv } from './server-env.mjs';

loadServerEnv();

const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

function firstEnv(names, fallback = '') {
  for (const name of names) {
    const value = env(name, '');
    if (value) return value;
  }
  return fallback;
}

function endpointUrl(base, model) {
  const url = new URL(base || 'wss://dashscope.aliyuncs.com/api-ws/v1/realtime');
  if (!url.searchParams.has('model')) url.searchParams.set('model', model);
  return url;
}

function wsAccept(key) {
  return createHash('sha1').update(`${key}${GUID}`).digest('base64');
}

function frame(opcode, payload) {
  const body = Buffer.isBuffer(payload) ? payload : Buffer.from(String(payload || ''), 'utf8');
  const header = [0x80 | (opcode & 0x0f)];
  if (body.length <= 125) {
    header.push(0x80 | body.length);
  } else if (body.length <= 65535) {
    header.push(0x80 | 126, (body.length >> 8) & 0xff, body.length & 0xff);
  } else {
    throw new Error('frame too large');
  }
  const mask = randomBytes(4);
  const masked = Buffer.alloc(body.length);
  for (let i = 0; i < body.length; i++) masked[i] = body[i] ^ mask[i % 4];
  return Buffer.concat([Buffer.from(header), mask, masked]);
}

function parseFrames(state, chunk) {
  state.buffer = state.buffer.length ? Buffer.concat([state.buffer, chunk]) : chunk;
  const frames = [];
  let offset = 0;
  while (state.buffer.length - offset >= 2) {
    const first = state.buffer[offset];
    const second = state.buffer[offset + 1];
    const opcode = first & 0x0f;
    let length = second & 0x7f;
    let cursor = offset + 2;
    if (length === 126) {
      if (state.buffer.length - cursor < 2) break;
      length = state.buffer.readUInt16BE(cursor);
      cursor += 2;
    } else if (length === 127) {
      if (state.buffer.length - cursor < 8) break;
      length = Number(state.buffer.readBigUInt64BE(cursor));
      cursor += 8;
    }
    if (state.buffer.length - cursor < length) break;
    frames.push({ opcode, payload: state.buffer.subarray(cursor, cursor + length) });
    cursor += length;
    offset = cursor;
  }
  state.buffer = state.buffer.subarray(offset);
  return frames;
}

function connectRealtime({ endpoint, apiKey, timeoutMs = 12000 }) {
  return new Promise((resolve, reject) => {
    const key = randomBytes(16).toString('base64');
    const state = { handshake: Buffer.alloc(0), buffer: Buffer.alloc(0), opened: false, events: [] };
    const socket = endpoint.protocol === 'wss:'
      ? tls.connect(endpoint.port ? Number(endpoint.port) : 443, endpoint.hostname, { servername: endpoint.hostname })
      : net.connect(endpoint.port ? Number(endpoint.port) : 80, endpoint.hostname);
    const timer = setTimeout(() => {
      try { socket.destroy(); } catch {}
      reject(new Error(`realtime smoke timed out; opened=${state.opened}; events=${state.events.map(item => item.type).join(',')}`));
    }, timeoutMs);

    function finish(payload) {
      clearTimeout(timer);
      try { socket.write(frame(0x8, Buffer.alloc(0))); } catch {}
      try { socket.destroy(); } catch {}
      resolve(payload);
    }

    socket.on('connect', () => {
      const target = `${endpoint.pathname || '/'}${endpoint.search || ''}`;
      socket.write([
        `GET ${target} HTTP/1.1`,
        `Host: ${endpoint.host}`,
        'Upgrade: websocket',
        'Connection: Upgrade',
        `Sec-WebSocket-Key: ${key}`,
        'Sec-WebSocket-Version: 13',
        `Authorization: Bearer ${apiKey}`,
        '',
        ''
      ].join('\r\n'));
    });

    socket.on('data', chunk => {
      let remaining = chunk;
      if (!state.opened) {
        state.handshake = Buffer.concat([state.handshake, chunk]);
        const splitAt = state.handshake.indexOf('\r\n\r\n');
        if (splitAt < 0) return;
        const header = state.handshake.subarray(0, splitAt).toString('utf8');
        const firstLine = header.split('\r\n')[0] || '';
        if (!firstLine.includes(' 101 ')) {
          clearTimeout(timer);
          socket.destroy();
          reject(new Error(`realtime websocket upgrade failed: ${firstLine}`));
          return;
        }
        const expected = wsAccept(key).toLowerCase();
        if (!header.toLowerCase().includes(`sec-websocket-accept: ${expected}`)) {
          clearTimeout(timer);
          socket.destroy();
          reject(new Error('realtime websocket accept key mismatch'));
          return;
        }
        state.opened = true;
        remaining = state.handshake.subarray(splitAt + 4);
        socket.write(frame(0x1, JSON.stringify({
          type: 'session.update',
          session: {
            modalities: ['text', 'audio'],
            voice: env('ALIYUN_REALTIME_VOICE', 'Cherry'),
            input_audio_format: 'pcm',
            output_audio_format: 'pcm',
            instructions: '你是睡了么的陪伴助手。只回复生活建议，不做医学诊断。',
            turn_detection: {
              type: env('ALIYUN_REALTIME_TURN_DETECTION', 'server_vad'),
              create_response: true,
              interrupt_response: true
            }
          }
        })));
        if (remaining.length === 0) return;
      }
      for (const item of parseFrames(state, remaining)) {
        if (item.opcode === 0x1) {
          let event;
          try {
            event = JSON.parse(item.payload.toString('utf8'));
          } catch {
            event = { type: 'text', raw: item.payload.toString('utf8').slice(0, 120) };
          }
          state.events.push(event);
          if (event.type === 'error') {
            finish({ ok: false, error_type: event.error?.type || event.error?.code || 'error', event_type: event.type });
            return;
          }
          if (String(event.type || '').startsWith('session.')) {
            finish({ ok: true, event_type: event.type, model: event.session?.model || endpoint.searchParams.get('model') || '' });
            return;
          }
        }
      }
    });

    socket.on('error', error => {
      clearTimeout(timer);
      reject(error);
    });
  });
}

const apiKey = firstEnv(['ALIYUN_REALTIME_API_KEY', 'DASHSCOPE_REALTIME_API_KEY', 'QWEN_REALTIME_API_KEY'], env('DASHSCOPE_API_KEY', ''));
const model = firstEnv(['ALIYUN_REALTIME_MODEL', 'DASHSCOPE_REALTIME_MODEL', 'QWEN_REALTIME_MODEL'], 'qwen3-omni-flash-realtime');
const endpoint = endpointUrl(env('ALIYUN_REALTIME_ENDPOINT', 'wss://dashscope.aliyuncs.com/api-ws/v1/realtime'), model);

if (!apiKey) {
  console.log(JSON.stringify({ ok: false, skipped: true, reason: 'DASHSCOPE_API_KEY / ALIYUN_REALTIME_API_KEY is not configured' }));
  process.exit(0);
}

try {
  const result = await connectRealtime({ endpoint, apiKey });
  console.log(JSON.stringify({
    ok: Boolean(result.ok),
    provider: 'aliyun-dashscope-realtime',
    endpoint_host: endpoint.host,
    model,
    event_type: result.event_type || '',
    error_type: result.error_type || ''
  }));
  if (!result.ok) process.exit(1);
} catch (error) {
  console.error(JSON.stringify({
    ok: false,
    provider: 'aliyun-dashscope-realtime',
    endpoint_host: endpoint.host,
    model,
    error: String(error?.message || error).slice(0, 260)
  }));
  process.exit(1);
}
