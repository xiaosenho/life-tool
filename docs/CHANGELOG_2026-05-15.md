# 2026-05-15 修改归档

## 概览

本次归档覆盖最近几轮围绕 AI 助手、好友互动、记录页预览、习惯打卡交互的连续修改，重点是把原本耦合较重的页面与状态拆开，并补齐一批直接影响可用性的交互与后端能力。

## AI 助手

### 页面拆分

- 将原本混在 Tab 页内的 AI 页面拆分为独立路由：
  - `frontend/app/ai.tsx`
  - `frontend/app/ai-chat.tsx`
- `frontend/app/_layout.tsx` 注册了新的栈页面。
- 删除旧的 `frontend/app/(tabs)/ai.tsx`，避免路由和状态重复挂载导致的页面异常。
- `frontend/app/(tabs)/_layout.tsx` 启用 `backBehavior="history"`，让返回行为更接近真实页面栈。

### 近期建议与长期记忆

- AI 首页拆成三块独立区域：
  - 近期建议
  - 长期记忆
  - 对话入口
- 近期建议与长期记忆改为独立加载，避免三块内容互相阻塞。
- 近期建议请求中增加右上角旋转加载态。
- 删除了旧的“已拆分到独立页面”等过渡性提示文案。

### 对话页体验

- AI 对话输入框固定到底部，并使用 `KeyboardAvoidingView`，减少输入法遮挡问题。
- 引入 `frontend/src/features/ai/aiChatCache.ts` 缓存会话与消息，重进页面时体验更稳定。
- 请求工具状态仅在执行期展示，不再把“正在读取...”混入用户气泡。

### 长期记忆改造

- 新增长期记忆工具调用：`save_long_term_memory`。
- 当前策略为保守模式：只有用户明确表达长期稳定偏好时才允许保存。
- 支持示例：
  - “以后都用简洁中文回答我”
  - “请记住我更关注减脂饮食”
- 后端会将长期记忆注入后续会话系统提示词中。
- 助手回复新增 `longTermMemorySaved` 标记。
- 当前轮助手回复若成功保存长期记忆，前端会显示轻提示：`已记住你的长期偏好`。

### Mock 与真实客户端行为修正

- `MockAiAssistantClient` 已支持显式偏好识别与长期记忆写入，便于本地调试。
- `SpringAiAssistantClient` 增加无记忆的 `statelessChatClient`，避免 AI 识图场景错误复用历史图片上下文。

## 习惯模块

### 今日页打卡交互

- 今日页习惯卡片支持直接点击切换：
  - 未完成：点击完成
  - 已完成：点击取消完成
- 卡片文案增加当前动作提示，减少误解。

### 后端接口

- 新增取消习惯打卡接口：
  - `DELETE /api/habits/{id}/checkins?checkinDate=YYYY-MM-DD`
- 支持按指定日期删除对应打卡记录，并同步刷新排行榜相关统计。

### 离线支持

- 前端本地习惯服务新增离线取消打卡能力。
- Web 本地数据库模拟层补齐 `DELETE FROM habit_checkins` 行为。
- 断网时完成/取消完成都可先落本地，待后续同步。

## 好友与互动

### 页面刷新

- 好友页在重新切换、返回聚焦、排行榜标签切换时会主动刷新。
- 排行榜四个按钮现在都会触发数据刷新，不再只是切换高亮。

### 排行榜说明

- 为四个排行榜补充语义说明：
  - 今日专注
  - 七日专注
  - 今日习惯完成率
  - 连续打卡
- “连续打卡”的定义已明确为：连续多少天至少完成过 1 个习惯打卡，中断 1 天即重新计算。

### 对话轮询

- 好友对话页加入轻量轮询，页面聚焦时定时刷新消息，离开时自动停止。

## 记录页

### 饮食图片预览

- 最近饮食记录的大图预览区域增加更稳定的容器布局。
- 预览区域点击事件做了隔离，避免遮罩层与图片区域互相抢事件。
- 图片预览尺寸增加最大宽高约束，降低异常长图造成的布局问题。

## 测试与验证

本轮已补充或更新以下验证：

- `backend/src/test/java/com/lifetool/ai/AiControllerTest.java`
- `backend/src/test/java/com/lifetool/ai/AiAssistantClientTest.java`
- `backend/src/test/java/com/lifetool/habits/HabitControllerTest.java`
- `frontend` 类型检查 `npm run typecheck`

## 备注

- 本次提交不包含无关的大体积文件 `scripts/gradle-8.13-bin.zip`。
- 若后续继续迭代 AI 记忆策略，建议在此基础上再区分：
  - 新保存成功
  - 已存在未重复保存
  - 被模型判断为不应保存
