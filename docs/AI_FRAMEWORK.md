# AI_FRAMEWORK：AI 框架设计

## 1. 目标

LifeTool 的 AI 能力不应只是几个孤立接口，而应形成统一框架：

- 支持图片识别、生活建议、多轮对话。
- 支持会话记忆和长期用户偏好记忆。
- 支持 function calling，让 AI 可以按权限调用用户数据查询工具。
- 基于 Spring AI 接入模型、工具调用、Advisor 和 Chat Memory，避免重复造底层框架。
- 支持多模型供应商切换，避免业务代码绑定某一家模型服务。
- 所有 AI 行为可审计、可追踪、可降级。

## 2. 核心原则

1. 客户端不直接调用 AI Provider，也不持有 AI API Key。
2. AI 只能通过后端注册的工具访问数据，不能直接拼 SQL。
3. 后端按当前登录用户注入 `userId`，工具参数中不接受客户端传入的 `userId`。
4. 默认只向模型提供汇总数据，原始明细必须按工具白名单和数量限制读取。
5. AI 输出必须标注“仅供参考”，不得作为医疗、营养、财务或法律结论。
6. 对话、记忆、工具调用记录默认私密，可删除。

## 3. 后端模块

```text
ai/
├── controller/
│   ├── AiChatController
│   ├── AiFoodRecognitionController
│   └── AiMemoryController
├── orchestrator/
│   ├── AiOrchestrator
│   ├── ContextAssembler
│   └── SafetyGuard
├── springai/
│   ├── SpringAiChatClientFactory
│   ├── SpringAiAdvisorConfig
│   ├── SpringAiToolConfig
│   └── MockAiModelConfig
├── tools/
│   ├── AiTool
│   ├── ToolRegistry
│   └── UserDataTools
├── memory/
│   ├── MemoryService
│   ├── SessionSummaryService
│   └── MemoryExtractor
└── persistence/
    ├── AiChatSessionRepository
    ├── AiChatMessageRepository
    ├── AiToolCallRepository
    └── AiMemoryRepository
```

## 4. Spring AI 技术选型

MVP 不自研底层 AI Provider 框架，优先使用 Spring AI：

| 能力 | Spring AI 组件 | LifeTool 负责 |
| --- | --- | --- |
| 对话调用 | `ChatClient` / `ChatModel` | 会话 API、用户鉴权、业务提示词 |
| Function Calling | `@Tool` / `ToolCallback` / `ToolCallAdvisor` | 工具白名单、用户数据隔离、结果审计 |
| 会话记忆 | `ChatMemory` / `MessageChatMemoryAdvisor` | 完整聊天记录、会话摘要、长期记忆管理 |
| 上下文增强 | Advisor API | 注入用户隐私配置、长期记忆、业务边界 |
| 结构化输出 | `entity(...)` / structured output | 饮食识别结果 DTO、校验和待确认流程 |
| 多供应商 | Spring AI model starter | 环境配置、模型选择和降级策略 |

业务代码不直接散落调用模型，而是通过 `AiOrchestrator` 统一编排：

- `AiOrchestrator` 组装系统提示词、用户上下文、长期记忆和可用工具。
- Spring AI `ChatClient` 负责模型调用、Advisor 链和工具调用循环。
- LifeTool 自己持久化完整聊天记录、工具调用审计和长期记忆。
- 本地和测试环境使用 `MockAiModelConfig` 或 Spring Bean 替身，不依赖真实外部模型。

建议配置项：

| 配置 | 说明 |
| --- | --- |
| `spring.ai.model.chat` | Spring AI 当前使用的 chat model 类型 |
| `spring.ai.openai.base-url` | OpenAI-compatible endpoint，按实际供应商调整 |
| `spring.ai.openai.chat.completions-path` | Chat Completions 路径，默认 `/v1/chat/completions`；豆包方舟可配置为 `/chat/completions` |
| `spring.ai.openai.api-key` | 服务端保存，禁止下发客户端 |
| `spring.ai.openai.chat.options.model` | 对话模型 |
| `spring.ai.openai.image.options.model` | 图片或多模态模型，按供应商能力调整 |
| `lifetool.ai.mock-enabled` | 本地和测试环境是否启用 mock 模型 |
| `lifetool.ai.max-tool-rounds` | 单次对话最大工具调用轮数，建议 3 |
| `lifetool.ai.memory.enabled` | 是否启用长期记忆 |

参考官方能力：

- Spring AI Chat Memory: https://docs.spring.io/spring-ai/reference/api/chat-memory.html
- Spring AI Tool Calling: https://docs.spring.io/spring-ai/reference/api/tools.html
- Spring AI Advisors API: https://docs.spring.io/spring-ai/reference/api/advisors.html

## 5. Function Calling 工具体系

### 5.1 工具调用流程

```text
用户消息
  -> ContextAssembler 组装系统提示词、近期消息、记忆摘要
  -> Spring AI ChatClient + Advisor 链
  -> Spring AI ToolCallAdvisor 解析 tool calls
  -> @Tool / ToolCallback 调用 LifeTool 工具
  -> 业务 Service 查询当前用户数据
  -> 工具结果写入 ai_tool_calls
  -> 再次调用模型生成最终回答
  -> 持久化 assistant 消息
```

### 5.2 工具注册格式

每个工具必须声明：

- `name`：工具名，稳定不随意改。
- `description`：给模型看的用途说明。
- `input_schema`：由 Spring AI 根据方法参数或 `ToolCallback` 生成，并由后端再次校验关键字段。
- `scope`：可访问的数据域，如 `focus`、`habit`、`diet`。
- `max_result_items`：最大返回条数。
- `sensitivity`：`summary_only` / `detail_allowed`。
- `executor`：Spring Bean 方法或显式 `ToolCallback`。

