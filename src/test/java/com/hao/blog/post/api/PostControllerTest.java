package com.hao.blog.post.api;

import com.hao.blog.post.application.PostNotFoundException;
import com.hao.blog.post.application.PostService;
import com.hao.blog.post.domain.Post;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostController.class)
public class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @Test
    void createPost_validRequest_returns201WithCreatedPost() throws Exception {
        //arrange
        Post createdPost =
                new Post(
                        1L,
                        "Learning Spring",
                        "My first post"
                );

        when(postService.createPost(
                "Learning Spring",
                "My first post"
        )).thenReturn(createdPost);

        //act + assert
        mockMvc.perform(
                        post("/posts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                            "title": "Learning Spring",
                                            "content": "My first post"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/posts/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Learning Spring"))
                .andExpect(jsonPath("$.content").value("My first post"));

        verify(postService).createPost(
                "Learning Spring",
                "My first post"
        );
    }

    @Test
    void createPost_blankFields_returns400WithValidationErrors() throws Exception{

        //act + assert
        mockMvc.perform(
            post("/posts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "title": "",
                                "content": ""
                            }
                            """
                    )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.title").value("must not be blank"))
                .andExpect(jsonPath("$.errors.content").value("must not be blank"));

        verifyNoInteractions(postService);
    }

    @Test
    void getPost_existingId_returns200WithPost() throws Exception{
        //Arrange
        Post post = new Post(
                1L,
                "Learning Spring",
                "My first post"
        );

        when(postService.getPost(1L))
                .thenReturn(post);

        //Act + Assert
        mockMvc.perform(
                get("/posts/{id}", 1L)
                        .accept(MediaType.APPLICATION_JSON)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Learning Spring"))
                .andExpect(jsonPath("$.content").value("My first post"));

        verify(postService).getPost(1L);
    }

    @Test
    void getPost_missingId_returns404WithErrorResponse() throws Exception {
        // Arrange
        when(postService.getPost(999L))
                .thenThrow(new PostNotFoundException(999L));

        // Act + Assert
        mockMvc.perform(
                        get("/posts/{id}", 999L)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Post not found: 999"));

        verify(postService).getPost(999L);
    }
}
