import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const tmp = mkdtempSync(join(tmpdir(), 'gouxiong-model-smoke-'));
process.env.GOUXIONG_DB_PATH = join(tmp, 'smoke.sqlite3');
process.env.GOUXIONG_DEV_SMS = '1';
process.env.GOUXIONG_ADMIN_TOKEN = process.env.GOUXIONG_ADMIN_TOKEN || 'model-smoke-admin-token';

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
  if (!response.ok || data.ok === false) {
    throw new Error(`${method} ${path} failed: ${response.status} ${data.error || JSON.stringify(data)}`);
  }
  return data;
}

const png32BlueDot = [
  'iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAAAXNSR0IArs4c6QAA',
  'AARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAABWSURBVFhH7dYx',
  'DQAgDANB9l+5mQwQJGBiE7hJZKltw8EBAAB4Eqc8f+U5GgAAwPPYAgAA2AIAANgC',
  'AADYAgAA2AIAANgCAADYAgAA2AIAANgCAADYAgAA2AIAANgCwLUEH1JCV6QAAAAA',
  'SUVORK5CYII='
].join('');

function makeToneWavBase64() {
  const sampleRate = 16000;
  const durationSeconds = 1.2;
  const samples = Math.floor(sampleRate * durationSeconds);
  const dataBytes = samples * 2;
  const buffer = Buffer.alloc(44 + dataBytes);
  buffer.write('RIFF', 0);
  buffer.writeUInt32LE(36 + dataBytes, 4);
  buffer.write('WAVE', 8);
  buffer.write('fmt ', 12);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20);
  buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(sampleRate, 24);
  buffer.writeUInt32LE(sampleRate * 2, 28);
  buffer.writeUInt16LE(2, 32);
  buffer.writeUInt16LE(16, 34);
  buffer.write('data', 36);
  buffer.writeUInt32LE(dataBytes, 40);
  for (let i = 0; i < samples; i++) {
    const t = i / sampleRate;
    const envelope = i < sampleRate * 0.15 || i > samples - sampleRate * 0.15 ? 0.4 : 1;
    const value = Math.round(Math.sin(2 * Math.PI * 440 * t) * 32767 * 0.18 * envelope);
    buffer.writeInt16LE(value, 44 + i * 2);
  }
  return buffer.toString('base64');
}

const server = createServer();
const port = await listen(server);
const base = `http://127.0.0.1:${port}`;

try {
  const health = await request(base, 'GET', '/health');
  if (!health.model?.aliyun_configured) {
    console.log(JSON.stringify({
      ok: true,
      skipped: true,
      reason: 'DASHSCOPE_API_KEY / ALIYUN_MODEL_API_KEY / ALIYUN_BAILIAN_API_KEY is not configured'
    }));
    process.exitCode = 0;
  } else {
    const sent = await request(base, 'POST', '/api/auth/request-code', { phone: '13800138166' });
    const verified = await request(base, 'POST', '/api/auth/verify', {
      phone: '13800138166',
      code: sent.dev_code,
      device_id: 'model-smoke'
    });
    const token = verified.token;

    await request(base, 'PUT', '/api/profile', {
      owner_address: '奶奶',
      assistant_name: '温柔姐姐',
      assistant_identity: '贴心家人',
      health: '高血压，睡醒有时头晕',
      medication: '早上按医嘱吃降压药',
      sleep: '夜里偶尔憋醒和打鼾',
      family: '女儿住附近',
      hobbies: '散步、听戏',
      care_preference: '少文字，直接说重点'
    }, token);

    const chat = await request(base, 'POST', '/api/chat', {
      message: '我昨晚醒了两次，今天有点困，你用一句话安慰我一下。'
    }, token);
    const vision = await request(base, 'POST', '/api/vision', {
      task: 'read_label',
      prompt: '这是一张测试图。请只用一句话说明你看到了什么。',
      image_base64: png32BlueDot
    }, token);
    const audio = await request(base, 'POST', '/api/audio', {
      task: 'sleep_audio',
      prompt: '这是一段用于接口验收的短 WAV 声音片段。请结合声波摘要给一句生活提醒，不做诊断。',
      waveform_summary: '00:00-00:20 呼吸平稳；02:14 出现较大鼾声和翻身；没有持续静默。',
      audio_data: makeToneWavBase64(),
      audio_format: 'wav'
    }, token);
    const care = await request(base, 'POST', '/api/care/brief', {
      type: 'morning',
      context: {
        owner_address: '奶奶',
        assistant_role: '温柔姐姐',
        sleep_summary: '昨晚醒了两次，疑似打鼾一次，没有持续静默。',
        owner_profile: '高血压，早上按医嘱吃降压药，喜欢听戏。'
      },
      create_message: true
    }, token);

    if (!chat.model_used || chat.model_provider !== 'aliyun-dashscope') throw new Error('chat did not use aliyun model');
    if (!vision.vision_used || vision.vision_provider !== 'aliyun-dashscope') throw new Error('vision did not use aliyun model');
    if (!audio.audio_used || audio.audio_provider !== 'aliyun-dashscope') throw new Error('audio did not use aliyun model');
    if (!care.model_used || care.model_provider !== 'aliyun-dashscope' || care.message_id <= 0) throw new Error('care brief did not use aliyun model or queue message');

    console.log(JSON.stringify({
      ok: true,
      chat: { provider: chat.model_provider, model: chat.model_name, answer_len: String(chat.answer || '').length },
      vision: { provider: vision.vision_provider, model: vision.vision_model_name, answer_len: String(vision.answer || '').length },
      audio: { provider: audio.audio_provider, model: audio.audio_model_name, with_wav_clip: true, answer_len: String(audio.answer || '').length },
      care: { provider: care.model_provider, model: care.model_name, queued: care.message_id > 0, answer_len: String(care.body || '').length }
    }));
  }
} finally {
  await new Promise(resolve => server.close(resolve));
}
