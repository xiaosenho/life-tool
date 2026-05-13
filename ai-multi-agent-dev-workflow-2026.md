# AI 多 Agent 软件开发工作流（2026 版）

## 1. 目标

搭建一套：

```text
一个人 + 多个 AI Agent
```

即可完成：

- 后端开发
- 前端开发
- AI 接入
- 测试
- Review
- 文档
- CI/CD

的软件工程体系。

目标不是“AI 帮写代码”，而是：

> 让 AI 成为可协作的软件团队。

## 2. Agent 角色分工

| Agent | 定位 | 主要职责 |
| --- | --- | --- |
| Codex | Tech Lead / 架构师 | 任务拆分、Review、架构治理 |
| Claude | 高级后端工程师 | 后端开发、重构、测试 |
| Gemini | 高级前端工程师 | React / Expo / UI / 移动端 |
| DeepSeek | 初中级工程师 | CRUD、DTO、文档、批量生成 |

## 3. 整体工作流

```text
你（负责人）
↓
Codex 拆任务
↓
Claude 开发后端
Gemini 开发前端
DeepSeek 补充样板代码
↓
Codex Review
↓
CI 自动测试
↓
人工确认
↓
合并代码
```

## 4. 项目目录规范

```text
project/
├── backend/
├── frontend/
├── docs/
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── API.md
│   ├── TASKS.md
│   └── AGENT_RULES.md
├── scripts/
├── .github/
│   └── workflows/
└── README.md
```

## 5. 核心文档说明

| 文件 | 作用 |
| --- | --- |
| PRD.md | 产品需求 |
| ARCHITECTURE.md | 系统架构 |
| API.md | 接口协议 |
| TASKS.md | 当前任务列表 |
| AGENT_RULES.md | AI 行为规范 |

## 6. AGENT_RULES.md（核心）

`AGENT_RULES.md` 是整个系统最重要的文件。

所有 Agent 执行前必须阅读。

### 示例

```md
# AGENT RULES

## 基本原则

1. 不允许直接修改 main 分支
2. 每个任务必须使用独立 feature 分支
3. 修改前必须阅读：
   - PRD.md
   - ARCHITECTURE.md
   - API.md
4. 不允许无关重构
5. 不允许删除已有测试
6. 所有代码必须可运行

---

## 后端规范

- 使用 Spring Boot
- Controller 不写业务逻辑
- Service 负责业务
- Repository 只负责数据库访问
- DTO / VO / Entity 分离

---

## 前端规范

- React + TypeScript
- API 请求统一管理
- 页面组件和业务组件分离
- 不允许重复状态管理

---

## 提交要求

提交时必须说明：

1. 做了什么
2. 修改了哪些文件
3. 如何测试
4. 风险点
```

## 7. Codex 作为 Tech Lead

Codex 不负责大量写代码。

Codex 负责：

- 阅读 PRD
- 拆任务
- 架构治理
- Review
- CI 检查
- Agent 协调

### 示例 Prompt

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

## 8. Claude 负责后端

Claude 适合：

- Java
- Spring Boot
- DDD
- SQL
- 重构
- 单测

### 示例 Prompt

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

## 9. Gemini 负责前端

Gemini 适合：

- React
- Expo
- TypeScript
- UI
- 多模态

### 示例 Prompt

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

## 10. DeepSeek 负责低成本任务

DeepSeek 适合：

- DTO
- CRUD
- Mapper
- 文档
- 批量单测
- TypeScript API Client

### 示例 Prompt

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

## 11. Review 工作流

最终由 Codex 做 Review。

### Review Prompt

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

## 12. Git 分支规范

```text
main
develop
feature/task-be-001
feature/task-fe-002
fix/bug-001
```

禁止：

```text
直接修改 main
```

## 13. CI/CD 最小配置

必须自动执行：

### Backend

```bash
mvn test
```

### Frontend

```bash
pnpm lint
pnpm build
```

### GitHub Actions 示例

```yaml
name: CI

on:
  pull_request:

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - run: mvn test

  frontend:
    runs-on: ubuntu-latest
    steps:
      - run: pnpm install
      - run: pnpm lint
      - run: pnpm build
```

## 14. 多 Agent 协作原则

### 原则 1：禁止同时修改同一文件

正确：

```text
Claude -> backend/*
Gemini -> frontend/*
DeepSeek -> docs/*
```

错误：

```text
多个 Agent 同时修改 UserService.java
```

### 原则 2：Agent 必须任务隔离

每次：

```text
一个任务
一个分支
一个负责人
```

### 原则 3：Prompt 必须明确

错误：

```text
帮我优化一下项目
```

正确：

```text
只修改：
backend/user/*

目标：
新增用户分页接口

不要修改：
鉴权系统
数据库结构
```

## 15. 推荐技术栈（适合 AI 协作）

### Backend

- Java
- Spring Boot
- PostgreSQL
- Redis
- Elasticsearch

### Frontend

- React
- Next.js
- Expo
- TypeScript

### Infra

- Docker
- GitHub Actions
- Vercel
- Railway / Fly.io

## 16. 最适合的项目类型

这套工作流非常适合：

- AI SaaS
- 企业系统
- AI Agent 平台
- AI App
- 知识库
- 中后台系统
- 工具型产品

## 17. 不适合的项目

暂时不适合：

- 超大型游戏引擎
- 底层数据库
- 操作系统
- 编译器
- 高性能 C++ 基础设施

原因：

AI 目前仍然缺乏：

- 长周期一致性
- 极复杂架构控制
- 超大型工程记忆能力

## 18. 你在整个体系中的角色

你不再是：

```text
纯编码工程师
```

而是：

> AI 软件团队管理者。

你的核心能力会变成：

- 拆任务
- 控制架构
- Review
- 验收
- 调度 Agent

## 19. 最终目标

最终形成：

```text
你
+
多个 AI Agent
+
自动化 CI/CD
+
标准化 Prompt
```

从而让一个人也能以接近小型软件团队的方式推进产品研发。

## 20. 落地建议

在具体项目中，建议先把本工作流拆成以下文档：

```text
docs/
├── PRD.md
├── ARCHITECTURE.md
├── API.md
├── TASKS.md
├── AGENT_RULES.md
└── WORKFLOW.md
```

其中：

- `WORKFLOW.md` 保存本文档内容。
- `AGENT_RULES.md` 保存所有 Agent 必须遵守的执行规范。
- `TASKS.md` 只保存当前阶段可执行任务。
- `API.md` 固定前后端协作协议。
- `ARCHITECTURE.md` 固定系统边界和技术决策。
- `PRD.md` 固定产品目标、功能范围和验收标准。
