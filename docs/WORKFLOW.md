# WORKFLOW：AI 多 Agent 软件开发工作流

## 1. 工作流目标

通过：

```text
一个人 + 多个 AI Agent
```

完成后端、前端、AI 接入、测试、Review、文档和 CI/CD。

目标不是让 AI 零散地帮写代码，而是让 AI 成为可协作的软件团队。

## 2. 标准流程

```text
负责人提出需求
↓
Codex 拆任务并更新 TASKS.md
↓
Claude 开发后端
Gemini 开发前端
DeepSeek 补充样板代码和文档
↓
Codex Review
↓
CI 自动测试
↓
人工确认
↓
合并代码
```

## 3. Agent 分工

| Agent | 定位 | 主要职责 |
| --- | --- | --- |
| Codex | Tech Lead / 架构师 | 任务拆分、Review、架构治理 |
| Claude | 高级后端工程师 | 后端开发、重构、测试 |
| Gemini | 高级前端工程师 | React / Expo / UI / 移动端 |
| DeepSeek | 初中级工程师 | CRUD、DTO、文档、批量生成 |

## 4. 本地 CLI 调度

当前项目已配置本地 Agent CLI：

| Agent | 命令 | 用法 |
| --- | --- | --- |
| Claude | `claude` | 后端开发、重构、测试 |
| Gemini | `gemini` | Expo、React Native、移动端 UI |
| DeepSeek | `opencode` | DTO、CRUD、文档、批量生成 |

详细命令模板见 `docs/AGENT_CLI.md`。

常用非交互形式：

```bash
claude -p "只完成 TASK-BE-001，只修改 backend/**。"
gemini -p "只完成 TASK-FE-001，只修改 frontend/**。"
opencode run "只完成 TASK-DOC-001，只修改任务指定文件。"
```

## 5. Codex 拆任务 Prompt

```text
阅读 docs/PRD.md 与 docs/ARCHITECTURE.md。

将当前需求拆分为：
- 后端任务
- 前端任务
- 测试任务

输出到 docs/TASKS.md。

每个任务包含：
1. ID
2. 描述
3. 影响文件
4. 验收标准
5. 推荐负责人
6. 风险等级
```

## 6. Claude 后端开发 Prompt

```text
你是高级后端工程师。

阅读：
- docs/AGENT_RULES.md
- docs/API.md
- docs/TASKS.md

只完成：
TASK-BE-001

要求：
1. 新建 feature/task-be-001
2. 实现接口
3. 补充单测
4. 跑测试
5. 输出变更说明
```

## 7. Gemini 前端开发 Prompt

```text
你是高级前端工程师。

阅读：
- docs/AGENT_RULES.md
- docs/API.md
- docs/TASKS.md

只完成：
TASK-FE-001

要求：
1. 新建 feature/task-fe-001
2. 实现页面
3. 对接 mock API
4. 保证 lint/build 通过
5. 输出变更说明
```

## 8. DeepSeek 辅助开发 Prompt

```text
根据 docs/API.md：

批量生成：
- DTO
- Mapper
- TypeScript API Client

要求：
1. 不修改业务逻辑
2. 类型必须完整
3. 不引入新依赖
```

## 9. Codex Review Prompt

```text
你是项目 Tech Lead。

Review：
- feature/task-be-001
- feature/task-fe-001

检查：

1. 是否符合 PRD
2. 是否符合架构规范
3. 是否有安全风险
4. 是否缺少测试
5. 是否存在重复代码
6. 前后端接口是否一致

只输出必须修改项。
```

## 10. 协作原则

- 一个任务，一个分支，一个负责人。
- 禁止同时修改同一文件。
- Prompt 必须明确文件范围、目标和禁止修改内容。
- 共享协议先改文档，再改代码。
- CI 通过后再进入人工确认。

## 11. 推荐执行顺序

1. Codex 建立文档和任务。
2. Claude 初始化后端。
3. Gemini 初始化前端。
4. DeepSeek 生成 DTO 和 API Client。
5. Claude 实现认证与同步。
6. Gemini 实现本地存储与页面。
7. Codex Review 前后端接口一致性。
8. CI 全量检查。
