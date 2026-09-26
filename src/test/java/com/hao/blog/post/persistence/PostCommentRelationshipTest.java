package com.hao.blog.post.persistence;

import com.hao.blog.testsupport.PostgresTestConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(PostgresTestConfiguration.class)
public class PostCommentRelationshipTest {

    @Autowired
    private EntityManager entityManager;

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
}
