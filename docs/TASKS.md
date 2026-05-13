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

- 状态：done
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

### TASK-FE-106：实现好友与排行榜前端页面

- 状态：todo
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
