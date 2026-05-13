# TASKS：当前任务列表

## 任务状态

```text
todo
in_progress
review
done
blocked
```

### TASK-INFRA-001：提供本地 Docker 基础设施与部署文档

- 状态：done
- 推荐负责人：DeepSeek
- 影响文件：
  - `docker-compose.yml`
  - `backend/.env.example`
  - `docs/DEPLOYMENT.md`
  - `README.md`
  - `docs/TASKS.md`
- 描述：
  - 提供 PostgreSQL 16 + Redis 7 的 docker-compose 配置。
  - 提供后端本地环境变量示例文件。
  - 编写本地部署文档，包含启动、停止、清理、端口映射、默认账号。
  - 更新 README 指向部署文档。
- 验收标准：
  - `docker compose up -d` 可正常启动 PostgreSQL 和 Redis。
  - `backend/.env.example` 中的配置与 docker-compose 一致。
  - `docs/DEPLOYMENT.md` 包含完整的本地开发流程。
- 风险等级：低

### TASK-INFRA-002：阿里云服务器基础设施部署配置

- 状态：done
- 推荐负责人：DeepSeek
- 影响文件：
  - `docker-compose.yml`
  - `backend/.env.example`
  - `docs/DEPLOYMENT.md`
  - `README.md`
  - `docs/TASKS.md`
- 描述：
  - PostgreSQL/Redis 端口绑定 127.0.0.1，默认不对公网开放。
  - 敏感值通过 .env 文件注入（POSTGRES_PASSWORD、JWT_SECRET 等），不硬编码生产密码。
  - backend/.env.example 增加阿里云部署说明和变量注释。
  - docs/DEPLOYMENT.md 增加阿里云 ECS 部署章节：安全组、SSH、Docker 安装、启动 compose、Nginx 反代、备份、日志、升级。
  - 公网只应开放 22/80/443，数据库和 Redis 不对公网暴露。
- 验收标准：
  - `docker compose up -d` 不传 .env 默认使用本地开发密码。
  - 传入 .env 生产密码后覆盖默认值。
  - PostgreSQL 和 Redis 端口只监听 127.0.0.1。
  - 部署文档包含完整阿里云 ECS 搭建流程。
- 风险等级：低

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

### TASK-DOC-002：落实本地 Agent CLI 调度规范

- 状态：done
- 推荐负责人：Codex
- 影响文件：
  - `README.md`
  - `docs/AGENT_CLI.md`
  - `docs/AGENT_RULES.md`
  - `docs/WORKFLOW.md`
  - `docs/TASKS.md`
- 描述：
  - 将本机已安装的 `claude`、`gemini`、`opencode` 命令写入项目文档。
  - 明确 Codex 作为管理者如何调度 Claude、Gemini、DeepSeek。
  - 补充每个 CLI 的推荐执行方式和 Prompt 模板。
- 验收标准：
  - 文档中能明确看出每个 Agent 使用哪个本地命令。
  - 文档中包含后端、前端、低风险任务的 CLI 调度模板。
  - Agent 执行前后的责任边界清晰。
- 风险等级：低

### TASK-BE-001：初始化 Spring Boot 后端工程

- 状态：done
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

- 状态：done
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

- 状态：done
- 推荐负责人：Codex
- 影响文件：
  - `.github/workflows/ci.yml`
- 描述：
  - 后端初始化后启用 Maven 测试。
  - 前端初始化后启用 npm lint/build。
- 验收标准：
  - pull request 时自动执行后端和前端检查。
- 风险等级：低

## Phase 1：MVP

### TASK-BE-101：实现认证模块

