package com.lifetool.appupdate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AppReleaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void latestReleaseIsPubliclyAvailable() throws Exception {
        mockMvc.perform(get("/api/app/releases/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platform").value("android"))
                .andExpect(jsonPath("$.data.versionName").value("1.0.0"))
                .andExpect(jsonPath("$.data.versionCode").value(1));
    }
}
