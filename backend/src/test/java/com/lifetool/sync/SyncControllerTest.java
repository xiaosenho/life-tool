package com.lifetool.sync;

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
class SyncControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void pushRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/sync/push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId":"device","clientSeq":1,"mutations":[]}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pushThenPullReturnsChanges() throws Exception {
        String token = registerAndGetAccessToken("sync-flow@example.com");

        mockMvc.perform(post("/api/sync/push")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId":"device-1",
                                  "clientSeq":1,
                                  "mutations":[{
                                    "mutationId":"mutation-1",
                                    "entityType":"task",
                                    "entityId":"task-1",
                                    "operation":"create",
                                    "baseVersion":null,
                                    "payload":{"title":"Read","completed":false}
                                  }]
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied[0].mutationId").value("mutation-1"))
                .andExpect(jsonPath("$.data.applied[0].serverVersion").isNumber())
                .andExpect(jsonPath("$.data.conflicts").isArray());

        mockMvc.perform(post("/api/sync/pull")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId":"device-1","cursor":null,"entityTypes":["task"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changes[0].entityType").value("task"))
                .andExpect(jsonPath("$.data.changes[0].entityId").value("task-1"))
                .andExpect(jsonPath("$.data.changes[0].deleted").value(false))
                .andExpect(jsonPath("$.data.changes[0].payload.title").value("Read"));
    }

    @Test
    void usersCannotPullEachOthersChanges() throws Exception {
        String userAToken = registerAndGetAccessToken("sync-a@example.com");
        String userBToken = registerAndGetAccessToken("sync-b@example.com");

        mockMvc.perform(post("/api/sync/push")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId":"device-a",
                                  "clientSeq":1,
                                  "mutations":[{
                                    "mutationId":"mutation-a",
                                    "entityType":"task",
                                    "entityId":"task-a",
                                    "operation":"create",
                                    "baseVersion":null,
                                    "payload":{"title":"Private"}
                                  }]
                                }"""))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sync/pull")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId":"device-b","cursor":null,"entityTypes":["task"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changes").isEmpty());
    }

    @Test
    void deleteMutationCreatesDeletedChange() throws Exception {
        String token = registerAndGetAccessToken("sync-delete@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/sync/push")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId":"device-delete",
                                  "clientSeq":1,
                                  "mutations":[{
                                    "mutationId":"mutation-create",
                                    "entityType":"task",
                                    "entityId":"task-delete",
                                    "operation":"create",
                                    "baseVersion":null,
                                    "payload":{"title":"Delete me"}
                                  }]
                                }"""))
                .andExpect(status().isOk())
                .andReturn();
        long serverVersion = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/applied/0/serverVersion")
                .asLong();

        mockMvc.perform(post("/api/sync/push")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId":"device-delete",
                                  "clientSeq":2,
                                  "mutations":[{
                                    "mutationId":"mutation-delete",
                                    "entityType":"task",
                                    "entityId":"task-delete",
                                    "operation":"delete",
                                    "baseVersion":%d,
                                    "payload":{}
                                  }]
                                }""".formatted(serverVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied[0].mutationId").value("mutation-delete"));

        mockMvc.perform(post("/api/sync/pull")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId":"device-delete","cursor":"%d","entityTypes":["task"]}""".formatted(serverVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changes[0].entityId").value("task-delete"))
                .andExpect(jsonPath("$.data.changes[0].deleted").value(true));
    }

    @Test
    void updateMissingEntityIsRejected() throws Exception {
        String token = registerAndGetAccessToken("sync-missing@example.com");

        mockMvc.perform(post("/api/sync/push")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId":"device-missing",
                                  "clientSeq":1,
                                  "mutations":[{
                                    "mutationId":"mutation-missing",
                                    "entityType":"task",
                                    "entityId":"task-missing",
                                    "operation":"update",
                                    "baseVersion":1,
                                    "payload":{"title":"Missing"}
                                  }]
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied").isEmpty())
                .andExpect(jsonPath("$.data.rejected[0].code").value("ENTITY_NOT_FOUND"));
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"secret123","displayName":"Sync"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return data.get("accessToken").asText();
    }
}
