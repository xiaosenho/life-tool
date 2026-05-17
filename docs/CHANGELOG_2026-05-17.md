# 2026-05-17 修改归档

## 概览

本次归档覆盖 2026-05-16 到 2026-05-17 之间的连续迭代，重点包括：

- 记录页重构为“日期首页”
- 纪念日 / 提醒规则修正
- 国内新闻轮播与缓存
- 背单词模块上线
- 好友与 AI 聊天多媒体能力
- 好友未读 badge、最近互动与聊天状态同步
- Android `1.0.0` 版本与应用图标

## 记录页

### 日期首页

- 记录页改为以日期视图为首页，支持日历形式浏览每日记录。
- 点击某一天后，下方统一展示该日：
  - 饮食
  - 记账
  - 专注
  - 习惯
  - 纪念日 / 提醒
- 统一了各类记录的展示风格，专注记录不再单独放大成大卡片。

### 批量查询优化

- 习惯 checkin 和日期页相关数据改为批量查询，不再按“每月每一天”循环请求。
- 首页和日期页的习惯完成数据也从逐条查询改为批量读取。

### 纪念日与提醒

- 修复纪念日未正确落库的问题。
- 统一后端字段为 `type`，清理旧 `event_type` 兼容问题。
- 返回结构新增：
  - `displayDate`
  - `reminderOffsetDays`
- 这样前端可以直接渲染“提前 1 天 / 7 天提醒”。
- 记录页现在不只展示即将到来的纪念日，也会保留过去的重要非重复事件，便于用户回看。

## 新闻

### 今日页新闻轮播

- 今日页顶部新增精选国内新闻轮播。
- 每条新闻支持：
  - 标题
  - 摘要
  - 发布时间
  - 封面图（若 RSS 可提取）
- 点击后在 App 内 WebView 打开原始链接。

### 服务端缓存

- 新闻接口切换为国内 RSS 源。
- 后端增加：
  - 进程内缓存
  - Redis 缓存
  - 应用启动后异步预热
- 用于降低 `GET /api/news/top` 的首屏耗时。

## 背单词

### 新模块上线

- 专注页新增背单词入口。
- 新增独立背单词页面：
  - 每次按顺序展示 30 个单词
  - 支持一键隐藏中文
  - 支持学习进度条
  - 支持选择词书

### 词书

- 当前内置六本词书：
  - 英语四级
  - 英语四级（乱序）
  - 英语六级
  - 英语六级（乱序）
  - 考研英语
  - 考研英语（乱序）

### 进度与音标

- 新增用户学习进度持久化：
  - `lastSeqNo`
  - `hideMeaning`
- 后端增加音标回退逻辑，尽可能为词条补齐音标。

## 好友聊天与互动

### 多媒体消息

- 好友聊天支持：
  - 文本消息
  - 图片消息
  - 语音消息
- 图片支持全屏预览与缩放。
- 语音支持播放进度展示。

### 录音交互

- 输入区改成长按录音、松开发送。
- 增加淡绿色取消浮层。
- 上滑进入取消区域时提供更明确反馈。

### 未读状态与最近互动

- 底栏好友 tab 增加未读 badge。
- badge 通过共享 store 统一维护。
- 好友页列表、“最近互动”、聊天页已读回落全部共用同一份未读 / 会话摘要状态。
- 发送消息成功后会本地回写会话摘要，不再等待下一轮轮询才看到最新文案。

## AI 助手

### 多模态聊天

- AI 聊天支持图片和语音附件。
- 附件统一走媒体资产链路，由后端在会话时读取 COS 私有资源。

### 长期记忆

- 长期记忆策略调整为保守模式：
  - 只有用户明确表达稳定偏好时才会入库
- 当前会话会注入已有长期记忆。
- 助手回复增加 `longTermMemorySaved` 标记，前端可据此给出提示。

### AI 识图链路

- AI 识图继续使用 COS 短时效 URL，不改为文件转存。
- 服务端针对图片读取失败场景增加重签名后重试。
- 识别成功后仍直接生成饮食记录；若估算热量为 0 且判断非食物，前端不再写入记录。

## 后端与基础设施

### 数据库连接

- 后端 `postgres` profile 已接入 HikariCP 连接池。
- 当前支持通过环境变量调节：
  - `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE`
  - `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE`
  - `SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT`
  - `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT`
  - `SPRING_DATASOURCE_HIKARI_MAX_LIFETIME`

### Flyway

- 新增并整理迁移：
  - `V8__add_vocab_tables.sql`
  - `V9__align_anniversary_event_type_column.sql`
- 修复过迁移版本冲突问题，确保后续环境更稳定落库。

## Android / 版本

### 版本号

- `frontend/app.json` 与 `frontend/package.json` 已统一更新到 `1.0.0`。

### 应用图标

- 新增应用图标资源：
  - `frontend/assets/app-icon.png`
  - `frontend/assets/adaptive-icon-foreground.png`

## 备注

- 文档更新重点同步到：
  - `README.md`
  - `backend/README.md`
  - `frontend/README.md`
  - `docs/PRD.md`
  - `docs/API.md`
  - `docs/ARCHITECTURE.md`
  - `docs/DATABASE.md`
  - `docs/ANDROID_BUILD.md`
