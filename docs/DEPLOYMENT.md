# DEPLOYMENT：本地开发与阿里云 ECS 部署

## 本地开发前置依赖

- Docker & Docker Compose
- Java 21+
- Maven 3.9+

## 1. 启动基础设施

```bash
# 启动 PostgreSQL 和 Redis
docker compose up -d

# 检查服务状态
docker compose ps

# 查看日志
docker compose logs -f
```

### 端口映射

| 服务 | 容器内端口 | 宿主机端口 |
| --- | --- | --- |
| PostgreSQL | 5432 | 5432 |
| Redis | 6379 | 6379 |

### 默认数据库账号

| 字段 | 值 |
| --- | --- |
| 数据库 | lifetool |
| 用户名 | lifetool |
| 密码 | lifetool_dev |

## 2. 配置后端

```bash
# 复制环境变量模板
cp backend/.env.example backend/.env

# 根据实际情况编辑 backend/.env（开发环境通常无需修改）
```

当前 `.env.example` 中的配置与 Spring Boot 3 命名保持一致：

- `SPRING_DATASOURCE_*` 对应数据库连接。
- `SPRING_DATA_REDIS_*` 对应 Redis 连接。
- `JWT_SECRET`、`JWT_ACCESS_TTL_MS`、`JWT_REFRESH_TTL_MS` 对应后端 JWT 配置。

> 注意：当前 `docker-compose.yml` 只提供 PostgreSQL 和 Redis 基础设施，后端默认直接运行在宿主机/本机 Maven 进程中，所以数据库和 Redis 地址使用 `localhost`。

## 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认监听 `http://localhost:8080`。

## 4. 停止

```bash
# 停止后端：Ctrl + C

# 停止基础设施（保留数据卷）
docker compose stop

# 停止并删除容器
docker compose down
```

## 5. 清理数据

```bash
# 停止并删除容器 + 数据卷（数据会丢失）
docker compose down -v
```

## 6. 常用命令速查

| 操作 | 命令 |
| --- | --- |
| 启动基础设施 | `docker compose up -d` |
| 重启基础设施 | `docker compose restart` |
| 查看所有容器 | `docker compose ps` |
| 查看实时日志 | `docker compose logs -f` |
| 停止基础设施 | `docker compose stop` |
| 停止并删除容器 | `docker compose down` |
| 完全清理（含数据卷） | `docker compose down -v` |

---

# 阿里云 ECS 生产部署

## 前置准备

- 一台阿里云 ECS 实例（推荐 2C4G 以上，Ubuntu 22.04 / CentOS 7+）。
- 已绑定弹性公网 IP。
- 已配置域名（可选，推荐使用域名而非 IP 访问）。

## 1. 安全组配置

登录阿里云 ECS 控制台 → 安全组 → 配置规则（入方向），按最小权限原则开放端口：

| 端口 | 协议 | 源 | 用途 |
| --- | --- | --- | --- |
| 22 | TCP | 你的办公网络 IP（不要设为 0.0.0.0） | SSH 登录 |
| 80 | TCP | 0.0.0.0/0 | HTTP 重定向到 HTTPS |
| 443 | TCP | 0.0.0.0/0 | HTTPS 后端 API |

**禁止开放端口**（默认不对公网暴露）：

- `5432`（PostgreSQL）
- `6379`（Redis）
- `8080`（后端直接暴露）

> 说明：docker-compose.yml 已将 PostgreSQL 和 Redis 的端口绑定到 127.0.0.1，即使安全组误开放了端口，外部也无法直连，但安全组层面仍然建议不开放。

## 2. SSH 登录服务器

```bash
# 使用密钥登录
ssh -i ~/.ssh/your-key.pem root@<ECS-公网IP>

# 或者使用密码登录（生产环境建议使用密钥）
ssh root@<ECS-公网IP>
```

首次登录后建议执行：

```bash
# 更新系统
apt update && apt upgrade -y

# 设置主机名
hostnamectl set-hostname lifetool-prod
```

## 3. 安装 Docker

