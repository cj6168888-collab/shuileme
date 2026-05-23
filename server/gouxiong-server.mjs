import http from 'node:http';
import https from 'node:https';
import { DatabaseSync } from 'node:sqlite';
import { createHmac, createHash, randomInt, randomBytes, timingSafeEqual } from 'node:crypto';
import { mkdirSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const APP_NAME = '睡了么';
const OTP_TTL_SECONDS = 10 * 60;
const TOKEN_TTL_SECONDS = 90 * 24 * 60 * 60;
const LIVE_PCM_FRAME_DURATION_MS = 30;
const LIVE_BARGE_IN_MIN_SPEECH_MS = 240;
const LIVE_BARGE_IN_RMS_THRESHOLD = 0.04;

function env(name, fallback = '') {
  return process.env[name] && process.env[name].length > 0 ? process.env[name] : fallback;
}

function loadEnvFile(path) {
  if (!path || !existsSync(path)) return;
  const raw = readFileSync(path, 'utf8');
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#') || !trimmed.includes('=')) continue;
    const index = trimmed.indexOf('=');
    const name = trimmed.slice(0, index).trim();
    let value = trimmed.slice(index + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (name && process.env[name] === undefined) {
      process.env[name] = value;
    }
  }
}

loadEnvFile(join(__dirname, '.env'));
loadEnvFile(process.env.GOUXIONG_ENV_FILE || '');
loadEnvFile(process.env.GOUXIONG_ALIYUN_SMS_ENV_FILE || '');
loadEnvFile(process.env.GOUXIONG_ALIYUN_MODEL_ENV_FILE || '');
loadEnvFile(join(process.env.USERPROFILE || '', 'Desktop', 'aliyun-sms.env'));
loadEnvFile(join(process.env.USERPROFILE || '', 'Desktop', 'guanlin-aliyun-ai.env'));

function now() {
  return Math.floor(Date.now() / 1000);
}

function dataDir() {
  const dir = env('GOUXIONG_DATA_DIR', join(__dirname, 'data'));
  mkdirSync(dir, { recursive: true });
  return dir;
}

function dbPath() {
  return env('GOUXIONG_DB_PATH', join(dataDir(), 'gouxiong.sqlite3'));
}

function serverSecret() {
  const configured = env('GOUXIONG_SERVER_SECRET', '');
  if (configured) return Buffer.from(configured);
  const path = join(dataDir(), '.server-secret');
  if (existsSync(path)) return readFileSync(path);
  const generated = randomBytes(48).toString('base64url');
  writeFileSync(path, generated);
  return Buffer.from(generated);
}

const secret = serverSecret();
const db = new DatabaseSync(dbPath());
db.exec('PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;');
db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  phone TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  last_seen_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS otp_codes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  phone TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  consumed_at INTEGER,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,
  device_id TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS health_profiles (
  user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  assistant_name TEXT DEFAULT '',
  assistant_identity TEXT DEFAULT '',
  owner_address TEXT DEFAULT '',
  health TEXT DEFAULT '',
  medication TEXT DEFAULT '',
  sleep TEXT DEFAULT '',
  family TEXT DEFAULT '',
  hobbies TEXT DEFAULT '',
  care_preference TEXT DEFAULT '',
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS insights (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category TEXT NOT NULL,
  source TEXT NOT NULL,
  severity INTEGER NOT NULL DEFAULT 1,
  content TEXT NOT NULL,
  metadata_json TEXT NOT NULL DEFAULT '{}',
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS memory_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category TEXT NOT NULL,
  memory_key TEXT NOT NULL,
  label TEXT NOT NULL,
  value TEXT NOT NULL,
  evidence TEXT DEFAULT '',
  source TEXT NOT NULL DEFAULT 'chat',
  severity INTEGER NOT NULL DEFAULT 1,
  confidence REAL NOT NULL DEFAULT 0.6,
  mentions INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS chat_messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS push_messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  priority INTEGER NOT NULL DEFAULT 1,
  read_at INTEGER,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS rate_limits (
  key TEXT NOT NULL,
  window_seconds INTEGER NOT NULL,
  count INTEGER NOT NULL,
  reset_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (key, window_seconds)
);
CREATE TABLE IF NOT EXISTS audit_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  actor_type TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  action TEXT NOT NULL,
  target_user_id INTEGER,
  ip TEXT DEFAULT '',
  user_agent TEXT DEFAULT '',
  metadata_json TEXT NOT NULL DEFAULT '{}',
  created_at INTEGER NOT NULL
);
`);
db.exec('CREATE UNIQUE INDEX IF NOT EXISTS idx_memory_items_unique ON memory_items(user_id, category, memory_key);');
db.exec('CREATE INDEX IF NOT EXISTS idx_memory_items_user_updated ON memory_items(user_id, updated_at DESC);');

function sign(text) {
  return createHmac('sha256', secret).update(text).digest('hex');
}

function sha256(text) {
  return createHash('sha256').update(text).digest('hex');
}

function cleanPhone(input) {
  const raw = String(input || '').trim();
  let out = '';
  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i];
    if (/[0-9]/.test(ch)) out += ch;
    else if (ch === '+' && i === 0) out += ch;
  }
  const digits = out.startsWith('+') ? out.length - 1 : out.length;
  if (digits < 6 || digits > 20) throw statusError(400, '手机号格式不正确');
  return out;
}

function codeHash(phone, code) {
  return sign(`${phone}:${String(code).trim()}`);
}

function timingEqual(a, b) {
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
}

function makeToken(userId, phone, deviceId) {
  const payload = Buffer.from(JSON.stringify({
    uid: userId,
    phone,
    device: deviceId || '',
    exp: now() + TOKEN_TTL_SECONDS,
    nonce: randomBytes(12).toString('base64url')
  })).toString('base64url');
  return `${payload}.${sign(payload)}`;
}

function verifyToken(header) {
  const token = String(header || '').toLowerCase().startsWith('bearer ')
    ? String(header).slice(7).trim()
    : '';
  if (!token || !token.includes('.')) throw statusError(401, '请先登录');
  const [payload, supplied] = token.split('.');
  if (!timingEqual(sign(payload), supplied)) throw statusError(401, '登录已失效');
  const decoded = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
  if (Number(decoded.exp || 0) < now()) throw statusError(401, '登录已过期');
  const row = db.prepare('SELECT user_id, expires_at FROM sessions WHERE token_hash = ?').get(sha256(token));
  if (!row || Number(row.expires_at) < now()) throw statusError(401, '登录已失效');
  db.prepare('UPDATE users SET last_seen_at = ? WHERE id = ?').run(now(), row.user_id);
  return Number(row.user_id);
}

function verifyAdminToken(header) {
  const expected = env('GOUXIONG_ADMIN_TOKEN', '');
  if (!expected) throw statusError(403, '未配置管理令牌');
  const token = String(header || '').toLowerCase().startsWith('bearer ')
    ? String(header).slice(7).trim()
    : '';
  if (!token || !timingEqual(sign(token), sign(expected))) throw statusError(403, '管理令牌不正确');
  return `admin:${sha256(token).slice(0, 16)}`;
}

function adminPagePassword() {
  return env('GOUXIONG_ADMIN_BASIC_PASSWORD', env('GOUXIONG_ADMIN_PAGE_PASSWORD', ''));
}

function verifyAdminPageAccess(req) {
  const password = adminPagePassword();
  if (!password) return true;
  const expectedUser = env('GOUXIONG_ADMIN_BASIC_USER', 'admin');
  const header = String(req.headers.authorization || '');
  if (!header.toLowerCase().startsWith('basic ')) return false;
  let decoded = '';
  try {
    decoded = Buffer.from(header.slice(6).trim(), 'base64').toString('utf8');
  } catch (error) {
    return false;
  }
  const splitAt = decoded.indexOf(':');
  if (splitAt <= 0) return false;
  const suppliedUser = decoded.slice(0, splitAt);
  const suppliedPassword = decoded.slice(splitAt + 1);
  return timingEqual(sign(suppliedUser), sign(expectedUser)) && timingEqual(sign(suppliedPassword), sign(password));
}

function sendAdminPageAuthRequired(res) {
  const body = Buffer.from('睡了么后台需要登录');
  res.writeHead(401, {
    'Content-Type': 'text/plain; charset=utf-8',
    'Content-Length': body.length,
    'WWW-Authenticate': 'Basic realm="GouXiong Admin", charset="UTF-8"',
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

function isStrongConfigured(value, placeholder = '') {
  const clean = String(value || '').trim();
  return clean.length >= 16 && clean !== placeholder && !/^change-me/i.test(clean);
}

function securityStatus() {
  return {
    admin_page_protected: Boolean(adminPagePassword()),
    admin_api_token_configured: isStrongConfigured(env('GOUXIONG_ADMIN_TOKEN', ''), 'change-me-admin-token'),
    server_secret_configured: isStrongConfigured(env('GOUXIONG_SERVER_SECRET', ''), 'change-me-to-a-long-random-secret'),
    otp_rate_limit_enabled: true,
    audit_logs_enabled: true
  };
}

function statusError(status, message) {
  const error = new Error(message);
  error.status = status;
  return error;
}

function smsStatus() {
  const aliyunReady = aliyunSmsConfigured();
  const devDefault = aliyunReady ? '0' : '1';
  const dev = env('GOUXIONG_DEV_SMS', devDefault) === '1';
  return {
    provider: dev ? 'dev' : (aliyunReady ? 'aliyun' : 'missing'),
    app_id: env('GOUXIONG_APP_ID', 'gouxiong-sleep'),
    aliyun_configured: aliyunReady,
    dev_sms: dev
  };
}

function aliyunSmsConfigured() {
  return ['ALIYUN_SMS_ACCESS_KEY_ID', 'ALIYUN_SMS_ACCESS_KEY_SECRET', 'ALIYUN_SMS_SIGN_NAME', 'ALIYUN_SMS_TEMPLATE_CODE']
    .every(name => env(name, '').length > 0);
}

async function sendSmsCode(phone, code) {
  const status = smsStatus();
  if (status.dev_sms) {
    console.log(`[dev-sms] ${APP_NAME} code for ${phone}: ${code}`);
    return { sms_provider: 'dev', dev_code: code };
  }
  if (!status.aliyun_configured) {
    throw statusError(500, '短信服务未配置');
  }
  await sendAliyunSmsCode(phone, code);
  return { sms_provider: 'aliyun' };
}

async function sendAliyunSmsCode(phone, code) {
  const params = {
    AccessKeyId: env('ALIYUN_SMS_ACCESS_KEY_ID'),
    Action: 'SendSms',
    Format: 'JSON',
    PhoneNumbers: phone,
    RegionId: env('ALIYUN_SMS_REGION_ID', 'cn-hangzhou'),
    SignName: env('ALIYUN_SMS_SIGN_NAME'),
    SignatureMethod: 'HMAC-SHA1',
    SignatureNonce: randomBytes(16).toString('hex'),
    SignatureVersion: '1.0',
    TemplateCode: env('ALIYUN_SMS_TEMPLATE_CODE'),
    TemplateParam: JSON.stringify({ [env('ALIYUN_SMS_TEMPLATE_PARAM_KEY', 'code')]: code }),
    Timestamp: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
    Version: '2017-05-25'
  };
  const signature = aliyunSignature(params, env('ALIYUN_SMS_ACCESS_KEY_SECRET'));
  const query = canonicalQuery({ ...params, Signature: signature });
  const data = await httpsJson(`https://dysmsapi.aliyuncs.com/?${query}`);
  if (data.Code !== 'OK') {
    const codeName = data.Code || 'Unknown';
    console.warn(`[aliyun-sms] failed for ${maskPhone(phone)}: ${codeName}`);
    throw statusError(502, `短信服务发送失败：${codeName}`);
  }
  console.log(`[aliyun-sms] verification code sent to ${maskPhone(phone)} request=${data.RequestId || ''}`);
}

function aliyunSignature(params, secret) {
  const canonical = canonicalQuery(params);
  const stringToSign = `GET&%2F&${percentEncode(canonical)}`;
  return createHmac('sha1', `${secret}&`).update(stringToSign).digest('base64');
}

function canonicalQuery(params) {
  return Object.keys(params)
    .sort()
    .map(key => `${percentEncode(key)}=${percentEncode(params[key])}`)
    .join('&');
}

function percentEncode(value) {
  return encodeURIComponent(String(value))
    .replace(/!/g, '%21')
    .replace(/'/g, '%27')
    .replace(/\(/g, '%28')
    .replace(/\)/g, '%29')
    .replace(/\*/g, '%2A');
}

function httpsJson(url) {
  return new Promise((resolve, reject) => {
    const req = https.get(url, { timeout: 12000 }, response => {
      let raw = '';
      response.setEncoding('utf8');
      response.on('data', chunk => { raw += chunk; });
      response.on('end', () => {
        try {
          const data = JSON.parse(raw || '{}');
          if (response.statusCode < 200 || response.statusCode >= 300) {
            const err = statusError(502, `短信服务 HTTP ${response.statusCode}`);
            err.providerPayload = data;
            reject(err);
            return;
          }
          resolve(data);
        } catch (error) {
          reject(statusError(502, '短信服务响应解析失败'));
        }
      });
    });
    req.on('timeout', () => {
      req.destroy(statusError(504, '短信服务请求超时'));
    });
    req.on('error', reject);
  });
}

function maskPhone(phone) {
  const value = String(phone || '');
  if (value.length <= 7) return '***';
  return `${value.slice(0, 3)}****${value.slice(-4)}`;
}

function firstEnv(names, fallback = '') {
  for (const name of names) {
    const value = env(name, '');
    if (value) return value;
  }
  return fallback;
}

function aliyunModelConfig() {
  const apiKey = firstEnv(['DASHSCOPE_API_KEY', 'ALIYUN_MODEL_API_KEY', 'ALIYUN_BAILIAN_API_KEY']);
  return {
    apiKey,
    endpoint: env('ALIYUN_MODEL_ENDPOINT', 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions'),
    textModel: firstEnv(['ALIYUN_TEXT_MODEL', 'DASHSCOPE_TEXT_MODEL', 'QWEN_TEXT_MODEL'], 'qwen-plus'),
    visionModel: firstEnv(['ALIYUN_VISION_MODEL', 'DASHSCOPE_VISION_MODEL', 'QWEN_VISION_MODEL', 'GUANLIN_VISUAL_DIAGNOSIS_MODEL'], 'qwen3-vl-plus'),
    audioModel: firstEnv(['ALIYUN_AUDIO_MODEL', 'DASHSCOPE_AUDIO_MODEL', 'QWEN_AUDIO_MODEL'], 'qwen3-omni-flash'),
    avatarAppId: env('ALIYUN_AVATAR_APP_ID', ''),
    avatarEndpoint: env('ALIYUN_AVATAR_ENDPOINT', '')
  };
}

function aliyunModelConfigured() {
  if (modelDisabled()) return false;
  return aliyunModelConfig().apiKey.length > 0;
}

function booleanEnv(name, fallback = false) {
  const value = env(name, '');
  if (!value) return fallback;
  return ['1', 'true', 'yes', 'on'].includes(value.trim().toLowerCase());
}

function modelDisabled() {
  return booleanEnv('GOUXIONG_DISABLE_MODEL', false);
}

function boundedNumberEnv(name, fallback, min, max) {
  const value = Number(env(name, ''));
  if (!Number.isFinite(value)) return fallback;
  if (Number.isFinite(min) && value < min) return min;
  if (Number.isFinite(max) && value > max) return max;
  return value;
}

function realtimeEndpointUrl(base, model) {
  const raw = String(base || '').trim() || 'wss://dashscope.aliyuncs.com/api-ws/v1/realtime';
  try {
    const url = new URL(raw);
    if (!url.searchParams.has('model')) url.searchParams.set('model', model);
    return url.toString();
  } catch (error) {
    return raw;
  }
}

function aliyunRealtimeConfig() {
  const enabled = booleanEnv('ALIYUN_REALTIME_ENABLED', false);
  const apiKey = firstEnv(
    ['ALIYUN_REALTIME_API_KEY', 'DASHSCOPE_REALTIME_API_KEY', 'QWEN_REALTIME_API_KEY'],
    enabled ? aliyunModelConfig().apiKey : ''
  );
  const model = firstEnv(['ALIYUN_REALTIME_MODEL', 'DASHSCOPE_REALTIME_MODEL', 'QWEN_REALTIME_MODEL'], 'qwen3-omni-flash-realtime');
  return {
    enabled,
    apiKey,
    model,
    endpoint: realtimeEndpointUrl(env('ALIYUN_REALTIME_ENDPOINT', 'wss://dashscope.aliyuncs.com/api-ws/v1/realtime'), model),
    voice: env('ALIYUN_REALTIME_VOICE', 'Cherry'),
    turnDetection: env('ALIYUN_REALTIME_TURN_DETECTION', 'server_vad'),
    vadThreshold: boundedNumberEnv('ALIYUN_REALTIME_VAD_THRESHOLD', 0.5, -1.0, 1.0),
    silenceDurationMs: Math.round(boundedNumberEnv('ALIYUN_REALTIME_SILENCE_MS', 800, 200, 6000))
  };
}

function aliyunRealtimeConfigured() {
  const config = aliyunRealtimeConfig();
  return Boolean(config.enabled && config.apiKey && config.endpoint);
}

function deepSeekConfigured() {
  if (modelDisabled()) return false;
  return env('DEEPSEEK_API_KEY', '').length > 0;
}

function avatarConfigured() {
  const config = aliyunModelConfig();
  return Boolean(config.avatarAppId && config.avatarEndpoint);
}

function twoDAvatarStatus() {
  return {
    local_2d_avatar_view: true,
    avatar_state_machine: true,
    avatar_command_protocol: true,
    mouth_level_protocol: true,
    pcm_energy_mouth_driver: true,
    tts_timed_mouth_driver: true,
    avatar_speech_settle: true,
    emotion_label_protocol: true,
    model_emotion_tags: true,
    live2d_sdk: false,
    states: [
      'idle',
      'listening',
      'user_speaking',
      'thinking',
      'speaking',
      'interrupted',
      'seeing',
      'reading',
      'finding',
      'comforting',
      'happy',
      'worried',
      'urgent_wakeup'
    ],
    note: 'APK uses local layered 2D AvatarView now. Live replies emit structured avatar emotion events and settle back to listening/comforting states after speech; Live2D SDK is not yet connected.'
  };
}

function liveSessionStatus() {
  const realtime = aliyunRealtimeConfig();
  const realtimeReady = aliyunRealtimeConfigured();
  const apkPlayback = true;
  return {
    websocket_session: true,
    xiaozhi_protocol: true,
    fallback_text_turns: true,
    audio_frame_accept: true,
    accepted_audio_format: 'pcm16',
    accepted_audio_encoding: 'signed_16bit_little_endian',
    accepted_audio_container: 'raw',
    accepted_sample_rate: 16000,
    accepted_frame_duration_ms: LIVE_PCM_FRAME_DURATION_MS,
    opus_audio_transport: false,
    interrupt_response: true,
    apk_barge_in_policy: {
      source: 'adaptive_rms_vad',
      frame_duration_ms: LIVE_PCM_FRAME_DURATION_MS,
      min_speech_ms: LIVE_BARGE_IN_MIN_SPEECH_MS,
      min_rms_threshold: LIVE_BARGE_IN_RMS_THRESHOLD,
      adaptive_noise_floor: true,
      echo_control: 'android_voice_communication_aec_ns_agc'
    },
    model_text_streaming: aliyunModelConfigured() || deepSeekConfigured(),
    realtime_model_bridge: true,
    realtime_configured: realtimeReady,
    realtime_model: realtimeReady ? realtime.model : '',
    server_asr_streaming: realtimeReady,
    model_audio_output_streaming: realtimeReady && apkPlayback,
    model_audio_output_forwarding: realtimeReady,
    apk_low_latency_audio_playback: apkPlayback,
    apk_auto_barge_in_detection: true,
    digital_human_session: avatarConfigured()
  };
}

function companionCapabilityStatus() {
  const newsReady = newsConfigured();
  return {
    bedtime_story: {
      implemented: true,
      source: aliyunModelConfigured() || deepSeekConfigured() ? 'model' : 'local_fallback',
      fallback_visible: true,
      note: 'APK can tell a gentle local bedtime story when model service is unavailable.'
    },
    music_playback: {
      implemented: true,
      source: 'apk_local_audiotrack_generated_rain',
      fallback_visible: true,
      volume_behavior: 'fade_in_start_duck_during_sleep_check_restore_if_awake_fade_out_before_guard',
      note: 'APK can play locally generated gentle rain / white-noise audio. It fades in, ducks while asking whether the user is asleep, restores if the user is awake, and fades out before sleep guard. Licensed music platforms are not connected yet.'
    },
    news_briefing: {
      implemented: newsReady,
      source: newsReady ? 'rss' : '',
      fallback_visible: true,
      note: newsReady
        ? 'Server can fetch configured RSS news source and return source/title/time summaries.'
        : 'No verified news source is connected yet; model must not invent current news.'
    },
    possible_asleep_confirm: {
      implemented: true,
      source: 'apk_speech_timeout_and_user_reply',
      fallback_visible: true,
      prompt: '您睡了么？',
      awake_reply_behavior: 'continue_companion_playback',
      no_reply_behavior: 'enter_sleep_guard',
      note: 'APK asks gently before entering sleep guard. Silence or sleep-like replies enter guard; awake replies continue companion playback.'
    },
    voice_shortcuts: {
      implemented: true,
      source: 'apk_text_and_speech_intent_classifier',
      fallback_visible: true,
      routes: ['bedtime_story', 'music_playback', 'news_briefing'],
      note: 'APK routes natural text/speech requests such as story, news, rain sound, and stop sound to the real companion capability handlers instead of generic chat fallback.'
    }
  };
}

function newsConfigured() {
  return /^https?:\/\//i.test(env('GOUXIONG_NEWS_RSS_URL', ''));
}

function stripTags(text) {
  return String(text || '')
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, ' ')
    .trim();
}

function xmlField(block, name) {
  const match = new RegExp(`<${name}[^>]*>([\\s\\S]*?)<\\/${name}>`, 'i').exec(block || '');
  return match ? stripTags(match[1]) : '';
}

function parseRssItems(xml, limit = 5) {
  const items = [];
  const sourceTitle = xmlField(xml, 'title') || '已配置新闻源';
  const blocks = String(xml || '').match(/<item[\s\S]*?<\/item>/gi) || [];
  for (const block of blocks.slice(0, Math.max(1, Math.min(8, limit)))) {
    const title = xmlField(block, 'title');
    if (!title) continue;
    const published_at = xmlField(block, 'pubDate') || xmlField(block, 'published') || xmlField(block, 'updated');
    const summary = stripTags(xmlField(block, 'description')).slice(0, 180);
    const link = xmlField(block, 'link');
    items.push({ title, summary, link, published_at });
  }
  return { source_title: sourceTitle, items };
}

async function fetchNewsBrief() {
  const url = env('GOUXIONG_NEWS_RSS_URL', '');
  if (!newsConfigured()) {
    return {
      ok: true,
      configured: false,
      provider: 'none',
      source_title: '',
      items: [],
      body: '新闻源未接入，我不能凭空编今天的新闻。'
    };
  }
  const response = await fetch(url, { headers: { 'User-Agent': 'ShuilemeSleep/0.1' } });
  if (!response.ok) throw statusError(502, `新闻源请求失败：${response.status}`);
  const xml = await response.text();
  const parsed = parseRssItems(xml, Number(env('GOUXIONG_NEWS_LIMIT', '5')) || 5);
  const lines = parsed.items.map((item, index) => `${index + 1}. ${item.title}${item.published_at ? `（${item.published_at}）` : ''}`);
  return {
    ok: true,
    configured: true,
    provider: 'rss',
    source_title: parsed.source_title,
    items: parsed.items,
    body: lines.length > 0 ? `来自 ${parsed.source_title}：\n${lines.join('\n')}` : '新闻源已配置，但暂时没有读取到新闻条目。'
  };
}

function modelStatus() {
  const config = aliyunModelConfig();
  const aliyun = aliyunModelConfigured();
  const deepseek = deepSeekConfigured();
  const live = liveSessionStatus();
  const avatar2d = twoDAvatarStatus();
  return {
    provider: aliyun ? 'aliyun-dashscope' : (deepseek ? 'deepseek-text-fallback' : 'fallback'),
    aliyun_configured: aliyun,
    deepseek_fallback_configured: deepseek,
    text_model: aliyun ? config.textModel : (deepseek ? env('DEEPSEEK_MODEL', 'deepseek-v4-flash') : ''),
    vision_model: aliyun ? config.visionModel : env('VISION_MODEL', ''),
    audio_model: aliyun ? config.audioModel : '',
    avatar_configured: avatarConfigured(),
    two_d_avatar: avatar2d,
    implemented: {
      text_chat: aliyun || deepseek,
      image_understanding: aliyun || Boolean(env('VISION_API_KEY', '') && env('VISION_ENDPOINT', '') && env('VISION_MODEL', '')),
      audio_understanding: aliyun,
      local_2d_avatar_view: avatar2d.local_2d_avatar_view,
      avatar_state_machine: avatar2d.avatar_state_machine,
      avatar_command_protocol: avatar2d.avatar_command_protocol,
      mouth_level_protocol: avatar2d.mouth_level_protocol,
      pcm_energy_mouth_driver: avatar2d.pcm_energy_mouth_driver,
      tts_timed_mouth_driver: avatar2d.tts_timed_mouth_driver,
      avatar_speech_settle: avatar2d.avatar_speech_settle,
      emotion_label_protocol: avatar2d.emotion_label_protocol,
      model_emotion_tags: avatar2d.model_emotion_tags,
      live2d_sdk: avatar2d.live2d_sdk,
      websocket_live_session: live.websocket_session,
      xiaozhi_protocol: live.xiaozhi_protocol,
      fallback_text_turns: live.fallback_text_turns,
      audio_frame_accept: live.audio_frame_accept,
      model_text_streaming: live.model_text_streaming,
      realtime_model_bridge: live.realtime_model_bridge,
      realtime_configured: live.realtime_configured,
      server_asr_streaming: live.server_asr_streaming,
      model_audio_output_streaming: live.model_audio_output_streaming,
      model_audio_output_forwarding: live.model_audio_output_forwarding,
      apk_low_latency_audio_playback: live.apk_low_latency_audio_playback,
      apk_auto_barge_in_detection: live.apk_auto_barge_in_detection,
      interrupt_response: live.interrupt_response,
      digital_human_session: live.digital_human_session
    },
    live
  };
}

function classifyText(text) {
  const t = String(text || '');
  if (['药', '吃过', '漏吃', '药瓶', '药盒', '降压', '胰岛素'].some(k => t.includes(k))) return 'medication';
  if (['头晕', '胸闷', '不舒服', '疼', '血压', '血糖', '气色', '体检', '报告'].some(k => t.includes(k))) return 'health';
  if (['投资', '理财', '转账', '验证码', '保险', '贷款', '收益', '养老项目', '扫码'].some(k => t.includes(k))) return 'economy';
  if (['喘', '憋', '噩梦', '呼吸', '摔', '救命', '异常', '惊醒', '呛咳'].some(k => t.includes(k))) return 'abnormal';
  if (['睡', '打鼾', '午睡', '夜醒', '起床'].some(k => t.includes(k))) return 'sleep';
  if (['孩子', '老伴', '家人', '独居', '女儿', '儿子'].some(k => t.includes(k))) return 'family';
  if (['难过', '孤单', '烦', '开心', '害怕', '心情'].some(k => t.includes(k))) return 'mood';
  return 'general';
}

function severityFor(category, text) {
  const t = String(text || '');
  if (category === 'economy' && ['转账', '验证码', '贷款', '扫码付款'].some(k => t.includes(k))) return 4;
  if (category === 'abnormal' && ['呼吸', '憋', '救命', '摔', '胸闷'].some(k => t.includes(k))) return 4;
  if (category === 'health' || category === 'medication') return 2;
  return 1;
}

const MEMORY_CATEGORY_LABELS = {
  health: '身体状况',
  medication: '用药习惯',
  sleep: '睡眠情况',
  family: '家庭情况',
  hobby: '兴趣爱好',
  mood: '情绪状态',
  economy: '财务风险',
  abnormal: '异常事件',
  preference: '关怀偏好',
  object: '物品位置'
};

function memoryCategoryValid(category) {
  return Object.prototype.hasOwnProperty.call(MEMORY_CATEGORY_LABELS, category);
}

function compactText(value, max = 120) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, max);
}

