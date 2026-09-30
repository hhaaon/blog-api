package com.hao.blog.post.application;

import com.hao.blog.post.domain.Post;
import com.hao.blog.post.domain.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Test
    void createPost_validInput_createsPost() {
        //arrange
        Post savedPost =
                new Post(
                        1L,
                        "Learning Spring",
                        "My first post"
                );

        PostService postService =
                new PostService(postRepository);

        when(postRepository.save(any(Post.class)))
                .thenReturn(savedPost);

        //Act
        Post result = postService.createPost(
                "Learning Spring",
                "My first post"
        );

        //Assert
        ArgumentCaptor<Post> captor =
                ArgumentCaptor.forClass(Post.class);

        verify(postRepository).save(captor.capture());

        Post postPassedToRepository = captor.getValue();

        assertNull(postPassedToRepository.id());
        assertEquals("Learning Spring", postPassedToRepository.title());
        assertEquals("My first post", postPassedToRepository.content());
        assertEquals(1L, result.id());
        assertEquals("Learning Spring", result.title());
        assertEquals("My first post", result.content());
    }

    @Test
    void getPost_existingId_returnsPost() {
        //Arrange
        Post post = new Post(
                1L,
                "Learning Spring",
                "My first post"
        );

        PostService postService = new PostService(postRepository);

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(post));

        //Act
        Post result = postService.getPost(1L);

        //Assert
        assertEquals(1L, result.id());
        assertEquals("Learning Spring", result.title());
        assertEquals("My first post", result.content());

        verify(postRepository).findById(1L);
    }

    @Test
    void getPost_missingId_throwsPostNotFoundException() {
        //Arrange
        PostService postService = new PostService(postRepository);

        //Act
        when(postRepository.findById(999L))
                .thenReturn(Optional.empty());

        //Throw
        assertThrows(
                PostNotFoundException.class,
                () -> postService.getPost(999L)
        );

        verify(postRepository).findById(999L);
    }

    @Test
    void updatePost_existingPost_returnsUpdatedPost() {
        //arrange
        PostService postService = new PostService(postRepository);

        Post updatedPost = new Post(
                1L,
                "Changed",
                "New content"
        );

        when(postRepository.update(
                1L,
                "Changed",
                "New content"
        )).thenReturn(Optional.of(updatedPost));

        //Act
        Post result = postService.updatePost(
                1L,
                "Changed",
                "New content"
        );

        //Assert
        assertEquals("Changed", result.title());
        assertEquals("New content", result.content());

        verify(postRepository).update(
                1L,
                "Changed",
                "New content"
        );
    }

    @Test
    void updatePost_missingPost_throwsPostNotFoundException() {
        //Arrange
        PostService postService = new PostService(postRepository);

        when(postRepository.update(
                999L,
                "Changed",
                "New content"
        )).thenReturn(Optional.empty());

        //Act + Assert
        assertThrows(
                PostNotFoundException.class,
                () -> postService.updatePost(
                        999L,
                        "Changed",
                        "New content"
                )
        );

        verify(postRepository).update(
                999L,
                "Changed",
                "New content"
        );
    }
}
