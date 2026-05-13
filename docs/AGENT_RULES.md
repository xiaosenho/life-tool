# AGENT RULES

## 1. 基本原则

1. 不允许直接修改 `main` 分支。
2. 每个任务必须使用独立 feature 分支。
3. 每次只处理一个任务 ID。
4. 修改前必须阅读：
   - `docs/PRD.md`
   - `docs/ARCHITECTURE.md`
   - `docs/API.md`
   - `docs/TASKS.md`
5. 不允许无关重构。
6. 不允许删除已有测试。
7. 所有代码必须可运行。
8. 不允许把密钥、token、密码写入仓库。
9. 不允许多个 Agent 同时修改同一文件。

## 2. 角色边界

| Agent | 角色 | 默认负责范围 |
| --- | --- | --- |
| Codex | Tech Lead / 架构师 | 任务拆分、Review、架构治理、CI 检查 |
| Claude | 高级后端工程师 | `backend/**` |
| Gemini | 高级前端工程师 | `frontend/**` |
| DeepSeek | 初中级工程师 | DTO、CRUD、文档、批量生成 |

## 3. 分支规范

```text
main
develop
feature/task-be-001
feature/task-fe-001
feature/task-doc-001
fix/bug-001
```

禁止：

```text
直接修改 main
```

## 4. 后端规范

- 使用 Java + Spring Boot。
- Controller 不写业务逻辑。
- Service 负责业务逻辑和事务边界。
- Repository 只负责数据库访问。
- DTO / VO / Entity 分离。
- 所有业务接口必须经过认证和权限校验。
- 不信任客户端传入的 `userId`。
- 好友侧接口默认只返回汇总数据。
- 密码必须哈希存储。
- 敏感信息不得写入日志。

## 5. 前端规范

- 使用 React Native + Expo + TypeScript。
- API 请求统一封装。
- 页面组件和业务组件分离。
- 不允许重复状态管理。
- 本地数据写入必须经过 DAO 或 repository 层。
- 离线写入必须记录同步队列。
- 敏感 token 使用安全存储。
- 不在前端自行绕过服务端隐私判断。

## 6. 文档规范

- 接口变更必须同步更新 `docs/API.md`。
- 架构决策变更必须同步更新 `docs/ARCHITECTURE.md`。
- 新任务或任务状态变化必须更新 `docs/TASKS.md`。
- Agent 行为规则变化必须更新 `docs/AGENT_RULES.md`。

## 7. 提交要求

每个 Agent 完成任务后必须输出：

1. 做了什么。
2. 修改了哪些文件。
3. 如何测试。
4. 风险点。
5. 是否需要后续 Agent 接力。

## 8. Review 要求

Codex Review 时检查：

1. 是否符合 PRD。
2. 是否符合架构规范。
3. 是否有安全风险。
4. 是否缺少测试。
5. 是否存在重复代码。
6. 前后端接口是否一致。
7. 是否越界修改了其他 Agent 的负责范围。

Review 只输出必须修改项和关键风险。