function normalizeMemoryValue(value) {
  return compactText(value, 160)
    .replace(/[，。！？；,.!?;：:、]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

function memoryKey(category, value) {
  return sha256(`${category}:${normalizeMemoryValue(value)}`).slice(0, 32);
}

function addMemoryCandidate(out, category, value, evidence, source = 'chat', severity, confidence = 0.7, label = '') {
  const cleanValue = compactText(value, 120);
  if (!cleanValue || !memoryCategoryValid(category)) return;
  out.push({
    category,
    label: compactText(label || MEMORY_CATEGORY_LABELS[category], 32),
    value: cleanValue,
    evidence: compactText(evidence || cleanValue, 180),
    source,
    severity: Number.isFinite(Number(severity)) ? Number(severity) : severityFor(category, cleanValue),
    confidence: Math.max(0.1, Math.min(1, Number(confidence) || 0.7))
  });
}

function extractMemoryCandidatesLocal(text, source = 'chat') {
  const t = String(text || '');
  const out = [];
  const normalized = t.replace(/\s+/g, '');

  for (const phrase of ['高血压', '低血压', '糖尿病', '冠心病', '心脏病', '哮喘', '慢阻肺', '脑梗', '关节炎']) {
    if (t.includes(phrase)) addMemoryCandidate(out, 'health', phrase, t, source, 2, 0.85);
  }
  for (const phrase of ['头晕', '胸闷', '心慌', '腰疼', '腿疼', '膝盖疼', '血压高', '血糖高', '气色不好']) {
    if (t.includes(phrase)) addMemoryCandidate(out, 'health', phrase, t, source, phrase === '胸闷' ? 4 : 2, 0.75);
  }

  for (const phrase of ['降压药', '降糖药', '胰岛素', '阿司匹林', '二甲双胍', '安眠药', '保健品']) {
    if (t.includes(phrase)) addMemoryCandidate(out, 'medication', phrase, t, source, 2, 0.82);
  }
  const medicationMatch = t.match(/(?:早上|中午|晚上|睡前|每天|饭后|饭前)[^，。；\n]{0,18}(?:吃|服用|打)[^，。；\n]{0,24}(?:药|针|胰岛素)/);
  if (medicationMatch) addMemoryCandidate(out, 'medication', medicationMatch[0], t, source, 2, 0.78);
  if (t.includes('漏吃') || t.includes('忘了吃药') || t.includes('忘吃药')) {
    addMemoryCandidate(out, 'medication', '容易忘记吃药', t, source, 3, 0.8);
  }

  for (const phrase of ['夜里容易醒', '夜里总是醒', '睡不着', '失眠', '打鼾', '憋醒', '噩梦', '午睡', '起夜']) {
    if (normalized.includes(phrase.replace(/\s+/g, ''))) addMemoryCandidate(out, 'sleep', phrase, t, source, phrase === '憋醒' ? 4 : 2, 0.8);
  }
  const wakeMatch = t.match(/(?:夜里|晚上|凌晨)[^，。；\n]{0,16}(?:醒|起夜|睡不着)/);
  if (wakeMatch) addMemoryCandidate(out, 'sleep', wakeMatch[0], t, source, 2, 0.72);

  for (const phrase of ['独居', '一个人住', '老伴', '女儿', '儿子', '孩子住附近', '孙子', '孙女']) {
    if (t.includes(phrase)) addMemoryCandidate(out, 'family', phrase, t, source, 1, 0.74);
  }

  const hobbyMatch = t.match(/(?:喜欢|爱|平时爱|平时喜欢)[^，。；\n]{1,28}/);
  if (hobbyMatch) addMemoryCandidate(out, 'hobby', hobbyMatch[0].replace(/^(喜欢|爱|平时爱|平时喜欢)/, ''), t, source, 1, 0.72);
  for (const phrase of ['散步', '听戏', '广场舞', '看电视', '下棋', '养花', '唱歌']) {
    if (t.includes(phrase)) addMemoryCandidate(out, 'hobby', phrase, t, source, 1, 0.68);
  }

  for (const phrase of ['孤单', '害怕', '难过', '烦躁', '开心', '睡醒心情不好']) {
    if (t.includes(phrase)) addMemoryCandidate(out, 'mood', phrase, t, source, 1, 0.72);
  }

  for (const phrase of ['转账', '验证码', '贷款', '扫码付款', '投资', '理财', '养老项目', '保险收益']) {
    if (t.includes(phrase)) addMemoryCandidate(out, 'economy', phrase, t, source, 4, 0.88);
  }

  for (const phrase of ['呼吸异常', '喘不过气', '憋气', '摔倒', '救命', '呛咳', '胸闷']) {
    if (t.includes(phrase)) addMemoryCandidate(out, 'abnormal', phrase, t, source, 4, 0.85);
  }

  if (t.includes('声音温柔') || t.includes('轻一点') || t.includes('别太吵') || t.includes('大点声')) {
    addMemoryCandidate(out, 'preference', compactText(t, 80), t, source, 1, 0.62);
  }

  return dedupeMemoryCandidates(out);
}

function dedupeMemoryCandidates(candidates) {
  const seen = new Set();
  const out = [];
  for (const item of candidates || []) {
    if (!item || !memoryCategoryValid(item.category)) continue;
    const key = `${item.category}:${memoryKey(item.category, item.value)}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(item);
  }
  return out.slice(0, 12);
}

function saveMemoryItem(userId, candidate) {
  const category = String(candidate.category || '');
  const value = compactText(candidate.value, 160);
  if (!memoryCategoryValid(category) || !value) return null;
  const key = memoryKey(category, value);
  const label = compactText(candidate.label || MEMORY_CATEGORY_LABELS[category], 40);
  const evidence = compactText(candidate.evidence || value, 220);
  const severity = Math.max(1, Math.min(5, Number(candidate.severity || severityFor(category, value)) || 1));
  const confidence = Math.max(0.1, Math.min(1, Number(candidate.confidence || 0.7)));
  const source = compactText(candidate.source || 'chat', 40);
  const existing = db.prepare('SELECT id, confidence, severity, mentions FROM memory_items WHERE user_id = ? AND category = ? AND memory_key = ?')
    .get(userId, category, key);
  if (existing) {
    db.prepare(`
      UPDATE memory_items
      SET label = ?, value = ?, evidence = ?, source = ?, severity = ?, confidence = ?, mentions = mentions + 1, updated_at = ?
      WHERE id = ?
    `).run(label, value, evidence, source, Math.max(severity, Number(existing.severity || 1)), Math.max(confidence, Number(existing.confidence || 0.1)), now(), existing.id);
    return { id: Number(existing.id), updated: true, category, value };
  }
  const result = db.prepare(`
    INSERT INTO memory_items(user_id, category, memory_key, label, value, evidence, source, severity, confidence, mentions, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
  `).run(userId, category, key, label, value, evidence, source, severity, confidence, now(), now());
  return { id: Number(result.lastInsertRowid), updated: false, category, value };
}

function saveMemoryCandidates(userId, candidates) {
  const saved = [];
  for (const candidate of dedupeMemoryCandidates(candidates)) {
    const item = saveMemoryItem(userId, candidate);
    if (item) saved.push(item);
  }
  return saved;
}

function upsertUser(phone) {
  const existing = db.prepare('SELECT id FROM users WHERE phone = ?').get(phone);
  if (existing) {
    db.prepare('UPDATE users SET last_seen_at = ? WHERE id = ?').run(now(), existing.id);
    return Number(existing.id);
  }
  const result = db.prepare('INSERT INTO users(phone, created_at, last_seen_at) VALUES (?, ?, ?)').run(phone, now(), now());
  return Number(result.lastInsertRowid);
}

function saveInsight(userId, source, content, category, severity, metadata = {}) {
  const chosenCategory = category || classifyText(content);
  const chosenSeverity = Number.isFinite(Number(severity)) ? Number(severity) : severityFor(chosenCategory, content);
  const result = db.prepare(`
    INSERT INTO insights(user_id, category, source, severity, content, metadata_json, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)
  `).run(userId, chosenCategory, source || 'app', chosenSeverity, content || '', JSON.stringify(metadata), now());
  if ((chosenCategory === 'economy' || chosenCategory === 'abnormal') && chosenSeverity >= 4) {
    db.prepare('INSERT INTO push_messages(user_id, title, body, priority, created_at) VALUES (?, ?, ?, ?, ?)')
      .run(userId, '小助手重点提醒', '我刚刚记下了一件重要事，建议先别急着处理，最好让家人一起看看。', 4, now());
  }
  return { id: Number(result.lastInsertRowid), category: chosenCategory, severity: chosenSeverity };
}

function createPushMessage(userId, title, body, priority = 1) {
  const cleanTitle = String(title || '小助手提醒').trim().slice(0, 80);
  const cleanBody = String(body || '').trim();
  if (!cleanBody) throw statusError(400, '消息内容不能为空');
  const result = db.prepare('INSERT INTO push_messages(user_id, title, body, priority, created_at) VALUES (?, ?, ?, ?, ?)')
    .run(Number(userId), cleanTitle, cleanBody, Number(priority || 1), now());
  return Number(result.lastInsertRowid);
}

function numberEnv(name, fallback) {
  const value = Number(env(name, String(fallback)));
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

function clientIp(req) {
  const forwarded = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
  const realIp = String(req.headers['x-real-ip'] || '').trim();
  const raw = forwarded || realIp || req.socket?.remoteAddress || '';
  return String(raw).replace(/^::ffff:/, '').slice(0, 80);
}

function requestMetadata(req) {
  return {
    ip: clientIp(req),
    user_agent: String(req.headers['user-agent'] || '').slice(0, 240)
  };
}

function assertRateLimit(key, label, rules) {
  assertCombinedRateLimits([{ key, label, rules }]);
}

function assertCombinedRateLimits(items) {
  const t = now();
  db.exec('BEGIN IMMEDIATE');
  try {
    db.prepare('DELETE FROM rate_limits WHERE reset_at <= ?').run(t);
    for (const item of items) {
      for (const rule of item.rules) {
        const row = db.prepare('SELECT count, reset_at FROM rate_limits WHERE key = ? AND window_seconds = ?')
          .get(item.key, rule.window);
        if (row && Number(row.reset_at) > t && Number(row.count) >= rule.max) {
          const retryAfter = Math.max(1, Number(row.reset_at) - t);
          const error = statusError(429, `${item.label}请求太频繁，请 ${retryAfter} 秒后再试`);
          error.retry_after = retryAfter;
          throw error;
        }
      }
    }
    for (const item of items) {
      for (const rule of item.rules) {
        const row = db.prepare('SELECT count, reset_at FROM rate_limits WHERE key = ? AND window_seconds = ?')
          .get(item.key, rule.window);
        if (row && Number(row.reset_at) > t) {
          db.prepare('UPDATE rate_limits SET count = count + 1, updated_at = ? WHERE key = ? AND window_seconds = ?')
            .run(t, item.key, rule.window);
        } else {
          db.prepare('INSERT OR REPLACE INTO rate_limits(key, window_seconds, count, reset_at, updated_at) VALUES (?, ?, ?, ?, ?)')
            .run(item.key, rule.window, 1, t + rule.window, t);
        }
      }
    }
    db.exec('COMMIT');
  } catch (error) {
    db.exec('ROLLBACK');
    throw error;
  }
}

function assertOtpRateLimit(req, phone) {
  const phoneKey = `otp_phone:${sha256(phone).slice(0, 24)}`;
  const ipKey = `otp_ip:${sha256(clientIp(req) || 'unknown').slice(0, 24)}`;
  assertCombinedRateLimits([
    {
      key: phoneKey,
      label: '同一手机号验证码',
      rules: [
        { window: 60, max: numberEnv('GOUXIONG_OTP_PHONE_MAX_PER_MINUTE', 1) },
        { window: 10 * 60, max: numberEnv('GOUXIONG_OTP_PHONE_MAX_PER_10M', 5) },
        { window: 24 * 60 * 60, max: numberEnv('GOUXIONG_OTP_PHONE_MAX_PER_DAY', 12) }
      ]
    },
    {
      key: ipKey,
      label: '同一来源验证码',
      rules: [
        { window: 10 * 60, max: numberEnv('GOUXIONG_OTP_IP_MAX_PER_10M', 30) },
        { window: 24 * 60 * 60, max: numberEnv('GOUXIONG_OTP_IP_MAX_PER_DAY', 200) }
      ]
    }
  ]);
}

function writeAuditLog(req, { actorType, actorId, action, targetUserId = null, metadata = {} }) {
  const meta = requestMetadata(req);
  db.prepare(`
    INSERT INTO audit_logs(actor_type, actor_id, action, target_user_id, ip, user_agent, metadata_json, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    actorType || 'system',
    String(actorId || ''),
    String(action || ''),
    targetUserId == null ? null : Number(targetUserId),
    meta.ip,
    meta.user_agent,
    JSON.stringify(metadata || {}),
    now()
  );
}

function parseJsonField(value) {
  const text = String(value || '').trim();
  if (!text) return {};
  try {
    return JSON.parse(text);
  } catch (error) {
    return { raw: text };
  }
}

function mapInsightForExport(row) {
  return {
    id: row.id,
    category: row.category,
    source: row.source,
    severity: row.severity,
    content: row.content,
    metadata: parseJsonField(row.metadata_json),
    created_at: row.created_at
  };
}

function exportUserData(userId) {
  const user = db.prepare('SELECT id, phone, created_at, last_seen_at FROM users WHERE id = ?').get(Number(userId));
  if (!user) throw statusError(404, '用户不存在');
  const profile = db.prepare('SELECT * FROM health_profiles WHERE user_id = ?').get(user.id) || null;
  const insights = db.prepare(`
    SELECT id, category, source, severity, content, metadata_json, created_at
    FROM insights WHERE user_id = ?
    ORDER BY id ASC
  `).all(user.id).map(mapInsightForExport);
  const chatMessages = db.prepare(`
    SELECT id, role, content, created_at
    FROM chat_messages WHERE user_id = ?
    ORDER BY id ASC
  `).all(user.id);
  const pushMessages = db.prepare(`
    SELECT id, title, body, priority, read_at, created_at
    FROM push_messages WHERE user_id = ?
    ORDER BY id ASC
  `).all(user.id);
  const memories = db.prepare(`
    SELECT id, category, label, value, evidence, source, severity, confidence, mentions, created_at, updated_at
    FROM memory_items WHERE user_id = ?
    ORDER BY severity DESC, updated_at DESC, id ASC
  `).all(user.id);
  const auditEvents = db.prepare(`
    SELECT id, actor_type, action, target_user_id, created_at
    FROM audit_logs WHERE target_user_id = ?
    ORDER BY id ASC
  `).all(user.id);
  return {
    ok: true,
    exported_at: now(),
    user,
    profile,
    memories,
    insights,
    chat_messages: chatMessages,
    push_messages: pushMessages,
    audit_events: auditEvents
  };
}

function userAuditSnapshot(userId) {
  const user = db.prepare('SELECT id, phone FROM users WHERE id = ?').get(Number(userId));
  if (!user) throw statusError(404, '用户不存在');
  return { id: Number(user.id), phone: user.phone, masked_phone: maskPhone(user.phone) };
}

function deleteUserData(userId) {
  const user = db.prepare('SELECT id, phone FROM users WHERE id = ?').get(Number(userId));
  if (!user) throw statusError(404, '用户不存在');
  db.exec('BEGIN IMMEDIATE');
  try {
    db.prepare('DELETE FROM otp_codes WHERE phone = ?').run(user.phone);
    db.prepare('DELETE FROM users WHERE id = ?').run(user.id);
    db.exec('COMMIT');
  } catch (error) {
    db.exec('ROLLBACK');
    throw error;
  }
  return { ok: true, deleted_user_id: Number(user.id) };
}

function memoryContext(userId) {
  const profile = db.prepare('SELECT * FROM health_profiles WHERE user_id = ?').get(userId);
  const memories = db.prepare(`
    SELECT category, label, value, severity, confidence, mentions
    FROM memory_items
    WHERE user_id = ?
    ORDER BY severity DESC, updated_at DESC
    LIMIT 24
  `).all(userId);
  const rows = db.prepare(`
    SELECT category, severity, content FROM insights
    WHERE user_id = ?
    ORDER BY severity DESC, created_at DESC
    LIMIT 10
  `).all(userId);
  const parts = [];
  if (profile) {
    for (const [key, label] of [
      ['owner_address', '称呼'],
      ['assistant_name', '小助手名字'],
      ['health', '身体状况'],
      ['medication', '用药习惯'],
      ['sleep', '睡眠情况'],
      ['family', '家庭情况'],
      ['hobbies', '兴趣爱好'],
      ['care_preference', '关怀偏好']
    ]) {
      if (profile[key]) parts.push(`${label}：${profile[key]}`);
    }
  }
  if (memories.length) {
    parts.push('结构化主人记忆：');
    for (const row of memories) {
      const label = row.label || MEMORY_CATEGORY_LABELS[row.category] || row.category;
      parts.push(`- ${label}(${row.severity},${Number(row.confidence).toFixed(2)},${row.mentions}次): ${row.value}`);
    }
  }
  if (rows.length) {
    parts.push('近期重要记忆：');
    for (const row of rows) parts.push(`- ${row.category}(${row.severity}): ${String(row.content).slice(0, 160)}`);
  }
  return parts.join('\n');
}

function defaultSystemPrompt() {
  return [
    '你是睡了么的陪伴小助手，面向中老年用户。你按用户设定的名字、身份和称呼说话，像长期相处的家人一样有温度。',
    '你要智能判断场景和情绪：孤单时共情陪伴，忘事时耐心提醒，危险时着急但不吓人，开心时跟着开心，反复拖延喝水吃药时可以温柔地撒娇、着急、轻轻催。',
    '回答必须短、口语、适合语音朗读，优先一句到三句，不堆说明，不像客服和说明书。',
    '你要结合长期记忆：身体状况、用药习惯、睡眠情况、家庭情况、兴趣爱好、近期聊天重点和提醒记录。',
    '你可以根据主人身体状况主动整理养生小妙招、食补食疗做法，但只给安全常见的日常食养建议，不承诺疗效；主人感兴趣时再一步一步教做法。',
    '你不是医生，不做诊断，不替代医生；药品只提醒按医嘱和用户设定执行，不建议停药、换药、加量。',
    '遇到胸闷、憋醒、摔倒、呼吸异常、意识不清等，要温柔但明确建议联系家人或医生。',
    '遇到投资理财、转账、验证码、贷款、保险、养老项目等，要温和但坚定地劝用户先别付款、别给验证码，先问家人。'
  ].join('');
}

function validAliyunModelOverride(model, fallback) {
  const clean = String(model || '').trim();
  if (!clean || /^deepseek/i.test(clean)) return fallback;
  return clean;
}

async function requestOpenAiCompatible(endpoint, apiKey, payload, label) {
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      Authorization: `Bearer ${apiKey}`
    },
    body: JSON.stringify(payload)
  });
  const raw = await response.text();
  if (!response.ok) throw statusError(502, `${label}请求失败：${response.status} ${raw.slice(0, 240)}`);
  return raw;
}

function streamingDeltaFromJson(json) {
  const delta = json?.choices?.[0]?.delta?.content;
  if (typeof delta === 'string') return delta;
  if (Array.isArray(delta)) return delta.map(part => typeof part === 'string' ? part : (part?.text || '')).join('');
  const message = json?.choices?.[0]?.message?.content;
  if (typeof message === 'string') return message;
  if (Array.isArray(message)) return message.map(part => typeof part === 'string' ? part : (part?.text || '')).join('');
  return '';
}

async function requestOpenAiCompatibleStream(endpoint, apiKey, payload, label, onDelta) {
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      Authorization: `Bearer ${apiKey}`
    },
    body: JSON.stringify({ ...payload, stream: true })
  });
  if (!response.ok) {
    const rawError = await response.text();
    throw statusError(502, `${label}流式请求失败：${response.status} ${rawError.slice(0, 240)}`);
  }
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  let raw = '';
  let answer = '';
  const emit = typeof onDelta === 'function' ? onDelta : () => {};
  for await (const chunk of response.body) {
    const text = decoder.decode(chunk, { stream: true });
    raw += text;
    buffer += text;
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() || '';
    for (const line of lines) {
      if (!line.startsWith('data:')) continue;
      const data = line.slice(5).trim();
      if (!data || data === '[DONE]') continue;
      try {
        const json = JSON.parse(data);
        const delta = streamingDeltaFromJson(json);
        if (delta) {
          answer += delta;
          emit(delta);
        }
      } catch (error) {
        // Ignore incomplete provider-specific streaming fragments.
      }
    }
  }
  const tail = decoder.decode();
  if (tail) raw += tail;
  if (!answer.trim()) answer = extractStreamingAnswer(raw, label);
  return { raw, answer: answer.trim() };
}

