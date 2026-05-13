package com.lifetool.friends;

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
class FriendControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String tokenA;
    private String tokenB;
    private String userIdB;

    @BeforeEach
    void setUp() throws Exception {
        String unique = String.valueOf(System.nanoTime());
        tokenA = registerAndGetToken("alice-" + unique + "@test.com", "Alice");
        MvcResult resultB = registerUser("bob-" + unique + "@test.com", "Bob");
        JsonNode dataB = objectMapper.readTree(resultB.getResponse().getContentAsString()).get("data");
        tokenB = dataB.get("accessToken").asText();
        userIdB = dataB.get("user").get("id").asText();
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/friends"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendAndAcceptFriendRequest() throws Exception {
        String bobEmail = getBobEmail();

        // Send request
        MvcResult reqResult = mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + bobEmail + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isString())
                .andReturn();

        String requestId = objectMapper.readTree(reqResult.getResponse().getContentAsString())
                .get("data").get("id").asText();

        // Bob accepts
        mockMvc.perform(patch("/api/friends/requests/" + requestId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"accept\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        // Both see each other in friend list
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].displayName").value("Bob"));

        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].displayName").value("Alice"));
    }

    @Test
    void deleteFriendRemovesFromList() throws Exception {
        String bobEmail = getBobEmail();
        String requestId = sendRequest(tokenA, bobEmail);
        acceptRequest(tokenB, requestId);

        // Delete
        mockMvc.perform(delete("/api/friends/" + userIdB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // No longer visible
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void cannotAcceptOthersRequest() throws Exception {
        String unique = String.valueOf(System.nanoTime());
        String tokenC = registerAndGetToken("carol-" + unique + "@test.com", "Carol");
        String bobEmail = getBobEmail();
        String requestId = sendRequest(tokenA, bobEmail);

        // Carol tries to accept Alice->Bob request
        mockMvc.perform(patch("/api/friends/requests/" + requestId)
                        .header("Authorization", "Bearer " + tokenC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"accept\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void duplicateRequestRejected() throws Exception {
        String bobEmail = getBobEmail();
        sendRequest(tokenA, bobEmail);

        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + bobEmail + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void reversePendingRequestRejected() throws Exception {
        String bobEmail = getBobEmail();
        sendRequest(tokenA, bobEmail);

        MvcResult alice = mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andReturn();
        String aliceEmail = objectMapper.readTree(alice.getResponse().getContentAsString())
                .get("data").get("email").asText();

        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + aliceEmail + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void cannotAddSelf() throws Exception {
        // Get Alice's email
        MvcResult meResult = mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andReturn();
        String aliceEmail = objectMapper.readTree(meResult.getResponse().getContentAsString())
                .get("data").get("email").asText();

        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + aliceEmail + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- helpers ---

    private String getBobEmail() throws Exception {
        MvcResult me = mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + tokenB))
                .andReturn();
        return objectMapper.readTree(me.getResponse().getContentAsString())
                .get("data").get("email").asText();
    }

    private String registerAndGetToken(String email, String name) throws Exception {
        MvcResult r = registerUser(email, name);
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private MvcResult registerUser(String email, String name) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"secret123\",\"displayName\":\"" + name + "\"}";
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String sendRequest(String token, String email) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    private void acceptRequest(String token, String requestId) throws Exception {
        mockMvc.perform(patch("/api/friends/requests/" + requestId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"accept\"}"))
                .andExpect(status().isOk());
    }
}
