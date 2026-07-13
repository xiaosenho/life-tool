# FIX 任务 002：修正为 App 启动加载动画 — RESULT.md

## 实际采用的结构

将任务 001 的"新闻开屏动画"修正为通用 App 启动加载动画，核心变化：

1. **`frontend/src/components/AppLaunchLoading.tsx`**（新增，替代 `NewsLoadingSplash.tsx`）
   - 通用 App 品牌启动画面：`sparkles` 图标 + 呼吸缩放圆环 + "LifeTool" 标题 + "整理今天，准备出发" 文案 + 三点脉冲指示
   - 移除了新闻图标（`newspaper`）、"今日精选"标题、"正在为你准备今日新闻"等所有新闻业务文案
   - props: `ready: boolean` + `onFadeOutComplete: () => void`
   - 时间控制：最短 900ms、最长 3000ms、淡出 220ms（均从组件挂载开始计算）
   - 使用 `readyRef` 同步最新 ready 值，避免闭包陈旧问题
   - 所有 timer、Animated loop 在卸载时清理，`mountedRef` 防止卸载后 setState

2. **`frontend/app/_layout.tsx`**（修正）
   - `showLaunch` 初始为 `true`，从 App 首次渲染就展示启动动画覆盖层
   - 移除了 `isLoading ? return null` 的提前返回；改为 `{!isLoading && (<>...</>)}` 条件渲染 Stack，动画覆盖层始终在最外层
   - 统一的 `launchReady` 状态替代了 `showSplash` / `newsReady` 双状态
   - 单一 effect 处理就绪逻辑：
     - `isLoading=false` + 未登录 → 立即 `setLaunchReady(true)`（最短时间仍由组件保证）
     - `isLoading=false` + 已登录 → 触发 `prefetch()`，订阅 store，新闻完成（success/error）后 `setLaunchReady(true)`

3. **`frontend/src/components/NewsLoadingSplash.tsx`**（删除）
   - 已删除，无残留引用

4. **从任务 001 保留的部分**
   - `frontend/src/store/newsBootstrapStore.ts` — 无需修改，预取/消费/去重逻辑不变
   - `frontend/app/(tabs)/index.tsx` — 无需修改，首次进入消费预取、后续回焦刷新的逻辑不变
   - `frontend/src/services/newsService.ts` — 未修改

## 修改文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `frontend/src/components/AppLaunchLoading.tsx` | 新增 | 通用 App 启动动画组件，品牌化视觉 |
| `frontend/src/components/NewsLoadingSplash.tsx` | 删除 | 移除旧的新闻专用动画组件 |
| `frontend/app/_layout.tsx` | 修改 | 动画从 t=0 展示；统一就绪逻辑；移除旧导入和状态 |

## 自动验证结果

| 命令 | 结果 |
|------|------|
| `npm run typecheck` | 通过（exit code 0） |
| `npm run web:build` | 通过（938 modules，1.9MB bundle） |
| `npx expo export --platform android` | 通过（1244 modules，3.7MB HBC bundle） |

## 手动验证（需人工确认）

1. **已登录冷启动** — 动画从 App 打开立即出现，SQLite 初始化和登录恢复期间不出现空白；新闻完成后淡出进入首页
2. **`/news/top` 延迟 2 秒** — 等待期间只看到通用启动动画，进入首页后新闻已显示
3. **`/news/top` 延迟 5 秒** — 约 3 秒后进入首页，不永久停留在启动画面
4. **新闻失败/空数组** — 均能正常进入首页，无阻塞提示
5. **未登录冷启动** — 不请求新闻，最短展示时间后淡出进入登录页
6. **Tab 间切换** — 回到今日页正常刷新，首轮不重复请求
7. **Android 三键导航/手势导航/Web** — 安全区和布局正常

## 从任务 001 保留和修正的内容

**保留：**
- `newsBootstrapStore.ts` 的预取状态管理、去重 promise、consume 机制
- `index.tsx` 的首次消费预取数据和后续回焦刷新逻辑
- `loadNews()` 中的 `newsRequestInFlightRef` 请求去重
- 动画时间参数（900ms / 3000ms / 220ms）
- Animated + Ionicons 技术方案，无新增依赖

**修正：**
- 动画语义：从"新闻加载动画"改为"App 启动加载动画"
- 视觉：移除 `newspaper` 图标和"今日精选"/"正在为你准备今日新闻"文案，改为 `sparkles` 图标 + "LifeTool" + "整理今天，准备出发"
- 展示时机：从"登录恢复后弹出"改为"App 冷启动即展示"，覆盖 SQLite 初始化和登录态恢复
- 状态结构：从 `showSplash` + `newsReady` 双状态合并为 `showLaunch` + `launchReady`
- 渲染结构：移除 `isLoading ? return null`，改为条件渲染 Stack + 始终渲染覆盖层
- 旧组件删除：`NewsLoadingSplash.tsx` 已删除，无两套动画并存

## 遗留风险

1. **主题色静态读取** — `colors` 模块在启动时确定主题色，动画期间切换主题不会实时生效。但动画持续时间很短，实际影响可忽略。

## Codex 审核修复

审核时修复了启动动画的两个计时边界：

- 最短 900ms 现在必须真实经过，即使新闻提前返回也不会立即闪退。
- 新闻最长等待 3000ms 由根启动流程控制，只放弃等待新闻，不会绕过 SQLite 和登录态初始化，因此基础初始化较慢时不会重新露出空白页。

审核后重新通过 TypeScript、Web、Android 构建以及后端完整测试。