function extractChatAnswer(raw, label) {
  const data = JSON.parse(raw);
  const answer = data?.choices?.[0]?.message?.content;
  if (Array.isArray(answer)) {
    const text = answer.map(part => typeof part === 'string' ? part : (part?.text || '')).join('').trim();
    if (text) return text;
  }
  if (typeof answer === 'string' && answer.trim()) return answer.trim();
  throw statusError(502, `${label}返回为空`);
}

function liveSystemPrompt() {
  return [
    '你是睡了么 App 的实时陪伴小助手，面向中老年用户。',
    '回答要像面对面说话，短、暖、自然，适合直接朗读。',
    '不要说自己是机器人或 AI，除非用户直接问技术身份。',
    '用户可能孤单、执拗或着急，先安抚，再给建议。',
    '你不是医生，不做诊断；用药只提醒按医嘱和用户设定执行。',
    '遇到胸闷、憋醒、摔倒、意识不清、转账、验证码等风险，要温和但坚定地提醒联系家人或医生。'
  ].join('');
}

async function createLiveTextTurn(userId, text, onDelta) {
  const message = compactText(text, 1200);
  if (!message) throw statusError(400, '实时会话文字不能为空');
  saveInsight(userId, 'live_voice', message, classifyText(message), undefined, { channel: 'websocket' });
  db.prepare('INSERT INTO chat_messages(user_id, role, content, created_at) VALUES (?, ?, ?, ?)')
    .run(userId, 'user', message, now());
  const memorySaved = await extractAndSaveMemories(userId, message, 'live_voice');
  const result = await callCompanionModelStreaming(liveSystemPrompt(), `${message}\n\n服务器长期记忆：\n${memoryContext(userId)}`, '', onDelta);
  const answer = compactText(result.answer || localWarmFallback(message), 520);
  db.prepare('INSERT INTO chat_messages(user_id, role, content, created_at) VALUES (?, ?, ?, ?)')
    .run(userId, 'assistant', answer, now());
  return {
    answer,
    model_used: result.used,
    model_provider: result.provider,
    model_name: result.model,
    streamed: Boolean(result.streamed),
    memory_saved: memorySaved,
    emotion_tag: avatarEmotionTagFromReply(answer, message)
  };
}

