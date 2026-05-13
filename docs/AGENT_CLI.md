# AGENT_CLI：本地多 Agent CLI 调度规范

## 1. 目标

本项目采用 Codex 作为 Tech Lead，由本机已安装的多个 Agent CLI 执行具体开发任务：

| Agent | 本地命令 | 定位 | 默认负责范围 |
| --- | --- | --- | --- |
| Codex | 当前会话 | Tech Lead / 架构师 | 任务拆分、调度、Review、合并治理 |
| Claude | `claude` | 高级后端工程师 | `backend/**` |
| Gemini | `gemini` | 高级前端工程师 | `frontend/**` |
| DeepSeek | `opencode` | 初中级工程师 | DTO、CRUD、文档、批量生成 |

Codex 负责管理任务边界、提示词、分支、Review 和最终验收。Claude、Gemini、DeepSeek 不直接决定架构方向。

## 2. 前置要求

执行任何 Agent CLI 前，必须确认：

1. 当前在项目根目录。
2. 当前任务已写入 `docs/TASKS.md`。
3. Agent 已阅读：
   - `docs/AGENT_RULES.md`
   - `docs/PRD.md`
   - `docs/ARCHITECTURE.md`
   - `docs/API.md`
   - `docs/TASKS.md`
4. 每个 Agent 只处理一个任务 ID。
5. 每个 Agent 只修改自己负责的目录。

## 3. 推荐调度模式

### 3.1 Codex 拆任务

Codex 先把需求拆成明确任务，写入 `docs/TASKS.md`。

每个任务必须包含：

- 任务 ID
- 描述
- 影响文件
- 验收标准
- 推荐负责人
- 风险等级

### 3.2 Agent 执行任务

Agent 执行前先新建分支：

```bash
git checkout -b feature/task-be-001
```

Agent 完成后必须输出：

```text
1. 做了什么
2. 修改了哪些文件
3. 如何测试
4. 风险点
5. 是否需要后续 Agent 接力
```

### 3.3 Codex Review

Codex 对 Agent 的分支做 Review。

只关注必须修改项：

- 是否符合 PRD
- 是否符合架构规范
- 是否有安全风险
- 是否缺少测试
- 是否存在重复代码
- 前后端接口是否一致
- 是否越界修改其他 Agent 的负责范围

## 4. Claude CLI：后端任务

Claude 使用 `claude` 命令。

适合任务：

- Spring Boot 后端开发
- 认证、权限、同步 API
- 数据库建模
- Service/Repository 重构
- 后端单测

### 4.1 交互模式

适合复杂后端任务和需要多轮确认的改动：

```bash
claude
```

进入后粘贴任务 Prompt。

### 4.2 非交互模式

适合边界清楚、可一次性执行的任务：

```bash
claude -p "阅读 docs/AGENT_RULES.md、docs/API.md、docs/TASKS.md。只完成 TASK-BE-001。不要修改 frontend/**。完成后输出变更说明、测试结果和风险点。"
```

### 4.3 推荐后端任务 Prompt

```text
你是高级后端工程师。

阅读：
- docs/AGENT_RULES.md
- docs/PRD.md
- docs/ARCHITECTURE.md
- docs/API.md
- docs/TASKS.md

只完成：
TASK-BE-001

允许修改：
- backend/**

禁止修改：
- frontend/**
- docs/API.md，除非接口实现发现文档必须修正
- docs/ARCHITECTURE.md，除非架构决策必须修正

要求：
1. 新建或使用 feature/task-be-001 分支
2. 实现任务要求
3. 补充必要测试
4. 跑后端测试
5. 输出：
   - 做了什么
   - 修改了哪些文件
   - 如何测试
   - 风险点
```

## 5. Gemini CLI：前端任务

Gemini 使用 `gemini` 命令。

适合任务：

- Expo 移动端
- React Native 页面
- UI/交互
- TypeScript 前端类型
- 前端 API 对接

### 5.1 交互模式

```bash
gemini
```

### 5.2 非交互模式

```bash
gemini -p "阅读 docs/AGENT_RULES.md、docs/API.md、docs/TASKS.md。只完成 TASK-FE-001。不要修改 backend/**。完成后输出变更说明、测试结果和风险点。"
```

### 5.3 推荐前端任务 Prompt

