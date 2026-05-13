package com.lifetool.leaderboards;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class LeaderboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LeaderboardStatsStore statsStore;

    private String token;
    private String userId;

    @BeforeEach
    void setUp() throws Exception {
        statsStore.clearAll();
        String email = "leaderboard-test-" + System.nanoTime() + "@example.com";
        String body = """
                {"email":"%s","password":"secret123","displayName":"LeaderboardUser"}""".formatted(email);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        token = data.get("accessToken").asText();
        userId = data.get("user").get("id").asText();
    }

    @Test
    void unauthorizedReturns401() throws Exception {
        mockMvc.perform(get("/api/leaderboards/focus?period=today"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/leaderboards/focus?period=week"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/leaderboards/habits?period=today"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/leaderboards/streaks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void focusTodayReturnsDefaultZero() throws Exception {
        mockMvc.perform(get("/api/leaderboards/focus?period=today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.period").value("today"))
                .andExpect(jsonPath("$.data.metric").value("focus_seconds"))
                .andExpect(jsonPath("$.data.entries[0].userId").value(userId))
                .andExpect(jsonPath("$.data.entries[0].displayName").value("LeaderboardUser"))
                .andExpect(jsonPath("$.data.entries[0].value").value(0))
                .andExpect(jsonPath("$.data.entries[0].rank").value(1));
    }

    @Test
    void focusWeekReturnsDefaultZero() throws Exception {
        mockMvc.perform(get("/api/leaderboards/focus?period=week")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.period").value("week"))
                .andExpect(jsonPath("$.data.metric").value("focus_seconds"))
                .andExpect(jsonPath("$.data.entries[0].userId").value(userId))
                .andExpect(jsonPath("$.data.entries[0].value").value(0))
                .andExpect(jsonPath("$.data.entries[0].rank").value(1));
    }

    @Test
    void habitsTodayReturnsDefaultZero() throws Exception {
        mockMvc.perform(get("/api/leaderboards/habits?period=today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.period").value("today"))
                .andExpect(jsonPath("$.data.metric").value("habit_completion"))
                .andExpect(jsonPath("$.data.entries[0].userId").value(userId))
                .andExpect(jsonPath("$.data.entries[0].value").value(0))
                .andExpect(jsonPath("$.data.entries[0].rank").value(1));
    }

    @Test
    void streaksReturnsDefaultZero() throws Exception {
        mockMvc.perform(get("/api/leaderboards/streaks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.period").value("all_time"))
                .andExpect(jsonPath("$.data.metric").value("streak_days"))
                .andExpect(jsonPath("$.data.entries[0].userId").value(userId))
                .andExpect(jsonPath("$.data.entries[0].value").value(0))
                .andExpect(jsonPath("$.data.entries[0].rank").value(1));
    }

    @Test
    void focusWithStoredValue() throws Exception {
        statsStore.setFocusTodaySeconds(userId, 3600L);
        mockMvc.perform(get("/api/leaderboards/focus?period=today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].value").value(3600));
    }

    @Test
    void invalidPeriodForFocusReturns400() throws Exception {
        mockMvc.perform(get("/api/leaderboards/focus?period=month")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidPeriodForHabitsReturns400() throws Exception {
        mockMvc.perform(get("/api/leaderboards/habits?period=week")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
