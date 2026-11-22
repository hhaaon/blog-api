
package com.hao.blog.post;

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

        // Arrange: create two different real users.
        User alice = registrationService.register(
                "alice@example.com",
                "secret123"
        );

        User bob = registrationService.register(
                "bob@example.com",
                "secret123"
        );

        assertNotEquals(alice.id(), bob.id());

        MockHttpSession bobSession =
                login("bob@example.com", "secret123");

        assertNotNull(bobSession);

        // Arrange: create an existing post owned by Alice.
        // This is test setup, not a test of POST /posts.
        // Publishing now requires ADMIN, so we insert
        // the fixture directly into the test database.
        createPostFixture(
                "Original title",
                "Original content",
                alice.id()
        );

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
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("POST_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message")
                        .value("Post access denied"));

        // A 403 alone is not sufficient.
        // Verify that the database was not modified.
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

    @Test
    void nonOwnerUserDenied_adminAllowedAndOwnershipPreserved()
            throws Exception {

        String aliceEmail = "alice@example.com";
        String bobEmail = "bob@example.com";
        String adminEmail = "admin@example.com";
        String password = "StrongPass123!";

        // Register through the real HTTP boundary.
        register(aliceEmail, password);
        register(bobEmail, password);
        register(adminEmail, password);

        Long aliceId = findUserIdByEmail(aliceEmail);
        Long adminId = findUserIdByEmail(adminEmail);

        // Registration must NOT allow clients to choose ADMIN.
        // Test infrastructure establishes the trusted ADMIN
        // fixture directly in the database.
        jdbcTemplate.update(
                "UPDATE users SET role = 'ADMIN' WHERE id = ?",
                adminId
        );

        // Login AFTER promotion so that the principal
        // contains the ADMIN authority.
        MockHttpSession bobSession =
                login(bobEmail, password);

        MockHttpSession adminSession =
                login(adminEmail, password);

        assertNotNull(bobSession);
        assertNotNull(adminSession);

        // Arrange: Alice owns an existing post.
        // Do not use POST /posts because Alice is a USER.
        createPostFixture(
                "Alice original title",
                "Alice original content",
                aliceId
        );

        Long postId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM posts
                WHERE title = ?
                """,
                Long.class,
                "Alice original title"
        );

        Long originalAuthorId = jdbcTemplate.queryForObject(
                "SELECT author_id FROM posts WHERE id = ?",
                Long.class,
                postId
        );

        assertEquals(aliceId, originalAuthorId);

        // Bob is authenticated but does not own Alice's post.
        mockMvc.perform(
                        put("/posts/{id}", postId)
                                .session(bobSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "title": "Bob changed title",
                                            "content": "Bob changed content"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("POST_ACCESS_DENIED"));

        // Verify denied access did not change the database.
        String titleAfterBob = jdbcTemplate.queryForObject(
                "SELECT title FROM posts WHERE id = ?",
                String.class,
                postId
        );

        String contentAfterBob = jdbcTemplate.queryForObject(
                "SELECT content FROM posts WHERE id = ?",
                String.class,
                postId
        );

        Long authorAfterBob = jdbcTemplate.queryForObject(
                "SELECT author_id FROM posts WHERE id = ?",
                Long.class,
                postId
        );

        assertEquals("Alice original title", titleAfterBob);
        assertEquals("Alice original content", contentAfterBob);
        assertEquals(aliceId, authorAfterBob);

        // ADMIN can update a post without owning it.
        mockMvc.perform(
                        put("/posts/{id}", postId)
                                .session(adminSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "title": "Admin changed title",
                                            "content": "Admin changed content"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title")
                        .value("Admin changed title"))
                .andExpect(jsonPath("$.content")
                        .value("Admin changed content"));

        // The administrator may modify the resource,
        // but ownership must remain unchanged.
        String titleAfterAdmin = jdbcTemplate.queryForObject(
                "SELECT title FROM posts WHERE id = ?",
                String.class,
                postId
        );

        String contentAfterAdmin = jdbcTemplate.queryForObject(
                "SELECT content FROM posts WHERE id = ?",
                String.class,
                postId
        );

        Long authorAfterAdmin = jdbcTemplate.queryForObject(
                "SELECT author_id FROM posts WHERE id = ?",
                Long.class,
                postId
        );

        assertEquals("Admin changed title", titleAfterAdmin);
        assertEquals("Admin changed content", contentAfterAdmin);
        assertEquals(aliceId, authorAfterAdmin);
    }

    // Create an existing article for ownership tests.
    // This helper bypasses the HTTP publishing endpoint
    // without bypassing authorization in the operations tested.
    private void createPostFixture(
            String title,
            String content,
            Long authorId
    ) {
        int insertedRows = jdbcTemplate.update(
                """
                INSERT INTO posts (title, content, author_id, version)
                VALUES (?, ?, ?, 0)
                """,
                title,
                content,
                authorId
        );

        assertEquals(1, insertedRows);
    }

    private void register(
            String email,
            String password
    ) throws Exception {

        mockMvc.perform(
                        post("/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "email": "%s",
                                            "password": "%s"
                                        }
                                        """.formatted(email, password))
                )
                .andExpect(status().isCreated());
    }

    private MockHttpSession login(
            String email,
            String password
    ) throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "email": "%s",
                                            "password": "%s"
                                        }
                                        """.formatted(email, password))
                )
                .andExpect(status().isNoContent())
                .andReturn();

        return (MockHttpSession)
                result.getRequest().getSession(false);
    }

    private Long findUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM users
                WHERE email = ?
                """,
                Long.class,
                email
        );
    }
}
