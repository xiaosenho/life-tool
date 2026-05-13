package com.lifetool.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class LedgerControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        String unique = String.valueOf(System.nanoTime());
        tokenA = registerAndToken("ledger-a-" + unique + "@test.com", "Ledger A");
        tokenB = registerAndToken("ledger-b-" + unique + "@test.com", "Ledger B");
    }

    @Test
    void createAndListTransactions() throws Exception {
        createTransaction(tokenA, "expense", 36.5, "餐饮", "2026-05-13T12:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("expense"))
                .andExpect(jsonPath("$.data.amount").value(36.5))
                .andExpect(jsonPath("$.data.currency").value("CNY"))
                .andExpect(jsonPath("$.data.category").value("餐饮"));

        mockMvc.perform(get("/api/ledger/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].category").value("餐饮"));
    }

    @Test
    void updateAndDeleteTransaction() throws Exception {
        String id = extractId(createTransaction(tokenA, "expense", 20, "交通", "2026-05-02T08:00:00Z")
                .andReturn());

        mockMvc.perform(patch("/api/ledger/transactions/{id}", id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":25,\"category\":\"地铁\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(25))
                .andExpect(jsonPath("$.data.category").value("地铁"));

        mockMvc.perform(delete("/api/ledger/transactions/{id}", id)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/ledger/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void userIsolation() throws Exception {
        String id = extractId(createTransaction(tokenA, "expense", 88, "购物", "2026-05-04T12:00:00Z")
                .andReturn());

        mockMvc.perform(patch("/api/ledger/transactions/{id}", id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/ledger/transactions")
                        .header("Authorization", "Bearer " + tokenB)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void summaryIncludesBudgetAndCategoryExpenses() throws Exception {
        createTransaction(tokenA, "income", 12000, "工资", "2026-05-01T12:00:00Z");
        createTransaction(tokenA, "expense", 36.5, "餐饮", "2026-05-13T12:00:00Z");
        createTransaction(tokenA, "expense", 100, "餐饮", "2026-05-14T12:00:00Z");
        createTransaction(tokenA, "expense", 50, "交通", "2026-05-15T12:00:00Z");

        mockMvc.perform(put("/api/ledger/budgets/2026-05")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000,\"currency\":\"CNY\",\"category\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(5000));

        mockMvc.perform(get("/api/ledger/summary")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.income").value(12000))
                .andExpect(jsonPath("$.data.expense").value(186.5))
                .andExpect(jsonPath("$.data.balance").value(11813.5))
                .andExpect(jsonPath("$.data.budget").value(5000))
                .andExpect(jsonPath("$.data.categoryExpenses[0].category").value("餐饮"))
                .andExpect(jsonPath("$.data.categoryExpenses[0].amount").value(136.5));
    }

    @Test
    void invalidInputReturns400() throws Exception {
        mockMvc.perform(post("/api/ledger/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"expense","amount":0,"occurredAt":"2026-05-13T12:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/ledger/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"bad","amount":10,"occurredAt":"2026-05-13T12:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/ledger/summary")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("month", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/ledger/summary").param("month", "2026-05"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActionsWrapper createTransaction(
            String token, String type, double amount, String category, String occurredAt) throws Exception {
        String body = """
                {
                  "type": "%s",
                  "amount": %s,
                  "currency": "CNY",
                  "category": "%s",
                  "account": "微信",
                  "occurredAt": "%s",
                  "note": "测试"
                }
                """.formatted(type, amount, category, occurredAt);
        return new ResultActionsWrapper(mockMvc.perform(post("/api/ledger/transactions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)));
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

    private String extractId(MvcResult result) throws Exception {
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return data.get("id").asText();
    }

    private static class ResultActionsWrapper {
        private final org.springframework.test.web.servlet.ResultActions actions;

        ResultActionsWrapper(org.springframework.test.web.servlet.ResultActions actions) {
            this.actions = actions;
        }

        ResultActionsWrapper andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        MvcResult andReturn() throws Exception {
            return actions.andReturn();
        }
    }
}
