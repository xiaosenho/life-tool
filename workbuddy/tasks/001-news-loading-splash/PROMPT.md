# 给 WorkBuddy 的执行提示词

> 已废弃：不要执行此提示词。请改为执行 `workbuddy/tasks/002-fix-app-launch-animation/PROMPT.md`。

你正在维护 LifeTool 项目。请完成任务：为已登录用户新增一个用于覆盖“今日新闻”首次加载时间的 App 内开屏动画。

开始前必须完整阅读：

1. `workbuddy/README.md`
2. `workbuddy/tasks/001-news-loading-splash/TASK.md`
3. `frontend/app/_layout.tsx`
4. `frontend/app/(tabs)/index.tsx`
5. `frontend/src/services/newsService.ts`
6. `frontend/src/theme/colors.ts`

核心要求：

- 这不是固定播放后才加载数据的装饰动画。动画展示与 `/news/top` 请求必须并行。
- 已登录冷启动时预取新闻，今日页直接复用结果，首轮不得重复请求。
- 动画最短展示约 900ms，最长等待 3000ms，之后淡出进入首页。
- 请求失败或超时不能阻塞用户。
- 未登录启动不展示新闻动画。
- 使用 React Native `Animated` 和现有图标库，不新增 Lottie/native 动画依赖。
- 使用现有主题色，保持简洁、现代、低干扰。
- 保留今日页回焦刷新能力，同时做好请求去重。
- 正确清理 timer、动画和订阅，兼容 Strict Mode。
- 保留工作区中所有无关修改，不要重写或回退其他人的代码。

建议先写一个简短实现计划，再开始修改。完成后必须运行：

```bash
cd frontend
npm run typecheck
npm run web:build
npx expo export --platform android
```

最后在 `workbuddy/tasks/001-news-loading-splash/RESULT.md` 写清实现结构、修改文件、验证结果和遗留风险。只有代码、自动验证和结果记录都完成后，任务才算结束。
