package com.hao.blog.post;

import com.hao.blog.post.application.PostService;
import com.hao.blog.post.application.PostView;
import com.hao.blog.post.domain.Post;
import com.hao.blog.testsupport.PostgresTestConfiguration;
import com.hao.blog.testsupport.RedisTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import({
        PostgresTestConfiguration.class,
        RedisTestConfiguration.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostCacheOutageIntegrationTest {

    private final PostService postService;
    private final JdbcTemplate jdbcTemplate;
    private final GenericContainer<?> redisContainer;

    @Autowired
    PostCacheOutageIntegrationTest(
            PostService postService,
            JdbcTemplate jdbcTemplate,
            @Qualifier("redisContainer")
            GenericContainer<?> redisContainer
    ) {
        this.postService = postService;
        this.jdbcTemplate = jdbcTemplate;
        this.redisContainer = redisContainer;
    }

    @Test
    void getPost_whenRedisIsDown_fallsBackToPostgres() {
        Long ownerId = insertUser();

        Post created = postService.createPost(
                "Redis outage test",
                "PostgreSQL should still serve this post",
                ownerId
        );

        assertNotNull(created.id());
        assertTrue(redisContainer.isRunning());

        redisContainer.stop();

        assertFalse(redisContainer.isRunning());

        PostView result = assertDoesNotThrow(
                () -> postService.getPost(created.id())
        );

        assertEquals(created.id(), result.id());
        assertEquals("Redis outage test", result.title());
        assertEquals(
                "PostgreSQL should still serve this post",
                result.content()
        );
        assertEquals(ownerId, result.authorId());
    }

    private Long insertUser() {
        String email =
                "redis-outage-" + UUID.randomUUID() + "@example.com";

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, password_hash, role)
                VALUES (?, ?, 'USER')
                RETURNING id
                """,
                Long.class,
                email,
                "{noop}unused"
        );
    }
}