package com.lifetool.habits;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
class HabitControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void checkinUsesClientProvidedLocalDate() throws Exception {
        String unique = String.valueOf(System.nanoTime());
        String token = registerAndToken("habit-" + unique + "@test.com");
        String habitId = createHabit(token);
        String localDate = LocalDate.now().plusDays(1).toString();

        mockMvc.perform(post("/api/habits/{id}/checkins", habitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "count": 1,
                                  "checkinDate": "%s"
                                }
                                """.formatted(localDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.habitId").value(habitId))
                .andExpect(jsonPath("$.data.checkinDate").value(localDate));

        mockMvc.perform(get("/api/habits/{id}/checkins?from={from}&to={to}", habitId, localDate, localDate)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].checkinDate").value(localDate));
    }

    @Test
    void cancelCheckinRemovesCompletedHabitForDate() throws Exception {
        String unique = String.valueOf(System.nanoTime());
        String token = registerAndToken("habit-cancel-" + unique + "@test.com");
        String habitId = createHabit(token);
        String localDate = LocalDate.now().toString();

        mockMvc.perform(post("/api/habits/{id}/checkins", habitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "count": 1,
                                  "checkinDate": "%s"
                                }
                                """.formatted(localDate)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/habits/{id}/checkins?checkinDate={date}", habitId, localDate)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/habits/{id}/checkins?from={from}&to={to}", habitId, localDate, localDate)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private String createHabit(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/habits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "喝水",
                                  "frequencyType": "daily",
                                  "targetCount": 1,
                                  "color": "#2563EB",
                                  "icon": "cup"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    private String registerAndToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "secret123",
                                  "displayName": "Habit Tester"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }
}
