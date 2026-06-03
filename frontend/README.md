# Frontend

LifeTool 多端客户端，基于 Expo、React Native、React Native Web、TypeScript 和 Expo Router。

## 当前能力

- 五个底部 Tab：今日、专注、记录、好友、我的。
- 今日页新闻轮播、专注页番茄钟与背单词入口、记录页日历总览、好友页互动与未读 badge、AI/好友多媒体聊天。
- 饮食图片全屏预览、语音消息、好友未读状态共享 store、背单词学习进度与隐藏中文。
- TypeScript 检查脚本。
- Web 端静态构建，默认请求 `http://xiaosenho.top:8091/api`，可通过 `EXPO_PUBLIC_API_BASE_URL` 覆盖。

## 本地命令

```bash
npm install
npm run typecheck
npm run start
npm run web:dev
npm run web:build
```

## Web Docker 部署

Web 端提供专属 Docker 多阶段构建文件：构建阶段使用 Node/Expo 导出静态产物，运行阶段内置 Nginx 托管单页应用。默认 API 地址为 `http://xiaosenho.top:8091/api`。

```bash
# 在仓库根目录一键构建并启动 Web 容器，默认映射到宿主机 8101 端口
docker compose -f docker-compose.web.yml up -d --build

# 如需覆盖 Web 端 API 地址或端口
EXPO_PUBLIC_API_BASE_URL=http://xiaosenho.top:8091/api WEB_PORT=8101 docker compose -f docker-compose.web.yml up -d --build
```

也可以只构建前端镜像：

```bash
docker build -f frontend/Dockerfile.web -t lifetool-web:latest frontend
```

## 模块边界

前端职责：

- 手机端主要交互体验
- Web 端 Expo Router 单页应用构建
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
