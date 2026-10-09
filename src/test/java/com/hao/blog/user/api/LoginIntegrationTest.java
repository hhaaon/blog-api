package com.hao.blog.user.api;

import com.hao.blog.testsupport.PostgresTestConfiguration;
import com.hao.blog.user.application.RegistrationService;
import com.hao.blog.user.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
        PostgresTestConfiguration.class,
        LoginIntegrationTest.ProtectedProbeController.class
})
class LoginIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RegistrationService registrationService;

    @Autowired
    UserJpaRepository userJpaRepository;

    @BeforeEach
    void cleanDatabase() {
        userJpaRepository.deleteAll();
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() throws Exception {
        registrationService.register(
                "alice@example.com",
                "secret123"
        );

        String body = """
            {
              "email": "alice@example.com",
              "password": "wrongPassword"
            }
            """;

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid email or password"));
    }

    @Test
    void login_success_sessionAuthenticatesNextRequest() throws Exception {
        registrationService.register(
                "alice@example.com",
                "secret123"
        );

        String body = """
            {
              "email": "alice@example.com",
              "password": "secret123"
            }
            """;

        MvcResult loginResult =
                mockMvc.perform(post("/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isNoContent())
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        assertNotNull(session);

        mockMvc.perform(post("/test/protected")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @RestController
    static class ProtectedProbeController {

        @PostMapping("/test/protected")
        ResponseEntity<Void> protectedEndpoint() {
            return ResponseEntity.noContent().build();
        }
    }
}
