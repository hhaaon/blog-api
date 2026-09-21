package com.hao.blog.post.application;

import com.hao.blog.post.domain.Post;
import com.hao.blog.post.domain.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
