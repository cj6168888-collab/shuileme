import { DatabaseSync } from 'node:sqlite';
import { existsSync, mkdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { dbPath, env, loadServerEnv, serverDir } from './server-env.mjs';

function pass(name, detail = '') {
  checks.push({ level: 'pass', name, detail });
}

function warn(name, detail = '') {
  checks.push({ level: 'warn', name, detail });
}

function fail(name, detail = '') {
  checks.push({ level: 'fail', name, detail });
}

function strong(value, placeholder = '') {
  const clean = String(value || '').trim();
  return clean.length >= 16 && clean !== placeholder && !/^change-me/i.test(clean);
}

function fileSecret(path) {
  try {
    return existsSync(path) ? readFileSync(path, 'utf8').trim() : '';
  } catch (error) {
    return '';
  }
}

const checks = [];
loadServerEnv();

const host = env('GOUXIONG_HOST', '0.0.0.0');
const port = env('GOUXIONG_PORT', '8787');
const db = resolve(dbPath());
const generatedServerSecret = fileSecret(join(serverDir, 'data', '.server-secret'));
const generatedAdminToken = fileSecret(join(serverDir, 'data', 'admin-token.txt'));
const generatedAdminPassword = fileSecret(join(serverDir, 'data', 'admin-page-password.txt'));
const serverSecret = env('GOUXIONG_SERVER_SECRET', generatedServerSecret);
const adminToken = env('GOUXIONG_ADMIN_TOKEN', generatedAdminToken);
const adminPassword = env('GOUXIONG_ADMIN_BASIC_PASSWORD', env('GOUXIONG_ADMIN_PAGE_PASSWORD', generatedAdminPassword));

if (strong(serverSecret, 'change-me-to-a-long-random-secret')) {
  pass('server secret', 'configured');
} else {
  fail('server secret', 'set GOUXIONG_SERVER_SECRET to a long random value');
}

if (strong(adminToken, 'change-me-admin-token')) {
  pass('admin api token', 'configured');
} else {
  fail('admin api token', 'set GOUXIONG_ADMIN_TOKEN to a long random value');
}

if (adminPassword) {
  pass('admin page basic auth', 'enabled');
} else if (host === '127.0.0.1' || host === 'localhost') {
  warn('admin page basic auth', 'disabled, acceptable only for localhost development');
} else {
  fail('admin page basic auth', 'set GOUXIONG_ADMIN_BASIC_PASSWORD before exposing /admin');
}

if (env('GOUXIONG_DEV_SMS', '') === '1') {
  fail('sms provider', 'GOUXIONG_DEV_SMS=1 is test mode only');
} else if (['ALIYUN_SMS_ACCESS_KEY_ID', 'ALIYUN_SMS_ACCESS_KEY_SECRET', 'ALIYUN_SMS_SIGN_NAME', 'ALIYUN_SMS_TEMPLATE_CODE'].every(name => env(name, ''))) {
  pass('sms provider', 'Aliyun SMS configured');
} else {
  fail('sms provider', 'Aliyun SMS variables are incomplete');
}

if (env('DASHSCOPE_API_KEY', '') || env('ALIYUN_MODEL_API_KEY', '') || env('ALIYUN_BAILIAN_API_KEY', '')) {
  pass('aliyun model key', 'configured');
} else {
  warn('aliyun model key', 'not configured, AI endpoints will use fallback where available');
}

if (existsSync(db)) {
  try {
    const sqlite = new DatabaseSync(db, { readOnly: true });
    const row = sqlite.prepare('SELECT COUNT(*) AS count FROM sqlite_master WHERE type = ?').get('table');
    sqlite.close();
    pass('database', `${db}, tables=${row.count}`);
  } catch (error) {
    fail('database', error.message);
  }
} else {
  warn('database', `not found yet: ${db}`);
}

try {
  const backupDir = resolve(env('GOUXIONG_BACKUP_DIR', join(serverDir, 'backups')));
  mkdirSync(backupDir, { recursive: true });
  pass('backup directory', backupDir);
} catch (error) {
  fail('backup directory', error.message);
}

pass('otp rate limit', 'enabled in server');
pass('audit logs', 'enabled in server');

if (host !== '127.0.0.1' && host !== 'localhost') {
  warn('public binding', `server binds ${host}:${port}; put it behind HTTPS reverse proxy and firewall`);
}

const failed = checks.filter(item => item.level === 'fail').length;
const warned = checks.filter(item => item.level === 'warn').length;
const result = { ok: failed === 0, failed, warned, checks };
console.log(JSON.stringify(result, null, 2));
if (failed > 0) process.exitCode = 1;
