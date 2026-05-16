# DEPLOYMENT：本地开发与阿里云 ECS 部署

## 本地开发前置依赖

- Docker & Docker Compose
- （可选）Java 21+ / Maven 3.9+，用于本地 IDE 开发

## 1. 一键启动（推荐）

前后端 + 数据库 + 缓存全部通过 Docker Compose 启动：

```bash
# 复制环境变量模板（如首次使用）
cp .env.docker.example .env.docker

# 按需编辑 .env.docker（本地开发通常无需修改）
# vim .env.docker

# 启动全部服务
docker compose --env-file .env.docker up -d --build

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f
```

首次 `--build` 会编译后端 Docker 镜像（约 3-5 分钟），后续重启无需重复构建：

```bash
docker compose --env-file .env.docker up -d
```

### 端口映射

| 服务 | 容器内端口 | 宿主机端口 |
| --- | --- | --- |
| Backend (Spring Boot) | 8091 | 8091 |
| PostgreSQL | 5432 | 5432 |
| Redis | 6379 | 6379 |

> PostgreSQL 和 Redis 端口绑定到 127.0.0.1，默认不对公网暴露。Backend 端口 8091 已对外开放。

### 服务架构

```
docker compose 网络
┌─────────────┐  ┌──────────┐  ┌──────────┐
│   backend   │  │ postgres  │  │  redis   │
│   :8091     │──│  :5432    │  │  :6379   │
└──────┬──────┘  └──────────┘  └──────────┘
       │
  127.0.0.1:8080
```

容器间通过服务名通信（如 `jdbc:postgresql://postgres:5432/lifetool`），无需使用 localhost 或宿主机 IP。

## 2. 仅启动基础设施（本地 IDE 开发后端）

如果需要在宿主机用 IDE 开发/调试后端，可以只启动基础设施：

```bash
docker compose up -d postgres redis
```

然后在 IDE 或终端中启动 Spring Boot（会直连本机 5432/6379 端口）。

## 3. 后端 Docker 镜像说明

### 构建

```bash
cd backend
docker build -t lifetool-backend .
```

Dockerfile 采用多阶段构建：

- **Stage 1 (builder)**：`maven:3.9-eclipse-temurin-21-alpine`，使用阿里云 Maven 镜像加速依赖下载，执行 `mvn package`
- **Stage 2 (runtime)**：`eclipse-temurin:21-jre-alpine`，仅包含 JRE，以 `lifetool` 用户运行

### 环境变量

后端从环境变量读取配置（Spring Boot 风格）。完整列表见 `.env.docker.example`。

关键变量：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `SERVER_PORT` | 后端监听端口 | 8080 |
| `SPRING_DATASOURCE_URL` | 数据库连接地址 | `jdbc:postgresql://postgres:5432/lifetool` |
| `SPRING_DATA_REDIS_HOST` | Redis 地址 | `redis` |
| `JWT_SECRET` | JWT 签名密钥 | （见模板） |
| `LIFETOOL_AI_MOCK_ENABLED` | 是否使用 AI mock | true |
| `AI_API_KEY` | AI 模型 API Key | mock-key |

> Docker 环境中，数据库和 Redis 地址使用 **容器服务名**（`postgres` / `redis`），而非 localhost。

## 4. 停止与清理

```bash
# 停止所有服务（保留数据卷）
docker compose stop

# 停止并删除容器
docker compose down

# 停止并删除容器 + 数据卷（数据丢失）
docker compose down -v
```

## 5. 常用命令速查

| 操作 | 命令 |
| --- | --- |
| 一键启动全部 | `docker compose --env-file .env.docker up -d --build` |
| 仅启动基础设施 | `docker compose up -d postgres redis` |
| 重启后端 | `docker compose restart backend` |
| 查看实时日志 | `docker compose logs -f` |
| 查看后端日志 | `docker compose logs -f backend` |
| 进入后端容器 | `docker compose exec backend sh` |
| 重新构建后端 | `docker compose build backend` |
| 停止全部 | `docker compose stop` |
| 完全清理 | `docker compose down -v` |

