# 阿里云单机部署

这套 Compose 配置面向 2 核 2G 的低流量部署：

- 只公开前端 `8080`，PostgreSQL 与 Redis 不映射宿主机端口。
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
APP_CORS_ALLOWED_ORIGINS=http://121.43.254.83:8080,http://5aicv.cn:8080
```

修改后执行 `docker compose ... up -d --force-recreate backend`。缺少来源时，登录请求会返回
`Invalid CORS request`，前端可能显示成“用户名或密码不正确”。

2G 内存的机器重建前端时，先停止 `backend` 和 `web` 释放内存：

```bash
docker compose --env-file .env -f deploy/aliyun/compose.yaml stop web backend
docker compose --env-file .env -f deploy/aliyun/compose.yaml build web
docker compose --env-file .env -f deploy/aliyun/compose.yaml up -d backend web
```

公网只开放 `8080`。`/actuator/health` 可供探活，其他 Actuator 路径由 nginx 返回 404。

首次进入没有域名时，可通过 `http://服务器公网IP:8080` 验证文字功能。摄像头、麦克风和
浏览器安全 API 需要 HTTPS，正式公测前应配置域名与 TLS。
