import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const serverDir = dirname(fileURLToPath(import.meta.url));
export const projectDir = dirname(serverDir);

export function env(name, fallback = '') {
  return process.env[name] && process.env[name].length > 0 ? process.env[name] : fallback;
}

export function loadEnvFile(path) {
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

export function loadServerEnv() {
  loadEnvFile(join(serverDir, '.env'));
  loadEnvFile(join(projectDir, '.env'));
  loadEnvFile(process.env.GOUXIONG_ENV_FILE || '');
  loadEnvFile(process.env.GOUXIONG_ALIYUN_SMS_ENV_FILE || '');
  loadEnvFile(process.env.GOUXIONG_ALIYUN_MODEL_ENV_FILE || '');
  loadEnvFile(join(process.env.USERPROFILE || '', 'Desktop', 'aliyun-sms.env'));
  loadEnvFile(join(process.env.USERPROFILE || '', 'Desktop', 'guanlin-aliyun-ai.env'));
}

export function dbPath() {
  return env('GOUXIONG_DB_PATH', join(serverDir, 'data', 'gouxiong.sqlite3'));
}