- 状态：done
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/auth/**`
  - `backend/src/main/**/users/**`
  - `backend/src/main/**/common/**`
- 验收标准：
  - 支持注册、登录、刷新 token、退出登录。
  - 密码哈希存储。
  - 未登录访问业务接口返回 401。
- 风险等级：中

### TASK-FE-101：实现登录注册页面

- 状态：done
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/**/auth/**`
- 验收标准：
  - 用户可以注册和登录。
  - token 安全保存。
  - 登录后进入 Today 页。
- 风险等级：中

### TASK-BE-102：实现同步 API

- 状态：done
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/sync/**`
- 验收标准：
  - `/api/sync/push` 可接收变更队列。
  - `/api/sync/pull` 可返回用户变更。
  - A 用户不能同步 B 用户数据。
- 风险等级：高

### TASK-FE-102：实现本地 SQLite 与同步队列

- 状态：done
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

## Phase 2：图片与 AI 能力

### TASK-INFRA-003：配置腾讯云 COS 媒体存储

- 状态：todo
- 推荐负责人：DeepSeek
- 影响文件：
  - `docs/DEPLOYMENT.md`
  - `backend/.env.example`
  - `docs/TASKS.md`
- 描述：
  - 增加腾讯云 COS bucket、地域、访问密钥、回源域名等配置说明。
  - 明确 bucket 默认私有读写，客户端不能持有长期密钥。
  - 增加图片大小、类型、生命周期和备份策略说明。
- 验收标准：
  - 文档说明如何在阿里云 ECS 后端中配置腾讯云 COS。
  - `.env.example` 包含 COS 相关变量占位。
  - 明确生产密钥不得提交到 Git。
- 风险等级：中

### TASK-BE-104：实现媒体上传授权与资产记录

- 状态：todo
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/media/**`
  - `docs/API.md`
- 描述：
  - 实现 `/api/media/upload-token`。
  - 实现媒体资产创建、查询和删除接口。
  - 上传授权应短有效期、最小权限，并绑定当前登录用户。
- 验收标准：
  - 客户端可获取图片上传授权。
  - 后端能保存 `MediaAsset` 元数据。
  - A 用户不能读取或删除 B 用户媒体资产。
- 风险等级：高

### TASK-BE-105：实现 AI 饮食图片识别任务

- 状态：todo
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/ai/**`
  - `backend/src/main/**/meals/**`
  - `docs/API.md`
- 描述：
  - 基于已上传图片创建 AI 识别任务。
  - 调用 AI 服务识别食物、估算重量和热量。
  - 保存待确认结果，用户确认后写入饮食记录。
- 验收标准：
  - 图片识别任务有 pending/succeeded/failed 状态。
  - AI 结果包含食物列表、估算热量和置信度。
  - AI 结果不会未经确认直接进入正式热量统计。
- 风险等级：高

### TASK-FE-104：实现饮食拍照上传与识别结果确认

- 状态：todo
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/**/records/**`
  - `frontend/**/media/**`
  - `frontend/**/meals/**`
- 描述：
  - Records 页增加饮食图片入口。
  - 支持拍照或选择图片、压缩、上传、触发识别。
  - 展示 AI 识别结果，并允许用户编辑确认。
- 验收标准：
  - 用户可以上传一张饮食图片。
  - 用户可以看到 AI 返回的食物和热量估算。
  - 用户确认后生成饮食记录并进入同步队列。
- 风险等级：高

### TASK-BE-106：实现 AI 生活建议与对话接口

- 状态：todo
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/ai/**`
  - `backend/src/main/**/stats/**`
  - `docs/API.md`
- 描述：
  - 汇总用户近期专注、习惯、饮食、记账数据。
  - 提供生活建议接口。
  - 提供 AI 对话 session 和 message 接口。
- 验收标准：
  - AI 只使用当前用户授权数据。
  - 返回建议包含免责声明。
  - 对话记录默认私密，并支持删除。
- 风险等级：高

### TASK-FE-105：实现 AI 建议与对话页面

- 状态：todo
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/**/ai/**`
  - `frontend/**/profile/**`
  - `frontend/**/records/**`
- 描述：
  - 增加 AI 建议入口和对话界面。
  - 展示近期生活摘要、建议卡片和聊天消息。
  - 对 AI 建议添加非专业建议提示。
- 验收标准：
  - 用户可以请求近期生活建议。
  - 用户可以进行多轮 AI 对话。
  - 页面清楚提示 AI 建议仅供参考。
- 风险等级：中
