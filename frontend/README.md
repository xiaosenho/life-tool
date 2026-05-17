# Frontend

LifeTool 手机端 App，基于 Expo、React Native、TypeScript 和 Expo Router。

## 当前能力

- 五个底部 Tab：今日、专注、记录、好友、我的。
- 今日页新闻轮播、专注页番茄钟与背单词入口、记录页日历总览、好友页互动与未读 badge、AI/好友多媒体聊天。
- 饮食图片全屏预览、语音消息、好友未读状态共享 store、背单词学习进度与隐藏中文。
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
- AI / 好友聊天体验
- 新闻与背单词页面交互

开发前必须阅读：

- `../docs/AGENT_RULES.md`
- `../docs/PRD.md`
- `../docs/API.md`
- `../docs/TASKS.md`
