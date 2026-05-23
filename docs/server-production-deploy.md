# 睡了么服务端生产部署说明

## 必填安全配置

生产环境至少配置：

```text
GOUXIONG_HOST=127.0.0.1
GOUXIONG_PORT=8787
GOUXIONG_DEV_SMS=0
GOUXIONG_SERVER_SECRET=<32位以上随机字符串>
GOUXIONG_ADMIN_TOKEN=<32位以上随机字符串>
GOUXIONG_ADMIN_BASIC_USER=admin
GOUXIONG_ADMIN_BASIC_PASSWORD=<后台页面独立密码>
GOUXIONG_BACKUP_DIR=/data/gouxiong/backups
```

短信和模型 Key 只放服务端环境变量，不进入 APK。

本地用 `server/start-server.ps1` 启动时，如果没有配置 `GOUXIONG_SERVER_SECRET` / `GOUXIONG_ADMIN_TOKEN`，脚本会自动生成 `server/data/.server-secret` 和 `server/data/admin-token.txt`。默认 `0.0.0.0` 启动且没有配置 `GOUXIONG_ADMIN_BASIC_PASSWORD` 时，还会生成 `server/data/admin-page-password.txt` 并启用 Basic Auth。生产环境建议显式配置到服务管理器或环境变量中。

## 启动前检查

```powershell
cd D:\www\睡了么\server
npm.cmd run ops:check
```

检查项包括服务端密钥、后台 API 令牌、后台页面 Basic Auth、阿里短信、阿里模型、数据库、备份目录、验证码限流和审计日志。

## 备份

```powershell
cd D:\www\睡了么\server
npm.cmd run backup
```

备份使用 SQLite `VACUUM INTO` 生成一致性数据库文件，并同时输出 `.json` 校验清单，包含路径、字节数和 SHA256。

## 恢复

恢复前先停止服务端。

```powershell
cd D:\www\睡了么\server
powershell -ExecutionPolicy Bypass -File .\restore-db.ps1 -BackupPath .\backups\gouxiong-20260517T120000Z.sqlite3
```

恢复脚本会先把当前数据库复制为 `*.before-restore-*` 安全副本，再覆盖目标数据库。恢复后重启服务端。

## 反向代理

生产部署不建议 Node 直接暴露公网。建议 Node 只监听 `127.0.0.1:8787`，公网由 HTTPS 反向代理进入。

### Nginx 示例

```nginx
server {
  listen 80;
  server_name sleep.example.com;
  return 301 https://$host$request_uri;
}

server {
  listen 443 ssl http2;
  server_name sleep.example.com;

  ssl_certificate /etc/letsencrypt/live/sleep.example.com/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/sleep.example.com/privkey.pem;

  client_max_body_size 6m;

  location / {
    proxy_pass http://127.0.0.1:8787;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
  }
}
```

### Caddy 示例

```caddyfile
sleep.example.com {
  encode gzip
  reverse_proxy 127.0.0.1:8787
}
```

## 防护边界

- `/admin` 页面启用 Basic Auth 后才适合放到内网或受控公网入口。
- `/api/admin/*` 仍必须携带 `GOUXIONG_ADMIN_TOKEN`。
- 验证码接口已有限流，但正式公网还应叠加 CDN/WAF、图形验证码或行为风控。
- 聊天会自动抽取长期记忆；这些数据属于用户画像，应纳入备份、导出、删除和审计范围。
- 删除用户会删除账号和关联业务数据，审计日志不级联删除，便于追溯。
- 当前仍是 SQLite 单机部署；多实例部署前不要多个进程同时写同一个数据库文件。
