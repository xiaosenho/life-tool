package com.lifetool.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AiControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        String unique = String.valueOf(System.nanoTime());
        tokenA = registerAndToken("ai-a-" + unique + "@test.com", "AI A");
        tokenB = registerAndToken("ai-b-" + unique + "@test.com", "AI B");
    }

    @Test
    void lifeAdviceReturnsChineseDisclaimer() throws Exception {
        mockMvc.perform(post("/api/ai/life-advice")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"last_7_days\",\"topics\":[\"focus\",\"ledger\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").isString())
                .andExpect(jsonPath("$.data.disclaimer").value("AI 建议仅供参考，不构成医疗、营养、财务或法律结论。"));
    }

    @Test
    void createSessionSendMessageAndListMessages() throws Exception {
        String sessionId = createSession(tokenA);

        mockMvc.perform(post("/api/ai/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "帮我结合专注和记账给点建议",
                                  "enabledTools": ["get_focus_summary", "get_ledger_summary"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("assistant"))
                .andExpect(jsonPath("$.data.disclaimer").value("AI 建议仅供参考，不构成医疗、营养、财务或法律结论。"))
                .andExpect(jsonPath("$.data.toolCalls.length()").value(2))
                .andExpect(jsonPath("$.data.toolCalls[0].toolName").value("get_focus_summary"))
                .andExpect(jsonPath("$.data.toolCalls[0].status").value("succeeded"));

        mockMvc.perform(get("/api/ai/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].role").value("user"))
                .andExpect(jsonPath("$.data.messages[1].role").value("assistant"));
    }

    @Test
    void sessionAndMemoryAreUserIsolated() throws Exception {
        String sessionId = createSession(tokenA);

        mockMvc.perform(get("/api/ai/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        MvcResult memories = mockMvc.perform(get("/api/ai/memories")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andReturn();

        String memoryId = objectMapper.readTree(memories.getResponse().getContentAsString())
                .get("data").get("items").get(0).get("id").asText();

        mockMvc.perform(delete("/api/ai/memories/{id}", memoryId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/ai/memories/{id}", memoryId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/ai/memories")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void unknownToolsAreSilentlyIgnored() throws Exception {
        String sessionId = createSession(tokenA);

        mockMvc.perform(post("/api/ai/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "测试未知工具",
                                  "enabledTools": ["get_focus_summary", "nonexistent_tool", "also_unknown"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toolCalls.length()").value(1))
                .andExpect(jsonPath("$.data.toolCalls[0].toolName").value("get_focus_summary"));
    }

    @Test
    void shortNamesFocusLedgerMapToFullToolNames() throws Exception {
        String sessionId = createSession(tokenA);

        mockMvc.perform(post("/api/ai/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "测试简写映射",
                                  "enabledTools": ["focus", "ledger"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toolCalls.length()").value(2))
                .andExpect(jsonPath("$.data.toolCalls[0].toolName").value("get_focus_summary"))
                .andExpect(jsonPath("$.data.toolCalls[0].status").value("succeeded"))
                .andExpect(jsonPath("$.data.toolCalls[1].toolName").value("get_ledger_summary"))
                .andExpect(jsonPath("$.data.toolCalls[1].status").value("succeeded"));
    }

    @Test
    void deleteNonExistentMemoryReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/ai/memories/{id}", "non-existent-memory-id")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void sendMessageWithEmptyContentReturns400() throws Exception {
        String sessionId = createSession(tokenA);

        mockMvc.perform(post("/api/ai/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "",
                                  "enabledTools": ["focus"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/ai/memories"))
                .andExpect(status().isUnauthorized());
    }

    private String createSession(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"最近状态分析\",\"useLongTermMemory\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("最近状态分析"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    private String registerAndToken(String email, String name) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"secret123\",\"displayName\":\"" + name + "\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }
}