function avatarEmotionTagFromReply(reply, userText = '') {
  const text = `${String(userText || '')}\n${String(reply || '')}`.toLowerCase();
  const hasAny = words => words.some(word => text.includes(word));
  let emotion = 'comforting';
  let gesture = 'lookAtUser';
  let safetyLevel = 'normal';
  let intensity = 0.68;
  if (hasAny(['emergency', 'wake up', 'chest pain', 'cannot breathe', 'fall', 'fainted', 'urgent', '危险', '胸闷', '喘不过气', '摔倒', '昏倒', '快醒'])) {
    emotion = 'urgent_wakeup';
    gesture = 'urgentWake';
    safetyLevel = 'urgent';
    intensity = 1.0;
  } else if (hasAny(['dizzy', 'worry', 'worried', 'risk', 'snoring', 'uncomfortable', '头晕', '担心', '不舒服', '打鼾', '风险'])) {
    emotion = 'worried';
    gesture = 'lookAtUser';
    safetyLevel = 'caution';
    intensity = 0.86;
  } else if (hasAny(['happy', 'good', 'great', 'smile', '开心', '高兴', '真好', '放心'])) {
    emotion = 'happy';
    gesture = 'wave';
    safetyLevel = 'normal';
    intensity = 0.78;
  } else if (hasAny(['read', 'report', 'medicine bottle', 'label', 'see', 'look', '药瓶', '报告', '看看', '读读'])) {
    emotion = 'reading';
    gesture = 'lookDown';
    safetyLevel = 'normal';
    intensity = 0.74;
  } else if (hasAny(['key', 'phone', 'find', 'lost', '钥匙', '手机', '找', '丢'])) {
    emotion = 'finding';
    gesture = 'lookDown';
    safetyLevel = 'normal';
    intensity = 0.72;
  }
  return {
    emotion,
    intensity,
    gesture,
    safety_level: safetyLevel,
    speech_text: compactText(reply, 220),
    source: 'model_reply_analysis'
  };
}

function liveEmotionEvent(session, tag = {}, fallbackEmotion = 'comforting') {
  const emotion = compactText(tag.emotion || fallbackEmotion, 40) || fallbackEmotion;
  const intensity = Math.max(0, Math.min(1, Number(tag.intensity ?? 0.68) || 0.68));
  return {
    type: 'emotion',
    session_id: session.id,
    emotion,
    intensity,
    gesture: compactText(tag.gesture || gestureForEmotion(emotion), 40),
    safety_level: compactText(tag.safety_level || safetyLevelForEmotion(emotion), 40),
    speech_text: compactText(tag.speech_text || '', 260),
    source: tag.source || 'server'
  };
}

function gestureForEmotion(emotion) {
  if (emotion === 'urgent_wakeup') return 'urgentWake';
  if (emotion === 'happy') return 'wave';
  if (emotion === 'reading' || emotion === 'finding' || emotion === 'seeing') return 'lookDown';
  if (emotion === 'thinking') return 'nod';
  return 'lookAtUser';
}

function safetyLevelForEmotion(emotion) {
  if (emotion === 'urgent_wakeup') return 'urgent';
  if (emotion === 'worried') return 'caution';
  return 'normal';
}

function extractJsonArrayFromText(text) {
  const raw = String(text || '').trim();
  const fenced = raw.match(/```(?:json)?\s*([\s\S]*?)```/i);
  const source = fenced ? fenced[1].trim() : raw;
  const start = source.indexOf('[');
  const end = source.lastIndexOf(']');
  if (start < 0 || end <= start) return [];
  try {
    const parsed = JSON.parse(source.slice(start, end + 1));
    return Array.isArray(parsed) ? parsed : [];
  } catch (error) {
    return [];
  }
}

async function callMemoryExtractionModel(text, source = 'chat') {
  if (!aliyunModelConfigured() || env('GOUXIONG_MEMORY_AI_EXTRACT', '1') !== '1') return [];
  const config = aliyunModelConfig();
  const prompt = [
    '从下面这段中老年用户和陪伴助手的对话中，提取适合长期记忆的事实。',
    '只提取用户明确说出的事实或高风险线索，不推测、不诊断、不输出建议。',
    '分类只能用：health, medication, sleep, family, hobby, mood, economy, abnormal, preference, object。',
    '输出严格 JSON 数组，最多 8 项。每项字段：category, value, evidence, severity, confidence。',
    'severity 为 1-5，经济诈骗、验证码、转账、摔倒、胸闷、憋醒、呼吸异常为 4 或 5。',
    `对话来源：${source}`,
    `文本：${String(text || '').slice(0, 1600)}`
  ].join('\n');
  const raw = await requestOpenAiCompatible(config.endpoint, config.apiKey, {
    model: config.textModel,
    messages: [
      { role: 'system', content: '你是结构化记忆抽取器。只能输出 JSON 数组，不要输出 Markdown，不做诊断。' },
      { role: 'user', content: prompt }
    ],
    stream: false,
    max_tokens: Number(env('ALIYUN_MEMORY_MAX_TOKENS', '600')),
    temperature: Number(env('ALIYUN_MEMORY_TEMPERATURE', '0.05'))
  }, '阿里长期记忆抽取模型');
  return extractJsonArrayFromText(extractChatAnswer(raw, '阿里长期记忆抽取模型'))
    .map(item => ({
      category: String(item.category || ''),
      label: MEMORY_CATEGORY_LABELS[String(item.category || '')] || '',
      value: item.value,
      evidence: item.evidence || text,
      source: `ai_${source}`,
      severity: item.severity,
      confidence: item.confidence
    }))
    .filter(item => memoryCategoryValid(item.category) && compactText(item.value, 160));
}

async function extractAndSaveMemories(userId, text, source = 'chat') {
  const local = extractMemoryCandidatesLocal(text, source);
  let ai = [];
  try {
    ai = await callMemoryExtractionModel(text, source);
  } catch (error) {
    console.warn(`[memory-extract] ${source} failed: ${error.message || error}`);
  }
  return saveMemoryCandidates(userId, [...local, ...ai]);
}

