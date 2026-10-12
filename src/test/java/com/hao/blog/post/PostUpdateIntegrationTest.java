package com.hao.blog.post;

import com.hao.blog.post.application.PostService;
import com.hao.blog.post.domain.Post;
import com.hao.blog.post.persistence.PostEntity;
import com.hao.blog.post.persistence.PostJpaRepository;
import com.hao.blog.testsupport.PostgresTestConfiguration;
import com.hao.blog.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
public class PostUpdateIntegrationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostJpaRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long authorId;

    @BeforeEach
    void setUp() {
        authorId = createTestUserAndReturnId();
    }

    private Long createTestUserAndReturnId() {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, password_hash, role)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "owner-" + UUID.randomUUID() + "@example.com",
                "unused-test-hash",
                "USER"
        );
    }

    @Test
    void updatePost_existingPost_updatesDatabaseThroughDirtyChecking() {
        //Arrange
        PostEntity saved = repository.save(
                new PostEntity("Original", "Content", authorId)
        );

        Long postId = saved.getId();

        //Act
        Post updated = postService.updatePost(
                postId,
                "Changed",
                "New content",
                authorId,
                UserRole.USER
        );

        //Assert
        assertThat(updated.id())
                .isEqualTo(postId);

        assertThat(updated.title())
                .isEqualTo("Changed");

        assertThat(updated.content())
                .isEqualTo("New content");

        PostEntity persisted = repository.findById(postId)
                .orElseThrow();

        assertThat(persisted.getTitle())
                .isEqualTo("Changed");

        assertThat(persisted.getContent())
                .isEqualTo("New content");


    }
}
