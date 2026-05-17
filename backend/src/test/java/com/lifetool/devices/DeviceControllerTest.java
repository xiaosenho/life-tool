package com.lifetool.devices;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerAndListDevice() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(post("/api/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "installationId":"device_test_installation",
                                  "deviceName":"Pixel 9",
                                  "deviceType":"android",
                                  "vendorDeviceId":"aliyun-device-1",
                                  "pushProvider":"aliyun",
                                  "pushEnabled":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.installationId").value("device_test_installation"))
                .andExpect(jsonPath("$.data.pushEnabled").value(true));

        mockMvc.perform(get("/api/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deviceName").value("Pixel 9"));
    }

    private String registerAndGetToken() throws Exception {
        String body = "{\"email\":\"device-" + System.nanoTime() + "@test.com\",\"password\":\"secret123\",\"displayName\":\"DeviceUser\"}";
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = objectMapper.readTree(response).get("data");
        return data.get("accessToken").asText();
    }
}
