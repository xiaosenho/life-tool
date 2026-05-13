# Backend

LifeTool 服务端，基于 Java 21、Spring Boot 和 Maven。

## 当前能力

- `GET /api/health`：健康检查接口。
- 基础 Web MVC 测试。

## 本地命令

```bash
mvn test
mvn spring-boot:run
```

本工程使用 `backend/.mvn/settings.xml` 覆盖全局 Maven settings，避免本项目依赖解析默认走公司私有仓库。

## 模块边界

服务端职责：

- 用户认证与授权
- 数据同步 API
- 专注、习惯、饮食、记账、事件等业务数据存储
- 好友关系
- 隐私权限
- 排行榜与统计
- 推送通知

开发前必须阅读：

- `../docs/AGENT_RULES.md`
- `../docs/ARCHITECTURE.md`
- `../docs/API.md`
- `../docs/TASKS.md`
