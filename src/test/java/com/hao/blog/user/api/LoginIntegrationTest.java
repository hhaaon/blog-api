package com.hao.blog.user.api;

import com.hao.blog.testsupport.PostgresTestConfiguration;
import com.hao.blog.user.application.RegistrationService;
import com.hao.blog.user.domain.User;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    ObjectMapper objectMapper;

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

    @Test
    void authMe_afterLogin_returnsAuthenticatedUser() throws Exception {
        User registeredUser =
                registrationService.register(
                        "alice@example.com",
                        "secret123"
                );

        String loginBody = """
                {
                    "email": "alice@example.com",
                    "password": "secret123" 
                }
                """;

        MvcResult loginResult =
                mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                        .andExpect(status().isNoContent())
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        assertNotNull(session);

        mockMvc.perform(get("/auth/me")
                    .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(registeredUser.id().intValue()))
                .andExpect(jsonPath("$.email")
                        .value("alice@example.com"))
                .andExpect(jsonPath("$.role")
                        .value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void authMe_withoutAuthentication_returnAuthorized() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    static class ProtectedProbeController {

        @PostMapping("/test/protected")
        ResponseEntity<Void> protectedEndpoint() {
            return ResponseEntity.noContent().build();
        }
    }

    @Test
    void csrf_withoutAuthentication_returnsTokenAndCreatesSession()
            throws Exception {

        MvcResult csrfResult =
                mockMvc.perform(
                                get("/auth/csrf")
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andExpect(
                                jsonPath("$.token")
                                        .isNotEmpty()
                        )
                        .andExpect(
                                jsonPath("$.headerName")
                                        .value("X-CSRF-TOKEN")
                        )
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession)
                        csrfResult
                                .getRequest()
                                .getSession(false);

        assertNotNull(session);
    }

    @Test
    void csrfThenLogin_sessionAuthenticatesNextRequest()
            throws Exception {

        registrationService.register(
                "alice@example.com",
                "secret123"
        );

        /*
         * Step 1:
         * Anonymous client asks the server for a CSRF token.
         */
        MvcResult csrfResult =
                mockMvc.perform(
                                get("/auth/csrf")
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andReturn();

        String csrfBody =
                csrfResult
                        .getResponse()
                        .getContentAsString();

        JsonNode csrfJson =
                objectMapper.readTree(
                        csrfBody
                );

        String csrfToken =
                csrfJson
                        .get("token")
                        .asText();

        String csrfHeaderName =
                csrfJson
                        .get("headerName")
                        .asText();

        MockHttpSession anonymousSession =
                (MockHttpSession)
                        csrfResult
                                .getRequest()
                                .getSession(false);

        assertNotNull(
                anonymousSession
        );

        /*
         * Step 2:
         * Client logs in using the same session and the
         * real CSRF token returned by the API.
         */
        String loginBody = """
            {
              "email": "alice@example.com",
              "password": "secret123"
            }
            """;

        MvcResult loginResult =
                mockMvc.perform(
                                post("/auth/login")
                                        .session(
                                                anonymousSession
                                        )
                                        .header(
                                                csrfHeaderName,
                                                csrfToken
                                        )
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                loginBody
                                        )
                        )
                        .andExpect(
                                status().isNoContent()
                        )
                        .andReturn();

        /*
         * The authentication strategy may change the
         * session ID during login, so use the session
         * from the login request afterward.
         */
        MockHttpSession authenticatedSession =
                (MockHttpSession)
                        loginResult
                                .getRequest()
                                .getSession(false);

        assertNotNull(
                authenticatedSession
        );

        /*
         * Step 3:
         * The resulting session must authenticate
         * a later request.
         */
        mockMvc.perform(
                        get("/auth/me")
                                .session(
                                        authenticatedSession
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.email")
                                .value(
                                        "alice@example.com"
                                )
                )
                .andExpect(
                        jsonPath("$.role")
                                .value(
                                        "USER"
                                )
                );
    }
}