```bash
# 使用官方脚本安装 Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 安装 Docker Compose 插件（如未自带）
sudo apt install -y docker-compose-plugin

# 验证安装
docker --version
docker compose version

# 将当前用户加入 docker 组（可选，避免每次 sudo）
sudo usermod -aG docker $USER
# 重新登录使组生效
```

## 4. 部署项目

```bash
# 创建项目目录
mkdir -p /opt/lifetool
cd /opt/lifetool

# 克隆代码
git clone <仓库地址> .
# 或者手动上传 docker-compose.yml、backend/、scripts/ 等

# 创建生产环境变量文件
cat > .env << 'EOF'
POSTGRES_PASSWORD=<生成一个强密码>
EOF
chmod 600 .env

# 创建后端生产环境变量。当前版本推荐后端直接运行在 ECS 宿主机上，
# 因此数据库和 Redis 使用 localhost。
cat > backend/.env << 'EOF'
SERVER_PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/lifetool
SPRING_DATASOURCE_USERNAME=lifetool
SPRING_DATASOURCE_PASSWORD=<与上面 POSTGRES_PASSWORD 一致>
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
JWT_SECRET=<通过 openssl rand -base64 32 生成>
JWT_ACCESS_TTL_MS=900000
JWT_REFRESH_TTL_MS=604800000
EOF
chmod 600 backend/.env
```

> 如果后续把后端也容器化并放进同一个 Docker Compose 网络，再把 `SPRING_DATASOURCE_URL` 改为 `jdbc:postgresql://postgres:5432/lifetool`，把 `SPRING_DATA_REDIS_HOST` 改为 `redis`。

### 启动服务

```bash
docker compose up -d

# 检查状态
docker compose ps

# 查看日志
docker compose logs -f
```

### 启动后端

当前版本后端直接运行在 ECS 宿主机上：

```bash
cd /opt/lifetool/backend
set -a
source .env
set +a
mvn spring-boot:run
```

生产环境建议后续补充 systemd 服务或后端 Docker 镜像，避免 SSH 断开后进程退出。

### 配置反向代理（Nginx）

推荐在宿主机安装 Nginx，通过 Nginx 把公网 HTTPS 流量反代到后端本机 8080 端口：

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
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

