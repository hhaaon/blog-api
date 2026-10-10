package com.hao.blog.post;

import com.hao.blog.post.domain.Post;
import com.hao.blog.post.domain.PostRepository;
import com.hao.blog.post.persistence.PostJpaRepository;
import com.hao.blog.testsupport.PostgresTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({
        PostgresTestConfiguration.class,
        TransactionRollbackTest.TestConfig.class
})
class TransactionRollbackTest {

    @Autowired
    private TransactionalPostWriter writer;

    @Autowired
    private PostJpaRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        TransactionalPostWriter transactionalPostWriter(
                PostRepository repository
        ) {
            return new TransactionalPostWriter(repository);
        }
    }

    static class TransactionalPostWriter {

        private final PostRepository repository;

        TransactionalPostWriter(PostRepository repository) {
            this.repository = repository;
        }

        @Transactional
        void saveTwoPostsAndFail(Long authorId) {
            repository.save(
                    new Post(null, "Post A", "Content A", authorId)
            );

            repository.save(
                    new Post(null, "Post B", "Content B", authorId)
            );

            throw new IllegalStateException("Simulated failure");
        }

        @Transactional
        void saveTwoPostsSuccessfully(Long authorId) {
            repository.save(
                    new Post(null, "Post A", "Content A", authorId)
            );

            repository.save(
                    new Post(null, "Post B", "Content B", authorId)
            );
        }

        @Transactional
        void saveTwoPostsAndThrowCheckedException(Long authorId) throws IOException {
            repository.save(new Post(null, "Checked A", "Content A", authorId));
            repository.save(new Post(null, "Checked B", "Content B", authorId));

            throw new IOException("Checked failure");
        }

        @Transactional(rollbackFor = IOException.class)
        void saveTwoPostsAndRollbackOnCheckedException(Long authorId) throws IOException {
            repository.save(
                    new Post(null, "Rollback A", "Content A", authorId)
            );

            repository.save(
                    new Post(null, "Rollback B", "Content B", authorId)
            );

            throw new IOException("Checked failure");
        }
    }

    @Test
    void runtimeExceptionRollsBackAllWrites() {
        long countBefore = repository.count();

        assertThatThrownBy(() -> writer.saveTwoPostsAndFail(authorId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated failure");

        long countAfter = repository.count();

        assertThat(countAfter).isEqualTo(countBefore);
    }

    @Test
    void successfulTransactionCommitsAllWrites() {
        long countBefore = repository.count();

        writer.saveTwoPostsSuccessfully(authorId);

        long countAfter = repository.count();

        assertThat(countAfter).isEqualTo(countBefore + 2);
    }

    @Test
    void checkedExceptionDoesNotRollBackByDefault() {
        long countBefore = repository.count();

        assertThatThrownBy(
                () -> writer.saveTwoPostsAndThrowCheckedException(authorId)
        )
                .isInstanceOf(IOException.class)
                .hasMessage("Checked failure");

        long countAfter = repository.count();

        assertThat(countAfter).isEqualTo(countBefore + 2);
    }

    @Test
    void rollbackForCausesRollbackOnCheckedException() {
        long countBefore = repository.count();

        assertThatThrownBy(
                () -> writer.saveTwoPostsAndRollbackOnCheckedException(authorId)
        )
                .isInstanceOf(IOException.class)
                .hasMessage("Checked failure");

        long countAfter = repository.count();

        assertThat(countAfter).isEqualTo(countBefore);
    }
}
