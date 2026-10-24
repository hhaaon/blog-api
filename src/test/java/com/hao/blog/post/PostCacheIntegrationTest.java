package com.hao.blog.post;

import com.hao.blog.post.application.PostService;
import com.hao.blog.post.application.PostView;
import com.hao.blog.post.domain.Post;
import com.hao.blog.testsupport.PostgresTestConfiguration;
import com.hao.blog.testsupport.RedisTestConfiguration;
import com.hao.blog.user.domain.UserRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({
        PostgresTestConfiguration.class,
        RedisTestConfiguration.class
})
class PostCacheIntegrationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearCache() {
        Cache posts = cacheManager.getCache("posts");

        if (posts != null) {
            posts.clear();
        }
    }

    @Test
    void getPost_secondReadReturnsCachedValueInsteadOfChangedDatabaseValue() {
        Long ownerId = insertOwner();

        Post created = postService.createPost(
                "Original title",
                "Original content",
                ownerId
        );

        Long postId = created.id();

        /*
         * First read:
         *
         * Redis miss
         * -> PostCacheService executes
         * -> PostgreSQL
         * -> Post mapped to PostView
         * -> JSON stored in Redis
         */
        PostView firstRead = postService.getPost(postId);

        assertThat(firstRead.title())
                .isEqualTo("Original title");

        assertThat(firstRead.content())
                .isEqualTo("Original content");

        /*
         * Optional but useful while proving serialization.
         */
        String rawJson = redisTemplate.opsForValue()
                .get("posts::" + postId);

        System.out.println("REDIS JSON = " + rawJson);

        assertThat(rawJson)
                .contains("\"title\":\"Original title\"");

        /*
         * Modify PostgreSQL directly.
         *
         * We intentionally bypass PostService.updatePost(),
         * therefore no cache eviction occurs.
         */
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE posts
                SET title = ?, content = ?
                WHERE id = ?
                """,
                "Changed directly in database",
                "Changed database content",
                postId
        );

        assertThat(updatedRows)
                .isEqualTo(1);

        String databaseTitle = jdbcTemplate.queryForObject(
                """
                SELECT title
                FROM posts
                WHERE id = ?
                """,
                String.class,
                postId
        );

        /*
         * Prove PostgreSQL changed.
         */
        assertThat(databaseTitle)
                .isEqualTo("Changed directly in database");

        /*
         * But Redis still contains the old representation.
         *
         * Therefore this call should return the cached value.
         */
        PostView secondRead = postService.getPost(postId);

        assertThat(secondRead.title())
                .isEqualTo("Original title");

        assertThat(secondRead.content())
                .isEqualTo("Original content");
    }

    @Test
    void updatePost_successfulCommitEvictsCacheSoNextReadReturnsUpdatedValue() {
        Long ownerId = insertOwner();

        Post created = postService.createPost(
                "Original title",
                "Original content",
                ownerId
        );

        Long postId = created.id();

        /*
         * Prime Redis.
         */
        PostView firstRead = postService.getPost(postId);

        assertThat(firstRead.title())
                .isEqualTo("Original title");

        /*
         * Authoritative write.
         *
         * PostService:
         * 1. loads authoritative Post
         * 2. checks owner/admin policy
         * 3. updates PostgreSQL
         * 4. requests cache eviction
         * 5. transaction commits
         * 6. transaction-aware cache eviction occurs
         */
        postService.updatePost(
                postId,
                "Updated title",
                "Updated content",
                ownerId,
                UserRole.USER
        );

        /*
         * Cache should now miss.
         *
         * PostCacheService reloads from PostgreSQL and stores
         * the new PostView in Redis.
         */
        PostView afterUpdate = postService.getPost(postId);

        assertThat(afterUpdate.title())
                .isEqualTo("Updated title");

        assertThat(afterUpdate.content())
                .isEqualTo("Updated content");

        String databaseTitle = jdbcTemplate.queryForObject(
                """
                SELECT title
                FROM posts
                WHERE id = ?
                """,
                String.class,
                postId
        );

        assertThat(databaseTitle)
                .isEqualTo("Updated title");

        String rawJson = redisTemplate.opsForValue()
                .get("posts::" + postId);

        assertThat(rawJson)
                .contains("\"title\":\"Updated title\"");
    }

    private Long insertOwner() {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO users (
                    email,
                    password_hash,
                    role
                )
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "cache-test-"
                        + UUID.randomUUID()
                        + "@example.com",
                "unused-password-hash",
                "USER"
        );
    }
}