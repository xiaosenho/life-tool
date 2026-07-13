# 给 WorkBuddy 的 FIX 执行提示词

请修正 LifeTool 任务 001 的实现。准确需求是：**这是 App 打开时的启动动画，但它需要利用启动等待阶段并行加载今日新闻，从而掩盖新闻接口较慢的问题。**

它不是今日新闻页面里的局部加载动画，也不能显示“正在加载新闻”之类的业务文案。

执行前完整阅读：

1. `workbuddy/README.md`
2. `workbuddy/tasks/001-news-loading-splash/TASK.md`
3. `workbuddy/tasks/001-news-loading-splash/RESULT.md`
4. `workbuddy/tasks/002-fix-app-launch-animation/TASK.md`
5. `frontend/app/_layout.tsx`
6. `frontend/app/(tabs)/index.tsx`
7. `frontend/src/store/authStore.ts`
8. `frontend/src/store/newsBootstrapStore.ts`
9. `frontend/src/components/NewsLoadingSplash.tsx`

核心执行要求：

- 把现有 `NewsLoadingSplash` 修正为通用的 `AppLaunchLoading`，使用 App 品牌元素和通用启动文案，移除新闻图标、今日精选标题及新闻加载文案。
- 动画应在 App 冷启动阶段立即出现，覆盖 SQLite 初始化、登录态恢复和首页数据准备，不应等认证结束后才突然弹出。
- 登录恢复完成且确认已登录后，立即并行预取 `/news/top`。
- 已登录场景下，最短展示约 800～1000ms；新闻完成后淡出。如果新闻过慢，最长约 3000ms 后必须进入首页。
- 未登录场景不请求新闻；基础初始化完成并满足最短展示时间后进入登录页。
- 今日页继续复用启动预取结果，避免首轮重复请求；后续回焦刷新保持正常。
- 使用 React Native `Animated` 和现有资源，不新增 Lottie 或原生动画依赖。
- 正确清理 timer、动画循环和 store subscription。
- 删除所有废弃的旧组件引用和无用状态，不能同时保留新闻启动动画与 App 启动动画。
- 保留工作区所有无关修改，不使用整体文件回退命令。

完成后运行：

```bash
cd frontend
npm run typecheck
npm run web:build
npx expo export --platform android
```

最后在 `workbuddy/tasks/002-fix-app-launch-animation/RESULT.md` 记录实现和验证结果。未生成 RESULT.md 不算完成。
