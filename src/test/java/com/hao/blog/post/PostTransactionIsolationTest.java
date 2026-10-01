package com.hao.blog.post;

import com.hao.blog.post.persistence.PostEntity;
import com.hao.blog.post.persistence.PostJpaRepository;
import com.hao.blog.testsupport.PostgresTestConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class PostTransactionIsolationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PostJpaRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void readCommitted_secondReadAfterConcurrentCommit_returnsChangedValue()
            throws Exception {
        //Arrange
        CountDownLatch aHasRead =
                new CountDownLatch(1);

        CountDownLatch bHasCommitted =
                new CountDownLatch(1);

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.setIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        PostEntity saved = repository.save(
                new PostEntity("Original", "Content")
        );

        Long postId = saved.getId();

        Future<List<String>> transactionA = executor.submit(() ->
                transactionTemplate.execute(status -> {
                    PostEntity post =
                            entityManager.find(PostEntity.class, postId);

                    String firstRead = post.getTitle();

                    aHasRead.countDown();

                    await(bHasCommitted);

                    entityManager.refresh(post);

                    String secondRead = post.getTitle();

                    return List.of(firstRead, secondRead);
                })
        );

        Future<?> transactionB = executor.submit(() -> {
            await(aHasRead);

            transactionTemplate.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                update posts
                set title = :title
                where id = :id
                """)
                        .setParameter("title", "Changed")
                        .setParameter("id", postId)
                        .executeUpdate();
            });

            bHasCommitted.countDown();
        });

        //Act + Assert
        try {
            List<String> observedTitles = transactionA.get();
            transactionB.get();

            assertThat(observedTitles.get(0))
                    .isEqualTo("Original");

            assertThat(observedTitles.get(1))
                    .isEqualTo("Changed");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void repeatableRead_secondReadAfterConcurrentCommit_returnsOriginalValue()
            throws Exception {
        // Arrange
        CountDownLatch aHasRead =
                new CountDownLatch(1);

        CountDownLatch bHasCommitted =
                new CountDownLatch(1);

        TransactionTemplate transactionATemplate =
                new TransactionTemplate(transactionManager);

        transactionATemplate.setIsolationLevel(
                TransactionDefinition.ISOLATION_REPEATABLE_READ
        );

        TransactionTemplate transactionBTemplate =
                new TransactionTemplate(transactionManager);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        PostEntity saved = repository.save(
                new PostEntity("Original", "Content")
        );

        Long postId = saved.getId();

        Future<List<String>> transactionA = executor.submit(() ->
                transactionATemplate.execute(status -> {
                    PostEntity post =
                            entityManager.find(PostEntity.class, postId);

                    String firstRead = post.getTitle();

                    aHasRead.countDown();

                    await(bHasCommitted);

                    entityManager.refresh(post);

                    String secondRead = post.getTitle();

                    return List.of(firstRead, secondRead);
                })
        );

        Future<?> transactionB = executor.submit(() -> {
            await(aHasRead);

            transactionBTemplate.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                    update posts
                    set title = :title
                    where id = :id
                    """)
                        .setParameter("title", "Changed")
                        .setParameter("id", postId)
                        .executeUpdate();
            });

            // B's transaction has committed before this signal.
            bHasCommitted.countDown();
        });

        // Act + Assert
        try {
            List<String> observedTitles = transactionA.get();
            transactionB.get();

            assertThat(observedTitles.get(0))
                    .isEqualTo("Original");

            assertThat(observedTitles.get(1))
                    .isEqualTo("Original");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            boolean completed =
                    latch.await(5, TimeUnit.SECONDS);

            if (!completed) {
                throw new IllegalStateException(
                        "Timed out waiting for concurrent transaction"
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
