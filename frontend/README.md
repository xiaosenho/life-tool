# Frontend

LifeTool 手机端 App，基于 Expo、React Native、TypeScript 和 Expo Router。

## 当前能力

- 五个底部 Tab：今日、专注、记录、好友、我的。
- 基础移动端布局和指标卡片。
- TypeScript 检查脚本。

## 本地命令

```bash
npm install
npm run typecheck
npm run start
```

## 模块边界

前端职责：

- 手机端主要交互体验
- 本地 SQLite 数据存储
- 离线记录
- 后台同步
- 本地提醒
- 好友排行榜和轻量对比

开发前必须阅读：

- `../docs/AGENT_RULES.md`
- `../docs/PRD.md`
- `../docs/API.md`
- `../docs/TASKS.md`
