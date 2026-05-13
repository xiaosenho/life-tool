# LifeTool

手机端优先的个人生活管理工具，支持专注计时、习惯提醒、重要事件记录、饮食记录、记账、服务器同步、好友数据汇总与轻量对比。

本仓库按照“一个人 + 多个 AI Agent”的协作方式组织：

- `backend/`：服务端，建议由 Claude 主责。
- `frontend/`：移动端 App，建议由 Gemini 主责。
- `docs/`：产品、架构、接口、任务、Agent 规则。
- `scripts/`：本地开发、检查、部署脚本。
- `.github/workflows/`：CI/CD 配置。

## 快速开始

当前阶段是项目骨架和基础文档阶段，开发前请先阅读：

1. [docs/AGENT_RULES.md](docs/AGENT_RULES.md)
2. [docs/PRD.md](docs/PRD.md)
3. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
4. [docs/API.md](docs/API.md)
5. [docs/TASKS.md](docs/TASKS.md)
6. [docs/AGENT_CLI.md](docs/AGENT_CLI.md)
7. [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) — 本地开发环境部署

## 推荐 Agent 分工

| Agent | 定位 | 负责范围 |
| --- | --- | --- |
| Codex | Tech Lead / 架构师 | 任务拆分、架构治理、Review、CI 检查 |
| Claude | 高级后端工程师 | `backend/`、服务端测试、数据库设计 |
| Gemini | 高级前端工程师 | `frontend/`、Expo、移动端 UI |
| DeepSeek | 初中级工程师 | DTO、CRUD、文档、批量生成、脚手架 |

## 本地 Agent CLI

本机已安装并配置：

- `claude`：Claude 后端开发 CLI。
- `gemini`：Gemini 前端开发 CLI。
- `opencode`：DeepSeek API 已配置，用于低风险批量任务。

具体调度方式见 [docs/AGENT_CLI.md](docs/AGENT_CLI.md)。

## 当前状态

- 已建立多 Agent 项目结构。
- 已建立基础文档。
- 已记录本地 Claude / Gemini / DeepSeek CLI 调度规范。
- 已初始化后端和前端工程代码。
- 已提供本地 Docker 基础设施（PostgreSQL + Redis）。
- 已支持阿里云 ECS 生产部署，含安全组配置、Docker 安装、反向代理、备份、日志管理。
- docker-compose.yml 中 PostgreSQL/Redis 端口绑定 127.0.0.1，默认不对公网开放。
- 敏感配置（数据库密码、JWT 密钥）通过 .env 文件注入，不硬编码在 compose 中。
- 部署方式见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。
