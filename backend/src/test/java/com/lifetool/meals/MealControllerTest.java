package com.lifetool.meals;

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
class MealControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        String unique = String.valueOf(System.nanoTime());
        tokenA = registerAndToken("meal-a-" + unique + "@test.com", "Meal A");
        tokenB = registerAndToken("meal-b-" + unique + "@test.com", "Meal B");
    }

    @Test
    void canGetDeleteAndRerunMeal() throws Exception {
        String mealLogId = createRecognizedMeal(tokenA);

        mockMvc.perform(get("/api/meals/" + mealLogId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(mealLogId))
                .andExpect(jsonPath("$.data.mediaAssetId").isString())
                .andExpect(jsonPath("$.data.aiGenerated").value(true));

        mockMvc.perform(get("/api/meals/" + mealLogId + "/image-url")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isString());

        mockMvc.perform(post("/api/meals/" + mealLogId + "/rerun-recognition")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(mealLogId))
                .andExpect(jsonPath("$.data.totalCalories").value(520));

        mockMvc.perform(delete("/api/meals/" + mealLogId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/meals/" + mealLogId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void otherUserCannotAccessMeal() throws Exception {
        String mealLogId = createRecognizedMeal(tokenA);

        mockMvc.perform(get("/api/meals/" + mealLogId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/meals/" + mealLogId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    private String createRecognizedMeal(String token) throws Exception {
        TestAsset asset = createMealAsset(token);
        MvcResult result = mockMvc.perform(post("/api/ai/food-recognition")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mediaAssetId": "%s",
                                  "mealType": "lunch"
                                }
                                """.formatted(asset.id())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("mealLogId").asText();
    }

    private TestAsset createMealAsset(String token) throws Exception {
        MvcResult uploadResult = mockMvc.perform(post("/api/media/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contentType": "image/jpeg",
                                  "purpose": "meal_photo",
                                  "fileSize": 512000
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        var uploadData = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data");
        String assetId = uploadData.get("assetId").asText();
        String objectKey = uploadData.get("objectKey").asText();

        mockMvc.perform(post("/api/media/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetId": "%s",
                                  "objectKey": "%s",
                                  "contentType": "image/jpeg",
                                  "purpose": "meal_photo",
                                  "fileSize": 512000,
                                  "width": 1280,
                                  "height": 960
                                }
                                """.formatted(assetId, objectKey)))
                .andExpect(status().isCreated());

        return new TestAsset(assetId, objectKey);
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

    private record TestAsset(String id, String objectKey) {}
}
