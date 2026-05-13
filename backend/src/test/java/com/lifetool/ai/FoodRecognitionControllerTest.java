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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"ai.mock-enabled=true"})
@AutoConfigureMockMvc
class FoodRecognitionControllerTest {

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
    void createJobReturnsPending() throws Exception {
        String token = registerAndGetToken("ai-job@example.com");
        mockMvc.perform(post("/api/ai/food-recognition/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaAssetId":"test-asset-id"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.jobId").isString());
    }

    @Test
    void createJobWithoutAuthReturns401() throws Exception {
        mockMvc.perform(post("/api/ai/food-recognition/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaAssetId":"test-asset-id"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getJobReturnsResult() throws Exception {
        String token = registerAndGetToken("ai-job-result@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/ai/food-recognition/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaAssetId":"test-asset-id"}"""))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createData = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        String jobId = createData.get("jobId").asText();

        Thread.sleep(500);

        mockMvc.perform(get("/api/ai/food-recognition/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("succeeded"))
                .andExpect(jsonPath("$.data.result.totalCalories").isNumber())
                .andExpect(jsonPath("$.data.result.items").isArray());
    }

    @Test
    void crossUserIsolation() throws Exception {
        String tokenA = registerAndGetToken("ai-user-a@example.com");
        String tokenB = registerAndGetToken("ai-user-b@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/ai/food-recognition/jobs")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaAssetId":"test-asset"}"""))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createData = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        String jobId = createData.get("jobId").asText();

        mockMvc.perform(get("/api/ai/food-recognition/jobs/" + jobId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }
}
