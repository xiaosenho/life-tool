# TASKS：当前任务列表

## 任务状态

```text
todo
in_progress
review
done
blocked
```

## Phase 0：项目初始化

### TASK-DOC-001：建立项目基础文档

- 状态：done
- 推荐负责人：Codex
- 影响文件：
  - `README.md`
  - `docs/PRD.md`
  - `docs/ARCHITECTURE.md`
  - `docs/API.md`
  - `docs/TASKS.md`
  - `docs/AGENT_RULES.md`
  - `docs/WORKFLOW.md`
- 验收标准：
  - 项目文档可以指导多个 Agent 并行开发。
  - 每个 Agent 能明确自己的负责范围。
- 风险等级：低

### TASK-BE-001：初始化 Spring Boot 后端工程

- 状态：todo
- 推荐负责人：Claude
- 影响文件：
  - `backend/**`
- 描述：
  - 初始化 Spring Boot + Maven 项目。
  - 建立基础包结构。
  - 添加健康检查接口。
  - 添加基础测试。
- 验收标准：
  - `mvn test` 通过。
  - `GET /api/health` 返回正常状态。
- 风险等级：低

### TASK-FE-001：初始化 Expo 移动端工程

- 状态：todo
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/**`
- 描述：
  - 初始化 Expo + TypeScript 项目。
  - 建立底部导航结构。
  - 创建 Today、Focus、Records、Friends、Profile 页面占位。
- 验收标准：
  - `pnpm lint` 通过。
  - `pnpm build` 或 Expo 类型检查通过。
  - 手机端能看到 5 个 Tab。
- 风险等级：低

### TASK-CI-001：完善 CI 工作流

- 状态：todo
- 推荐负责人：Codex
- 影响文件：
  - `.github/workflows/ci.yml`
- 描述：
  - 后端初始化后启用 Maven 测试。
  - 前端初始化后启用 pnpm lint/build。
- 验收标准：
  - pull request 时自动执行后端和前端检查。
- 风险等级：低

## Phase 1：MVP

### TASK-BE-101：实现认证模块

- 状态：todo
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/auth/**`
  - `backend/src/main/**/users/**`
- 验收标准：
  - 支持注册、登录、刷新 token、退出登录。
  - 密码哈希存储。
  - 未登录访问业务接口返回 401。
- 风险等级：中

### TASK-FE-101：实现登录注册页面

- 状态：todo
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/**/auth/**`
- 验收标准：
  - 用户可以注册和登录。
  - token 安全保存。
  - 登录后进入 Today 页。
- 风险等级：中

### TASK-BE-102：实现同步 API

- 状态：todo
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/sync/**`
- 验收标准：
  - `/api/sync/push` 可接收变更队列。
  - `/api/sync/pull` 可返回用户变更。
  - A 用户不能同步 B 用户数据。
- 风险等级：高

### TASK-FE-102：实现本地 SQLite 与同步队列

- 状态：todo
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/**/db/**`
  - `frontend/**/sync/**`
- 验收标准：
  - 断网时可写入本地数据。
  - 联网后可上传同步。
  - 同步失败有可见状态。
- 风险等级：高

### TASK-BE-103：实现好友与排行榜

- 状态：todo
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/friends/**`
  - `backend/src/main/**/leaderboards/**`
- 验收标准：
  - 支持好友申请、通过、删除。
  - 排行榜只返回汇总数据。
- 风险等级：中

### TASK-FE-103：实现专注和习惯 MVP

- 状态：todo
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/**/focus/**`
  - `frontend/**/habits/**`
- 验收标准：
  - 可完成一次专注计时。
  - 可创建习惯并打卡。
  - 数据写入本地并进入同步队列。
- 风险等级：中
