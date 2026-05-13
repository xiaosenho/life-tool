package com.lifetool.media;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class MediaControllerTest {

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
    void uploadTokenWithoutAuthReturns401() throws Exception {
        String body = """
                {"contentType":"image/jpeg","purpose":"meal_photo","fileSize":512000}""";
        mockMvc.perform(post("/api/media/upload-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAssetWithoutAuthReturns401() throws Exception {
        mockMvc.perform(get("/api/media/assets/some-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadTokenSuccess() throws Exception {
        String token = registerAndGetToken("media-upload@example.com");
        String body = """
                {"contentType":"image/jpeg","purpose":"meal_photo","fileSize":512000}""";

        mockMvc.perform(post("/api/media/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetId").isString())
                .andExpect(jsonPath("$.data.uploadUrl").isString())
                .andExpect(jsonPath("$.data.objectKey").isString())
                .andExpect(jsonPath("$.data.expiresAt").isString())
                .andExpect(jsonPath("$.data.headers.Content-Type").value("image/jpeg"));
    }

    @Test
    void uploadTokenPngExtension() throws Exception {
        String token = registerAndGetToken("media-png@example.com");
        String body = """
                {"contentType":"image/png","purpose":"avatar","fileSize":100000}""";

        MvcResult result = mockMvc.perform(post("/api/media/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        String objectKey = data.get("objectKey").asText();
        assert objectKey.endsWith(".png") : "Expected .png extension, got: " + objectKey;
    }

    @Test
    void uploadTokenUnsupportedContentTypeRejected() throws Exception {
        String token = registerAndGetToken("media-badtype@example.com");
        String body = """
                {"contentType":"application/pdf","purpose":"meal_photo","fileSize":512000}""";

        mockMvc.perform(post("/api/media/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void uploadTokenFileTooLargeRejected() throws Exception {
        String token = registerAndGetToken("media-toolarge@example.com");
        String body = """
                {"contentType":"image/jpeg","purpose":"meal_photo","fileSize":20000000}""";

        mockMvc.perform(post("/api/media/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void uploadTokenInvalidPurposeRejected() throws Exception {
        String token = registerAndGetToken("media-badpurpose@example.com");
        String body = """
                {"contentType":"image/jpeg","purpose":"random_thing","fileSize":512000}""";

        mockMvc.perform(post("/api/media/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createAndGetAsset() throws Exception {
        String token = registerAndGetToken("media-asset@example.com");

        // Get upload token first to get a valid objectKey
        String uploadBody = """
                {"contentType":"image/jpeg","purpose":"meal_photo","fileSize":512000}""";
        MvcResult uploadResult = mockMvc.perform(post("/api/media/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(uploadBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode uploadData = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data");
        String assetId = uploadData.get("assetId").asText();
        String objectKey = uploadData.get("objectKey").asText();

        // Create asset
        String createBody = """
                {"assetId":"%s","objectKey":"%s","contentType":"image/jpeg","purpose":"meal_photo","fileSize":512000,"width":1280,"height":960}"""
                .formatted(assetId, objectKey);

        mockMvc.perform(post("/api/media/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(assetId))
                .andExpect(jsonPath("$.data.status").value("uploaded"))
                .andExpect(jsonPath("$.data.purpose").value("meal_photo"))
                .andExpect(jsonPath("$.data.width").value(1280))
                .andExpect(jsonPath("$.data.height").value(960));

        // Get asset
        mockMvc.perform(get("/api/media/assets/" + assetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(assetId))
                .andExpect(jsonPath("$.data.objectKey").value(objectKey));
    }

    @Test
    void deleteAssetMarksSoftDeleted() throws Exception {
        String token = registerAndGetToken("media-delete@example.com");

        // Create via upload-token + assets
        String uploadBody = """
                {"contentType":"image/webp","purpose":"event_photo","fileSize":200000}""";
        MvcResult uploadResult = mockMvc.perform(post("/api/media/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(uploadBody))
                .andReturn();
        JsonNode uploadData = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data");
        String assetId = uploadData.get("assetId").asText();
        String objectKey = uploadData.get("objectKey").asText();

        String createBody = """
                {"assetId":"%s","objectKey":"%s","contentType":"image/webp","purpose":"event_photo","fileSize":200000}"""
                .formatted(assetId, objectKey);
        mockMvc.perform(post("/api/media/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated());

        // Delete
        mockMvc.perform(delete("/api/media/assets/" + assetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Get after delete returns 404
        mockMvc.perform(get("/api/media/assets/" + assetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void crossUserIsolation() throws Exception {
        String tokenA = registerAndGetToken("media-usera@example.com");
        String tokenB = registerAndGetToken("media-userb@example.com");

        // User A creates asset
        String uploadBody = """
                {"contentType":"image/jpeg","purpose":"meal_photo","fileSize":100000}""";
        MvcResult uploadResult = mockMvc.perform(post("/api/media/upload-token")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(uploadBody))
                .andReturn();
        JsonNode uploadData = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data");
        String assetId = uploadData.get("assetId").asText();
        String objectKey = uploadData.get("objectKey").asText();

        String createBody = """
                {"assetId":"%s","objectKey":"%s","contentType":"image/jpeg","purpose":"meal_photo","fileSize":100000}"""
                .formatted(assetId, objectKey);
        mockMvc.perform(post("/api/media/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated());

        // User B cannot read A's asset
        mockMvc.perform(get("/api/media/assets/" + assetId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // User B cannot delete A's asset
        mockMvc.perform(delete("/api/media/assets/" + assetId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void getNonExistentAssetReturns404() throws Exception {
        String token = registerAndGetToken("media-404@example.com");

        mockMvc.perform(get("/api/media/assets/nonexistent-id")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void createAssetWithMismatchedObjectKeyForbidden() throws Exception {
        String token = registerAndGetToken("media-mismatch@example.com");

        String body = """
                {"assetId":"fake-id","objectKey":"users/other-user/media/fake.jpg","contentType":"image/jpeg","purpose":"meal_photo","fileSize":100000}""";
        mockMvc.perform(post("/api/media/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void createAssetObjectKeyMustMatchAssetId() throws Exception {
        String token = registerAndGetToken("media-mismatched-asset-id@example.com");

        String uploadBody = """
                {"contentType":"image/jpeg","purpose":"meal_photo","fileSize":100000}""";
        MvcResult uploadResult = mockMvc.perform(post("/api/media/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(uploadBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode uploadData = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data");
        String assetId = uploadData.get("assetId").asText();
        String objectKey = uploadData.get("objectKey").asText().replace(assetId, "other-asset-id");

        String body = """
                {"assetId":"%s","objectKey":"%s","contentType":"image/jpeg","purpose":"meal_photo","fileSize":100000}"""
                .formatted(assetId, objectKey);
        mockMvc.perform(post("/api/media/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void validationErrorOnMissingFields() throws Exception {
        String token = registerAndGetToken("media-validate@example.com");

        String body = """
                {"contentType":"","purpose":"","fileSize":0}""";
        mockMvc.perform(post("/api/media/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
