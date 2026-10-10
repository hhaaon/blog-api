package com.hao.blog.post.persistence;

import com.hao.blog.post.domain.PageResult;
import com.hao.blog.post.domain.Post;
import com.hao.blog.post.domain.PostRepository;
import com.hao.blog.testsupport.PostgresTestConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({
        PostgresTestConfiguration.class,
        JpaPostRepositoryAdapter.class
})
class JpaPostRepositoryAdapterTest {

    @Autowired
    private PostRepository repository;

    @Autowired
    private EntityManager entityManager;

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
    void save_persistsPostAndReturnsGeneratedId() {
        //Arrange
        Post post = new Post(
                null,
                "Learning JPA",
                "Testing persistence with PostgreSQL",
                authorId
        );

        //Act
        Post savedPost = repository.save(post);

        //Assert
        assertThat(savedPost.id()).isNotNull();
        assertThat(savedPost.title()).isEqualTo("Learning JPA");
        assertThat(savedPost.content())
                .isEqualTo("Testing persistence with PostgreSQL");
    }

    @Test
    void findById_returnsPersistedPostAfterPersistenceIsCleared() {
        //Arrange
        Post post = new Post(
                null,
                "Finding a persisted post",
                "Testing the real database read path",
                authorId
        );

        Post savedPost = repository.save(post);
        Long id = savedPost.id();

        entityManager.flush();
        entityManager.clear();

        Post foundPost = repository.findById(id)
                .orElseThrow();

        assertThat(foundPost.id()).isEqualTo(id);
        assertThat(foundPost.title())
                .isEqualTo("Finding a persisted post");
        assertThat(foundPost.content())
                .isEqualTo("Testing the real database read path");
    }

    @Test
    void findById_returnsEmptyWhenPostDoesNotExist() {
        //Arrange
        Optional<Post> result = repository.findById(999_999L);

        //Act + Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findAll_returnsRequestedPageWithNewestPostsFirst() {
        // Arrange
        Post post1 = repository.save(
                new Post(null, "Post 1", "Content 1", authorId)
        );
        Post post2 = repository.save(
                new Post(null, "Post 2", "Content 2", authorId)
        );
        Post post3 = repository.save(
                new Post(null, "Post 3", "Content 3", authorId)
        );
        Post post4 = repository.save(
                new Post(null, "Post 4", "Content 4", authorId)
        );
        Post post5 = repository.save(
                new Post(null, "Post 5", "Content 5", authorId)
        );

        entityManager.flush();
        entityManager.clear();

        // Act
        PageResult<Post> result =
                repository.findAll(0, 2);

        // Assert
        assertThat(result.items()).hasSize(2);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);

        assertThat(result.items())
                .extracting(Post::id)
                .containsExactly(
                        post5.id(),
                        post4.id()
                );
    }

}