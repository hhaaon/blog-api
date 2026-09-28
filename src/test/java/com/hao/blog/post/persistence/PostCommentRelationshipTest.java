package com.hao.blog.post.persistence;

import com.hao.blog.testsupport.PostgresTestConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest(properties = {
        "Spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureTestDatabase(replace = NONE)
@Import(PostgresTestConfiguration.class)
public class PostCommentRelationshipTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PostJpaRepository repository;

    @Test
    void commentReferencesItsPost() {
        //ACT
        PostEntity post = new PostEntity("Title", "Content");
        entityManager.persist(post);

        CommentEntity comment =
                new CommentEntity("First comment", post);

        post.addComment(comment);
        entityManager.persist(comment);

        entityManager.flush();

        Long postId = post.getId();
        Long commentId = comment.getId();

        entityManager.clear();

        CommentEntity foundComment =
                entityManager.find(CommentEntity.class, commentId);

        //Assert
        assertThat(foundComment).isNotNull();
        assertThat(foundComment.getPost().getId()).isEqualTo(postId);
        assertThat(foundComment.getPost().getTitle()).isEqualTo("Title");
    }

    @Test
    void postReferencesItsPersistedComments() {
        PostEntity post = new PostEntity("Title", "Content");
        entityManager.persist(post);

        CommentEntity comment =
                new CommentEntity("First comment", post);

        post.addComment(comment);
        entityManager.persist(comment);

        entityManager.flush();

        Long postId = post.getId();

        entityManager.clear();

        PostEntity foundPost =
                entityManager.find(PostEntity.class, postId);

        assertThat(foundPost).isNotNull();
        assertThat(foundPost.getComments()).hasSize(1);
        assertThat(foundPost.getComments().getFirst().getContent())
                .isEqualTo("First comment");
    }

    @Test
    void removingCommentFromPostDeletesOrphan() {
        PostEntity post = new PostEntity("Title", "Content");
        entityManager.persist(post);

        CommentEntity comment =
                new CommentEntity("Comment to remove", post);

        post.addComment(comment);
        entityManager.persist(comment);

        entityManager.flush();
        entityManager.clear();

        PostEntity foundPost =
                entityManager.find(PostEntity.class, post.getId());

        CommentEntity persistedComment =
                foundPost.getComments().getFirst();

        Long commentId = persistedComment.getId();

        foundPost.removeComment(persistedComment);

        entityManager.flush();
        entityManager.clear();

        CommentEntity deletedComment =
                entityManager.find(CommentEntity.class, commentId);

        assertThat(deletedComment).isNull();
    }

    @Test
    void deletingPostDirectlyInDatabaseDeletesItsComments() {
        PostEntity post = new PostEntity("Title", "Content");
        entityManager.persist(post);

        CommentEntity comment =
                new CommentEntity("Comment", post);

        post.addComment(comment);
        entityManager.persist(comment);

        entityManager.flush();

        Long postId = post.getId();
        Long commentId = comment.getId();

        entityManager.clear();

        int deletedRows = entityManager.createNativeQuery(
                        "DELETE FROM posts WHERE id = :postId"
                )
                .setParameter("postId", postId)
                .executeUpdate();

        entityManager.flush();
        entityManager.clear();

        CommentEntity deletedComment =
                entityManager.find(CommentEntity.class, commentId);

        assertThat(deletedRows).isEqualTo(1);
        assertThat(deletedComment).isNull();
    }

    @Test
    void findAllWithCommentsLoadsCommentsWithoutNPlusOne() {
        // Arrange
        PostEntity post1 = new PostEntity("Post 1", "Content 1");
        entityManager.persist(post1);

        CommentEntity comment1 =
                new CommentEntity("Comment 1", post1);

        post1.addComment(comment1);
        entityManager.persist(comment1);


        PostEntity post2 = new PostEntity("Post 2", "Content 2");
        entityManager.persist(post2);

        CommentEntity comment2 =
                new CommentEntity("Comment 2", post2);

        post2.addComment(comment2);
        entityManager.persist(comment2);


        // This post intentionally has no comments.
        // LEFT JOIN FETCH should still return it.
        PostEntity post3 = new PostEntity("Post 3", "Content 3");
        entityManager.persist(post3);


        entityManager.flush();
        entityManager.clear();


        SessionFactory sessionFactory =
                entityManagerFactory.unwrap(SessionFactory.class);

        Statistics statistics =
                sessionFactory.getStatistics();

        // Ignore SQL used to create the test data.
        statistics.clear();


        // Act
        List<PostEntity> posts =
                repository.findAllWithComments();

        // Access every collection.
        // If they were not fetched by the query,
        // this is where additional SELECTs could happen.
        int totalComments = posts.stream()
                .mapToInt(post -> post.getComments().size())
                .sum();

        long statementCount =
                statistics.getPrepareStatementCount();


        // Assert
        assertThat(posts).hasSize(3);
        assertThat(totalComments).isEqualTo(2);

        assertThat(posts)
                .extracting(PostEntity::getTitle)
                .containsExactlyInAnyOrder(
                        "Post 1",
                        "Post 2",
                        "Post 3"
                );

        assertThat(statementCount).isEqualTo(1);
    }

    @Test
    void findAllThenAccessingCommentsCausesNPlusOne() {
        // Arrange
        PostEntity post1 = new PostEntity("Post 1", "Content 1");
        entityManager.persist(post1);

        CommentEntity comment1 =
                new CommentEntity("Comment 1", post1);

        post1.addComment(comment1);
        entityManager.persist(comment1);


        PostEntity post2 = new PostEntity("Post 2", "Content 2");
        entityManager.persist(post2);

        CommentEntity comment2 =
                new CommentEntity("Comment 2", post2);

        post2.addComment(comment2);
        entityManager.persist(comment2);


        PostEntity post3 = new PostEntity("Post 3", "Content 3");
        entityManager.persist(post3);


        entityManager.flush();
        entityManager.clear();


        Statistics statistics =
                entityManagerFactory
                        .unwrap(SessionFactory.class)
                        .getStatistics();

        statistics.clear();


        // Act
        List<PostEntity> posts =
                repository.findAll();

        int totalComments = posts.stream()
                .mapToInt(post -> post.getComments().size())
                .sum();

        long statementCount =
                statistics.getPrepareStatementCount();


        // Assert
        assertThat(posts).hasSize(3);
        assertThat(totalComments).isEqualTo(2);

        assertThat(statementCount).isEqualTo(4);
    }
}