---

# 阿里云 ECS 生产部署

## 前置准备

- 一台阿里云 ECS 实例（推荐 2C4G 以上，Ubuntu 22.04）。
- 已绑定弹性公网 IP。
- 已配置域名（可选，推荐使用域名）。

## 1. 安全组配置

登录阿里云 ECS 控制台 → 安全组 → 配置规则（入方向）：

| 端口 | 协议 | 源 | 用途 |
| --- | --- | --- | --- |
| 22 | TCP | 你的办公 IP | SSH |
| 80 | TCP | 0.0.0.0/0 | HTTP → HTTPS |
| 443 | TCP | 0.0.0.0/0 | HTTPS (Nginx → Backend) |
| 8091 | TCP | 0.0.0.0/0 | Backend API（直连或测试用） |

> PostgreSQL（5432）、Redis（6379）端口已绑定 127.0.0.1，外部无法直连。Backend 端口 8091 已对外开放。

## 2. SSH 登录 & 安装 Docker

```bash
ssh -i ~/.ssh/your-key.pem root@<ECS-公网IP>

# 更新系统
apt update && apt upgrade -y

# 安装 Docker
curl -fsSL https://get.docker.com | sh

# 安装 Docker Compose 插件
apt install -y docker-compose-plugin

# 验证
docker --version
docker compose version
```

## 3. 部署项目

```bash
# 创建项目目录
mkdir -p /opt/lifetool && cd /opt/lifetool

# 上传项目文件（通过 git clone 或 scp）
git clone <仓库地址> .

# 复制并编辑生产环境配置
cp .env.docker.example .env.docker
chmod 600 .env.docker

# 编辑生产配置：
#   - SPRING_DATASOURCE_PASSWORD: 强密码
#   - POSTGRES_PASSWORD: 同上
#   - JWT_SECRET: openssl rand -base64 32 生成
#   - LIFETOOL_AI_MOCK_ENABLED: false
#   - AI_API_KEY: 实际 API Key
#   - AI_CHAT_MODEL: 实际模型名
```

### 生产 .env.docker 示例

```bash
SERVER_PORT=8091
SPRING_PROFILES_ACTIVE=postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/lifetool
SPRING_DATASOURCE_USERNAME=pgvector
SPRING_DATASOURCE_PASSWORD=<强密码>
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379
POSTGRES_PASSWORD=<与上面相同的密码>
JWT_SECRET=<openssl rand -base64 32>
LIFETOOL_DB_MIGRATION_ENABLED=true
LIFETOOL_AI_MOCK_ENABLED=false
AI_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
AI_CHAT_COMPLETIONS_PATH=/chat/completions
AI_API_KEY=<真实API Key>
AI_CHAT_MODEL=doubao-seed-2-0-mini-260428
```

### 启动

```bash
docker compose --env-file .env.docker up -d --build

# 检查状态
docker compose ps
docker compose logs -f
```

## 4. 配置反向代理（Nginx）

推荐在宿主机安装 Nginx，把 HTTPS 流量反代到后端容器：

```bash
apt install -y nginx
```

```nginx
# /etc/nginx/sites-available/lifetool
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate /etc/nginx/ssl/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8091;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # API 路径
    location /api/ {
        proxy_pass http://127.0.0.1:8091;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

SSL 证书推荐使用 [acme.sh](https://github.com/acme-sh/acme.sh) 自动申请 Let's Encrypt。

## 5. 数据备份

```bash
# PostgreSQL 备份
docker exec lifetool-postgres pg_dump -U pgvector lifetool > /opt/backups/lifetool_$(date +%Y%m%d_%H%M%S).sql

