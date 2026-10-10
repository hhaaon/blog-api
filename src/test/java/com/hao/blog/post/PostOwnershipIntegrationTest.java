package com.hao.blog.post.api;

import com.hao.blog.testsupport.PostgresTestConfiguration;
import com.hao.blog.user.application.RegistrationService;
import com.hao.blog.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class PostOwnershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanBefore() {
        cleanDatabase();
    }

    @AfterEach
    void cleanAfter() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM comments");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void nonOwnerCannotUpdatePost_andDatabaseRemainsUnchanged()
            throws Exception {

        // Arrange: create two different real users
        User alice = registrationService.register(
                "alice@example.com",
                "secret123"
        );

        User bob = registrationService.register(
                "bob@example.com",
                "secret123"
        );

        assertNotEquals(alice.id(), bob.id());

        // Authenticate each user independently.
        // These must be separate sessions.
        MockHttpSession aliceSession =
                login("alice@example.com", "secret123");

        MockHttpSession bobSession =
                login("bob@example.com", "secret123");

        assertNotNull(aliceSession);
        assertNotNull(bobSession);

        // Alice creates the post through the real HTTP endpoint.
        mockMvc.perform(
                        post("/posts")
                                .session(aliceSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "title": "Original title",
                                            "content": "Original content"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title")
                        .value("Original title"))
                .andExpect(jsonPath("$.content")
                        .value("Original content"));

        // Verify server-side ownership in PostgreSQL.
        Map<String, Object> createdRow =
                jdbcTemplate.queryForMap(
                        """
                        SELECT id, title, content, author_id
                        FROM posts
                        WHERE author_id = ?
                        """,
                        alice.id()
                );

        Long postId =
                ((Number) createdRow.get("id"))
                        .longValue();

        assertEquals(
                "Original title",
                createdRow.get("title")
        );

        assertEquals(
                "Original content",
                createdRow.get("content")
        );

        assertEquals(
                alice.id().longValue(),
                ((Number) createdRow.get("author_id"))
                        .longValue()
        );

        // Act:
        // Bob is authenticated, but he does not own Alice's post.
        mockMvc.perform(
                        put("/posts/{id}", postId)
                                .session(bobSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "title": "Hacked title",
                                            "content": "Hacked content"
                                        }
                                        """)
                )

                // Assert HTTP authorization result.
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("POST_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message")
                        .value("Post access denied"));

        // Assert database state:
        // a 403 alone is not enough evidence.
        Map<String, Object> rowAfterForbiddenUpdate =
                jdbcTemplate.queryForMap(
                        """
                        SELECT id, title, content, author_id
                        FROM posts
                        WHERE id = ?
                        """,
                        postId
                );

        assertEquals(
                "Original title",
                rowAfterForbiddenUpdate.get("title")
        );

        assertEquals(
                "Original content",
                rowAfterForbiddenUpdate.get("content")
        );

        assertEquals(
                alice.id().longValue(),
                ((Number) rowAfterForbiddenUpdate.get("author_id"))
                        .longValue()
        );
    }

    private MockHttpSession login(
            String email,
            String password
    ) throws Exception {

        String loginBody = """
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, password);

        MvcResult loginResult =
                mockMvc.perform(
                                post("/auth/login")
                                        .with(csrf())
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(loginBody)
                        )
                        .andExpect(status().isNoContent())
                        .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        assertNotNull(session);

        return session;
    }
}