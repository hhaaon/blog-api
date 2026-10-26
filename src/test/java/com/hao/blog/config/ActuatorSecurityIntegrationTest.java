package com.hao.blog.config;

import com.hao.blog.testsupport.PostgresTestConfiguration;
import com.hao.blog.testsupport.RedisTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
        PostgresTestConfiguration.class,
        RedisTestConfiguration.class
})
class ActuatorSecurityIntegrationTest {

    private final MockMvc mockMvc;

    @Autowired
    ActuatorSecurityIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void health_anonymousRequest_isAllowed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void liveness_anonymousRequest_isAllowed() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readiness_anonymousRequest_isAllowed() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void authenticatedApplicationEndpoint_anonymousRequest_isStillUnauthorized()
            throws Exception {

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otherActuatorEndpoint_anonymousRequest_isNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void infoEndpoint_authenticatedRequest_isStillNotExposed() throws Exception {
        mockMvc.perform(
                        get("/actuator/info")
                                .with(user("actuator-test").roles("USER"))
                )
                .andExpect(status().isNotFound());
    }
}