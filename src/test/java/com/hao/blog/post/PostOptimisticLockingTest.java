package com.hao.blog.post;

import com.hao.blog.post.application.PostService;
import com.hao.blog.post.persistence.PostEntity;
import com.hao.blog.post.persistence.PostJpaRepository;
import com.hao.blog.testsupport.PostgresTestConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.dao.OptimisticLockingFailureException;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
public class PostOptimisticLockingTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostJpaRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void updatePost_existingPost_incrementsVersion() {
        //Arrange
        PostEntity saved = repository.save(
                new PostEntity("Original", "Content")
        );

        Long postId = saved.getId();

        assertThat(saved.getVersion()).isZero();

        //Act
        postService.updatePost(
                postId,
                "Changed",
                "New content"
        );

        //Assert
        PostEntity persisted = repository.findById(postId)
                .orElseThrow();

        assertThat(persisted.getVersion()).isEqualTo(1L);
        assertThat(persisted.getTitle()).isEqualTo("Changed");
    }

    @Test
    void optimisticLocking_staleConcurrentUpdate_rejectsSecondWriter()
            throws Exception {
        //Arrange
        CountDownLatch bHasRead = new CountDownLatch(1);
        CountDownLatch aHasCommitted = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        Long postId = repository.save(
                new PostEntity(
                        "Original",
                        "Content"
                )
        )
                .getId();

        Future<?> transactionA = executor.submit(() -> {
            TransactionTemplate template =
                    new TransactionTemplate(transactionManager);

            template.executeWithoutResult(status -> {
                PostEntity post =
                        entityManager.find(PostEntity.class, postId);

                await(bHasRead);

                post.update("A edit", "A content");
            });

            // executeWithoutResult has returned, so commit completed
            aHasCommitted.countDown();
        });

        Future<?> transactionB = executor.submit(() -> {
            TransactionTemplate template =
                    new TransactionTemplate(transactionManager);

            template.executeWithoutResult(status -> {
                PostEntity post =
                        entityManager.find(PostEntity.class, postId);

                bHasRead.countDown();

                await(aHasCommitted);

                post.update("B edit", "B content");
            });
        });

        //Act + Assert
        try {
            transactionA.get();

            ExecutionException exception =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            ExecutionException.class,
                            transactionB::get
                    );

            assertThat(exception.getCause())
                    .isInstanceOf(OptimisticLockingFailureException.class);

            PostEntity persisted = repository.findById(postId)
                    .orElseThrow();

            assertThat(persisted.getTitle())
                    .isEqualTo("A edit");

            assertThat(persisted.getContent())
                    .isEqualTo("A content");

            assertThat(persisted.getVersion())
                    .isEqualTo(1L);
        }
        finally {
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
