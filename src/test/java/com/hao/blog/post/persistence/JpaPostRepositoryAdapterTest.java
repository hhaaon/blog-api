package com.hao.blog.post.persistence;

import com.hao.blog.post.domain.Post;
import com.hao.blog.post.domain.PostRepository;
import com.hao.blog.testsupport.PostgresTestConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.Optional;

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

    @Test
    void save_persistsPostAndReturnsGeneratedId() {
        //Arrange
        Post post = new Post(
                null,
                "Learning JPA",
                "Testing persistence with PostgreSQL"
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
                "Testing the real database read path"
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

}