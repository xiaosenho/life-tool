# 任务 001：今日新闻加载开屏动画 — RESULT.md

## 实际采用的结构

采用 TASK.md 推荐的文件结构，保持职责清晰：

1. **`frontend/src/store/newsBootstrapStore.ts`** — Zustand store，管理新闻启动预取的全局状态：
   - `status: idle | loading | success | error`
   - `data: NewsItem[]` — 预取结果
   - `consumed: boolean` — 是否已被今日页消费
   - `prefetch()` — 去重触发预取（共享 promise 防止 Strict Mode 双调用）
   - `consume()` — 今日页消费预取数据并标记 consumed=true
   - `reset()` — 清理全部状态

2. **`frontend/src/components/NewsLoadingSplash.tsx`** — 纯展示型动画组件：
   - props: `newsReady` + `onFadeOutComplete`
   - 动画元素：Ionicons `newspaper` 图标浮动 + "今日精选"标题 + 三点脉冲加载指示
   - 时间控制：最短 900ms、最长 3000ms、淡出 220ms
   - 使用 `Animated.Value` + `Animated.loop` / `Animated.timing`
   - 安全区适配 `useSafeAreaInsets`
   - 主题色：`colors.background`、`colors.surface`、`colors.accent`、`colors.text`、`colors.muted`

3. **`frontend/app/_layout.tsx`** — 集成层：
   - 新增 `showSplash` / `newsReady` state 和 `newsPrefetchStartedRef`
   - 登录恢复完成（`isLoading=false` 且 `isAuthenticated=true`）后触发 `prefetch()` 并展示开屏层
   - 通过 `useNewsBootstrapStore.subscribe` 监听预取完成
   - 未登录用户不展示动画，不触发预取
   - `NewsLoadingSplash` 渲染为 Stack 上方的绝对定位覆盖层（zIndex 9999）

4. **`frontend/app/(tabs)/index.tsx`** — 今日页消费侧：
   - 首次 `useFocusEffect` 时检查 `consume()`，若返回数据则直接复用
   - 预取还在 loading 时订阅 store 等待完成
   - 后续回焦时正常调用 `loadNews()` 刷新
   - `loadNews()` 内部增加 `newsRequestInFlightRef` 去重防并发

## 修改文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `frontend/src/store/newsBootstrapStore.ts` | 新增 | 新闻启动预取状态管理 |
| `frontend/src/components/NewsLoadingSplash.tsx` | 新增 | 开屏动画组件 |
| `frontend/app/_layout.tsx` | 修改 | 引入 store 和组件，添加 splash 逻辑 |
| `frontend/app/(tabs)/index.tsx` | 修改 | 复用预取数据，请求去重 |

未修改 `newsService.ts`（预取直接调用 `newsService.getTopNews()`，无需改动服务层）。

## 自动验证结果

| 命令 | 结果 |
|------|------|
| `npm run typecheck` | ✅ 通过（exit code 0） |
| `npm run web:build` | ✅ 通过（944 modules，1.9MB bundle） |
| `npx expo export --platform android` | ✅ 通过（1244 modules，3.7MB HBC bundle） |

## 手动验证结果（需人工确认）

以下场景需要人工在设备或模拟器上验证：

1. **正常网络冷启动** — 应看到开屏动画，动画结束后新闻正常显示
2. **网络延迟 5 秒** — 动画应在 3 秒后淡出进入首页，后台请求完成后新闻更新
3. **`/news/top` 请求失败** — 动画最短展示后淡出，首页展示空态或"加载中"
4. **未登录态启动** — 不展示开屏动画，直接进入登录页
5. **Tab 间来回切换** — 回到今日页应触发刷新但不重复请求（同一次 focus 内去重）

## 遗留风险与待确认事项

1. **Strict Mode 双调用** — 已通过 `prefetchPromise` 共享 promise 和 `newsPrefetchStartedRef` 防止双触发。但 React 18 Strict Mode 的 effect 双调用可能导致 `subscribe` 被创建两次，第二次 subscribe 的 unsubscribe 不会影响第一次订阅的结果消费。建议在真机或 production build 上确认无重复请求。

2. **主题适配** — 动画使用了 `colors` 模块（默认 minimal 主题），三套主题的文字和背景对比需在实机上切换主题后确认视觉效果。当前代码读取的是模块级 `colors` 常量（启动时确定），如果用户在动画期间切换主题，颜色不会实时变化——但动画持续时间极短（≤3 秒），实际风险很低。

3. **`newsService.ts` 无请求级别去重** — `loadNews()` 通过 `newsRequestInFlightRef` 在组件级去重，但如果其他地方也调用 `newsService.getTopNews()`（比如预取和手动刷新几乎同时触发），仍可能产生并发请求。当前逻辑通过时间差（动画期间不会手动刷新）和 consume 标记来避免首轮重复，后续回焦时如果预取已完成且 consumed=true，`loadNews` 正常调用不会与预取冲突。

4. **Splash 层覆盖导航** — `NewsLoadingSplash` 使用 `pointerEvents="none"`，用户无法在动画期间点击其他区域。这是预期行为，但需确认不影响系统手势导航（Android 返回手势在 splash 期间是否可用）。由于动画时间 ≤3 秒，实际影响极小。