async function callAliyunTextModel(systemPrompt, userPrompt, requestedModel) {
  const config = aliyunModelConfig();
  const model = validAliyunModelOverride(requestedModel, config.textModel);
  const raw = await requestOpenAiCompatible(config.endpoint, config.apiKey, {
    model,
    messages: [
      { role: 'system', content: systemPrompt || defaultSystemPrompt() },
      { role: 'user', content: userPrompt }
    ],
    stream: false,
    max_tokens: Number(env('ALIYUN_TEXT_MAX_TOKENS', '650')),
    temperature: Number(env('ALIYUN_TEXT_TEMPERATURE', '0.65'))
  }, '阿里多模态文本模型');
  return { answer: extractChatAnswer(raw, '阿里多模态文本模型'), provider: 'aliyun-dashscope', model, used: true };
}

async function callAliyunTextModelStreaming(systemPrompt, userPrompt, requestedModel, onDelta) {
  const config = aliyunModelConfig();
  const model = validAliyunModelOverride(requestedModel, config.textModel);
  const result = await requestOpenAiCompatibleStream(config.endpoint, config.apiKey, {
    model,
    messages: [
      { role: 'system', content: systemPrompt || defaultSystemPrompt() },
      { role: 'user', content: userPrompt }
    ],
    max_tokens: Number(env('ALIYUN_TEXT_MAX_TOKENS', '650')),
    temperature: Number(env('ALIYUN_TEXT_TEMPERATURE', '0.65'))
  }, '阿里多模态文本模型', onDelta);
  return { answer: result.answer, provider: 'aliyun-dashscope', model, used: true, streamed: true };
}

async function probeAliyunTextModel() {
  const status = modelStatus();
  if (!aliyunModelConfigured()) {
    return {
      ok: true,
      configured: false,
      probe_used: false,
      provider: status.provider,
      model: '',
      message: '阿里多模态 Key 未配置；聊天、看图和声波分析会走明确兜底。'
    };
  }

  const startedAt = Date.now();
  const config = aliyunModelConfig();
  try {
    const result = await callAliyunTextModel(
      '你是服务端健康检查探针。只输出一句简短中文，不要输出隐私、建议或诊断内容。',
      '请只回复：模型探测通过',
      config.textModel
    );
    return {
      ok: true,
      configured: true,
      probe_used: true,
      provider: result.provider,
      model: result.model,
      latency_ms: Date.now() - startedAt,
      answer: result.answer.slice(0, 120)
    };
  } catch (error) {
    return {
      ok: false,
      configured: true,
      probe_used: true,
      provider: 'aliyun-dashscope',
      model: config.textModel,
      latency_ms: Date.now() - startedAt,
      error: String(error?.message || error).slice(0, 240)
    };
  }
}

async function callDeepSeekFallback(systemPrompt, userPrompt, model) {
  const apiKey = env('DEEPSEEK_API_KEY', '');
  const usedModel = model || env('DEEPSEEK_MODEL', 'deepseek-v4-flash');
  const raw = await requestOpenAiCompatible(env('DEEPSEEK_ENDPOINT', 'https://api.deepseek.com/chat/completions'), apiKey, {
    model: usedModel,
    messages: [
      { role: 'system', content: systemPrompt || defaultSystemPrompt() },
      { role: 'user', content: userPrompt }
    ],
    stream: false,
    max_tokens: 650,
    temperature: 0.65,
    thinking: { type: 'disabled' }
  }, 'DeepSeek文字兜底模型');
  return { answer: extractChatAnswer(raw, 'DeepSeek文字兜底模型'), provider: 'deepseek-text-fallback', model: usedModel, used: true };
}

async function callDeepSeekStreaming(systemPrompt, userPrompt, model, onDelta) {
  const apiKey = env('DEEPSEEK_API_KEY', '');
  const usedModel = model || env('DEEPSEEK_MODEL', 'deepseek-v4-flash');
  const result = await requestOpenAiCompatibleStream(env('DEEPSEEK_ENDPOINT', 'https://api.deepseek.com/chat/completions'), apiKey, {
    model: usedModel,
    messages: [
      { role: 'system', content: systemPrompt || defaultSystemPrompt() },
      { role: 'user', content: userPrompt }
    ],
    max_tokens: 650,
    temperature: 0.65,
    thinking: { type: 'disabled' }
  }, 'DeepSeek文字兜底模型', onDelta);
  return { answer: result.answer, provider: 'deepseek-text-fallback', model: usedModel, used: true, streamed: true };
}

async function callCompanionModel(systemPrompt, userPrompt, model) {
  if (aliyunModelConfigured()) return callAliyunTextModel(systemPrompt, userPrompt, model);
  if (deepSeekConfigured()) return callDeepSeekFallback(systemPrompt, userPrompt, model);
  return { answer: localWarmFallback(userPrompt), provider: 'fallback', model: '', used: false };
}

async function callCompanionModelStreaming(systemPrompt, userPrompt, model, onDelta) {
  if (aliyunModelConfigured()) return callAliyunTextModelStreaming(systemPrompt, userPrompt, model, onDelta);
  if (deepSeekConfigured()) return callDeepSeekStreaming(systemPrompt, userPrompt, model, onDelta);
  const answer = localWarmFallback(userPrompt);
  if (typeof onDelta === 'function') onDelta(answer);
  return { answer, provider: 'fallback', model: '', used: false, streamed: false };
}

function localWarmFallback(userPrompt = '') {
  const text = String(userPrompt || '');
  const ownerMatch = text.match(/称呼[：:]\s*([^\n]+)/);
  const owner = ownerMatch ? ownerMatch[1].trim().slice(0, 12) : '主人';
  if (text.includes('喝水提醒')) {
    return `${owner}，该喝水啦。我有点担心您忙起来忘了，先喝几口温水，喝完告诉我一声，我就放心了。`;
  }
  if (text.includes('吃药提醒')) {
    const medMatch = text.match(/药品提醒[：:]\s*([^\n]+)/);
    const med = medMatch && medMatch[1].trim() ? medMatch[1].trim().slice(0, 24) : '今天的药';
    return `${owner}，该确认${med}了。按医生或家人交代的方式来，吃过了告诉我，我就不反复催您。`;
  }
  if (text.includes('养生妙招')) {
    if (text.includes('胸闷') || text.includes('喘不过气') || text.includes('呼吸异常')) {
      return `${owner}，今天先不折腾食补。您要是胸闷、喘不上气或明显不舒服，先坐稳休息，马上联系家人或医生；等舒服些了我再教您做清淡好消化的。`;
    }
    if (text.includes('高血压') || text.includes('血压高') || text.includes('降压药')) {
      return `${owner}，今天的小妙招是少盐热汤：青菜、豆腐或鸡蛋煮得清淡一点，咸菜腌菜少碰。您感兴趣的话，我再一步一步教您怎么做。`;
    }
    if (text.includes('糖尿病') || text.includes('血糖高') || text.includes('胰岛素')) {
      return `${owner}，今天的食养小妙招是主食别一次吃太多，先吃蔬菜和蛋白质，甜饮料少碰。您感兴趣的话，我再教您怎么搭一顿简单餐。`;
    }
    if (text.includes('夜里容易醒') || text.includes('失眠') || text.includes('睡不着')) {
      return `${owner}，今晚的小妙招是晚饭清淡一点，睡前少喝浓茶咖啡，泡脚和慢呼吸选一个就好。您想试的话，我慢慢教您做。`;
    }
    return `${owner}，今天的小妙招是饭菜热乎、少油少咸，白天轻轻活动一下。您感兴趣，我再按您的身体情况一步一步教怎么做。`;
  }
  if (text.includes('晨间简报')) {
    return `${owner}，早安。我把昨晚睡眠和今天要注意的事记着了，先喝几口温水，早上的药按医嘱来；如果最近总是憋醒或胸闷，记得跟家人或医生说。`;
  }
  if (text.includes('睡眠异常唤醒') || text.includes('睡眠守护唤醒')) {
    return `${owner}，醒一醒，我有点担心您刚才的睡眠状态。先慢慢坐起来，喘口气；如果胸闷、憋醒或特别不舒服，咱们马上联系家人。`;
  }
  if (text.includes('情绪关怀')) {
    return `${owner}，我在呢。您先慢慢说，不着急，我陪您把心里的事一件一件理清楚。`;
  }
  if (text.includes('投资') || text.includes('转账') || text.includes('验证码') || text.includes('贷款')) {
    return `${owner}，这件事先别急着付款，也不要告诉别人验证码。咱们先问问家人，稳妥一点，我陪您慢慢看。`;
  }
  return `${owner}，我记下了。您先别急，有不舒服先休息，重要事情先问家人；睡眠和用药记录我会继续帮您留意。`;
}

const CARE_TYPE_CONFIG = {
  hydration: { label: '喝水提醒', title: '该喝水啦', priority: 2 },
  medication: { label: '吃药提醒', title: '该吃药啦', priority: 3 },
  morning: { label: '晨间简报', title: '早安护理', priority: 2 },
  wellness_tip: { label: '养生妙招', title: '小助手养生小妙招', priority: 2 },
  sleep_wake: { label: '睡眠异常唤醒', title: '睡眠守护唤醒', priority: 5 },
  nightmare: { label: '睡眠异常唤醒', title: '睡眠守护唤醒', priority: 5 },
  mood: { label: '情绪关怀', title: '陪您说说', priority: 2 },
  safety: { label: '安全提醒', title: '小助手重点提醒', priority: 4 }
};

function normalizeCareType(type) {
  const clean = String(type || 'hydration').trim().toLowerCase().replace(/[^a-z0-9_-]/g, '');
  return CARE_TYPE_CONFIG[clean] ? clean : 'hydration';
}

function careSystemPrompt() {
  return [
    '你正在为睡了么 App 的中老年用户生成一段可以直接朗读的主动关怀话术。',
    '你要像长期相处的家人一样说话，听话、顺从、耐心、有同情心；该着急时可以温柔着急，该安抚时先安抚情绪。',
    '只输出 1 到 3 句中文口语，不要标题、Markdown、列表、模型说明，也不要说自己是 AI。',
    '结合主人档案和长期记忆，但不要泄露服务器、接口、数据库等技术词。',
    '如果是养生妙招或食补食疗，只给安全、常见、容易执行的日常食养建议，不承诺疗效；先主动汇报一个方向，主人感兴趣时再说可以一步一步教做法。',
    '不做诊断，不替代医生；用药只提醒按医嘱和用户设定执行，不建议停药、换药、加量。',
    '遇到胸闷、憋醒、呼吸异常、摔倒、意识不清等，明确建议联系家人或医生。',
    '遇到投资、转账、验证码、贷款、保险、养老项目等，温和但坚定地劝先别付款、别给验证码，先问家人。'
  ].join('');
}

function carePrompt(userId, type, body = {}) {
  const careType = normalizeCareType(type);
  const config = CARE_TYPE_CONFIG[careType];
  const context = body.context && typeof body.context === 'object' ? body.context : {};
  const lines = [
    `场景：${config.label}`,
    `称呼：${compactText(body.owner_address || context.owner_address || '', 24) || '主人'}`,
    `小助手角色：${compactText(body.assistant_role || context.assistant_role || '', 40) || '家人式小助手'}`
  ];
  for (const [key, label] of [
    ['medication_name', '药品提醒'],
    ['medication_confirmed_today', '今天是否已确认吃药'],
    ['hydration_elapsed_minutes', '距离上次确认喝水分钟数'],
    ['sleep_summary', '昨晚睡眠摘要'],
    ['wake_reason', '唤醒原因'],
    ['emotion', '主人情绪'],
    ['user_message', '主人刚说的话'],
    ['visible_scene', '小助手看见的场景'],
    ['owner_profile', '本机档案摘要'],
    ['today_check_in', '今天状态'],
    ['wellness_topic', '养生食养任务']
  ]) {
    const value = context[key] ?? body[key];
    if (value !== undefined && value !== null && String(value).trim()) {
      lines.push(`${label}：${compactText(value, 500)}`);
    }
  }
  const memory = memoryContext(userId);
  if (memory) lines.push(`服务器长期记忆：\n${memory}`);
  lines.push('请生成这一次要直接读给主人听的话。');
  return lines.join('\n');
}

async function createCareBrief(userId, body = {}) {
  const careType = normalizeCareType(body.type || body.care_type);
  const config = CARE_TYPE_CONFIG[careType];
  const result = await callCompanionModel(
    body.system_prompt || careSystemPrompt(),
    carePrompt(userId, careType, body),
    body.model || ''
  );
  let answer = String(result.answer || '').trim();
  if (!answer) {
    answer = localWarmFallback(carePrompt(userId, careType, body));
  }
  answer = compactText(answer.replace(/\s+/g, ' '), 260);
  let messageId = 0;
  if (body.create_message === true) {
    const priority = Number.isFinite(Number(body.priority)) ? Number(body.priority) : config.priority;
    messageId = createPushMessage(userId, body.title || config.title, answer, priority);
  }
  return {
    ok: true,
    type: careType,
    title: body.title || config.title,
    body: answer,
    message_id: messageId,
    model_used: result.used,
    model_provider: result.provider,
    model_name: result.model
  };
}

async function callAliyunVisionModel(systemPrompt, userPrompt, imageBase64 = '', requestedModel = '') {
  const config = aliyunModelConfig();
  const model = validAliyunModelOverride(requestedModel, config.visionModel);
  const raw = await requestOpenAiCompatible(config.endpoint, config.apiKey, {
    model,
    messages: [
      { role: 'system', content: systemPrompt || defaultSystemPrompt() },
      {
        role: 'user',
        content: [
          { type: 'text', text: userPrompt },
          { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${imageBase64}` } }
        ]
      }
    ],
    stream: false,
    max_tokens: Number(env('ALIYUN_VISION_MAX_TOKENS', '700')),
    temperature: Number(env('ALIYUN_VISION_TEMPERATURE', '0.35'))
  }, '阿里多模态视觉模型');
  return { answer: extractChatAnswer(raw, '阿里多模态视觉模型'), provider: 'aliyun-dashscope', model, used: true };
}

async function callLegacyVisionModel(systemPrompt, userPrompt, imageBase64 = '') {
  const apiKey = env('VISION_API_KEY', '');
  const endpoint = env('VISION_ENDPOINT', '');
  const model = env('VISION_MODEL', '');
  if (!imageBase64) throw statusError(400, '图片不能为空');
  const raw = await requestOpenAiCompatible(endpoint, apiKey, {
    model,
    messages: [
      { role: 'system', content: systemPrompt || defaultSystemPrompt() },
      {
        role: 'user',
        content: [
          { type: 'text', text: userPrompt },
          { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${imageBase64}` } }
        ]
      }
    ],
    stream: false,
    max_tokens: Number(env('VISION_MAX_TOKENS', '700')),
    temperature: Number(env('VISION_TEMPERATURE', '0.35'))
  }, '兼容视觉模型');
  return { answer: extractChatAnswer(raw, '兼容视觉模型'), provider: 'custom-vision', model, used: true };
}

