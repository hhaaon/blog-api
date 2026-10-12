package com.hao.blog.post.application;

import com.hao.blog.post.domain.Post;
import com.hao.blog.post.domain.PostRepository;
import com.hao.blog.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final Long AUTHOR_ID = 42L;
    private static final Long OWNER_ID = 17L;
    private static final Long OTHER_USER_ID = 99L;

    @Mock
    private PostRepository postRepository;

    @Test
    void createPost_validInput_createsPostWithAuthenticatedAuthor() {
        // Arrange
        Post savedPost = new Post(
                1L,
                "Learning Spring",
                "My first post",
                AUTHOR_ID
        );

        PostService postService = new PostService(postRepository);

        when(postRepository.save(any(Post.class)))
                .thenReturn(savedPost);

        // Act
        Post result = postService.createPost(
                "Learning Spring",
                "My first post",
                AUTHOR_ID
        );

        // Assert
        ArgumentCaptor<Post> captor =
                ArgumentCaptor.forClass(Post.class);

        verify(postRepository).save(captor.capture());

        Post postPassedToRepository = captor.getValue();

        assertNull(postPassedToRepository.id());
        assertEquals(
                "Learning Spring",
                postPassedToRepository.title()
        );
        assertEquals(
                "My first post",
                postPassedToRepository.content()
        );
        assertEquals(
                AUTHOR_ID,
                postPassedToRepository.authorId()
        );

        assertEquals(1L, result.id());
        assertEquals("Learning Spring", result.title());
        assertEquals("My first post", result.content());
        assertEquals(AUTHOR_ID, result.authorId());
    }

    @Test
    void getPost_existingId_returnsPost() {
        // Arrange
        Post post = new Post(
                1L,
                "Learning Spring",
                "My first post",
                AUTHOR_ID
        );

        PostService postService =
                new PostService(postRepository);

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(post));

        // Act
        Post result = postService.getPost(1L);

        // Assert
        assertEquals(1L, result.id());
        assertEquals("Learning Spring", result.title());
        assertEquals("My first post", result.content());
        assertEquals(AUTHOR_ID, result.authorId());

        verify(postRepository).findById(1L);
    }

    @Test
    void getPost_missingId_throwsPostNotFoundException() {
        // Arrange
        PostService postService =
                new PostService(postRepository);

        when(postRepository.findById(999L))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                PostNotFoundException.class,
                () -> postService.getPost(999L)
        );

        verify(postRepository).findById(999L);
    }

    @Test
    void updatePost_ownerUser_returnsUpdatedPost() {
        // Arrange
        PostService postService =
                new PostService(postRepository);

        Post existingPost = new Post(
                1L,
                "Original",
                "Original content",
                OWNER_ID
        );

        Post updatedPost = new Post(
                1L,
                "Changed",
                "New content",
                OWNER_ID
        );

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(existingPost));

        when(postRepository.update(
                1L,
                "Changed",
                "New content"
        )).thenReturn(Optional.of(updatedPost));

        // Act
        Post result = postService.updatePost(
                1L,
                "Changed",
                "New content",
                OWNER_ID,
                UserRole.USER
        );

        // Assert
        assertEquals(1L, result.id());
        assertEquals("Changed", result.title());
        assertEquals("New content", result.content());
        assertEquals(OWNER_ID, result.authorId());

        verify(postRepository).findById(1L);

        verify(postRepository).update(
                1L,
                "Changed",
                "New content"
        );
    }

    @Test
    void updatePost_missingPost_throwsPostNotFoundException() {
        // Arrange
        PostService postService =
                new PostService(postRepository);

        when(postRepository.findById(999L))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                PostNotFoundException.class,
                () -> postService.updatePost(
                        999L,
                        "Changed",
                        "New content",
                        AUTHOR_ID,
                        UserRole.USER
                )
        );

        verify(postRepository).findById(999L);

        verify(postRepository, never())
                .update(
                        anyLong(),
                        anyString(),
                        anyString()
                );
    }

    @Test
    void updatePost_nonOwnerUser_throwsPostAccessDeniedException() {
        // Arrange
        PostService postService =
                new PostService(postRepository);

        Post existingPost = new Post(
                1L,
                "Original",
                "Content",
                OWNER_ID
        );

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(existingPost));

        // Act + Assert
        assertThrows(
                PostAccessDeniedException.class,
                () -> postService.updatePost(
                        1L,
                        "Changed",
                        "New content",
                        OTHER_USER_ID,
                        UserRole.USER
                )
        );

        verify(postRepository).findById(1L);

        verify(postRepository, never())
                .update(
                        anyLong(),
                        anyString(),
                        anyString()
                );
    }

    @Test
    void updatePost_nonOwnerAdmin_updatesPost() {

        //Arrange
        PostService postService =
                new PostService(postRepository);

        Post existingPost = new Post(
                1L,
                "Original title",
                "Original content",
                OWNER_ID
        );

        Post updatedPost = new Post(
                1L,
                "Admin changed title",
                "Admin changed content",
                OWNER_ID
        );

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(existingPost));

        when(postRepository.update(
                1L,
                "Admin changed title",
                "Admin changed content"
        )).thenReturn(Optional.of(updatedPost));


        //Act
        Post result = postService.updatePost(
                1L,
                "Admin changed title",
                "Admin changed content",
                OTHER_USER_ID,
                UserRole.ADMIN
        );

        //Assert
        assertEquals(
                "Admin changed title",
                result.title()
        );

        assertEquals(
                "Admin changed content",
                result.content()
        );

        assertEquals(
                OWNER_ID,
                result.authorId()
        );

        verify(postRepository)
                .update(
                        1L,
                        "Admin changed title",
                        "Admin changed content"
                );
    }
}