# 保留最近 7 天
find /opt/backups -name "lifetool_*.sql" -mtime +7 -delete
```

crontab 定时备份：

```bash
crontab -e
# 每天凌晨 3 点
0 3 * * * docker exec lifetool-postgres pg_dump -U pgvector lifetool > /opt/backups/lifetool_$(date +\%Y\%m\%d).sql && find /opt/backups -name "lifetool_*.sql" -mtime +7 -delete
```

## 6. 日志管理

```bash
# 查看服务日志
docker compose logs -f backend

# 配置 Docker 日志轮转 /etc/docker/daemon.json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}

systemctl restart docker
```

## 7. 升级流程

```bash
cd /opt/lifetool
git pull
docker compose --env-file .env.docker up -d --build backend

# 验证
curl http://localhost:8080/api/health
```

---

# 腾讯云 COS 媒体存储配置

（保持不变，略）

---

# AI 接口排障记录

本节记录 2026-05 中 AI 会话与 AI 识图排障过程中踩过的坑，后续部署或排障时优先参考。

## 1. AI 识图 / AI 会话常见故障

### 1.1 现象一：AI 识图报错，日志中出现 `Error while downloading ... status code: 403`

第一直觉通常会怀疑 COS 签名 URL 过期，但这并不一定成立。实际排查时应先通过日志确认：

- 后端生成的 `q-sign-time`
- AI 请求发起时间
- 是否在 5 分钟有效期内

如果签名时间是新鲜的，但调用仍失败，需要继续看后续日志，判断失败点是否已经从 COS 转移到模型接口自身。

### 1.2 现象二：AI 识图 / AI 会话报 `404 Not Found: [no body]`

这类错误在本项目中已经确认过一次真实根因：**不是 COS、不是用户鉴权，而是豆包兼容接口的 completions 路径被代码写死成了错误地址。**

错误组合：

```text
AI_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
代码里写死 /v1/chat/completions
```

最终会请求到：

```text
https://ark.cn-beijing.volces.com/api/v3/v1/chat/completions
```

该地址返回 404。

正确做法：

```text
AI_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
AI_CHAT_COMPLETIONS_PATH=/chat/completions
```

代码中必须通过配置读取 `spring.ai.openai.chat.completions-path`，不要把 `/v1/chat/completions` 写死在 `RestClient` 调用里。

### 1.3 现象三：AI 会话前端提示像是 401

如果后端日志里真正抛出的是：

```text
org.springframework.web.client.HttpClientErrorException$NotFound: 404 Not Found: [no body]
```

那么这不是用户登录态失效，而是外部 AI 接口调用失败。应优先检查：

1. `AI_BASE_URL`
2. `AI_CHAT_COMPLETIONS_PATH`
3. `AI_CHAT_MODEL`
4. 外部服务返回码和响应体

不要仅凭前端提示文案把问题误判成登录鉴权失败。

## 2. 推荐 AI 环境变量

豆包 Ark OpenAI-compatible 接口建议使用以下组合：

```bash
LIFETOOL_AI_MOCK_ENABLED=false
AI_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
AI_CHAT_COMPLETIONS_PATH=/chat/completions
AI_API_KEY=<真实 API Key>
AI_CHAT_MODEL=doubao-seed-2-0-mini-260428
```

注意：

- `AI_BASE_URL` 负责主机和前缀
- `AI_CHAT_COMPLETIONS_PATH` 负责具体 completions 资源路径
- 二者不要重复拼出 `/v1`

## 3. 部署后自检清单

后端启动后，建议至少执行以下检查：

```bash
docker compose logs -f backend
curl http://127.0.0.1:8091/api/health
```

AI 功能排查时，重点观察日志中是否出现：

- `AI food recognition start`
- `AI image recognition request`
- `Calling AI chatWithImage via direct REST API`
- `AI chatWithImage direct REST call failed`

如果 AI 会话失败，还要看：

- `SpringAiAssistantClient.chatWithMedia(...)`

优先确认是：

- COS 下载失败
- completions 路径错误
- API key / 模型名错误
- 外部模型服务返回 4xx / 5xx