```text
你是高级前端工程师。

阅读：
- docs/AGENT_RULES.md
- docs/PRD.md
- docs/ARCHITECTURE.md
- docs/API.md
- docs/TASKS.md

只完成：
TASK-FE-001

允许修改：
- frontend/**

禁止修改：
- backend/**
- docs/API.md，除非接口契约发现明显不一致
- docs/ARCHITECTURE.md，除非前端架构必须修正

要求：
1. 新建或使用 feature/task-fe-001 分支
2. 实现页面或前端模块
3. 对接 mock API 或文档中的真实 API 契约
4. 保证 lint/build/typecheck 通过
5. 输出：
   - 做了什么
   - 修改了哪些文件
   - 如何测试
   - 风险点
```

## 6. DeepSeek CLI：样板代码和低风险任务

DeepSeek 通过 `opencode` 命令执行。当前 `opencode` 已配置 DeepSeek API。

适合任务：

- DTO
- CRUD
- Mapper
- TypeScript API Client
- 文档补充
- 批量测试样板
- 简单重复性代码

### 6.1 交互模式

```bash
opencode
```

### 6.2 非交互模式

```bash
opencode run "阅读 docs/AGENT_RULES.md、docs/API.md、docs/TASKS.md。只完成 TASK-DOC-001。不要修改业务逻辑。完成后输出变更说明、测试结果和风险点。"
```

### 6.3 推荐 DeepSeek 任务 Prompt

```text
你是初中级工程师，负责低风险、边界清晰的批量任务。

阅读：
- docs/AGENT_RULES.md
- docs/API.md
- docs/TASKS.md

只完成：
TASK-XXX

允许修改：
- 任务中明确列出的文件

禁止修改：
- 核心业务逻辑
- 鉴权逻辑
- 数据库架构
- 其他 Agent 负责的文件

要求：
1. 不做无关重构
2. 不引入新依赖，除非任务明确要求
3. 类型必须完整
4. 输出：
   - 做了什么
   - 修改了哪些文件
   - 如何测试
   - 风险点
```

## 7. Codex 调度命令模板

### 7.1 派发后端任务

```bash
git checkout -b feature/task-be-001
claude -p "你是高级后端工程师。阅读 docs/AGENT_RULES.md、docs/PRD.md、docs/ARCHITECTURE.md、docs/API.md、docs/TASKS.md。只完成 TASK-BE-001。只修改 backend/**。完成后跑测试并输出变更说明。"
```

### 7.2 派发前端任务

```bash
git checkout -b feature/task-fe-001
gemini -p "你是高级前端工程师。阅读 docs/AGENT_RULES.md、docs/PRD.md、docs/ARCHITECTURE.md、docs/API.md、docs/TASKS.md。只完成 TASK-FE-001。只修改 frontend/**。完成后跑 lint/build/typecheck 并输出变更说明。"
```

### 7.3 派发 DeepSeek 任务

```bash
git checkout -b feature/task-doc-001
opencode run "你是初中级工程师。阅读 docs/AGENT_RULES.md、docs/API.md、docs/TASKS.md。只完成 TASK-DOC-001。只修改任务指定文件。不要修改业务逻辑。完成后输出变更说明。"
```

## 8. 并行开发约束

允许并行：

```text
Claude  -> backend/**
Gemini  -> frontend/**
DeepSeek -> docs/** 或明确的 DTO/API Client 文件
```

禁止并行：

```text
Claude  和 DeepSeek 同时修改 backend/src/main/**/UserService.java
Gemini  和 DeepSeek 同时修改 frontend/src/services/apiClient.ts
任何 Agent 未经确认修改 docs/API.md
```

## 9. Review 和合并

Agent 完成后，Codex 执行：

```bash
git status
git diff
```

根据任务类型运行检查：

```bash
# Backend
mvn test

# Frontend
pnpm lint
pnpm build
```

Review 通过后再合并到 `develop` 或 `main`。

## 10. 故障处理

### 10.1 Agent 修改范围越界

处理方式：

1. 暂停该 Agent。
2. 查看 `git diff`。
3. 只保留任务相关改动。
4. 更新 Prompt，明确禁止修改范围。

### 10.2 前后端 API 不一致

处理方式：

1. 以 `docs/API.md` 为准。
2. 如果文档错误，先由 Codex 更新 `docs/API.md`。
3. 再让 Claude/Gemini 分别修正实现。

### 10.3 Agent 生成无测试代码

处理方式：

1. 不进入合并。
2. 退回给原 Agent 补测试。
3. Codex Review 确认测试覆盖核心路径。

## 11. 当前本机 CLI 状态

已确认可用：

```text
claude --help
gemini --help
opencode --help
```

命令能力摘要：

- `claude -p`：Claude 非交互执行任务。
- `gemini -p`：Gemini 非交互执行任务。
- `opencode run`：DeepSeek 非交互执行任务。

后续如果 CLI 版本变化，以本机 `--help` 输出为准。
