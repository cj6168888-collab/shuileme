import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import net from 'node:net';
import { createServer as createHttpServer } from 'node:http';
import { createHash, randomBytes } from 'node:crypto';

const tmp = mkdtempSync(join(tmpdir(), 'gouxiong-server-'));
process.env.GOUXIONG_DB_PATH = join(tmp, 'test.sqlite3');
process.env.GOUXIONG_DEV_SMS = '1';
process.env.GOUXIONG_ADMIN_TOKEN = 'test-admin-token';
process.env.GOUXIONG_ADMIN_BASIC_USER = 'admin';
process.env.GOUXIONG_ADMIN_BASIC_PASSWORD = 'test-page-password';
process.env.GOUXIONG_SERVER_SECRET = 'test-server-secret-with-enough-length';
process.env.DASHSCOPE_API_KEY = '';
process.env.ALIYUN_MODEL_API_KEY = '';
process.env.ALIYUN_BAILIAN_API_KEY = '';
process.env.DEEPSEEK_API_KEY = '';
process.env.VISION_API_KEY = '';
process.env.VISION_ENDPOINT = '';
process.env.VISION_MODEL = '';
process.env.ALIYUN_REALTIME_ENABLED = '';
process.env.ALIYUN_REALTIME_API_KEY = '';
process.env.ALIYUN_REALTIME_ENDPOINT = '';
process.env.GOUXIONG_NEWS_RSS_URL = '';
process.env.LINLY_TALKER_STREAM_ENABLED = '';
process.env.LINLY_TALKER_STREAM_URL = '';
process.env.LINLY_TALKER_STREAM_WEB_URL = '';

const { createServer } = await import('./gouxiong-server.mjs');

function listen(server) {
  return new Promise(resolve => {
    server.listen(0, '127.0.0.1', () => resolve(server.address().port));
  });
}

