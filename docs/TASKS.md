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

### TASK-FE-000：全局页面中文化

- 状态：done
- 推荐负责人：Codex
- 影响文件：
  - `frontend/app/**`
  - `frontend/src/services/**`
  - `docs/AGENT_RULES.md`
  - `docs/TASKS.md`
- 描述：
  - 面向中国用户，所有用户可见页面文案统一使用简体中文。
  - 登录、注册、演示账号、错误提示、上传状态、空状态和入口说明不得保留英文。
  - 将中文化要求固化到 Agent 前端规范，后续 Agent 开发必须遵守。
- 验收标准：
  - 主要页面标题、按钮、表单标签、占位符、Tab 和错误提示为中文。
  - `npm run typecheck` 通过。
- 风险等级：低

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

- 状态：done
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/friends/**`
  - `backend/src/main/**/leaderboards/**`
- 验收标准：
  - 支持好友申请、通过、删除。
  - 排行榜只返回汇总数据。
- 风险等级：中

### TASK-FE-103：实现专注和习惯 MVP

- 状态：done
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

- 状态：done
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

- 状态：done
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

- 状态：done
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/ai/**`
  - `backend/src/main/**/meals/**`
  - `docs/API.md`
- 描述：
  - 基于已上传图片触发 AI 饮食识别。
  - 调用 AI 服务识别食物、估算重量和热量。
  - 识别成功后直接写入饮食记录，并返回 `mealLogId` 和 `totalCalories`。
- 验收标准：
  - 已上传图片可以成功触发识别。
  - AI 结果包含识别文本和热量估算。
  - 识别成功后会生成当前用户饮食记录。
- 风险等级：高

### TASK-FE-104：实现饮食拍照上传与识别结果确认

- 状态：done
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/**/records/**`
  - `frontend/**/media/**`
  - `frontend/**/meals/**`
- 描述：
  - Records 页增加饮食图片入口。
  - 支持拍照或选择图片、压缩、上传、触发识别。
  - 展示 AI 识别结果，并完成基础写入流程。
- 验收标准：
  - 用户可以上传一张饮食图片。
  - 用户可以看到 AI 返回的食物和热量估算。
  - 识别成功后生成饮食记录并进入同步队列。
- 风险等级：高

### TASK-BE-106：实现 AI 框架、会话记忆与 Function Calling

- 状态：in_progress
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/ai/**`
  - `backend/pom.xml`
  - `backend/src/main/resources/application*.yml`
  - `backend/src/main/**/stats/**`
  - `docs/AI_FRAMEWORK.md`
- 描述：
  - 接入 Spring AI，配置 `ChatClient` / `ChatModel`、Advisor、Chat Memory、Tool Calling。
  - 实现 `AiOrchestrator`、`ToolRegistry`、`MemoryService`、`SafetyGuard`。
  - 支持 OpenAI-compatible 模型供应商配置和本地 mock 模型 Bean。
  - 实现 AI 对话 session、message、session summary、长期记忆管理。
  - 模型只能通过后端注册工具访问当前用户数据。
- 验收标准：
  - 可创建会话并完成一次多轮对话。
  - AI 可通过 Spring AI tool calling 触发至少一个只读工具调用，并记录 `ai_tool_calls`。
  - 长期记忆可查看、禁用、删除。
  - AI 只使用当前用户授权数据，返回建议包含免责声明。
  - 测试环境不依赖真实 AI Key。
  - `mvn test` 通过。
- 风险等级：高

### TASK-BE-110：实现 AI 用户数据查询工具集

- 状态：in_progress
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/ai/tools/**`
  - `backend/src/main/**/focus/**`
  - `backend/src/main/**/habits/**`
  - `backend/src/main/**/ledger/**`
  - `backend/src/main/**/events/**`
- 描述：
  - 实现 `get_focus_summary`、`get_habit_summary`、`get_diet_summary`、`get_ledger_summary`、`get_upcoming_events`、`get_user_profile_context`。
  - 工具优先使用 Spring AI `@Tool` 或 `ToolCallback` 暴露。
  - 所有工具由服务端注入当前 `userId`。
  - 工具默认返回汇总数据，不返回过量原始明细。
- 验收标准：
  - 每个工具都有参数校验和用户隔离测试。
  - 工具结果可被 `AiOrchestrator` 作为 function result 继续传给模型。
  - `mvn test` 通过。
- 风险等级：高

### TASK-DB-003：补充 AI Framework 持久化 DDL

- 状态：done
- 推荐负责人：DeepSeek
- 影响文件：
  - `backend/src/main/resources/db/migration/**`
  - `docs/DATABASE.md`
- 描述：
  - 新增 AI 工具调用、长期记忆、会话摘要、agent run 审计表。
  - 不修改已发布 V1/V2，新增 V3 migration。
- 验收标准：
  - DDL 覆盖 `ai_tool_calls`、`ai_memory_items`、`ai_session_summaries`、`ai_agent_runs`。
  - 包含用户隔离索引、状态 CHECK、软删除字段。
  - `docs/DATABASE.md` 与迁移一致。
- 风险等级：中

### TASK-FE-105：实现 AI 建议与对话页面

- 状态：in_progress
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/**/ai/**`
  - `frontend/**/profile/**`
  - `frontend/**/records/**`
- 描述：
  - 增加 AI 建议入口和对话界面。
  - 展示近期生活摘要、建议卡片和聊天消息。
  - 展示长期记忆开关和记忆管理入口。
  - 对 function calling 工具调用提供简化状态提示，例如“正在读取专注汇总”。
  - 对 AI 建议添加非专业建议提示。
- 验收标准：
  - 用户可以请求近期生活建议。
  - 用户可以进行多轮 AI 对话。
  - 用户可以查看和删除长期记忆。
  - 页面清楚提示 AI 建议仅供参考。
- 风险等级：中

### TASK-DB-001：生成 PostgreSQL 数据库 DDL

- 状态：done
- 推荐负责人：DeepSeek
- 影响文件：
  - `docs/DATABASE.md`
  - `docs/TASKS.md`
  - `backend/src/main/resources/db/migration/V1__init_schema.sql`
- 描述：
  - 为 LifeTool 生成 PostgreSQL 16 数据库 DDL，覆盖 12 个业务域：用户与认证、隐私、同步、专注、习惯、好友、排行榜/统计、饮食、记账、重要事件、媒体、AI。
  - 使用 uuid 主键 + pgcrypto 扩展。
  - 所有用户私有数据带 user_id 外键。
  - 关键状态字段使用 CHECK 约束，不创建 enum。
  - 常用查询字段添加索引。
  - 同步模型基于全局递增 server_version + sync_mutations 表，支持 /sync/push 和 /sync/pull 的 cursor 分页。
  - 编写 docs/DATABASE.md 中文文档说明表分组、关系、同步版本模型、隐私原则和迁移建议。
- 验收标准：
  - DDL 在 PostgreSQL 16 空库上可执行。
  - 所有 12 个业务域都有对应表。
  - 同步版本模型支持游标拉取和实体版本管理。
  - docs/DATABASE.md 文档完整，可指导后端开发。
- 风险等级：低

## Phase 3：记录、提醒与体验补全

### TASK-PRD-003：定义 Records 页重构与饮食记录增强需求

- 状态：done
- 推荐负责人：Codex
- 影响文件：
  - `docs/PRD.md`
  - `docs/TASKS.md`
- 描述：
  - 明确下一阶段 Records 页从堆叠结构调整为分区切换结构。
  - 明确饮食记录详情、删除、重新识别等产品需求。
  - 将现有文档中与真实实现不一致的“识别确认流”描述修正为当前行为。
- 验收标准：
  - PRD 明确 Records 页拆分方向、饮食增强范围和验收标准。
  - TASKS 可直接指导前后端进入下一阶段开发。
- 风险等级：低

### TASK-FE-106：实现好友与排行榜前端页面

- 状态：review
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/src/services/friendService.ts`
  - `frontend/src/services/leaderboardService.ts`
  - `frontend/app/(tabs)/friends.tsx`
- 描述：
  - 实现好友搜索与添加（通过邮箱）。
  - 实现好友请求列表（收到/发出）与接受/拒绝操作。
  - 实现好友列表展示与删除好友。
  - 实现排行榜页面：今日/本周专注时长、习惯完成率、连续打卡天数。
  - 对接后端 `/api/friends/*` 和 `/api/leaderboards/*`。
- 验收标准：
  - 可通过邮箱搜索并发送好友请求。
  - 可查看待处理的好友请求并接受/拒绝。
  - 好友列表可展示并支持删除。
  - 排行榜展示好友排名数据。
  - 未登录、空列表、接口失败、加载中均有中文状态。
  - `npm run typecheck` 通过。
- 风险等级：中

### TASK-PRD-004：定义好友页重构与互动需求

- 状态：done
- 推荐负责人：Codex
- 影响文件：
  - `docs/PRD.md`
  - `docs/TASKS.md`
- 描述：
  - 明确好友页当前问题：结构过浅、排行榜不可用、缺少互动闭环。
  - 定义好友、排行榜、互动三个主分区及其目标。
  - 产出下一阶段可执行的前后端任务拆分。
- 验收标准：
  - PRD 明确好友页重构方向和互动能力范围。
  - TASKS 能直接指导前后端进入实现阶段。
- 风险等级：低

### TASK-FE-112：重构好友页为“好友/排行榜/互动”三分区

- 状态：done
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/app/(tabs)/friends.tsx`
  - `frontend/src/components/**`
  - `frontend/src/services/friendService.ts`
  - `frontend/src/services/leaderboardService.ts`
- 描述：
  - 将好友页从简单堆叠结构改为三分区结构。
  - 好友分区展示好友列表、申请处理、添加好友和状态摘要。
  - 排行榜分区支持榜单切换、完整排名列表和个人名次提示。
  - 互动分区展示最近消息、鼓励动作和未读状态。
- 验收标准：
  - 用户可以在好友、排行榜、互动之间快速切换。
  - 页面不再停留在摘要卡片级别，而是能完成实际查看和操作。
  - 所有空状态、加载状态、失败状态都有中文提示。
  - `npm run typecheck` 通过。
- 风险等级：中

### TASK-BE-112：补齐排行榜详情与好友互动接口

- 状态：done
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/leaderboards/**`
  - `backend/src/main/**/friends/**`
  - `backend/src/main/**/notifications/**`
  - `docs/API.md`
- 描述：
  - 为排行榜补齐完整榜单详情接口，返回好友排名列表、当前用户名次、与前一名差距等信息。
  - 增加好友互动接口，支持好友间发送文本消息和轻量鼓励动作。
  - 支持未读计数、消息列表和已读状态。
  - 保证所有互动只在好友关系成立后可用，并受用户隔离控制。
- 验收标准：
  - 用户可以获取完整榜单，而不是只有摘要。
  - 用户可以给好友发送一条站内消息。
  - 用户可以查看与某位好友的互动记录和未读状态。
  - 非好友之间不能互发消息。
  - `mvn test` 通过。
- 风险等级：高

### TASK-DB-004：补充好友互动消息与未读状态 DDL

- 状态：done
- 推荐负责人：DeepSeek
- 影响文件：
  - `backend/src/main/resources/db/migration/**`
  - `docs/DATABASE.md`
- 描述：
  - 为好友互动增加站内消息和轻量互动记录表。
  - 支持发送者、接收者、消息类型、消息内容、已读状态、软删除和时间索引。
  - 更新数据库文档中的社交域说明和索引策略。
- 验收标准：
  - 新增迁移可在现有数据库上执行。
  - 表结构可支持好友消息、鼓励动作和未读统计。
  - `docs/DATABASE.md` 与迁移保持一致。
- 风险等级：中

### TASK-FE-113：实现完整排行榜页与好友互动 UI

- 状态：done
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/app/(tabs)/friends.tsx`
  - `frontend/src/services/leaderboardService.ts`
  - `frontend/src/services/friendService.ts`
  - `frontend/src/services/messageService.ts`
- 描述：
  - 将排行榜从摘要卡片升级为完整榜单视图。
  - 展示榜单切换、排名列表、个人名次和差距信息。
  - 实现好友消息列表、会话入口、发送消息和鼓励按钮。
  - 为未读消息和新互动提供可见状态提示。
- 验收标准：
  - 用户可以完整浏览各类榜单。
  - 用户可以进入某位好友的互动会话并发送消息。
  - 用户可以看到未读消息提示和最近互动列表。
  - `npm run typecheck` 通过。
- 风险等级：高

### TASK-FE-110：重构 Records 页为分区结构

- 状态：done
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/app/(tabs)/records.tsx`
  - `frontend/src/components/**`
  - `frontend/src/services/mealService.ts`
  - `frontend/src/services/ledgerService.ts`
  - `frontend/src/services/eventService.ts`
- 描述：
  - 将 Records 页从单页纵向堆叠改为分区切换结构。
  - 提供饮食、记账、纪念日三个主分区的切换入口。
  - 每个分区只展示自己的摘要卡片、列表和新增按钮。
  - 保证移动端切换效率和视觉层级清晰。
- 验收标准：
  - 用户可以在同一页内快速切换饮食、记账、纪念日分区。
  - 默认视图不再同时堆叠三类完整内容。
  - 每个分区都有清晰的空状态、加载状态和新增入口。
  - `npm run typecheck` 通过。
- 风险等级：中

### TASK-BE-111：补齐饮食记录详情、删除与重新识别接口

- 状态：done
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/meals/**`
  - `backend/src/main/**/ai/**`
  - `backend/src/main/**/media/**`
  - `docs/API.md`
- 描述：
  - 为饮食记录补齐详情查询接口，返回图片资产、识别结果文本、热量和基础元数据。
  - 支持删除指定饮食记录，并保证今日/区间汇总同步更新。
  - 支持基于已有 `mediaAssetId` 对某条饮食记录重新触发热量识别，无需重复上传图片。
  - 明确重新识别后的落库策略：更新原记录或生成新识别结果，并在接口文档中说明。
- 验收标准：
  - 用户可以查看自己的饮食记录详情。
  - 用户可以删除自己的饮食记录，删除后汇总结果正确更新。
  - 用户可以基于原图重新触发一次识别。
  - A 用户不能查看、删除或重识别 B 用户的饮食记录。
  - `mvn test` 通过。
- 风险等级：高

### TASK-FE-111：实现饮食记录详情、图片查看、删除与重新识别

- 状态：done
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/app/(tabs)/records.tsx`
  - `frontend/src/services/mealService.ts`
  - `frontend/src/services/aiService.ts`
  - `frontend/src/services/mediaService.ts`
- 描述：
  - 在饮食分区展示近期记录列表，支持进入详情。
  - 在详情中展示原图、识别结果、热量、免责声明和记录时间。
  - 提供删除操作，并在成功后刷新列表与摘要。
  - 提供“重新计算热量”入口，复用已有图片重新触发识别。
- 验收标准：
  - 用户可以查看饮食记录对应图片和识别结果。
  - 用户可以删除一条饮食记录，并立即看到 UI 更新。
  - 用户可以点击重新计算热量，并看到新的识别结果反馈。
  - 失败状态、空状态、加载状态均有中文提示。
  - `npm run typecheck` 通过。
- 风险等级：高

### TASK-BE-107：实现专注偏好接口

- 状态：done
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/focus/**`
  - `docs/API.md`
- 描述：
  - 实现用户专注偏好接口：默认专注时长、短休息、长休息、是否自动开始休息。
  - 校验默认专注时长范围为 1 到 180 分钟。
  - 偏好只属于当前用户，不能跨用户访问。
- 验收标准：
  - `GET /api/focus/preferences` 返回当前用户偏好，首次访问返回默认值。
  - `PATCH /api/focus/preferences` 可更新偏好。
  - 非法时长返回 400。
  - `mvn test` 通过。
- 风险等级：中

### TASK-FE-107：实现可设置专注时长

- 状态：done
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/app/(tabs)/focus.tsx`
  - `frontend/src/services/focusService.ts`
  - `frontend/src/db/schema.ts`
- 描述：
  - 在专注页增加时长设置入口。
  - 支持 15/25/45/60 分钟预设和 1 到 180 分钟自定义。
  - 保存默认专注时长，本地可用并进入同步队列。
  - 开始专注前可以临时调整本次时长。
- 验收标准：
  - 用户可以修改默认专注时长。
  - 用户可以用自定义时长完成一次专注。
  - 今日统计按实际完成时长计算。
  - `npm run typecheck` 通过。
- 风险等级：中

### TASK-BE-108：实现记账接口与统计

- 状态：done
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/ledger/**`
  - `docs/API.md`
- 描述：
  - 实现收入、支出、转账流水 CRUD。
  - 实现月度收支汇总和分类支出统计。
  - 实现月度预算接口。
  - 支持关联媒体凭证 `mediaAssetId`。
- 验收标准：
  - 用户可以创建、修改、删除自己的记账流水。
  - A 用户不能读取或修改 B 用户流水。
  - 月度汇总返回收入、支出、结余、预算和分类支出。
  - `mvn test` 通过。
- 风险等级：高

### TASK-FE-108：实现记账页面与本地同步

- 状态：done
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/app/(tabs)/records.tsx`
  - `frontend/src/services/ledgerService.ts`
  - `frontend/src/db/schema.ts`
- 描述：
  - Records 页增加记账入口。
  - 支持新增收入/支出、选择分类、账户、日期、备注和图片凭证。
  - 展示当月收入、支出、结余和预算进度。
  - 离线写入本地 SQLite，并进入同步队列。
- 验收标准：
  - 用户可以新增一笔支出。
  - 用户可以看到当月支出汇总。
  - 用户可以设置月度预算。
  - 数据写入本地并进入同步队列。
  - `npm run typecheck` 通过。
- 风险等级：高

### TASK-BE-109：实现纪念日与重要事件接口

- 状态：done
- 推荐负责人：Claude
- 影响文件：
  - `backend/src/main/**/events/**`
  - `docs/API.md`
- 描述：
  - 实现纪念日、生日、重要日期、待办提醒 CRUD。
  - 支持重复规则和提前提醒天数。
  - 支持查询即将到来的事件。
  - 支持关联媒体图片。
- 验收标准：
  - 用户可以创建、修改、删除自己的纪念日。
  - A 用户不能读取或修改 B 用户事件。
  - 即将到来的事件返回倒数天数和下一次发生日期。
  - `mvn test` 通过。
- 风险等级：中

### TASK-FE-109：实现纪念日页面与提醒

- 状态：done
- 推荐负责人：Gemini
- 影响文件：
  - `frontend/app/(tabs)/records.tsx`
  - `frontend/src/services/eventService.ts`
  - `frontend/src/db/schema.ts`
- 描述：
  - Records 页增加纪念日入口。
  - 支持创建纪念日、生日、重要日期和提醒。
  - 展示倒数天数、周年信息和最近即将到来的事件。
  - 使用本地通知调度提醒，服务端保存规则用于多设备恢复。
- 验收标准：
  - 用户可以创建一个纪念日。
  - 用户可以看到倒数天数。
  - 用户可以设置提前提醒。
  - 数据写入本地并进入同步队列。
  - `npm run typecheck` 通过。
- 风险等级：中

### TASK-DB-002：补充专注偏好、预算和纪念日 DDL

- 状态：done
- 推荐负责人：DeepSeek
- 影响文件：
  - `backend/src/main/resources/db/migration/**`
  - `docs/DATABASE.md`
- 描述：
  - 检查现有 DDL 是否足够表达专注偏好、月度预算和纪念日重复提醒。
  - 如不足，新增迁移脚本而不是修改已经发布的 `V1__init_schema.sql`。
  - 更新数据库文档说明表关系和索引。
- 验收标准：
  - 新增迁移可在已有数据库上执行。
  - 不破坏现有表和索引。
  - `docs/DATABASE.md` 能指导后端实现。
- 风险等级：中
