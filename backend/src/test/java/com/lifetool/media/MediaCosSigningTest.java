package com.lifetool.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "COS_REGION=ap-guangzhou",
        "COS_BUCKET=life-tool-test-1250000000",
        "COS_SECRET_ID=test-secret-id",
        "COS_SECRET_KEY=test-secret-key",
        "COS_PUBLIC_BASE_URL=https://cdn.example.com",
        "COS_PUBLIC_READ_ENABLED=true"
})
@AutoConfigureMockMvc
class MediaCosSigningTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void uploadTokenUsesCosPresignedPutUrlWhenSecretsConfigured() throws Exception {
        String token = registerAndGetToken("media-cos-signing@example.com");

        mockMvc.perform(post("/api/media/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType":"image/jpeg","purpose":"meal_photo","fileSize":512000}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl", containsString("life-tool-test-1250000000.cos.ap-guangzhou.myqcloud.com")))
                .andExpect(jsonPath("$.data.uploadUrl", containsString("q-signature=")))
                .andExpect(jsonPath("$.data.uploadUrl", not(containsString("/mock-cos/"))));
    }

    @Test
    void createdAssetUsesConfiguredPublicReadUrl() throws Exception {
        String token = registerAndGetToken("media-public-read@example.com");

        MvcResult tokenResult = mockMvc.perform(post("/api/media/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType":"image/jpeg","purpose":"meal_photo","fileSize":512000}"""))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode tokenData = objectMapper.readTree(tokenResult.getResponse().getContentAsString()).get("data");
        String assetId = tokenData.get("assetId").asText();
        String objectKey = tokenData.get("objectKey").asText();

        mockMvc.perform(post("/api/media/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetId":"%s",
                                  "objectKey":"%s",
                                  "contentType":"image/jpeg",
                                  "purpose":"meal_photo",
                                  "fileSize":512000
                                }""".formatted(assetId, objectKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.readUrl").value("https://cdn.example.com/" + objectKey));
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"secret123","displayName":"Tester"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return data.get("accessToken").asText();
    }
}
