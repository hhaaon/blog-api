package com.hao.blog.post;

import com.hao.blog.post.domain.Post;
import com.hao.blog.post.domain.PostRepository;
import com.hao.blog.post.persistence.PostJpaRepository;
import com.hao.blog.testsupport.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.io.IOException;

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
        void saveTwoPostsAndFail() {
            repository.save(
                    new Post(null, "Post A", "Content A")
            );

            repository.save(
                    new Post(null, "Post B", "Content B")
            );

            throw new IllegalStateException("Simulated failure");
        }

        @Transactional
        void saveTwoPostsSuccessfully() {
            repository.save(
                    new Post(null, "Post A", "Content A")
            );

            repository.save(
                    new Post(null, "Post B", "Content B")
            );
        }

        @Transactional
        void saveTwoPostsAndThrowCheckedException() throws IOException {
            repository.save(new Post(null, "Checked A", "Content A"));
            repository.save(new Post(null, "Checked B", "Content B"));

            throw new IOException("Checked failure");
        }

        @Transactional(rollbackFor = IOException.class)
        void saveTwoPostsAndRollbackOnCheckedException() throws IOException {
            repository.save(
                    new Post(null, "Rollback A", "Content A")
            );

            repository.save(
                    new Post(null, "Rollback B", "Content B")
            );

            throw new IOException("Checked failure");
        }
    }

    @Test
    void runtimeExceptionRollsBackAllWrites() {
        long countBefore = repository.count();

        assertThatThrownBy(() -> writer.saveTwoPostsAndFail())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated failure");

        long countAfter = repository.count();

        assertThat(countAfter).isEqualTo(countBefore);
    }

    @Test
    void successfulTransactionCommitsAllWrites() {
        long countBefore = repository.count();

        writer.saveTwoPostsSuccessfully();

        long countAfter = repository.count();

        assertThat(countAfter).isEqualTo(countBefore + 2);
    }

    @Test
    void checkedExceptionDoesNotRollBackByDefault() {
        long countBefore = repository.count();

        assertThatThrownBy(
                () -> writer.saveTwoPostsAndThrowCheckedException()
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
                () -> writer.saveTwoPostsAndRollbackOnCheckedException()
        )
                .isInstanceOf(IOException.class)
                .hasMessage("Checked failure");

        long countAfter = repository.count();

        assertThat(countAfter).isEqualTo(countBefore);
    }
}