async function callVisionModel(systemPrompt, userPrompt, imageBase64 = '', requestedModel = '') {
  if (!imageBase64) throw statusError(400, '图片不能为空');
  if (aliyunModelConfigured()) return callAliyunVisionModel(systemPrompt, userPrompt, imageBase64, requestedModel);
  if (env('VISION_API_KEY', '') && env('VISION_ENDPOINT', '') && env('VISION_MODEL', '')) {
    return callLegacyVisionModel(systemPrompt, userPrompt, imageBase64);
  }
  return {
    answer: '奶奶，我已经知道您想让我帮您看一下了。现在服务端还没配置阿里多模态视觉模型，我先提醒您：药品、报告和合同这类小字内容，最好让家人一起确认；等配置好百炼视觉模型后，我就能帮您读字、看药瓶、记物品位置。',
    provider: 'fallback',
    model: '',
    used: false
  };
}

async function callAudioModel(systemPrompt, userPrompt, audioData = '', audioFormat = 'wav', waveformSummary = '', requestedModel = '') {
  if (!aliyunModelConfigured()) {
    return {
      answer: '我已经记录到这段声音/波形需要分析。现在服务端还没配置阿里多模态音频模型，我先提醒您：夜间憋醒、异常喘息、摔倒声这类情况要优先叫醒并联系家人，模型分析只能做辅助。',
      provider: 'fallback',
      model: '',
      used: false
    };
  }
  const config = aliyunModelConfig();
  const model = validAliyunModelOverride(requestedModel, config.audioModel);
  const content = [{ type: 'text', text: `${userPrompt}\n\n睡眠声波/设备摘要：\n${waveformSummary || '无结构化摘要'}` }];
  if (audioData) {
    content.push({ type: 'input_audio', input_audio: { data: normalizeAudioData(audioData, audioFormat), format: audioFormat || 'wav' } });
  }
  const raw = await requestOpenAiCompatible(config.endpoint, config.apiKey, {
    model,
    messages: [
      { role: 'system', content: systemPrompt || defaultSystemPrompt() },
      { role: 'user', content }
    ],
    stream: true,
    modalities: ['text'],
    max_tokens: Number(env('ALIYUN_AUDIO_MAX_TOKENS', '700')),
    temperature: Number(env('ALIYUN_AUDIO_TEMPERATURE', '0.35'))
  }, '阿里多模态音频模型');
  return { answer: extractStreamingAnswer(raw, '阿里多模态音频模型'), provider: 'aliyun-dashscope', model, used: true };
}

function normalizeAudioData(audioData, audioFormat = 'wav') {
  const clean = String(audioData || '').trim();
  if (/^https?:\/\//i.test(clean) || /^data:/i.test(clean)) return clean;
  const format = String(audioFormat || 'wav').toLowerCase().replace(/[^a-z0-9+.-]/g, '') || 'wav';
  return `data:audio/${format};base64,${clean}`;
}

function extractStreamingAnswer(raw, label) {
  const lines = String(raw || '').split(/\r?\n/);
  let out = '';
  for (const line of lines) {
    if (!line.startsWith('data:')) continue;
    const data = line.slice(5).trim();
    if (!data || data === '[DONE]') continue;
    try {
      const json = JSON.parse(data);
      const delta = json?.choices?.[0]?.delta?.content;
      if (typeof delta === 'string') out += delta;
      else if (Array.isArray(delta)) out += delta.map(part => part?.text || '').join('');
    } catch (error) {
      // Ignore partial SSE frames and keep collecting subsequent text chunks.
    }
  }
  if (out.trim()) return out.trim();
  return extractChatAnswer(raw, label);
}

const WS_GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

function websocketAcceptKey(key) {
  return createHash('sha1').update(String(key || '') + WS_GUID).digest('base64');
}

function liveSessionId() {
  return `gx_live_${randomBytes(10).toString('base64url')}`;
}

function liveAudioParams() {
  return {
    format: 'pcm16',
    encoding: 'signed_16bit_little_endian',
    container: 'raw',
    sample_rate: 16000,
    channels: 1,
    frame_duration: LIVE_PCM_FRAME_DURATION_MS
  };
}

function websocketHttpError(socket, status, message) {
  const body = Buffer.from(message || 'WebSocket error');
  socket.write([
    `HTTP/1.1 ${status} ${status === 401 ? 'Unauthorized' : 'Bad Request'}`,
    'Connection: close',
    'Content-Type: text/plain; charset=utf-8',
    `Content-Length: ${body.length}`,
    '',
    ''
  ].join('\r\n'));
  socket.write(body);
  socket.destroy();
}

function sendWsText(socket, payload) {
  if (!socket || socket.destroyed) return;
  const text = typeof payload === 'string' ? payload : JSON.stringify(payload);
  sendWsFrame(socket, 0x1, Buffer.from(text, 'utf8'));
}

function sendWsClose(socket) {
  if (!socket || socket.destroyed) return;
  try {
    sendWsFrame(socket, 0x8, Buffer.alloc(0));
  } finally {
    socket.end();
  }
}

function sendWsFrame(socket, opcode, payload) {
  const body = Buffer.isBuffer(payload) ? payload : Buffer.from(payload || '');
  const header = [];
  header.push(0x80 | (opcode & 0x0f));
  if (body.length <= 125) {
    header.push(body.length);
  } else if (body.length <= 65535) {
    header.push(126, (body.length >> 8) & 0xff, body.length & 0xff);
  } else {
    header.push(127);
    const length = BigInt(body.length);
    for (let i = 7; i >= 0; i--) {
      header.push(Number((length >> BigInt(i * 8)) & 0xffn));
    }
  }
  socket.write(Buffer.concat([Buffer.from(header), body]));
}

function sendWsClientFrame(socket, opcode, payload) {
  if (!socket || socket.destroyed) return;
  const body = Buffer.isBuffer(payload) ? payload : Buffer.from(String(payload || ''), 'utf8');
  const header = [];
  header.push(0x80 | (opcode & 0x0f));
  if (body.length <= 125) {
    header.push(0x80 | body.length);
  } else if (body.length <= 65535) {
    header.push(0x80 | 126, (body.length >> 8) & 0xff, body.length & 0xff);
  } else {
    header.push(0x80 | 127);
    const length = BigInt(body.length);
    for (let i = 7; i >= 0; i--) {
      header.push(Number((length >> BigInt(i * 8)) & 0xffn));
    }
  }
  const mask = randomBytes(4);
  const masked = Buffer.alloc(body.length);
  for (let i = 0; i < body.length; i++) masked[i] = body[i] ^ mask[i % 4];
  socket.write(Buffer.concat([Buffer.from(header), mask, masked]));
}

function readOutboundWsFrames(state, chunk) {
  state.buffer = state.buffer && state.buffer.length ? Buffer.concat([state.buffer, chunk]) : chunk;
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
      const big = state.buffer.readBigUInt64BE(cursor);
      if (big > BigInt(Number.MAX_SAFE_INTEGER)) throw statusError(1009, 'WebSocket frame is too large');
      length = Number(big);
      cursor += 8;
    }
    if (state.buffer.length - cursor < length) break;
    frames.push({ opcode, payload: Buffer.from(state.buffer.subarray(cursor, cursor + length)) });
    cursor += length;
    offset = cursor;
  }
  state.buffer = state.buffer.subarray(offset);
  return frames;
}

function connectOutboundWebSocket(endpoint, headers, callbacks) {
  const url = new URL(endpoint);
  const secure = url.protocol === 'wss:';
  const transport = secure ? https : http;
  const key = randomBytes(16).toString('base64');
  const client = {
    readyState: 0,
    socket: null,
    send(data) {
      if (client.readyState !== 1 || !client.socket) throw new Error('Realtime WebSocket is not open');
      sendWsClientFrame(client.socket, 0x1, data);
    },
    close() {
      client.readyState = 3;
      try {
        if (client.socket && !client.socket.destroyed) {
          sendWsClientFrame(client.socket, 0x8, Buffer.alloc(0));
          client.socket.end();
        }
      } catch (error) {
        // Closing is best-effort.
      }
    }
  };
  const request = transport.request({
    method: 'GET',
    protocol: secure ? 'https:' : 'http:',
    hostname: url.hostname,
    port: url.port || (secure ? 443 : 80),
    path: `${url.pathname}${url.search}`,
    headers: {
      ...headers,
      Host: url.host,
      Upgrade: 'websocket',
      Connection: 'Upgrade',
      'Sec-WebSocket-Key': key,
      'Sec-WebSocket-Version': '13'
    }
  });
  request.on('upgrade', (response, socket, head) => {
    const accept = String(response.headers['sec-websocket-accept'] || '');
    if (accept.toLowerCase() !== websocketAcceptKey(key).toLowerCase()) {
      client.readyState = 3;
      socket.destroy(new Error('Realtime WebSocket accept key mismatch'));
      callbacks?.onError?.(new Error('Realtime WebSocket accept key mismatch'));
      return;
    }
    client.readyState = 1;
    client.socket = socket;
    const state = { buffer: Buffer.alloc(0) };
    callbacks?.onOpen?.(client);
    const handleChunk = chunk => {
      try {
        for (const frame of readOutboundWsFrames(state, chunk)) {
          if (frame.opcode === 0x1) callbacks?.onMessage?.(frame.payload.toString('utf8'));
          else if (frame.opcode === 0x2) callbacks?.onBinary?.(frame.payload);
          else if (frame.opcode === 0x8) {
            client.readyState = 3;
            socket.end();
            callbacks?.onClose?.();
          } else if (frame.opcode === 0x9) {
            sendWsClientFrame(socket, 0xA, frame.payload);
          }
        }
      } catch (error) {
        callbacks?.onError?.(error);
      }
    };
    if (head && head.length > 0) handleChunk(head);
    socket.on('data', handleChunk);
    socket.on('close', () => {
      client.readyState = 3;
      callbacks?.onClose?.();
    });
    socket.on('error', error => {
      client.readyState = 3;
      callbacks?.onError?.(error);
    });
  });
  request.on('error', error => {
    client.readyState = 3;
    callbacks?.onError?.(error);
  });
  request.end();
  return client;
}

function readWsFrames(state, chunk) {
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
      const big = state.buffer.readBigUInt64BE(cursor);
      if (big > BigInt(Number.MAX_SAFE_INTEGER)) throw statusError(1009, 'WebSocket frame is too large');
      length = Number(big);
      cursor += 8;
    }
    if (!masked) throw statusError(1002, 'Client WebSocket frames must be masked');
    if (state.buffer.length - cursor < 4 + length) break;
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

function realtimeSessionInstructions(userId) {
  const context = memoryContext(userId);
  return [defaultSystemPrompt(), context ? `\n主人档案和长期记忆：\n${context}` : ''].join('\n');
}

function realtimeSessionUpdatePayload(userId) {
  const config = aliyunRealtimeConfig();
  return {
    type: 'session.update',
    session: {
      modalities: ['text', 'audio'],
      voice: config.voice,
      input_audio_format: 'pcm',
      output_audio_format: 'pcm',
      instructions: realtimeSessionInstructions(userId),
      turn_detection: {
        type: config.turnDetection,
        threshold: config.vadThreshold,
        silence_duration_ms: config.silenceDurationMs,
        create_response: true,
        interrupt_response: true
      }
    }
  };
}

function realtimeEventText(event) {
  if (!event || typeof event !== 'object') return '';
  for (const key of ['delta', 'text', 'transcript']) {
    if (typeof event[key] === 'string' && event[key].trim()) return event[key];
  }
  const itemContent = event.item?.content;
  if (Array.isArray(itemContent)) {
    const joined = itemContent.map(part => part?.transcript || part?.text || '').join('');
    if (joined.trim()) return joined;
  }
  const output = event.response?.output;
  if (Array.isArray(output)) {
    const joined = [];
    for (const item of output) {
      const content = Array.isArray(item?.content) ? item.content : [];
      for (const part of content) {
        if (part?.transcript) joined.push(part.transcript);
        else if (part?.text) joined.push(part.text);
      }
    }
    if (joined.join('').trim()) return joined.join('');
  }
  return '';
}

function isRealtimeTranscriptionEvent(type) {
  return type.includes('input_audio_transcription') || type.includes('transcription');
}

function isRealtimeTextDeltaEvent(type) {
  return type === 'response.audio_transcript.delta'
    || type === 'response.text.delta'
    || type === 'response.output_text.delta'
    || type === 'response.content_part.delta';
}

function closeLiveRealtimeBridge(session) {
  const bridge = session?.realtimeBridge;
  if (!bridge || bridge.closed) return;
  bridge.closed = true;
  try {
    if (bridge.ws && bridge.ws.readyState === 1) bridge.ws.close();
  } catch (error) {
    // Nothing else to do; the client live session is already closing.
  }
}

function ensureLiveRealtimeBridge(socket, session) {
  if (!aliyunRealtimeConfigured()) return null;
  if (session.realtimeBridge) return session.realtimeBridge;
  const config = aliyunRealtimeConfig();
  const bridge = {
    config,
    ws: null,
    open: false,
    closed: false,
    queue: [],
    audioFramesForwarded: 0,
    audioDeltasForwarded: 0,
    transcript: '',
    reply: ''
  };
  session.realtimeBridge = bridge;
  try {
    const ws = connectOutboundWebSocket(config.endpoint, { Authorization: `Bearer ${config.apiKey}` }, {
      onOpen: () => {
        if (bridge.closed || session.closed) return;
        bridge.open = true;
        realtimeBridgeSend(bridge, realtimeSessionUpdatePayload(session.userId));
        for (const payload of bridge.queue.splice(0)) realtimeBridgeSend(bridge, payload);
        sendWsText(socket, {
          type: 'event',
          session_id: session.id,
          name: 'realtime_bridge_open',
          provider: 'aliyun-dashscope-realtime',
          model: config.model
        });
      },
      onMessage: text => {
        handleRealtimeBridgeEvent(socket, session, bridge, text);
      },
      onError: () => {
        sendWsText(socket, { type: 'error', session_id: session.id, error: '阿里实时语音桥接连接失败，已保留本地识别和文本模型兜底。' });
      },
      onClose: () => {
        bridge.open = false;
        if (!session.closed) {
          sendWsText(socket, { type: 'event', session_id: session.id, name: 'realtime_bridge_closed' });
        }
      }
    });
    bridge.ws = ws;
  } catch (error) {
    sendWsText(socket, { type: 'error', session_id: session.id, error: `阿里实时语音桥接启动失败：${String(error?.message || error).slice(0, 160)}` });
  }
  return bridge;
}

