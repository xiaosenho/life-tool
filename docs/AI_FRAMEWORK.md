# AI_FRAMEWORK：AI 框架与当前实现

## 1. 目标

LifeTool 的 AI 能力作为统一后端能力存在，而不是几个零散接口：

- 支持饮食图片识别和热量估算。
- 支持生活建议和多轮对话。
- 支持多模态对话（文本、图片、语音）。
- 支持会话消息、长期记忆和记忆删除。
- 支持 function calling，让 AI 可以按权限读取用户数据汇总。
- 基于 Spring AI 接入模型、工具调用和 OpenAI-compatible Provider。
- 支持本地 mock，测试环境不依赖真实 AI Key。

## 2. 核心原则

1. 客户端不直接调用 AI Provider，也不持有 AI API Key。
2. AI 只能通过后端注册的工具访问数据，不能直接拼 SQL。
3. 后端按当前登录用户注入 `userId`，工具参数中不接受客户端传入的 `userId`。
4. 默认只向模型提供汇总数据。
5. AI 输出必须标注“仅供参考”，不得作为医疗、营养、财务或法律结论。
6. 对话、记忆、工具调用记录默认私密，可删除。
7. 长期记忆默认保守写入，只有用户明确表达稳定偏好时才入库。

## 3. 当前后端模块

当前实现集中在 `backend/src/main/java/com/lifetool/ai`：

```text
ai/
├── AiController                 # /api/ai 对外接口
├── AiService                    # AI 业务编排
├── AiAssistantClient            # 模型调用抽象
├── SpringAiAssistantClient      # Spring AI 真实模型实现
├── MockAiAssistantClient        # 本地 / 测试 mock 实现
├── AiProperties                 # lifetool.ai 配置
├── UserDataTools                # Spring AI @Tool 工具集合
├── AiChatStore                  # 会话、消息、工具调用存储抽象
├── AiMemoryStore                # 长期记忆存储抽象
├── InMemory*Store               # 本地内存实现
├── Postgres*Store               # PostgreSQL 实现
└── dto/                         # 请求和响应 DTO
```

当前代码没有单独的 `AiOrchestrator` 或 `ToolRegistry` 类；对应职责由 `AiService`、`AiAssistantClient` 和 `UserDataTools` 分担。后续如果 AI 流程继续变复杂，再拆出独立编排器。

## 4. Spring AI 接入方式

当前服务端通过 `AiAssistantClient` 屏蔽模型供应商差异：

- `SpringAiAssistantClient` 使用 Spring AI `ChatClient` 调用真实模型。
- `MockAiAssistantClient` 在本地、测试或未配置 Key 时返回确定性 mock 结果。
- OpenAI-compatible Provider 可以通过 base URL、model 和 completions path 配置。

重要配置：

| 配置 | 说明 |
| --- | --- |
| `AI_MOCK_ENABLED` / `lifetool.ai.mock-enabled` | 是否启用 mock AI |
| `AI_API_KEY` | 服务端 AI Key，禁止下发客户端 |
| `AI_BASE_URL` | OpenAI-compatible endpoint |
| `AI_MODEL` | 对话/多模态模型名 |
| `AI_CHAT_COMPLETIONS_PATH` | Chat Completions 路径；豆包方舟可配置为 `/chat/completions` |
| `lifetool.ai.disclaimer` | AI 免责声明 |

针对豆包等 Provider，不应在业务代码中硬编码 `/v3/v1` 或 `/v3`。优先通过 `AI_BASE_URL` + `AI_CHAT_COMPLETIONS_PATH` 组合适配：

```bash
AI_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
AI_CHAT_COMPLETIONS_PATH=/chat/completions
```

## 5. Function Calling 工具体系

### 5.1 当前执行流程

```text
用户消息
  -> AiService 校验会话归属
  -> 保存 user message
  -> 根据 enabledTools 或默认工具清单执行 UserDataTools
  -> 保存工具调用状态和结果摘要
  -> SpringAiAssistantClient / MockAiAssistantClient 生成 assistant 回复
  -> 保存 assistant message
  -> 返回消息、免责声明和工具调用状态
```

当前实现是“后端先执行允许的工具，再把工具结果交给模型总结”。`UserDataTools` 同时使用 Spring AI `@Tool` 标注，方便后续升级为由模型发起 tool call 的完整循环。

### 5.2 已实现工具

| 工具名 | 数据域 | 当前实现 |
| --- | --- | --- |
| `get_focus_summary` | focus | 读取专注偏好；历史专注统计待接入更完整聚合 |
| `get_habit_summary` | habit | 读取习惯数量、今日打卡数、完成率和最近打卡 |
| `get_diet_summary` | diet | 读取今日饮食热量、餐次分布和记录数 |
| `get_ledger_summary` | ledger | 读取本月收入、支出、结余、预算和分类数量 |
| `get_upcoming_events` | event | 读取未来纪念日和提醒摘要 |
| `get_user_profile_context` | profile | 返回基础偏好、时区和隐私边界摘要 |

