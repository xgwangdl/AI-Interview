# 阿里云单机部署

这套 Compose 配置面向 2 核 2G 的低流量部署：

- Caddy 公开 `80/443` 并为域名自动申请、续期 HTTPS 证书。
- 前端 `8080` 保留为过渡入口，PostgreSQL 与 Redis 不映射宿主机端口。
- 后端最多使用 900MB，PostgreSQL 500MB，Redis 96MB。
- 公网代码沙箱固定关闭。
- 录像默认保存在 Docker 卷 `app-media` 中。

在仓库根目录准备好生产 `.env` 后执行：

```bash
docker compose --env-file .env -f deploy/aliyun/compose.yaml build backend
docker compose --env-file .env -f deploy/aliyun/compose.yaml build web
docker compose --env-file .env -f deploy/aliyun/compose.yaml up -d
docker compose --env-file .env -f deploy/aliyun/compose.yaml ps
```

每次更换公网域名或端口，都要把完整来源加入 `.env`，多个来源用逗号分隔：

```dotenv
APP_CORS_ALLOWED_ORIGINS=http://121.43.254.83:8080,http://5aicv.cn:8080,https://5aicv.cn
```

修改后执行 `docker compose ... up -d --force-recreate backend`。缺少来源时，登录请求会返回
`Invalid CORS request`，前端可能显示成“用户名或密码不正确”。

2G 内存的机器重建前端时，先停止 `backend` 和 `web` 释放内存：

```bash
docker compose --env-file .env -f deploy/aliyun/compose.yaml stop web backend
docker compose --env-file .env -f deploy/aliyun/compose.yaml build web
docker compose --env-file .env -f deploy/aliyun/compose.yaml up -d backend web
```

公网开放 `80/443`；过渡期可同时保留 `8080`。`/actuator/health` 可供探活，其他 Actuator 路径由 nginx 返回 404。

## 域名与 HTTPS

`deploy/aliyun/Caddyfile` 默认配置 `5aicv.cn`，Caddy 会自动申请和续期 Let's Encrypt 证书，并把 HTTP 请求跳转到 HTTPS：

```bash
docker compose --env-file .env -f deploy/aliyun/compose.yaml up -d caddy
docker compose --env-file .env -f deploy/aliyun/compose.yaml logs -f caddy
```

更换域名时，同时修改 `Caddyfile` 的域名、DNS A/AAAA 记录和 `APP_CORS_ALLOWED_ORIGINS`。云安全组需放行 TCP `80`、TCP `443`；如果使用 HTTP/3，还需放行 UDP `443`。