async function request(base, method, path, body, token) {
  const headers = { 'Content-Type': 'application/json; charset=utf-8' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(base + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const data = await response.json();
  if (!response.ok) throw new Error(`${response.status} ${data.error || JSON.stringify(data)}`);
  return data;
}

async function textRequest(base, path, headers = {}) {
  const response = await fetch(base + path, { headers });
  const text = await response.text();
  if (!response.ok) throw new Error(`${response.status} ${text.slice(0, 120)}`);
  return text;
}

function basic(user, password) {
  return `Basic ${Buffer.from(`${user}:${password}`).toString('base64')}`;
}

function wsAccept(key) {
  return createHash('sha1').update(`${key}258EAFA5-E914-47DA-95CA-C5AB0DC85B11`).digest('base64');
}

function clientWsFrame(opcode, payload) {
  const body = Buffer.isBuffer(payload) ? payload : Buffer.from(String(payload || ''), 'utf8');
  const header = [];
  header.push(0x80 | (opcode & 0x0f));
  if (body.length <= 125) {
    header.push(0x80 | body.length);
  } else if (body.length <= 65535) {
    header.push(0x80 | 126, (body.length >> 8) & 0xff, body.length & 0xff);
  } else {
    throw new Error('test websocket frame too large');
  }
  const mask = randomBytes(4);
  const masked = Buffer.alloc(body.length);
  for (let i = 0; i < body.length; i++) masked[i] = body[i] ^ mask[i % 4];
  return Buffer.concat([Buffer.from(header), mask, masked]);
}

function serverWsFrame(opcode, payload) {
  const body = Buffer.isBuffer(payload) ? payload : Buffer.from(String(payload || ''), 'utf8');
  const header = [];
  header.push(0x80 | (opcode & 0x0f));
  if (body.length <= 125) {
    header.push(body.length);
  } else if (body.length <= 65535) {
    header.push(126, (body.length >> 8) & 0xff, body.length & 0xff);
  } else {
    throw new Error('test websocket frame too large');
  }
  return Buffer.concat([Buffer.from(header), body]);
}

function maskedWsFrames(state, chunk) {
  if (!state.buffer) state.buffer = Buffer.alloc(0);
  state.buffer = state.buffer.length ? Buffer.concat([state.buffer, chunk]) : chunk;
  const frames = [];
  let offset = 0;
  while (state.buffer.length - offset >= 2) {
    const first = state.buffer[offset];
    const second = state.buffer[offset + 1];
    const opcode = first & 0x0f;
    const masked = (second & 0x80) !== 0;
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
    if (!masked || state.buffer.length - cursor < 4 + length) break;
    const mask = state.buffer.subarray(cursor, cursor + 4);
    cursor += 4;
    const payload = Buffer.from(state.buffer.subarray(cursor, cursor + length));
    cursor += length;
    for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
    frames.push({ opcode, payload });
    offset = cursor;
  }
  state.buffer = state.buffer.subarray(offset);
  return frames;
}

function serverWsFrames(state, chunk) {
  if (!state.buffer) state.buffer = Buffer.alloc(0);
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

async function liveWebSocketProbe(port, token) {
  return new Promise((resolve, reject) => {
    const socket = net.connect(port, '127.0.0.1');
    const key = randomBytes(16).toString('base64');
    const events = [];
    const state = { handshake: Buffer.alloc(0), buffer: Buffer.alloc(0), opened: false };
    const timer = setTimeout(() => {
      socket.destroy();
      reject(new Error(`live websocket probe timed out; events=${JSON.stringify(events).slice(0, 400)}`));
    }, 9000);
    function finish() {
      clearTimeout(timer);
      try {
        socket.write(clientWsFrame(0x8, Buffer.alloc(0)));
      } catch (error) {
      }
      socket.destroy();
      resolve(events);
    }
    socket.on('connect', () => {
      socket.write([
        'GET /api/live/session HTTP/1.1',
        `Host: 127.0.0.1:${port}`,
        'Upgrade: websocket',
        'Connection: Upgrade',
        `Sec-WebSocket-Key: ${key}`,
        'Sec-WebSocket-Version: 13',
        `Authorization: Bearer ${token}`,
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
        if (!header.startsWith('HTTP/1.1 101')) throw new Error(`websocket upgrade failed: ${header.split('\r\n')[0]}`);
        if (!header.toLowerCase().includes(`sec-websocket-accept: ${wsAccept(key).toLowerCase()}`)) {
          throw new Error('websocket accept key mismatch');
        }
        state.opened = true;
        remaining = state.handshake.subarray(splitAt + 4);
        socket.write(clientWsFrame(0x1, JSON.stringify({ type: 'hello' })));
        socket.write(clientWsFrame(0x1, JSON.stringify({ type: 'start', mode: 'auto' })));
        socket.write(clientWsFrame(0x2, Buffer.from([1, 2, 3, 4])));
        socket.write(clientWsFrame(0x1, JSON.stringify({ type: 'input_text', text: '奶奶说我晚上总醒，今天有点头晕，想让你陪陪我。' })));
        socket.write(clientWsFrame(0x1, JSON.stringify({ type: 'abort', reason: 'wake_word_detected' })));
      }
      if (remaining.length === 0) return;
      for (const frame of serverWsFrames(state, remaining)) {
        if (frame.opcode === 0x1) {
          const event = JSON.parse(frame.payload.toString('utf8'));
          events.push(event);
          if (event.type === 'tts' && event.model_provider === 'fallback') finish();
        }
      }
    });
    socket.on('error', error => {
      clearTimeout(timer);
      reject(error);
    });
  });
}

async function startFakeRealtimeServer() {
  const received = { updates: 0, audioAppends: 0, cancels: 0, clears: 0, auth: '' };
  const sockets = new Set();
  const server = net.createServer(socket => {
    sockets.add(socket);
    socket.on('close', () => sockets.delete(socket));
    const state = { handshake: Buffer.alloc(0), buffer: Buffer.alloc(0), opened: false, replied: false };
    socket.on('data', chunk => {
      let remaining = chunk;
      if (!state.opened) {
        state.handshake = Buffer.concat([state.handshake, chunk]);
        const splitAt = state.handshake.indexOf('\r\n\r\n');
        if (splitAt < 0) return;
        const header = state.handshake.subarray(0, splitAt).toString('utf8');
        const keyLine = header.split('\r\n').find(line => /^sec-websocket-key:/i.test(line));
        const authLine = header.split('\r\n').find(line => /^authorization:/i.test(line));
        received.auth = authLine || '';
        const key = keyLine ? keyLine.split(':').slice(1).join(':').trim() : '';
        socket.write([
          'HTTP/1.1 101 Switching Protocols',
          'Upgrade: websocket',
          'Connection: Upgrade',
          `Sec-WebSocket-Accept: ${wsAccept(key)}`,
          '',
          ''
        ].join('\r\n'));
        state.opened = true;
        remaining = state.handshake.subarray(splitAt + 4);
        if (remaining.length === 0) return;
      }
      for (const frame of maskedWsFrames(state, remaining)) {
        if (frame.opcode !== 0x1) continue;
        const event = JSON.parse(frame.payload.toString('utf8'));
        if (event.type === 'session.update') {
          received.updates++;
          socket.write(serverWsFrame(0x1, JSON.stringify({
            type: 'session.updated',
            session: { model: 'fake-qwen-realtime' }
          })));
        }
        if (event.type === 'input_audio_buffer.append') {
          received.audioAppends++;
          if (!state.replied) {
            state.replied = true;
            socket.write(serverWsFrame(0x1, JSON.stringify({ type: 'input_audio_buffer.speech_started' })));
            socket.write(serverWsFrame(0x1, JSON.stringify({
              type: 'conversation.item.input_audio_transcription.completed',
              transcript: '实时转写测试：奶奶刚刚说夜里醒了。'
            })));
            socket.write(serverWsFrame(0x1, JSON.stringify({
              type: 'response.audio_transcript.delta',
              delta: '奶奶别急，我听见了。'
            })));
            socket.write(serverWsFrame(0x1, JSON.stringify({
              type: 'response.audio.delta',
              delta: Buffer.from([1, 2, 3, 4, 5, 6]).toString('base64')
            })));
            socket.write(serverWsFrame(0x1, JSON.stringify({
              type: 'response.done',
              response: {
                output: [{
                  content: [{ transcript: '奶奶别急，我听见了。今晚我会继续帮您留意，但这不是诊断。' }]
                }]
              }
            })));
          }
        }
        if (event.type === 'response.cancel') {
          received.cancels++;
        }
        if (event.type === 'input_audio_buffer.clear') {
          received.clears++;
        }
      }
    });
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  return {
    server,
    port: server.address().port,
    received,
    close() {
      for (const socket of sockets) socket.destroy();
      server.close();
    }
  };
}

async function startFakeLinlyServer() {
  const received = { offers: 0, human: 0, interrupts: 0, speaking: 0, lastHuman: null };
  const server = createHttpServer((req, res) => {
    let raw = '';
    req.setEncoding('utf8');
    req.on('data', chunk => { raw += chunk; });
    req.on('end', () => {
      const body = raw ? JSON.parse(raw) : {};
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      if (req.method === 'GET' && req.url === '/health') {
        res.end(JSON.stringify({ ok: true, service: 'fake-linly' }));
        return;
      }
      if (req.method === 'POST' && req.url === '/offer') {
        received.offers++;
        res.end(JSON.stringify({ code: 0, sdp: `answer:${body.sdp}`, type: 'answer', sessionid: 42 }));
        return;
      }
      if (req.method === 'POST' && req.url === '/human') {
        received.human++;
        received.lastHuman = body;
        if (body.text === 'force-linly-error') {
          res.end(JSON.stringify({ code: -1, msg: 'forced failure' }));
          return;
        }
        res.end(JSON.stringify({ code: 0, msg: 'ok', response: body.text || '' }));
        return;
      }
      if (req.method === 'POST' && req.url === '/interrupt_talk') {
        received.interrupts++;
        res.end(JSON.stringify({ code: 0, msg: 'ok' }));
        return;
      }
      if (req.method === 'POST' && req.url === '/is_speaking') {
        received.speaking++;
        res.end(JSON.stringify({ code: 0, data: false }));
        return;
      }
      res.statusCode = 404;
      res.end(JSON.stringify({ code: -1, msg: 'not found' }));
    });
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  return {
    server,
    port: server.address().port,
    received,
    close() {
      server.close();
    }
  };
}

async function liveWebSocketAudioProbe(port, token) {
  return new Promise((resolve, reject) => {
    const socket = net.connect(port, '127.0.0.1');
    const key = randomBytes(16).toString('base64');
    const events = [];
    const state = { handshake: Buffer.alloc(0), buffer: Buffer.alloc(0), opened: false };
    let abortSent = false;
    const timer = setTimeout(() => {
      socket.destroy();
      reject(new Error(`live websocket audio probe timed out; events=${JSON.stringify(events).slice(0, 2000)}`));
    }, 9000);
    function maybeFinish() {
      const hasAsr = events.some(item => item.type === 'stt' && item.source === 'aliyun_realtime' && String(item.text || '').includes('实时转写测试'));
      const hasTts = events.some(item => item.type === 'tts' && item.model_provider === 'aliyun-dashscope-realtime');
      const hasAudio = events.some(item => item.type === 'binary_audio' && item.bytes > 0);
      if (hasAsr && hasTts && hasAudio && !abortSent) {
        abortSent = true;
        socket.write(clientWsFrame(0x1, JSON.stringify({ type: 'abort', reason: 'barge_in_test' })));
        return;
      }
      const hasBridgeAbort = events.some(item => item.type === 'event' && item.name === 'realtime_bridge_abort' && item.realtime_aborted === true);
      const hasInterrupted = events.some(item => item.type === 'tts' && item.state === 'interrupted' && item.realtime_aborted === true);
      if (hasAsr && hasTts && hasAudio && hasBridgeAbort && hasInterrupted) {
        clearTimeout(timer);
        try {
          socket.write(clientWsFrame(0x8, Buffer.alloc(0)));
        } catch (error) {
        }
        socket.destroy();
        resolve(events);
      }
    }
    socket.on('connect', () => {
      socket.write([
        'GET /api/live/session HTTP/1.1',
        `Host: 127.0.0.1:${port}`,
        'Upgrade: websocket',
        'Connection: Upgrade',
        `Sec-WebSocket-Key: ${key}`,
        'Sec-WebSocket-Version: 13',
        `Authorization: Bearer ${token}`,
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
        if (!header.startsWith('HTTP/1.1 101')) throw new Error(`websocket upgrade failed: ${header.split('\r\n')[0]}`);
        state.opened = true;
        remaining = state.handshake.subarray(splitAt + 4);
        socket.write(clientWsFrame(0x1, JSON.stringify({ type: 'hello' })));
        socket.write(clientWsFrame(0x2, Buffer.from([1, 2, 3, 4, 5, 6, 7, 8])));
      }
      for (const frame of serverWsFrames(state, remaining)) {
        if (frame.opcode === 0x1) {
          events.push(JSON.parse(frame.payload.toString('utf8')));
        } else if (frame.opcode === 0x2) {
          events.push({ type: 'binary_audio', bytes: frame.payload.length });
        }
      }
      maybeFinish();
    });
    socket.on('error', error => {
      clearTimeout(timer);
      reject(error);
    });
  });
}

const server = createServer();
const port = await listen(server);
const base = `http://127.0.0.1:${port}`;

try {
  const health = await request(base, 'GET', '/health');
  if (!health.ok) throw new Error('health failed');
  if (!health.sms || health.sms.provider !== 'dev' || health.sms.dev_sms !== true) {
    throw new Error('dev sms health status failed');
  }
  if (!health.model || health.model.provider !== 'fallback' || health.model.aliyun_configured !== false) {
    throw new Error('fallback model health status failed');
  }
  if (!health.model.implemented || health.model.implemented.websocket_live_session !== true || health.model.implemented.xiaozhi_protocol !== true || health.model.implemented.server_asr_streaming !== false) {
    throw new Error('live websocket health status failed');
  }
  if (!health.model.two_d_avatar || health.model.implemented.local_2d_avatar_view !== true || health.model.implemented.avatar_state_machine !== true || health.model.implemented.mouth_level_protocol !== true || health.model.implemented.avatar_speech_settle !== true || health.model.implemented.model_emotion_tags !== true || health.model.implemented.live2d_sdk !== false) {
    throw new Error('2d avatar health status failed');
  }
  if (!health.model.live || health.model.live.fallback_text_turns !== true || health.model.live.model_audio_output_streaming !== false || health.model.live.apk_low_latency_audio_playback !== true || health.model.live.apk_auto_barge_in_detection !== true || health.model.live.interrupt_response !== true || health.model.live.accepted_audio_format !== 'pcm16' || health.model.live.accepted_frame_duration_ms !== 30 || health.model.live.opus_audio_transport !== false || health.model.live.model_text_streaming !== false || !health.model.live.apk_barge_in_policy || health.model.live.apk_barge_in_policy.source !== 'adaptive_rms_vad' || health.model.live.apk_barge_in_policy.min_speech_ms !== 240) {
    throw new Error('live capability detail failed');
  }
  if (health.model.implemented.digital_human_stream !== false || health.model.live.digital_human_stream !== false || !health.model.linly_digital_human || health.model.linly_digital_human.configured !== false) {
    throw new Error('linly digital human default status failed');
  }
  process.env.LINLY_TALKER_STREAM_ENABLED = '1';
  process.env.LINLY_TALKER_STREAM_URL = 'http://127.0.0.1:8010/';
  process.env.LINLY_TALKER_STREAM_WEB_URL = 'http://127.0.0.1:3000/';
  const linlyHealth = await request(base, 'GET', '/health');
  if (linlyHealth.model.implemented.digital_human_stream !== true || linlyHealth.model.live.digital_human_stream !== true || linlyHealth.model.live.digital_human_session !== true || linlyHealth.model.linly_digital_human?.provider !== 'linly-talker-stream' || linlyHealth.model.linly_digital_human?.offer_endpoint !== 'http://127.0.0.1:8010/offer' || linlyHealth.model.linly_digital_human?.web_url !== 'http://127.0.0.1:3000') {
    throw new Error('linly digital human configured status failed');
  }
  process.env.LINLY_TALKER_STREAM_ENABLED = '';
  process.env.LINLY_TALKER_STREAM_URL = '';
  process.env.LINLY_TALKER_STREAM_WEB_URL = '';
  if (!health.companion || health.companion.bedtime_story?.implemented !== true || health.companion.music_playback?.implemented !== true || health.companion.music_playback?.source !== 'apk_local_audiotrack_generated_rain' || health.companion.music_playback?.volume_behavior !== 'fade_in_start_duck_during_sleep_check_restore_if_awake_fade_out_before_guard' || health.companion.news_briefing?.implemented !== false || health.companion.possible_asleep_confirm?.implemented !== true || health.companion.possible_asleep_confirm?.prompt !== '您睡了么？' || health.companion.possible_asleep_confirm?.awake_reply_behavior !== 'continue_companion_playback' || health.companion.possible_asleep_confirm?.no_reply_behavior !== 'enter_sleep_guard' || health.companion.voice_shortcuts?.implemented !== true || !health.companion.voice_shortcuts?.routes?.includes('music_playback')) {
    throw new Error('companion capability truth flags failed');
  }
  if (!health.security || health.security.admin_page_protected !== true || health.security.admin_api_token_configured !== true || health.security.server_secret_configured !== true) {
    throw new Error('security health status failed');
  }
  let adminPageDenied = false;
  try {
    await textRequest(base, '/admin');
  } catch (error) {
    adminPageDenied = String(error.message).startsWith('401');
  }
  if (!adminPageDenied) throw new Error('admin page basic auth guard failed');

  let adminPageWrongDenied = false;
  try {
    await textRequest(base, '/admin', { Authorization: basic('admin', 'wrong-password') });
  } catch (error) {
    adminPageWrongDenied = String(error.message).startsWith('401');
  }
  if (!adminPageWrongDenied) throw new Error('admin page wrong basic auth guard failed');

  const adminHtml = await textRequest(base, '/admin', { Authorization: basic('admin', 'test-page-password') });
  if (!adminHtml.includes('睡了么后台') || !adminHtml.includes('/api/admin/users') || !adminHtml.includes('/api/admin/model/probe') || !adminHtml.includes('/api/admin/audit-logs') || !adminHtml.includes('导出用户数据') || !adminHtml.includes('删除用户') || !adminHtml.includes('审计日志') || !adminHtml.includes('结构化记忆')) {
    throw new Error('admin html failed');
  }

  let probeDenied = false;
  try {
    await request(base, 'POST', '/api/admin/model/probe');
  } catch (error) {
    probeDenied = String(error.message).startsWith('403');
  }
  if (!probeDenied) throw new Error('admin model probe auth guard failed');

  const probe = await request(base, 'POST', '/api/admin/model/probe', undefined, 'test-admin-token');
  if (probe.probe_used !== false || probe.configured !== false || probe.provider !== 'fallback') {
    throw new Error('admin model probe fallback status failed');
  }

  const sent = await request(base, 'POST', '/api/auth/request-code', { phone: '13800138000' });
  if (!sent.dev_code) throw new Error('dev code missing');
  if (sent.sms_provider !== 'dev') throw new Error('dev sms provider flag missing');
  let otpLimited = false;
  try {
    await request(base, 'POST', '/api/auth/request-code', { phone: '13800138000' });
  } catch (error) {
    otpLimited = String(error.message).startsWith('429');
  }
  if (!otpLimited) throw new Error('otp rate limit failed');

  const verified = await request(base, 'POST', '/api/auth/verify', {
    phone: '13800138000',
    code: sent.dev_code,
    device_id: 'test-device'
  });
  const token = verified.token;
  if (!token) throw new Error('token missing');

  const avatarStatus = await request(base, 'GET', '/api/avatar/status', undefined, token);
  if (!avatarStatus.linly_digital_human || avatarStatus.linly_digital_human.configured !== false || avatarStatus.linly_digital_human.live !== false || avatarStatus.linly_digital_human.health?.error !== 'not_configured' || !avatarStatus.two_d_avatar?.local_2d_avatar_view) {
    throw new Error('avatar status fallback failed');
  }

  const fakeLinly = await startFakeLinlyServer();
  try {
    process.env.LINLY_TALKER_STREAM_ENABLED = '1';
    process.env.LINLY_TALKER_STREAM_URL = `http://127.0.0.1:${fakeLinly.port}`;
    process.env.LINLY_TALKER_STREAM_WEB_URL = `http://127.0.0.1:${fakeLinly.port}/`;
    const liveAvatarStatus = await request(base, 'GET', '/api/avatar/status', undefined, token);
    if (liveAvatarStatus.linly_digital_human?.live !== true || liveAvatarStatus.linly_digital_human?.health?.data?.service !== 'fake-linly' || liveAvatarStatus.linly_digital_human?.web_url !== `http://127.0.0.1:${fakeLinly.port}`) {
      throw new Error('avatar status linly health probe failed');
    }
    const avatarOffer = await request(base, 'POST', '/api/avatar/session/offer', { sdp: 'fake-offer', type: 'offer' }, token);
    if (avatarOffer.sessionid !== 42 || avatarOffer.type !== 'answer' || !String(avatarOffer.sdp || '').includes('fake-offer')) {
      throw new Error('avatar offer proxy failed');
    }
    const avatarSay = await request(base, 'POST', '/api/avatar/session/42/say', { text: '奶奶别急，我在这里。' }, token);
    if (avatarSay.response !== '奶奶别急，我在这里。' || fakeLinly.received.lastHuman?.type !== 'echo' || fakeLinly.received.lastHuman?.sessionid !== 42 || fakeLinly.received.lastHuman?.interrupt !== true) {
      throw new Error('avatar say proxy failed');
    }
    const avatarSpeaking = await request(base, 'GET', '/api/avatar/session/42/speaking', undefined, token);
    if (avatarSpeaking.data !== false) throw new Error('avatar speaking proxy failed');
    let linlyBusinessError = false;
    try {
      await request(base, 'POST', '/api/avatar/session/42/say', { text: 'force-linly-error' }, token);
    } catch (error) {
      linlyBusinessError = String(error.message || '').includes('502') && String(error.message || '').includes('forced failure');
    }
    if (!linlyBusinessError) throw new Error('avatar say linly business error handling failed');
    const avatarStop = await request(base, 'POST', '/api/avatar/session/42/stop', undefined, token);
    if (avatarStop.code !== 0 || fakeLinly.received.interrupts !== 1) throw new Error('avatar stop proxy failed');
    if (fakeLinly.received.offers !== 1 || fakeLinly.received.human !== 2 || fakeLinly.received.speaking !== 1) {
      throw new Error(`fake linly counts failed: ${JSON.stringify(fakeLinly.received)}`);
    }
  } finally {
    process.env.LINLY_TALKER_STREAM_ENABLED = '';
    process.env.LINLY_TALKER_STREAM_URL = '';
    process.env.LINLY_TALKER_STREAM_WEB_URL = '';
    fakeLinly.close();
  }

  await request(base, 'PUT', '/api/profile', {
    owner_address: '奶奶',
    health: '高血压，容易头晕',
    medication: '早上吃降压药',
    sleep: '夜里容易醒',
    family: '孩子住附近',
    hobbies: '散步、听戏',
    care_preference: '提醒轻一点'
  }, token);

  const insight = await request(base, 'POST', '/api/insights', {
    source: 'voice_chat',
    content: '有人让我转账，还要验证码'
  }, token);
  if (insight.category !== 'economy' || insight.severity < 4) throw new Error('insight classify failed');

  const chat = await request(base, 'POST', '/api/chat', {
    message: '我今天有点头晕，最近夜里总是醒，早上吃降压药，女儿住附近，我喜欢听戏，该怎么办？',
    system_prompt: '你是陪伴助手，不做诊断。'
  }, token);
  if (!chat.answer) throw new Error('chat answer missing');
  if (chat.model_used !== false) throw new Error('chat fallback model_used flag missing');
  if (chat.model_provider !== 'fallback') throw new Error('chat fallback provider flag missing');
  if (!Array.isArray(chat.memory_saved) || !chat.memory_saved.some(item => item.category === 'health') || !chat.memory_saved.some(item => item.category === 'medication') || !chat.memory_saved.some(item => item.category === 'sleep')) {
    throw new Error('chat memory extraction failed');
  }

  const vision = await request(base, 'POST', '/api/vision', {
    task: 'read_label',
    prompt: '帮我看看药瓶上的字',
    image_base64: 'dGVzdA==',
    system_prompt: '你是陪伴助手，不做诊断。'
  }, token);
  if (!vision.answer || !vision.answer.includes('阿里多模态视觉模型')) throw new Error('vision fallback failed');
  if (vision.vision_used !== false) throw new Error('vision fallback flag missing');
  if (vision.vision_provider !== 'fallback') throw new Error('vision fallback provider flag missing');

  const audio = await request(base, 'POST', '/api/audio', {
    task: 'sleep_audio',
    prompt: '帮我看看昨晚这段呼吸声有没有异常趋势',
    waveform_summary: '00:10-00:30 呼吸平稳，02:14 出现较大鼾声和体动',
    system_prompt: '你是陪伴助手，不做诊断。'
  }, token);
  if (!audio.answer || audio.audio_used !== false || audio.audio_provider !== 'fallback') {
    throw new Error('audio fallback failed');
  }

  const liveEvents = await liveWebSocketProbe(port, token);
  if (!liveEvents.some(item => item.type === 'hello' && item.capabilities && item.capabilities.xiaozhi_protocol === true)) {
    throw new Error('live websocket hello failed');
  }
  if (!liveEvents.some(item => item.type === 'hello' && item.audio_params && item.audio_params.format === 'pcm16' && item.audio_params.encoding === 'signed_16bit_little_endian' && item.audio_params.frame_duration === 30)) {
    throw new Error('live websocket pcm audio params failed');
  }
  if (!liveEvents.some(item => item.type === 'audio_received' && item.frames === 1 && item.audio_format === 'pcm16')) {
    throw new Error('live websocket audio frame ack failed');
  }
  if (!liveEvents.some(item => item.type === 'stt' && item.source === 'client_text' && String(item.text || '').includes('晚上总醒'))) {
    throw new Error('live websocket stt event failed');
  }
  if (!liveEvents.some(item => item.type === 'tts' && item.state === 'sentence_delta' && String(item.text || '').length > 0)) {
    throw new Error('live websocket streaming delta failed');
  }
  if (!liveEvents.some(item => item.type === 'tts' && item.model_provider === 'fallback' && String(item.text || '').length > 0)) {
    throw new Error('live websocket model tts failed');
  }
  if (!liveEvents.some(item => item.type === 'emotion' && item.source === 'model_reply_analysis' && typeof item.intensity === 'number' && item.gesture && item.safety_level && String(item.speech_text || '').length > 0)) {
    throw new Error('live websocket structured avatar emotion tag failed');
  }

  const fakeRealtime = await startFakeRealtimeServer();
  try {
    process.env.ALIYUN_REALTIME_ENABLED = '1';
    process.env.ALIYUN_REALTIME_API_KEY = 'test-realtime-api-key';
    process.env.ALIYUN_REALTIME_MODEL = 'fake-qwen-realtime';
    process.env.ALIYUN_REALTIME_ENDPOINT = `ws://127.0.0.1:${fakeRealtime.port}/api-ws/v1/realtime`;
    const realtimeHealth = await request(base, 'GET', '/health');
    if (!realtimeHealth.model.implemented.server_asr_streaming || !realtimeHealth.model.implemented.realtime_model_bridge || !realtimeHealth.model.implemented.model_audio_output_forwarding || !realtimeHealth.model.implemented.model_audio_output_streaming || !realtimeHealth.model.implemented.apk_auto_barge_in_detection || !realtimeHealth.model.implemented.interrupt_response) {
      throw new Error('realtime bridge health flags failed');
    }
    if (!realtimeHealth.model.live || realtimeHealth.model.live.realtime_model !== 'fake-qwen-realtime' || realtimeHealth.model.live.apk_low_latency_audio_playback !== true) {
      throw new Error('realtime bridge live detail failed');
    }
    const realtimeEvents = await liveWebSocketAudioProbe(port, token);
    if (!realtimeEvents.some(item => item.type === 'audio_received' && item.realtime_forwarded === true && item.server_asr_streaming === true)) {
      throw new Error('live realtime audio forward ack failed');
    }
    if (!realtimeEvents.some(item => item.type === 'stt' && item.source === 'aliyun_realtime' && String(item.text || '').includes('实时转写测试'))) {
      throw new Error('live realtime transcription bridge failed');
    }
    if (!realtimeEvents.some(item => item.type === 'tts' && item.model_provider === 'aliyun-dashscope-realtime' && item.model_audio_streaming === true)) {
      throw new Error('live realtime tts bridge failed');
    }
    if (!realtimeEvents.some(item => item.type === 'emotion' && item.source === 'model_reply_analysis' && typeof item.intensity === 'number' && item.gesture && item.safety_level)) {
      throw new Error('live realtime structured avatar emotion tag failed');
    }
    if (!realtimeEvents.some(item => item.type === 'binary_audio' && item.bytes > 0)) {
      throw new Error('live realtime binary audio forward failed');
    }
    if (!realtimeEvents.some(item => item.type === 'event' && item.name === 'realtime_bridge_abort' && item.realtime_aborted === true)) {
      throw new Error('live realtime abort bridge event failed');
    }
    if (!realtimeEvents.some(item => item.type === 'tts' && item.state === 'interrupted' && item.realtime_aborted === true)) {
      throw new Error('live realtime interrupted tts failed');
    }
    if (fakeRealtime.received.updates < 1 || fakeRealtime.received.audioAppends < 1 || fakeRealtime.received.cancels < 1 || fakeRealtime.received.clears < 1 || !fakeRealtime.received.auth.includes('Bearer test-realtime-api-key')) {
      throw new Error(`fake realtime backend did not receive session update, audio append, cancel, clear, or auth header: ${JSON.stringify(fakeRealtime.received)}`);
    }
  } finally {
    process.env.ALIYUN_REALTIME_ENABLED = '';
    process.env.ALIYUN_REALTIME_API_KEY = '';
    process.env.ALIYUN_REALTIME_MODEL = '';
    process.env.ALIYUN_REALTIME_ENDPOINT = '';
    fakeRealtime.close();
  }

  const care = await request(base, 'POST', '/api/care/brief', {
    type: 'hydration',
    context: {
      owner_address: '奶奶',
      assistant_role: '贴心小妹',
      hydration_elapsed_minutes: 135,
      owner_profile: '高血压，早上吃降压药，夜里容易醒'
    },
    create_message: true
  }, token);
  if (!care.body || care.type !== 'hydration' || care.model_used !== false || care.model_provider !== 'fallback' || care.message_id <= 0) {
    throw new Error('care brief fallback failed');
  }
  const carePending = await request(base, 'GET', '/api/messages/pending', undefined, token);
  if (!carePending.messages.some(item => item.id === care.message_id && item.body === care.body)) {
    throw new Error('care brief message queue failed');
  }
  await request(base, 'POST', `/api/messages/${care.message_id}/read`, undefined, token);

  const wellness = await request(base, 'POST', '/api/care/brief', {
    type: 'wellness_tip',
    context: {
      owner_address: '奶奶',
      assistant_role: '温柔姐姐',
      owner_profile: '高血压，早上吃降压药，夜里容易醒',
      wellness_topic: '根据主人身体状况自动整理养生小妙招和食补食疗方法'
    },
    create_message: true
  }, token);
  if (!wellness.body || wellness.type !== 'wellness_tip' || wellness.model_used !== false || wellness.model_provider !== 'fallback' || wellness.message_id <= 0 || !wellness.title.includes('养生')) {
    throw new Error('wellness care brief fallback failed');
  }

  const newsMissing = await request(base, 'GET', '/api/news/brief', undefined, token);
  if (newsMissing.configured !== false || newsMissing.provider !== 'none' || !String(newsMissing.body || '').includes('新闻源未接入')) {
    throw new Error('news missing source truth gate failed');
  }
  const rssServer = createHttpServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/rss+xml; charset=utf-8' });
    res.end(`<?xml version="1.0" encoding="UTF-8" ?>
<rss version="2.0">
  <channel>
    <title>测试新闻源</title>
    <item>
      <title>社区夜间照护服务开通</title>
      <link>https://example.test/care</link>
      <pubDate>Sat, 23 May 2026 08:00:00 GMT</pubDate>
      <description><![CDATA[社区提供夜间照护咨询。]]></description>
    </item>
  </channel>
</rss>`);
  });
  const rssPort = await listen(rssServer);
  process.env.GOUXIONG_NEWS_RSS_URL = `http://127.0.0.1:${rssPort}/news.xml`;
  const newsConfigured = await request(base, 'GET', '/api/news/brief', undefined, token);
  rssServer.close();
  process.env.GOUXIONG_NEWS_RSS_URL = '';
  if (newsConfigured.configured !== true || newsConfigured.provider !== 'rss' || newsConfigured.source_title !== '测试新闻源' || !newsConfigured.items?.some(item => item.title === '社区夜间照护服务开通') || !String(newsConfigured.body || '').includes('来自 测试新闻源')) {
    throw new Error('news configured rss source failed');
  }
  await request(base, 'POST', `/api/messages/${wellness.message_id}/read`, undefined, token);

  const users = await request(base, 'GET', '/api/admin/users', undefined, 'test-admin-token');
  if (!users.users.some(item => item.id === verified.user_id)) throw new Error('admin users failed');
  if (!users.users.some(item => item.id === verified.user_id && item.memory_count >= 3)) throw new Error('admin memory count failed');

  const detail = await request(base, 'GET', `/api/admin/users/${verified.user_id}`, undefined, 'test-admin-token');
  if (!detail.profile || !detail.insights.length) throw new Error('admin user detail failed');
  if (!detail.memories || detail.memories.length < 3) throw new Error('admin user memories failed');

  const msg = await request(base, 'POST', `/api/admin/users/${verified.user_id}/messages`, {
    title: '吃药提醒',
    body: '奶奶，记得按医嘱吃早上的药。',
    priority: 3
  }, 'test-admin-token');
  const pending = await request(base, 'GET', '/api/messages/pending', undefined, token);
  if (!pending.messages.some(item => item.id === msg.id)) throw new Error('message pending failed');
  await request(base, 'POST', `/api/messages/${msg.id}/read`, undefined, token);

  const ownExport = await request(base, 'GET', '/api/me/export', undefined, token);
  if (ownExport.user.id !== verified.user_id || !ownExport.profile || ownExport.memories.length < 3 || ownExport.insights.length < 3 || ownExport.chat_messages.length < 2 || ownExport.push_messages.length < 1) {
    throw new Error('user export failed');
  }
  if (JSON.stringify(ownExport).includes('token_hash') || JSON.stringify(ownExport).includes('code_hash')) {
    throw new Error('export leaked auth internals');
  }

  const adminExport = await request(base, 'GET', `/api/admin/users/${verified.user_id}/export`, undefined, 'test-admin-token');
  if (adminExport.user.id !== verified.user_id || adminExport.chat_messages.length < 2) {
    throw new Error('admin export failed');
  }
  const auditAfterExport = await request(base, 'GET', '/api/admin/audit-logs', undefined, 'test-admin-token');
  const exportedActions = auditAfterExport.logs.map(item => item.action);
  for (const action of ['admin.model_probe', 'admin.user_detail', 'admin.message_create', 'user.export_self', 'admin.user_export']) {
    if (!exportedActions.includes(action)) throw new Error(`audit action missing: ${action}`);
  }
  if (JSON.stringify(auditAfterExport).includes('test-admin-token') || JSON.stringify(auditAfterExport).includes(sent.dev_code)) {
    throw new Error('audit leaked secret material');
  }

  await request(base, 'DELETE', '/api/me', undefined, token);
  let deletedTokenRejected = false;
  try {
    await request(base, 'GET', '/api/me', undefined, token);
  } catch (error) {
    deletedTokenRejected = String(error.message).startsWith('401');
  }
  if (!deletedTokenRejected) throw new Error('deleted user token still works');
  const usersAfterSelfDelete = await request(base, 'GET', '/api/admin/users', undefined, 'test-admin-token');
  if (usersAfterSelfDelete.users.some(item => item.id === verified.user_id)) {
    throw new Error('self delete did not remove admin list user');
  }

  const sent2 = await request(base, 'POST', '/api/auth/request-code', { phone: '13900139000' });
  const verified2 = await request(base, 'POST', '/api/auth/verify', {
    phone: '13900139000',
    code: sent2.dev_code,
    device_id: 'test-device-2'
  });
  await request(base, 'DELETE', `/api/admin/users/${verified2.user_id}`, undefined, 'test-admin-token');
  let adminDeletedTokenRejected = false;
  try {
    await request(base, 'GET', '/api/me', undefined, verified2.token);
  } catch (error) {
    adminDeletedTokenRejected = String(error.message).startsWith('401');
  }
  if (!adminDeletedTokenRejected) throw new Error('admin-deleted user token still works');
  const finalAudit = await request(base, 'GET', '/api/admin/audit-logs', undefined, 'test-admin-token');
  const finalActions = finalAudit.logs.map(item => item.action);
  if (!finalActions.includes('user.delete_self') || !finalActions.includes('admin.user_delete')) {
    throw new Error('delete audit actions missing');
  }

  console.log('server tests passed');
} finally {
  server.closeAllConnections?.();
  await new Promise(resolve => server.close(resolve));
}