SSL 证书推荐使用 [acme.sh](https://github.com/acme-sh/acme.sh) 或 certbot 自动申请 Let's Encrypt 证书。

## 5. 数据备份

```bash
# 创建备份目录
mkdir -p /opt/backups

# PostgreSQL 备份
docker exec lifetool-postgres pg_dump -U lifetool lifetool > /opt/backups/lifetool_$(date +%Y%m%d_%H%M%S).sql

# 保留最近 7 天备份，清理旧备份
find /opt/backups -name "lifetool_*.sql" -mtime +7 -delete
```

建议配置 crontab 定时备份：

```bash
crontab -e
# 每天凌晨 3 点备份
0 3 * * * docker exec lifetool-postgres pg_dump -U lifetool lifetool > /opt/backups/lifetool_$(date +\%Y\%m\%d).sql && find /opt/backups -name "lifetool_*.sql" -mtime +7 -delete
```

## 6. 日志管理

```bash
# 查看实时日志
docker compose logs -f

# 查看最近 100 行
docker compose logs --tail=100

# 查看特定容器日志
docker compose logs postgres
docker compose logs redis
```

Docker 日志默认保存在 `/var/lib/docker/containers/`。建议配置日志轮转：

```bash
# 修改 /etc/docker/daemon.json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}

# 重启 Docker 使配置生效
systemctl restart docker
```

## 7. 升级流程

```bash
cd /opt/lifetool

# 拉取最新代码
git pull

# 重启基础设施
docker compose up -d

# 验证服务正常
docker compose ps
curl http://localhost:8080/api/health
```

---

# 腾讯云 COS 媒体存储配置

本应用使用腾讯云 COS（Cloud Object Storage）作为媒体存储后端，主要用于存储用户上传的饮食图片。

## 1. 创建 COS Bucket

1. 登录 [腾讯云 COS 控制台](https://console.cloud.tencent.com/cos5)。
2. 点击「创建存储桶」，配置如下：

| 配置项 | 推荐值 |
| --- | --- |
| 名称 | `lifetool-media-{环境}`，如 `lifetool-media-prod`。腾讯云实际 bucket 会带 APPID 后缀，例如 `lifetool-media-prod-1234567890` |
| 所属地域 | 选择距离用户最近的区域，如 `ap-guangzhou`（华南）、`ap-shanghai`（华东） |
| 访问权限 | **私有读写**（私有读 + 私有写） |
| 版本控制 | 建议关闭（减少存储成本） |

3. 创建完成后，记录 Bucket 名称和所在区域。

## 2. 配置 CORS

出于安全考虑，必须为 Bucket 配置跨域访问规则，允许移动端客户端直传：

进入 Bucket → 安全管理 → 跨域访问 CORS 设置 → 添加规则：

```json
[
  {
    "AllowedOrigin": ["*"],
    "AllowedMethod": ["PUT", "GET", "HEAD", "OPTIONS"],
    "AllowedHeader": ["*"],
    "ExposeHeader": ["ETag", "x-cos-request-id"],
    "MaxAgeSeconds": 3600
  }
]
```

> 注意：`AllowedOrigin` 设为 `*` 是因为移动端 App 没有固定域名来源。如果后续有固定 Web 端域名，可以替换为具体域名。

## 3. 创建最小权限子账号

不要使用主账号密钥，必须创建子账号并授予最小权限。

1. 进入 [腾讯云访问管理（CAM）](https://console.cloud.tencent.com/cam)。
2. 创建用户 → 新建自定义权限策略 → 选择 JSON 方式：

```json
{
  "version": "2.0",
  "statement": [
    {
      "effect": "allow",
      "action": [
        "cos:PutObject",
        "cos:GetObject",
        "cos:HeadObject",
        "cos:DeleteObject",
        "cos:PostObject"
      ],
      "resource": [
        "qcs::cos:ap-guangzhou:uid/12345678:lifetool-media-prod-12345678/*"
      ]
    }
  ]
}
```

> `resource` 中的 `ap-guangzhou` 替换为你的 Bucket 地域，`12345678` 替换为你的腾讯云 APPID（可在「账号信息」中查看），`lifetool-media-prod-12345678` 为 Bucket 完整域名（含 appid）。

3. 将该策略关联到新建的子账号。
4. 生成子账号的 **SecretId** 和 **SecretKey**，由后端持有。

## 4. 环境变量配置

在 `backend/.env` 添加以下配置：

```bash
# COS 地域，与 Bucket 创建时选择的地域一致
COS_REGION=ap-guangzhou
# Bucket 完整名称，通常包含 APPID 后缀
COS_BUCKET=lifetool-media-prod-1234567890
# 子账号 SecretId
COS_SECRET_ID=your-secret-id
# 子账号 SecretKey
COS_SECRET_KEY=your-secret-key
# CDN 加速域名或 COS 源站域名，后端据此生成对象地址
# 私有 bucket 不应直接公网读，后续可改为后端签发短有效期下载 URL
COS_PUBLIC_BASE_URL=https://lifetool-media-prod-1234567890.cos.ap-guangzhou.myqcloud.com
# 上传授权临时令牌有效期（秒），建议 300（5 分钟）
COS_UPLOAD_TOKEN_TTL_SECONDS=300
# 单张图片最大字节数（默认 10MB）
MEDIA_MAX_IMAGE_BYTES=10485760
```

后端只有在 `COS_SECRET_ID`、`COS_SECRET_KEY`、`COS_BUCKET`、`COS_REGION` 都配置后才会签发真实腾讯云 COS 预签名 PUT URL；本地开发未配置密钥时会返回 `http://localhost:8080/mock-cos/...`，前端会把它当作模拟上传处理。

> 注意：预签名上传 URL 使用 COS 源站域名生成，不使用 CDN 域名上传。`COS_PUBLIC_BASE_URL` 后续主要用于展示/下载图片地址，私有读场景建议改为后端签发短有效期下载 URL。

## 5. 在阿里云 ECS 上注入 COS 配置

在阿里云 ECS 服务器上，COS 配置直接追加到已有的 `backend/.env` 文件中：

```bash
cat >> /opt/lifetool/backend/.env << 'EOF'

# COS
COS_REGION=ap-guangzhou
COS_BUCKET=lifetool-media-prod-1234567890
COS_SECRET_ID=<子账号 SecretId>
COS_SECRET_KEY=<子账号 SecretKey>
COS_PUBLIC_BASE_URL=https://lifetool-media-prod-1234567890.cos.ap-guangzhou.myqcloud.com
COS_UPLOAD_TOKEN_TTL_SECONDS=300
MEDIA_MAX_IMAGE_BYTES=10485760
EOF
chmod 600 /opt/lifetool/backend/.env
```

然后重新启动后端使配置生效：

```bash
sudo systemctl restart lifetool-backend
# 或如果后端直接运行在 Maven 进程中：
cd /opt/lifetool/backend && pkill -f "spring-boot:run" && set -a && source .env && set +a && nohup mvn spring-boot:run > /tmp/lifetool.log 2>&1 &
```

## 6. 安全注意事项

| 项目 | 建议 |
| --- | --- |
| 密钥管理 | COS_SECRET_ID / COS_SECRET_KEY 是敏感凭据，**必须**通过 `.env` 文件注入，**不得**硬编码在代码或提交到 Git |
| 子账号隔离 | 生产环境和开发环境使用不同的 Bucket 和不同的子账号密钥 |
| 客户端授权 | **客户端不得持有长期密钥**。客户端通过 `POST /api/media/upload-token` 获取临时、短有效期、绑定当前登录用户的上传授权（STS 临时密钥或预签名 URL） |
| 上传限制 | 后端签发上传授权时验证文件类型和大小，只允许 `image/jpeg`、`image/png`、`image/webp`，默认不超过 10MB |
| 最小权限 | CAM 策略只允许对指定 Bucket 下的文件进行 Put、Get、Head、Delete、Post 操作，不允许 ListAllMyBuckets 等管理操作 |
| HTTPS | COS 默认支持 HTTPS，与 CDN 配合时确保回源和访问链路均为 HTTPS |
| 对象生命周期 | 建议配置存储桶生命周期规则，自动清理未确认的临时文件或多天前的无效碎片 |
| 日志审计 | 开启 COS 日志记录或通过云审计追踪密钥调用记录 |

## 7. 当前支持的文件类型和限制

| 项 | 值 |
| --- | --- |
| 允许的 MIME 类型 | `image/jpeg`、`image/png`、`image/webp` |
| 单文件大小上限 | 10 MB（可通过 `MEDIA_MAX_IMAGE_BYTES` 调整） |
| 上传授权有效期 | 300 秒（5 分钟，可通过 `COS_UPLOAD_TOKEN_TTL_SECONDS` 调整） |

> 客户端上传流程：客户端调用 `POST /api/media/upload-token` 获取短有效期上传凭证 → 客户端直传 COS → 上传完成后调用 `POST /api/media/assets` 创建媒体资产记录 → 后端记录文件元数据并关联当前用户。

后端当前使用腾讯云 XML Java SDK `com.qcloud:cos_api` 生成 `PUT` 预签名 URL，客户端不得持有 `COS_SECRET_ID` 或 `COS_SECRET_KEY`。

## 8. 安全注意事项

| 项目 | 建议 |
| --- | --- |
| SSH 登录 | 使用密钥登录，禁用密码登录；修改默认端口（可选） |
| 防火墙 | 除了安全组，ECS 内部 iptables/ufw 也应限制 |
| 数据库密码 | 生产环境使用强密码，不要使用默认密码 |
| JWT 密钥 | 使用 `openssl rand -base64 32` 生成 256 位密钥 |
| 敏感文件 | `.env` 文件权限设为 600，不要提交到 Git |
| 系统更新 | 定期执行 `apt update && apt upgrade -y` |
| Docker 版本 | 保持 Docker Engine 和 Compose 为最新稳定版 |
| 监控 | 建议配置云监控或 Prometheus + Grafana |