写操作工具暂不开放给模型直接执行。创建饮食记录、记账、事件等写操作仍由普通业务接口完成。

## 6. 会话记忆

### 6.1 当前能力

- `POST /api/ai/chat/sessions` 创建会话。
- `POST /api/ai/chat/sessions/{id}/messages` 发送消息。
- `GET /api/ai/chat/sessions/{id}/messages` 获取会话消息。
- `GET /api/ai/memories` 查看启用的长期记忆。
- `DELETE /api/ai/memories/{id}` 禁用一条长期记忆。
- `SendAiMessageRequest` 支持通过 `attachment` 携带图片 / 语音媒体资产。

当前长期记忆通过 `AiMemoryStore` 保存，并可在生成系统提示词时注入。对话消息通过 `AiChatStore` 保存。

### 6.2 持久化表

数据库迁移已包含：

- `ai_chat_sessions`
- `ai_chat_messages`
- `ai_analysis_jobs`
- `ai_tool_calls`
- `ai_memory_items`
- `ai_session_summaries`
- `ai_agent_runs`

其中 `ai_tool_calls`、`ai_session_summaries` 和 `ai_agent_runs` 主要用于后续审计、摘要和更完整 Agent 运行记录。

## 7. 饮食识别

当前公开接口：

```text
POST /api/ai/food-recognition
```

请求包含：

- `mediaAssetId`：必填，绑定已上传的媒体资产，后端会在识图时实时生成可访问的短时效图片 URL。
- `mealType`：可选，默认由后端按当前时间或请求值归类。
- `customPrompt`：可选，用户补充说明。

当前流程：

```text
mediaAssetId
  -> AiService 生成 COS 短时效读链接
  -> AiService 调用 AiAssistantClient.chatWithImage
  -> 若模型下载图片返回 403，则刷新读链接并自动重试一次
  -> 模型返回中文识别结果和热量估算
  -> MealService.recordAiRecognition 写入 meal_logs
  -> 返回 result、disclaimer、mealLogId、totalCalories
```

当前行为是识别成功后直接生成当前用户饮食记录，并更新今日饮食统计。后续如果要加强用户控制，可以在前端增加“识别后编辑确认”步骤，或在后端增加 draft 状态。

## 7.1 多模态会话

AI 对话附件与好友聊天附件共用媒体资产能力：

- 图片：上传后通过 `assetId + width + height` 传入对话接口。
- 语音：上传后通过 `assetId + durationSeconds` 传入对话接口。
- 服务端在调用模型前按当前用户重新生成 COS 读链接，避免前端直接持有长期公网地址。

## 8. 安全与隐私

- 所有 AI 接口必须登录。
- `AI_API_KEY` 只允许存在服务端环境变量。
- 工具执行必须由服务端注入当前用户。
- 模型返回内容不作为专业医疗、营养、财务或法律建议。
- AI 不读取好友原始记录。
- 日志中禁止输出完整 AI prompt、完整对话、完整工具结果。
- 生产环境建议记录 trace id、工具名称、耗时和摘要，不记录敏感明细。
- 长期记忆写入必须通过后端判定，不允许客户端直接指定“写入记忆”。

## 9. API 分层

对客户端公开：

- `/api/ai/food-recognition`
- `/api/ai/life-advice`
- `/api/ai/chat/sessions`
- `/api/ai/chat/sessions/{id}/messages`
- `/api/ai/memories`

仅服务端内部使用：

- Spring AI `ChatClient`
- Spring AI `@Tool`
- `AiAssistantClient`
- `UserDataTools`
- Prompt Template / 系统提示词

客户端不直接请求 function calling 工具，工具调用由服务端完成。

## 10. 后续路线

1. 将 `get_focus_summary` 接入完整专注历史聚合。
2. 对 AI 工具调用补全 PostgreSQL 审计落库和查询页面。
3. 增加饮食识别编辑确认流程或 draft 状态。
4. 增加会话摘要生成和 token 控制。
5. 增加 pgvector 长期记忆检索。
6. 增加周报、月报和主动建议。

## 12. 最近更新

- AI 对话已支持图片与语音附件。
- 长期记忆策略已调整为“保守模式”，只在用户明确表达稳定偏好时写入。
- 助手消息响应已增加 `longTermMemorySaved` 标记，前端可据此提示“已记住你的长期偏好”。

## 11. 非目标

- MVP 不让 AI 直接修改用户任意数据。
- MVP 不实现自主 Agent 长任务。
- MVP 不把好友数据作为用户对话上下文。
- MVP 不提供医疗诊断、财务投资建议或法律建议。
