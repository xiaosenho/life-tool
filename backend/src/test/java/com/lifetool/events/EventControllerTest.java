package com.lifetool.events;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
class EventControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        String unique = String.valueOf(System.nanoTime());
        tokenA = registerAndToken("event-a-" + unique + "@test.com", "Event A");
        tokenB = registerAndToken("event-b-" + unique + "@test.com", "Event B");
    }

    @Test
    void createAndListEvent() throws Exception {
        LocalDate eventDate = LocalDate.now().plusDays(10);

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "anniversary",
                                  "title": "结婚纪念日",
                                  "eventDate": "%s",
                                  "repeatRule": "yearly",
                                  "remindDaysBefore": [30, 7, 1],
                                  "note": "提前准备礼物"
                                }
                                """.formatted(eventDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("结婚纪念日"))
                .andExpect(jsonPath("$.data.repeatRule").value("yearly"))
                .andExpect(jsonPath("$.data.nextOccurrenceDate").value(eventDate.toString()));

        mockMvc.perform(get("/api/events")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().plusDays(30).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].title").value("结婚纪念日"));
    }

    @Test
    void updateAndDeleteEvent() throws Exception {
        String id = createEvent(tokenA, LocalDate.now().plusDays(20), "生日");

        mockMvc.perform(patch("/api/events/{id}", id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"妈妈生日\",\"repeatRule\":\"yearly\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("妈妈生日"))
                .andExpect(jsonPath("$.data.repeatRule").value("yearly"));

        mockMvc.perform(delete("/api/events/{id}", id)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/events/upcoming")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("days", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void userIsolation() throws Exception {
        String id = createEvent(tokenA, LocalDate.now().plusDays(5), "私密事件");

        mockMvc.perform(patch("/api/events/{id}", id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"偷看\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/events/upcoming")
                        .header("Authorization", "Bearer " + tokenB)
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void upcomingReturnsNextOccurrenceForRepeatingEvent() throws Exception {
        LocalDate lastWeek = LocalDate.now().minusDays(7);
        createEventWithRepeat(tokenA, lastWeek, "每周复盘", "weekly");

        mockMvc.perform(get("/api/events/upcoming")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("每周复盘"))
                .andExpect(jsonPath("$.data[0].daysUntil").value(0));
    }

    @Test
    void invalidInputReturns400() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"bad","title":"x","eventDate":"2026-10-01","repeatRule":"none"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/events/upcoming")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("days", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/events/upcoming"))
                .andExpect(status().isUnauthorized());
    }

    private String createEvent(String token, LocalDate eventDate, String title) throws Exception {
        return createEventWithRepeat(token, eventDate, title, "none");
    }

    private String createEventWithRepeat(String token, LocalDate eventDate, String title, String repeatRule) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "important_day",
                                  "title": "%s",
                                  "eventDate": "%s",
                                  "repeatRule": "%s",
                                  "remindDaysBefore": [7, 1]
                                }
                                """.formatted(title, eventDate, repeatRule)))
                .andExpect(status().isCreated())
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
