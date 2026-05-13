# DEPLOYMENT：本地开发环境部署

## 前置依赖

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
