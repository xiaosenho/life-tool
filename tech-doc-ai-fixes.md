# 修复豆包 AI 接口路径问题

## 概述

本次排查集中解决了 AI 识图和 AI 会话调用豆包兼容接口时报错的问题。最终确认根因不是 COS 签名 URL 过期，而是 completions 路径被代码写死导致请求打错。

## 背景

相关技术栈：

| 项目 | 说明 |
| --- | --- |
| 后端框架 | Spring Boot 3.3 |
| AI 接口 | 豆包 Ark OpenAI-compatible |
| 对象存储 | 腾讯云 COS |
| 运行环境 | Docker / Docker Compose |
| Java 运行时 | Eclipse Temurin 21 |

实际问题表现：

- AI 识图报错，最初看起来像是 COS 图片无法访问
- AI 会话请求失败，前端感知为 AI 不可用

## 问题分析

### 1. AI 识图问题

初始异常表现类似：

```text
Error while downloading: https://...cos....jpg ... status code: 403
```

但进一步查看日志后发现：

- 签名 URL 是刚生成的
- `q-sign-time` 与实际请求时间间隔很短
- COS 5 分钟签名窗口本身没有过期

继续补日志后，真实错误被定位为：

```text
404 Not Found: [no body]
```

并且调用栈落在：

```text
com.lifetool.ai.SpringAiAssistantClient.chatWithImage(...)
com.lifetool.ai.SpringAiAssistantClient.chatWithMedia(...)
```

根本原因：

- 环境变量配置为：

```text
AI_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
AI_CHAT_COMPLETIONS_PATH=/chat/completions
```

- 但代码里仍把 REST 目标地址写死为：

```text
/v1/chat/completions
```

最终拼成错误路径：

```text
https://ark.cn-beijing.volces.com/api/v3/v1/chat/completions
```

外部模型接口直接返回 404。

## 解决方案

1. 为 AI 识图链路补充最小必要日志，确认签名 URL 是否新鲜、错误发生在哪一层。
2. 将 `chatWithImage(...)` 的直连 REST 请求改为读取 completions-path 配置，不再写死 `/v1/chat/completions`。
3. 同步修复 `chatWithMedia(...)` 使用的 completions 地址，确保 AI 会话与 AI 识图共用同一配置。
4. 在装配层注入 `spring.ai.openai.chat.completions-path`，统一由配置驱动。
5. 将本次排障结论回写到部署文档和任务文档，减少后续重复踩坑。

## 关键改动

**`backend/src/main/java/com/lifetool/ai/SpringAiAssistantClient.java`**：改为通过配置读取 completions 路径

```java
private final String chatCompletionsPath;

public SpringAiAssistantClient(
        ChatClient.Builder chatClientBuilder,
        UserDataTools userDataTools,
        @Value("${spring.ai.openai.api-key}") String apiKey,
        @Value("${spring.ai.openai.base-url}") String baseUrl,
        @Value("${spring.ai.openai.chat.options.model}") String chatModel,
        @Value("${spring.ai.openai.chat.completions-path:/v1/chat/completions}") String chatCompletionsPath) {
    this.chatCompletionsPath = normalizePath(chatCompletionsPath);
}
```

**`backend/src/main/java/com/lifetool/ai/SpringAiAssistantClient.java`**：直连 REST 不再写死 `/v1/chat/completions`

```java
response = restClient.post()
        .uri(chatCompletionsPath)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of(
                "model", chatModel,
                "messages", messages))
        .retrieve()
        .body(Map.class);
```

**`backend/src/main/java/com/lifetool/ai/AiConfiguration.java`**：把 completions-path 注入到客户端

```java
public AiAssistantClient springAiAssistantClient(
        ChatClient.Builder chatClientBuilder,
        UserDataTools userDataTools,
        @Value("${spring.ai.openai.api-key}") String apiKey,
        @Value("${spring.ai.openai.base-url}") String baseUrl,
        @Value("${spring.ai.openai.chat.options.model}") String model,
        @Value("${spring.ai.openai.chat.completions-path:/v1/chat/completions}") String chatCompletionsPath) {
    return new SpringAiAssistantClient(chatClientBuilder, userDataTools, apiKey, baseUrl, model, chatCompletionsPath);
}
```

## 验证

后端 AI 相关测试：

```bash
$ cd backend
$ mvn -q -Dtest=AiControllerTest,AiServiceFoodRecognitionRetryTest,AiAssistantClientTest test
```

实际结果：

- `AiControllerTest` 通过
- `AiServiceFoodRecognitionRetryTest` 通过
- `AiAssistantClientTest` 通过

日志验证结论：

```text
AI food recognition start ... qSignTime=...
Calling AI chatWithImage via direct REST API ...
404 Not Found: [no body]
```

这组日志证明：

- COS 签名 URL 是新鲜的
- 请求已经发到外部 AI 网关
- 真正失败点是 completions 路径错误

## 总结

- AI 识图与 AI 会话失败不一定是 COS 问题，必须先区分外部模型接口错误和对象存储下载错误。
- 对 OpenAI-compatible 网关接入时，`base-url` 和 `completions-path` 必须拆开管理，不能把 `/v1/chat/completions` 写死在代码里。

### 注意事项

- 前端若把所有 AI 请求失败统一提示成鉴权错误，容易误导排查方向，后续建议继续区分 401 / 404 / 5xx。
- 诊断日志在问题未稳定复现结束前不要过早移除。
