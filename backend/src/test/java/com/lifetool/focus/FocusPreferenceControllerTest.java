package com.lifetool.focus;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
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
class FocusPreferenceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FocusPreferenceStore store;

    private String tokenA;
    private String tokenB;
    private String userIdA;
    private String userIdB;

    @BeforeEach
    void setUp() throws Exception {
        String unique = String.valueOf(System.nanoTime());
        MvcResult resultA = registerUser("alice-" + unique + "@test.com", "Alice");
        MvcResult resultB = registerUser("bob-" + unique + "@test.com", "Bob");

        JsonNode dataA = objectMapper.readTree(resultA.getResponse().getContentAsString()).get("data");
        JsonNode dataB = objectMapper.readTree(resultB.getResponse().getContentAsString()).get("data");

        tokenA = dataA.get("accessToken").asText();
        tokenB = dataB.get("accessToken").asText();
        userIdA = dataA.get("user").get("id").asText();
        userIdB = dataB.get("user").get("id").asText();
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/focus/preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void firstGetReturnsDefaults() throws Exception {
        mockMvc.perform(get("/api/focus/preferences")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.defaultFocusMinutes").value(25))
                .andExpect(jsonPath("$.data.shortBreakMinutes").value(5))
                .andExpect(jsonPath("$.data.longBreakMinutes").value(15))
                .andExpect(jsonPath("$.data.autoStartBreak").value(false))
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    void patchSuccess() throws Exception {
        mockMvc.perform(patch("/api/focus/preferences")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultFocusMinutes\":45,\"shortBreakMinutes\":10,\"longBreakMinutes\":30,\"autoStartBreak\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.defaultFocusMinutes").value(45))
                .andExpect(jsonPath("$.data.shortBreakMinutes").value(10))
                .andExpect(jsonPath("$.data.longBreakMinutes").value(30))
                .andExpect(jsonPath("$.data.autoStartBreak").value(true));
    }

    @Test
    void patchThenGetKeepsNewValues() throws Exception {
        // Patch
        mockMvc.perform(patch("/api/focus/preferences")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultFocusMinutes\":60,\"shortBreakMinutes\":15}"))
                .andExpect(status().isOk());

        // Get
        mockMvc.perform(get("/api/focus/preferences")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultFocusMinutes").value(60))
                .andExpect(jsonPath("$.data.shortBreakMinutes").value(15))
                .andExpect(jsonPath("$.data.longBreakMinutes").value(15))
                .andExpect(jsonPath("$.data.autoStartBreak").value(false));
    }

    @Test
    void invalidFocusMinutesReturns400() throws Exception {
        // Too low
        mockMvc.perform(patch("/api/focus/preferences")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultFocusMinutes\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // Too high
        mockMvc.perform(patch("/api/focus/preferences")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultFocusMinutes\":181}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidBreakMinutesReturns400() throws Exception {
        // shortBreakMinutes too high
        mockMvc.perform(patch("/api/focus/preferences")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shortBreakMinutes\":61}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // longBreakMinutes too high
        mockMvc.perform(patch("/api/focus/preferences")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longBreakMinutes\":61}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // shortBreakMinutes negative
        mockMvc.perform(patch("/api/focus/preferences")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shortBreakMinutes\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void userIsolation() throws Exception {
        // User A sets preference
        mockMvc.perform(patch("/api/focus/preferences")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultFocusMinutes\":50}"))
                .andExpect(status().isOk());

        // User B still has defaults
        mockMvc.perform(get("/api/focus/preferences")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultFocusMinutes").value(25));

        // User A still has own value
        mockMvc.perform(get("/api/focus/preferences")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultFocusMinutes").value(50));
    }

    // --- helpers ---

    private MvcResult registerUser(String email, String name) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"secret123\",\"displayName\":\"" + name + "\"}";
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }
}
