import { DatabaseSync } from 'node:sqlite';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';
import { dbPath, env, loadServerEnv, serverDir } from './server-env.mjs';

function sqlString(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

function timestamp() {
  return new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
}

loadServerEnv();

const source = resolve(dbPath());
if (!existsSync(source)) {
  throw new Error(`database not found: ${source}`);
}

const backupDir = resolve(env('GOUXIONG_BACKUP_DIR', join(serverDir, 'backups')));
mkdirSync(backupDir, { recursive: true });

const target = join(backupDir, `${basename(source, '.sqlite3')}-${timestamp()}.sqlite3`);
if (existsSync(target)) {
  throw new Error(`backup already exists: ${target}`);
}

const db = new DatabaseSync(source, { readOnly: true });
try {
  db.exec(`VACUUM INTO ${sqlString(target)}`);
} finally {
  db.close();
}

const bytes = readFileSync(target);
const sha256 = createHash('sha256').update(bytes).digest('hex').toUpperCase();
const manifest = {
  ok: true,
  created_at: Math.floor(Date.now() / 1000),
  source,
  backup: target,
  bytes: statSync(target).size,
  sha256
};
writeFileSync(`${target}.json`, JSON.stringify(manifest, null, 2));
console.log(JSON.stringify(manifest));
