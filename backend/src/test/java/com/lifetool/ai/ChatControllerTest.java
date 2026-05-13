package com.lifetool.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"ai.mock-enabled=true"})
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String registerAndGetToken(String email) throws Exception {
        String body = """
                {"email":"%s","password":"secret123","displayName":"Tester"}""".formatted(email);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return data.get("accessToken").asText();
    }

    @Test
    void createSessionAndSendMessage() throws Exception {
        String token = registerAndGetToken("ai-chat@example.com");

        MvcResult sessionResult = mockMvc.perform(post("/api/ai/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"测试对话"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("测试对话"))
                .andReturn();

        JsonNode sessionData = objectMapper.readTree(sessionResult.getResponse().getContentAsString()).get("data");
        String sessionId = sessionData.get("id").asText();

        mockMvc.perform(post("/api/ai/chat/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"你好"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("assistant"))
                .andExpect(jsonPath("$.data.content").isString());

        mockMvc.perform(get("/api/ai/chat/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listSessions() throws Exception {
        String token = registerAndGetToken("ai-sessions@example.com");

        mockMvc.perform(post("/api/ai/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"会话1"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/ai/chat/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void deleteSession() throws Exception {
        String token = registerAndGetToken("ai-delete@example.com");

        MvcResult sessionResult = mockMvc.perform(post("/api/ai/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"待删除"}"""))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode sessionData = objectMapper.readTree(sessionResult.getResponse().getContentAsString()).get("data");
        String sessionId = sessionData.get("id").asText();

        mockMvc.perform(delete("/api/ai/chat/sessions/" + sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/ai/chat/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