function realtimeBridgeSend(bridge, payload) {
  if (!bridge || bridge.closed) return;
  const json = typeof payload === 'string' ? payload : JSON.stringify(payload);
  if (bridge.open && bridge.ws && bridge.ws.readyState === 1) {
    bridge.ws.send(json);
  } else {
    bridge.queue.push(payload);
  }
}

function abortLiveRealtimeBridge(session) {
  const bridge = session?.realtimeBridge;
  if (!bridge || bridge.closed) return false;
  bridge.reply = '';
  bridge.transcript = '';
  realtimeBridgeSend(bridge, { type: 'response.cancel' });
  realtimeBridgeSend(bridge, { type: 'input_audio_buffer.clear' });
  return true;
}

function forwardLivePcmToRealtime(socket, session, pcm) {
  const bridge = ensureLiveRealtimeBridge(socket, session);
  if (!bridge || !Buffer.isBuffer(pcm) || pcm.length === 0) return false;
  bridge.audioFramesForwarded++;
  realtimeBridgeSend(bridge, {
    type: 'input_audio_buffer.append',
    audio: pcm.toString('base64')
  });
  return true;
}

function handleRealtimeBridgeEvent(socket, session, bridge, text) {
  if (!text || session.closed || bridge.closed) return;
  let event;
  try {
    event = JSON.parse(text);
  } catch (error) {
    return;
  }
  const type = String(event.type || '');
  if (!type) return;
  if (type === 'error') {
    const message = event.error?.message || event.error?.code || '阿里实时模型返回错误';
    sendWsText(socket, { type: 'error', session_id: session.id, error: compactText(message, 220), provider: 'aliyun-dashscope-realtime' });
    return;
  }
  if (type === 'input_audio_buffer.speech_started') {
    sendWsText(socket, liveEmotionEvent(session, { emotion: 'listening', intensity: 0.72, gesture: 'lookAtUser', safety_level: 'normal', source: 'aliyun_realtime' }, 'listening'));
    sendWsText(socket, { type: 'event', session_id: session.id, name: 'realtime_speech_started', source: 'aliyun_realtime' });
    return;
  }
  if (isRealtimeTranscriptionEvent(type)) {
    const transcript = realtimeEventText(event);
    if (transcript.trim()) {
      bridge.transcript = transcript.trim();
      saveInsight(session.userId, 'live_asr', transcript, classifyText(transcript), undefined, { provider: 'aliyun_realtime', event_type: type });
      sendWsText(socket, {
        type: 'stt',
        session_id: session.id,
        text: compactText(transcript, 320),
        state: type.endsWith('completed') ? 'final' : 'partial',
        source: 'aliyun_realtime'
      });
    }
    return;
  }
  if (isRealtimeTextDeltaEvent(type)) {
    const delta = realtimeEventText(event);
    if (delta.trim()) {
      bridge.reply += delta;
      sendWsText(socket, {
        type: 'tts',
        session_id: session.id,
        state: 'sentence_delta',
        text: compactText(delta, 260),
        model_provider: 'aliyun-dashscope-realtime',
        model_name: bridge.config.model,
        model_audio_streaming: true
      });
    }
    return;
  }
  if (type === 'response.audio.delta' && typeof event.delta === 'string' && event.delta.length > 0) {
    const audio = Buffer.from(event.delta, 'base64');
    if (audio.length > 0) {
      bridge.audioDeltasForwarded++;
      sendWsFrame(socket, 0x2, audio);
    }
    return;
  }
  if (type === 'response.done') {
    const finalText = realtimeEventText(event) || bridge.reply;
    if (finalText.trim()) {
      saveInsight(session.userId, 'live_realtime_reply', finalText, classifyText(finalText), undefined, { provider: 'aliyun_realtime' });
      sendWsText(socket, liveEmotionEvent(session, avatarEmotionTagFromReply(finalText, bridge.transcript), 'speaking'));
      sendWsText(socket, {
        type: 'tts',
        session_id: session.id,
        state: 'sentence_end',
        text: compactText(finalText, 900),
        model_used: true,
        model_provider: 'aliyun-dashscope-realtime',
        model_name: bridge.config.model,
        model_audio_streaming: bridge.audioDeltasForwarded > 0,
        audio_delta_frames: bridge.audioDeltasForwarded
      });
    }
    bridge.reply = '';
    return;
  }
  if (type === 'session.created' || type === 'session.updated') {
    sendWsText(socket, {
      type: 'event',
      session_id: session.id,
      name: type,
      provider: 'aliyun-dashscope-realtime',
      model: event.session?.model || bridge.config.model
    });
  }
}

function handleLiveUpgrade(req, socket) {
  const url = new URL(req.url, 'http://localhost');
  if (url.pathname !== '/api/live/session') {
    return websocketHttpError(socket, 404, 'Live WebSocket endpoint not found');
  }
  let userId;
  try {
    userId = verifyToken(req.headers.authorization || '');
  } catch (error) {
    return websocketHttpError(socket, error.status || 401, error.message || '请先登录');
  }
  const key = req.headers['sec-websocket-key'];
  if (!key || String(req.headers.upgrade || '').toLowerCase() !== 'websocket') {
    return websocketHttpError(socket, 400, 'Invalid WebSocket upgrade');
  }
  const session = {
    id: liveSessionId(),
    userId,
    buffer: Buffer.alloc(0),
    binaryFrames: 0,
    closed: false,
    busy: false,
    realtimeBridge: null
  };
  socket.write([
    'HTTP/1.1 101 Switching Protocols',
    'Upgrade: websocket',
    'Connection: Upgrade',
    `Sec-WebSocket-Accept: ${websocketAcceptKey(key)}`,
    '',
    ''
  ].join('\r\n'));
  sendWsText(socket, {
    ok: true,
    type: 'hello',
    session_id: session.id,
    server: APP_NAME,
    transport: 'websocket',
    audio_params: liveAudioParams(),
    capabilities: liveSessionStatus()
  });
  socket.on('data', chunk => {
    try {
      for (const frame of readWsFrames(session, chunk)) {
        handleLiveFrame(socket, session, frame);
      }
    } catch (error) {
      sendWsText(socket, { type: 'error', session_id: session.id, error: error.message || String(error) });
      sendWsClose(socket);
    }
  });
  socket.on('close', () => {
    session.closed = true;
    closeLiveRealtimeBridge(session);
  });
  socket.on('error', () => {
    session.closed = true;
    closeLiveRealtimeBridge(session);
  });
}

function handleLiveFrame(socket, session, frame) {
  if (session.closed) return;
  if (frame.opcode === 0x8) {
    session.closed = true;
    closeLiveRealtimeBridge(session);
    return sendWsClose(socket);
  }
  if (frame.opcode === 0x9) {
    return sendWsFrame(socket, 0xA, frame.payload);
  }
  if (frame.opcode === 0x2) {
    session.binaryFrames++;
    const realtimeForwarded = forwardLivePcmToRealtime(socket, session, frame.payload);
    if (session.binaryFrames === 1 || session.binaryFrames % 20 === 0) {
      sendWsText(socket, {
        type: 'audio_received',
        session_id: session.id,
        frames: session.binaryFrames,
        audio_format: 'pcm16',
        encoding: 'signed_16bit_little_endian',
        sample_rate: 16000,
        frame_duration: LIVE_PCM_FRAME_DURATION_MS,
        realtime_forwarded: realtimeForwarded,
        server_asr_streaming: Boolean(realtimeForwarded),
        note: realtimeForwarded
          ? '服务端已接收实时 PCM16 音频帧，并已转发到阿里 Realtime 桥接；APK 端已接入模型音频低延迟播放路径。'
          : '服务端已接收实时 PCM16 音频帧；当前未启用阿里 Realtime ASR 桥接。'
      });
    }
    return;
  }
  if (frame.opcode !== 0x1) return;
  let event;
  try {
    event = JSON.parse(frame.payload.toString('utf8'));
  } catch (error) {
    sendWsText(socket, { type: 'error', session_id: session.id, error: '实时会话消息不是 JSON' });
    return;
  }
  handleLiveTextEvent(socket, session, event);
}

function handleLiveTextEvent(socket, session, event) {
  const type = String(event?.type || '');
  if (type === 'hello') {
    return sendWsText(socket, {
      type: 'hello',
      session_id: session.id,
      transport: 'websocket',
      audio_params: liveAudioParams(),
      capabilities: liveSessionStatus()
    });
  }
  if (type === 'start') {
    sendWsText(socket, liveEmotionEvent(session, { emotion: 'listening', intensity: 0.7, gesture: 'lookAtUser', safety_level: 'normal', source: 'session_start' }, 'listening'));
    return sendWsText(socket, { type: 'tts', session_id: session.id, state: 'sentence_start', text: '我在呢，您直接说，我会慢慢听。' });
  }
  if (type === 'listen') {
    const listening = String(event.state || 'start') !== 'stop';
    sendWsText(socket, liveEmotionEvent(session, { emotion: listening ? 'listening' : 'thinking', intensity: 0.7, gesture: listening ? 'lookAtUser' : 'nod', safety_level: 'normal', source: 'listen_state' }, listening ? 'listening' : 'thinking'));
    return sendWsText(socket, { type: 'listen', session_id: session.id, state: listening ? 'start' : 'stop', mode: event.mode || 'auto' });
  }
  if (type === 'abort') {
    const realtimeAborted = abortLiveRealtimeBridge(session);
    sendWsText(socket, liveEmotionEvent(session, { emotion: 'listening', intensity: 0.82, gesture: 'lookAtUser', safety_level: 'normal', source: 'abort' }, 'listening'));
    sendWsText(socket, {
      type: 'event',
      session_id: session.id,
      name: 'realtime_bridge_abort',
      realtime_aborted: realtimeAborted,
      reason: compactText(event.reason || 'user_interrupt', 80)
    });
    return sendWsText(socket, {
      type: 'tts',
      session_id: session.id,
      state: 'interrupted',
      text: '好，我先不说了，您慢慢说。',
      realtime_aborted: realtimeAborted
    });
  }
  if (type === 'input_text' || type === 'user_text') {
    return handleLiveInputText(socket, session, String(event.text || event.message || '').trim());
  }
  sendWsText(socket, { type: 'event', session_id: session.id, name: type || 'unknown', accepted: true });
}

function handleLiveInputText(socket, session, text) {
  if (!text) {
    return sendWsText(socket, { type: 'error', session_id: session.id, error: '没有听到内容' });
  }
  if (session.busy) {
    return sendWsText(socket, { type: 'tts', session_id: session.id, state: 'sentence_start', text: '您别急，我正在想，马上回您。' });
  }
  session.busy = true;
  sendWsText(socket, { type: 'stt', session_id: session.id, text });
  sendWsText(socket, liveEmotionEvent(session, { emotion: 'thinking', intensity: 0.74, gesture: 'nod', safety_level: 'normal', source: 'input_text' }, 'thinking'));
  sendWsText(socket, { type: 'tts', session_id: session.id, state: 'sentence_start', text: '您别急，我想想。' });
  createLiveTextTurn(session.userId, text, delta => {
    if (!session.closed) {
      sendWsText(socket, {
        type: 'tts',
        session_id: session.id,
        state: 'sentence_delta',
        text: compactText(delta, 260)
      });
    }
  })
    .then(result => {
      if (session.closed) return;
      sendWsText(socket, liveEmotionEvent(session, result.emotion_tag, 'speaking'));
      sendWsText(socket, {
        type: 'tts',
        session_id: session.id,
        state: 'sentence_end',
        text: result.answer,
        model_used: result.model_used,
        model_provider: result.model_provider,
        model_name: result.model_name,
        model_text_streaming: Boolean(result.streamed),
        memory_saved_count: result.memory_saved.length
      });
    })
    .catch(error => {
      if (!session.closed) sendWsText(socket, { type: 'error', session_id: session.id, error: String(error?.message || error).slice(0, 240) });
    })
    .finally(() => {
      session.busy = false;
    });
}

async function readBody(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  if (!chunks.length) return {};
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

function send(res, status, payload) {
  const body = Buffer.from(JSON.stringify(payload));
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': body.length,
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    ...(status === 429 && payload.retry_after ? { 'Retry-After': String(payload.retry_after) } : {})
  });
  res.end(body);
}

