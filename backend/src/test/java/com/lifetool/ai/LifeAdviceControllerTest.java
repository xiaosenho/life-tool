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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"ai.mock-enabled=true"})
@AutoConfigureMockMvc
class LifeAdviceControllerTest {

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
    void getLifeAdviceReturnsSuggestions() throws Exception {
        String token = registerAndGetToken("ai-advice@example.com");

        mockMvc.perform(post("/api/ai/life-advice")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"period":"last_7_days","topics":["focus","habits"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").isString())
                .andExpect(jsonPath("$.data.suggestions").isArray())
                .andExpect(jsonPath("$.data.disclaimer").isString());
    }

    @Test
    void getLifeAdviceWithoutAuthReturns401() throws Exception {
        mockMvc.perform(post("/api/ai/life-advice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"period":"last_7_days","topics":["focus"]}"""))
                .andExpect(status().isUnauthorized());
    }
}