### 5.3 MVP 工具清单

| 工具名 | 数据域 | 说明 | 返回粒度 |
| --- | --- | --- | --- |
| `get_focus_summary` | focus | 查询近 N 天专注总时长、次数、趋势 | 汇总 |
| `get_habit_summary` | habit | 查询习惯完成率、连续打卡、薄弱习惯 | 汇总 |
| `get_diet_summary` | diet | 查询已确认饮食热量、餐次分布、识别草稿数量 | 汇总 |
| `get_ledger_summary` | ledger | 查询月度收支、预算、分类支出 | 汇总 |
| `get_upcoming_events` | event | 查询未来纪念日和提醒 | 摘要 |
| `get_user_profile_context` | profile | 查询用户基础偏好、时区、隐私配置 | 摘要 |

写操作工具暂不开放给模型直接执行。创建饮食记录、记账、事件等写操作仍由用户在客户端确认后调用普通业务接口。

## 6. 会话记忆

### 6.1 记忆分层

| 层级 | 生命周期 | 存储 | 用途 |
| --- | --- | --- | --- |
| 短期上下文 | 当前会话 | `ai_chat_messages` | 保持多轮对话连贯 |
| 会话摘要 | 当前会话持续更新 | `ai_session_summaries` | 压缩长对话，减少 token |
| 长期记忆 | 跨会话 | `ai_memory_items` | 记录用户明确偏好、目标、限制条件 |
| 工具结果缓存 | 单次 run | `ai_tool_calls` | 审计与复盘，不直接长期记忆 |

Spring AI `ChatMemory` 只负责给模型恢复必要上下文，不替代产品侧完整聊天记录。完整历史、删除能力、审计字段仍由 LifeTool 的业务表维护。

### 6.2 长期记忆类型

| type | 示例 |
| --- | --- |
| `preference` | “用户更喜欢早上安排深度专注” |
| `goal` | “用户希望本月减少夜宵” |
| `constraint` | “用户不希望 AI 使用记账明细，只看汇总” |
| `health_note` | “用户声明乳糖不耐受”，必须标记为用户自述，不作诊断 |
| `routine` | “工作日午餐通常在 12:30 左右” |

### 6.3 记忆写入规则

- AI 不自动把所有对话写入长期记忆。
- 只有满足以下条件之一才写入长期记忆：
  - 用户明确表达稳定偏好或目标。
  - 用户点击“记住这个偏好”。
  - 系统从多次会话中提取到稳定模式，并等待用户确认。
- 敏感信息默认不写入长期记忆。
- 用户必须可以查看、禁用、删除长期记忆。

## 7. 上下文组装策略

单次对话输入按以下优先级组装：

1. 系统规则：角色、边界、安全约束。
2. 当前用户隐私设置和授权范围。
3. 当前会话最近 N 条消息。
4. 会话摘要。
5. 长期记忆中与问题相关的条目。
6. 工具调用结果。

上下文大小控制：

- 最近消息默认 20 条以内。
- 工具结果优先返回统计摘要。
- 原始明细默认最多 20 条，且必须由具体工具显式允许。
- 超出限制时先生成摘要，不直接塞入模型。

## 8. 饮食识别接入方式

饮食识别使用同一个 AI 框架，但走视觉模型：

```text
mediaAssetId
  -> 校验媒体归属和 purpose = meal_photo
  -> 读取图片可访问 URL 或短期下载 URL
  -> Spring AI 多模态模型调用
  -> 结构化输出 FoodRecognitionResult
  -> 保存 ai_analysis_jobs
  -> 客户端展示待确认结果
  -> 用户确认后写入 meal_logs / meal_items
```

结构化输出必须包含：

- 食物名称。
- 估算重量。
- 估算热量。
- 置信度。
- 总热量。
- 说明和不确定性提示。

## 9. 安全与隐私

- 所有 AI 接口必须登录。
- `AI_API_KEY` 只允许存在服务端环境变量。
- 工具执行必须记录 `sessionId`、`messageId`、`toolName`、参数、结果摘要和耗时。
- 模型返回的 tool call 必须经过工具白名单匹配。
- 工具参数必须通过 JSON Schema 校验。
- AI 不读取好友原始记录。
- 对话删除时，消息、会话摘要和关联长期记忆按用户选择处理。
- 日志中禁止输出完整 AI prompt、完整对话、完整工具结果；生产环境只记录摘要和 trace id。

## 10. API 分层

对客户端公开：

- `/api/ai/food-recognition/jobs`
- `/api/ai/life-advice`
- `/api/ai/chat/sessions`
- `/api/ai/chat/sessions/{id}/messages`
- `/api/ai/memories`

仅服务端内部使用：

- Spring AI `ChatClient`。
- Spring AI Advisor 链。
- Spring AI Tool Callback。
- Prompt Template。
- LifeTool Tool Registry。

客户端不直接请求 function calling 工具，工具调用由 AI Orchestrator 在服务端完成。

## 11. 实施路线

1. `TASK-DB-003`：补充 AI memory、tool call、agent run 表。
2. `TASK-BE-106`：接入 Spring AI，配置 `ChatClient`、Chat Memory、Advisor 链、Tool Calling 和 Mock 模型。
3. `TASK-BE-110`：实现只读用户数据查询工具。
4. `TASK-BE-105`：将饮食图片识别接入 Spring AI 多模态能力。
5. `TASK-FE-105`：实现 AI 对话、建议、记忆管理页面。
6. 后续：接入向量检索、周期性周报/月报、主动提醒。

## 12. 非目标

- MVP 不让 AI 直接修改用户数据。
- MVP 不实现自主 Agent 长任务。
- MVP 不把好友数据作为用户对话上下文。
- MVP 不提供医疗诊断、财务投资建议或法律建议。