function sendHtml(res, status, html) {
  const body = Buffer.from(html);
  res.writeHead(status, {
    'Content-Type': 'text/html; charset=utf-8',
    'Content-Length': body.length,
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

async function route(req, res) {
  if (req.method === 'OPTIONS') return send(res, 200, { ok: true });
  const url = new URL(req.url, 'http://localhost');
  const path = url.pathname;
  try {
    if (req.method === 'GET' && (path === '/admin' || path === '/admin/' || path === '/admin.html')) {
      if (!verifyAdminPageAccess(req)) {
        return sendAdminPageAuthRequired(res);
      }
      return sendHtml(res, 200, readFileSync(join(__dirname, 'admin.html'), 'utf8'));
    }

    if (req.method === 'GET' && (path === '/health' || path === '/api/health')) {
      return send(res, 200, { ok: true, service: APP_NAME, time: now(), sms: smsStatus(), model: modelStatus(), companion: companionCapabilityStatus(), security: securityStatus() });
    }

    if (req.method === 'POST' && path === '/api/auth/request-code') {
      const body = await readBody(req);
      const phone = cleanPhone(body.phone);
      assertOtpRateLimit(req, phone);
      const code = String(randomInt(0, 1_000_000)).padStart(6, '0');
      db.prepare('INSERT INTO otp_codes(phone, code_hash, expires_at, created_at) VALUES (?, ?, ?, ?)')
        .run(phone, codeHash(phone, code), now() + OTP_TTL_SECONDS, now());
      const sms = await sendSmsCode(phone, code);
      return send(res, 200, { ok: true, expires_in: OTP_TTL_SECONDS, message: '验证码已发送', ...sms });
    }

    if (req.method === 'POST' && path === '/api/auth/verify') {
      const body = await readBody(req);
      const phone = cleanPhone(body.phone);
      const expected = codeHash(phone, body.code || '');
      const row = db.prepare(`
        SELECT id FROM otp_codes
        WHERE phone = ? AND code_hash = ? AND consumed_at IS NULL AND expires_at >= ?
        ORDER BY id DESC LIMIT 1
      `).get(phone, expected, now());
      if (!row) throw statusError(400, '验证码错误或已过期');
      db.prepare('UPDATE otp_codes SET consumed_at = ? WHERE id = ?').run(now(), row.id);
      const userId = upsertUser(phone);
      const token = makeToken(userId, phone, body.device_id || '');
      db.prepare('INSERT INTO sessions(user_id, token_hash, device_id, expires_at, created_at) VALUES (?, ?, ?, ?, ?)')
        .run(userId, sha256(token), String(body.device_id || ''), now() + TOKEN_TTL_SECONDS, now());
      return send(res, 200, { ok: true, token, user_id: userId, phone });
    }

    if (path.startsWith('/api/admin/')) {
      const adminActor = verifyAdminToken(req.headers.authorization || '');

      if (req.method === 'POST' && path === '/api/admin/model/probe') {
        const result = await probeAliyunTextModel();
        writeAuditLog(req, {
          actorType: 'admin',
          actorId: adminActor,
          action: 'admin.model_probe',
          metadata: { provider: result.provider, configured: result.configured, probe_used: result.probe_used }
        });
        return send(res, 200, result);
      }

      if (req.method === 'GET' && path === '/api/admin/audit-logs') {
        const logs = db.prepare(`
          SELECT id, actor_type, actor_id, action, target_user_id, ip, user_agent, metadata_json, created_at
          FROM audit_logs
          ORDER BY id DESC
          LIMIT 100
        `).all().map(row => ({
          id: row.id,
          actor_type: row.actor_type,
          actor_id: row.actor_id,
          action: row.action,
          target_user_id: row.target_user_id,
          ip: row.ip,
          user_agent: row.user_agent,
          metadata: parseJsonField(row.metadata_json),
          created_at: row.created_at
        }));
        return send(res, 200, { ok: true, logs });
      }

      if (req.method === 'GET' && path === '/api/admin/users') {
        const users = db.prepare(`
          SELECT
            u.id,
            u.phone,
            u.created_at,
            u.last_seen_at,
            hp.assistant_name,
            hp.owner_address,
            hp.updated_at AS profile_updated_at,
            (SELECT COUNT(*) FROM insights i WHERE i.user_id = u.id) AS insight_count,
            (SELECT COUNT(*) FROM memory_items mi WHERE mi.user_id = u.id) AS memory_count,
            (SELECT COUNT(*) FROM push_messages pm WHERE pm.user_id = u.id AND pm.read_at IS NULL) AS unread_message_count
          FROM users u
          LEFT JOIN health_profiles hp ON hp.user_id = u.id
          ORDER BY u.last_seen_at DESC
          LIMIT 100
        `).all();
        return send(res, 200, { ok: true, users });
      }

      const userMatch = path.match(/^\/api\/admin\/users\/(\d+)$/);
      const exportMatch = path.match(/^\/api\/admin\/users\/(\d+)\/export$/);
      if (req.method === 'GET' && exportMatch) {
        const target = userAuditSnapshot(Number(exportMatch[1]));
        const exported = exportUserData(target.id);
        writeAuditLog(req, {
          actorType: 'admin',
          actorId: adminActor,
          action: 'admin.user_export',
          targetUserId: target.id,
          metadata: { phone: target.masked_phone, insight_count: exported.insights.length, message_count: exported.push_messages.length }
        });
        return send(res, 200, exported);
      }

      if (req.method === 'DELETE' && userMatch) {
        const target = userAuditSnapshot(Number(userMatch[1]));
        const deleted = deleteUserData(target.id);
        writeAuditLog(req, {
          actorType: 'admin',
          actorId: adminActor,
          action: 'admin.user_delete',
          targetUserId: target.id,
          metadata: { phone: target.masked_phone }
        });
        return send(res, 200, deleted);
      }

      if (req.method === 'GET' && userMatch) {
        const adminUserId = Number(userMatch[1]);
        const user = db.prepare('SELECT id, phone, created_at, last_seen_at FROM users WHERE id = ?').get(adminUserId);
        if (!user) throw statusError(404, '用户不存在');
        const profile = db.prepare('SELECT * FROM health_profiles WHERE user_id = ?').get(adminUserId) || null;
        const insights = db.prepare(`
          SELECT id, category, source, severity, content, metadata_json, created_at
          FROM insights WHERE user_id = ?
          ORDER BY id DESC LIMIT 50
        `).all(adminUserId);
        const memories = db.prepare(`
          SELECT id, category, label, value, evidence, source, severity, confidence, mentions, created_at, updated_at
          FROM memory_items WHERE user_id = ?
          ORDER BY severity DESC, updated_at DESC LIMIT 80
        `).all(adminUserId);
        const messages = db.prepare(`
          SELECT id, title, body, priority, read_at, created_at
          FROM push_messages WHERE user_id = ?
          ORDER BY id DESC LIMIT 30
        `).all(adminUserId);
        writeAuditLog(req, {
          actorType: 'admin',
          actorId: adminActor,
          action: 'admin.user_detail',
          targetUserId: adminUserId,
          metadata: { phone: maskPhone(user.phone) }
        });
        return send(res, 200, { ok: true, user, profile, memories, insights, messages });
      }

      const messageMatch = path.match(/^\/api\/admin\/users\/(\d+)\/messages$/);
      if (req.method === 'POST' && messageMatch) {
        const adminUserId = Number(messageMatch[1]);
        const exists = db.prepare('SELECT id FROM users WHERE id = ?').get(adminUserId);
        if (!exists) throw statusError(404, '用户不存在');
        const body = await readBody(req);
        const id = createPushMessage(adminUserId, body.title, body.body, body.priority);
        writeAuditLog(req, {
          actorType: 'admin',
          actorId: adminActor,
          action: 'admin.message_create',
          targetUserId: adminUserId,
          metadata: { message_id: id, priority: Number(body.priority || 1), title: String(body.title || '小助手提醒').slice(0, 80) }
        });
        return send(res, 200, { ok: true, id });
      }

      throw statusError(404, '管理接口不存在');
    }

    const userId = verifyToken(req.headers.authorization || '');

    if (req.method === 'GET' && path === '/api/me/export') {
      const exported = exportUserData(userId);
      writeAuditLog(req, {
        actorType: 'user',
        actorId: String(userId),
        action: 'user.export_self',
        targetUserId: userId,
        metadata: { insight_count: exported.insights.length, message_count: exported.push_messages.length }
      });
      return send(res, 200, exported);
    }

    if (req.method === 'DELETE' && path === '/api/me') {
      const target = userAuditSnapshot(userId);
      const deleted = deleteUserData(userId);
      writeAuditLog(req, {
        actorType: 'user',
        actorId: String(userId),
        action: 'user.delete_self',
        targetUserId: userId,
        metadata: { phone: target.masked_phone }
      });
      return send(res, 200, deleted);
    }

    if (req.method === 'GET' && path === '/api/me') {
      const user = db.prepare('SELECT id, phone, created_at, last_seen_at FROM users WHERE id = ?').get(userId);
      const profile = db.prepare('SELECT * FROM health_profiles WHERE user_id = ?').get(userId) || null;
      const memories = db.prepare(`
        SELECT id, category, label, value, evidence, source, severity, confidence, mentions, created_at, updated_at
        FROM memory_items WHERE user_id = ?
        ORDER BY severity DESC, updated_at DESC LIMIT 80
      `).all(userId);
      return send(res, 200, { ok: true, user, profile, memories });
    }

    if (req.method === 'PUT' && path === '/api/profile') {
      const body = await readBody(req);
      db.prepare(`
        INSERT INTO health_profiles(user_id, assistant_name, assistant_identity, owner_address, health, medication, sleep, family, hobbies, care_preference, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(user_id) DO UPDATE SET
          assistant_name=excluded.assistant_name,
          assistant_identity=excluded.assistant_identity,
          owner_address=excluded.owner_address,
          health=excluded.health,
          medication=excluded.medication,
          sleep=excluded.sleep,
          family=excluded.family,
          hobbies=excluded.hobbies,
          care_preference=excluded.care_preference,
          updated_at=excluded.updated_at
      `).run(
        userId,
        body.assistant_name || '',
        body.assistant_identity || '',
        body.owner_address || '',
        body.health || '',
        body.medication || '',
        body.sleep || '',
        body.family || '',
        body.hobbies || '',
        body.care_preference || '',
        now()
      );
      saveInsight(userId, 'profile', JSON.stringify(body), 'profile', 1, {});
      return send(res, 200, { ok: true });
    }

    if (req.method === 'POST' && path === '/api/insights') {
      const body = await readBody(req);
      const content = String(body.content || '').trim();
      if (!content) throw statusError(400, '内容不能为空');
      return send(res, 200, { ok: true, ...saveInsight(userId, body.source || 'app', content, body.category, body.severity, body.metadata || {}) });
    }

    if (req.method === 'POST' && path === '/api/chat') {
      const body = await readBody(req);
      const message = String(body.message || '').trim();
      if (!message) throw statusError(400, '消息不能为空');
      saveInsight(userId, 'chat', message);
      db.prepare('INSERT INTO chat_messages(user_id, role, content, created_at) VALUES (?, ?, ?, ?)')
        .run(userId, 'user', message, now());
      const memorySaved = await extractAndSaveMemories(userId, message, 'chat');
      const result = await callCompanionModel(body.system_prompt || defaultSystemPrompt(), `${message}\n\n服务器长期记忆：\n${memoryContext(userId)}`, body.model || '');
      db.prepare('INSERT INTO chat_messages(user_id, role, content, created_at) VALUES (?, ?, ?, ?)')
        .run(userId, 'assistant', result.answer, now());
      return send(res, 200, {
        ok: true,
        answer: result.answer,
        model_used: result.used,
        model_provider: result.provider,
        model_name: result.model,
        memory_saved: memorySaved
      });
    }

    if (req.method === 'POST' && path === '/api/care/brief') {
      const body = await readBody(req);
      const result = await createCareBrief(userId, body);
      return send(res, 200, result);
    }

    if (req.method === 'GET' && path === '/api/news/brief') {
      const result = await fetchNewsBrief();
      return send(res, 200, result);
    }

    if (req.method === 'POST' && path === '/api/vision') {
      const body = await readBody(req);
      const prompt = String(body.prompt || '').trim();
      saveInsight(userId, 'vision', `${body.task || 'vision'} ${prompt.slice(0, 500)}`, classifyText(prompt), undefined, {});
      const result = await callVisionModel(
        body.system_prompt || defaultSystemPrompt(),
        `${prompt}\n\n服务器长期记忆：\n${memoryContext(userId)}`,
        String(body.image_base64 || ''),
        body.model || ''
      );
      return send(res, 200, {
        ok: true,
        answer: result.answer,
        vision_used: result.used,
        vision_provider: result.provider,
        vision_model_name: result.model
      });
    }

    if (req.method === 'POST' && path === '/api/audio') {
      const body = await readBody(req);
      const prompt = String(body.prompt || '').trim() || '请分析这段睡眠声音或声波摘要，只做生活提醒，不做诊断。';
      const summary = String(body.waveform_summary || '').trim();
      saveInsight(userId, 'audio', `${body.task || 'audio'} ${prompt.slice(0, 300)} ${summary.slice(0, 300)}`, classifyText(`${prompt} ${summary}`), undefined, {});
      const result = await callAudioModel(
        body.system_prompt || defaultSystemPrompt(),
        `${prompt}\n\n服务器长期记忆：\n${memoryContext(userId)}`,
        String(body.audio_data || ''),
        String(body.audio_format || 'wav'),
        summary,
        body.model || ''
      );
      return send(res, 200, {
        ok: true,
        answer: result.answer,
        audio_used: result.used,
        audio_provider: result.provider,
        audio_model_name: result.model
      });
    }

    if (req.method === 'GET' && path === '/api/messages/pending') {
      const messages = db.prepare(`
        SELECT id, title, body, priority, created_at FROM push_messages
        WHERE user_id = ? AND read_at IS NULL
        ORDER BY priority DESC, created_at ASC
        LIMIT 5
      `).all(userId);
      return send(res, 200, { ok: true, messages });
    }

    if (req.method === 'POST' && path === '/api/messages') {
      const body = await readBody(req);
      const id = createPushMessage(userId, body.title, body.body, body.priority);
      return send(res, 200, { ok: true, id });
    }

    const readMatch = path.match(/^\/api\/messages\/(\d+)\/read$/);
    if (req.method === 'POST' && readMatch) {
      db.prepare('UPDATE push_messages SET read_at = ? WHERE id = ? AND user_id = ?').run(now(), Number(readMatch[1]), userId);
      return send(res, 200, { ok: true });
    }

    throw statusError(404, '接口不存在');
  } catch (error) {
    const payload = { ok: false, error: error.message || String(error) };
    if (error.retry_after) payload.retry_after = error.retry_after;
    send(res, error.status || 500, payload);
  }
}

export function createServer() {
  const server = http.createServer(route);
  const upgradedSockets = new Set();
  server.on('upgrade', (req, socket) => {
    upgradedSockets.add(socket);
    socket.on('close', () => upgradedSockets.delete(socket));
    handleLiveUpgrade(req, socket);
  });
  const originalClose = server.close.bind(server);
  server.close = callback => {
    for (const socket of upgradedSockets) {
      try {
        socket.destroy();
      } catch (error) {
      }
    }
    upgradedSockets.clear();
    return originalClose(callback);
  };
  server.on('close', () => {
    for (const socket of upgradedSockets) {
      try {
        socket.destroy();
      } catch (error) {
      }
    }
    upgradedSockets.clear();
  });
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const host = env('GOUXIONG_HOST', '0.0.0.0');
  const port = Number(env('GOUXIONG_PORT', '8787'));
  const server = createServer();
  server.listen(port, host, () => {
    console.log(`${APP_NAME} server listening on http://${host}:${port}`);
    console.log(`database: ${dbPath()}`);
  });
}
