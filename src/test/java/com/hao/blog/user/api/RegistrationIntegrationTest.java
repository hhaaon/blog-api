package com.hao.blog.user.api;

import com.hao.blog.testsupport.PostgresTestConfiguration;
import com.hao.blog.user.domain.UserRole;
import com.hao.blog.user.persistence.UserEntity;
import com.hao.blog.user.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class RegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM comments");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void register_validRequest_returnsCreatedWithoutExposingPasswordHash()
            throws Exception {

        String body = """
                {
                  "email": "  Alice@Example.COM  ",
                  "password": "secret123"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email")
                        .value("alice@example.com"))
                .andExpect(jsonPath("$.role")
                        .value("USER"))
                .andExpect(jsonPath("$.passwordHash")
                        .doesNotExist());
    }

    @Test
    void register_validRequest_persistsNormalizedEmailEncodedPasswordAndUserRole()
            throws Exception {

        String body = """
                {
                  "email": "  Alice@Example.COM  ",
                  "password": "secret123"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        UserEntity stored = userJpaRepository
                .findByEmail("alice@example.com")
                .orElseThrow();

        assertEquals(
                "alice@example.com",
                stored.email()
        );

        assertNotEquals(
                "secret123",
                stored.passwordHash()
        );

        assertTrue(
                passwordEncoder.matches(
                        "secret123",
                        stored.passwordHash()
                )
        );

        assertEquals(
                UserRole.USER,
                stored.role()
        );
    }

    @Test
    void register_duplicateNormalizedEmail_returnsConflict()
            throws Exception {

        String firstRequest = """
                {
                  "email": "Alice@Example.COM",
                  "password": "secret123"
                }
                """;

        String duplicateRequest = """
                {
                  "email": "  alice@example.com  ",
                  "password": "anotherPassword"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message")
                        .value("Email is already registered"));

        assertEquals(
                1,
                userJpaRepository.count()
        );
    }

    @Test
    void register_withoutCsrf_isRejectedAndDoesNotCreateUser()
            throws Exception {

        String body = """
                {
                  "email": "alice@example.com",
                  "password": "secret123"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        assertEquals(
                0,
                userJpaRepository.count()
        );
    }
}