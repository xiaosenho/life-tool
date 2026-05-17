# Backend

LifeTool 服务端，基于 Java 21、Spring Boot 和 Maven。

## 当前能力

- `GET /api/health`：健康检查接口。
- 认证、专注、习惯、好友、排行榜、饮食、记账、纪念日、媒体、AI、新闻、背单词等核心 API。
- PostgreSQL + Redis + COS 集成，`postgres` profile 下使用 HikariCP 连接池。
- Flyway 迁移已覆盖 `V1` 到 `V9`。
- 基础 Web MVC 与核心业务测试。

## 本地命令

```bash
mvn test
mvn spring-boot:run
```

本工程使用 `backend/.mvn/settings.xml` 覆盖全局 Maven settings，避免本项目依赖解析默认走公司私有仓库。

如需使用数据库 profile，本地建议同时准备 PostgreSQL 与 Redis，并通过环境变量注入：

```bash
SPRING_PROFILES_ACTIVE=postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/lifetool
SPRING_DATA_REDIS_HOST=127.0.0.1
```

## 模块边界

服务端职责：

- 用户认证与授权
- 数据同步 API
- 专注、习惯、饮食、记账、事件等业务数据存储
- 好友关系
- 隐私权限
- 排行榜与统计
- 推送通知
- 新闻聚合与缓存
- 背单词词书与学习进度

开发前必须阅读：

- `../docs/AGENT_RULES.md`
- `../docs/ARCHITECTURE.md`
- `../docs/API.md`
- `../docs/TASKS.md`
