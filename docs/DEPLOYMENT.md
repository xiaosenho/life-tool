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
