package com.hao.blog.post.api;

import com.hao.blog.post.application.PostNotFoundException;
import com.hao.blog.post.application.PostService;
import com.hao.blog.post.domain.PageResult;
import com.hao.blog.post.domain.Post;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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

    @Test
    void findAll_validRequest_return200() throws Exception {
        // Arrange
        PageResult<Post> result = new PageResult<>(
                List.of(
                        new Post(5L, "Post 5", "Content 5"),
                        new Post(4L, "Post 4", "Content 4")
                ),
                0,
                2,
                5,
                3
        );

        when(postService.findAll(0, 2))
                .thenReturn(result);

        //Act + Assert
        mockMvc.perform(get("/posts")
                .param("page", "0")
                .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].id").value(5))
                .andExpect(jsonPath("$.items[0].title").value("Post 5"))
                .andExpect(jsonPath("$.items[1].id").value(4));

        verify(postService).findAll(0, 2);
    }

    @Test
    void findAll_sizeAboveMaximum_returns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(get("/posts")
                        .param("page", "0")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(postService);
    }

    @Test
    void findAll_withoutPaginationParameters_usesDefaults() throws Exception {
        PageResult<Post> result = new PageResult<>(
                List.of(),
                0,
                20,
                0,
                0
        );

        when(postService.findAll(0, 20))
                .thenReturn(result);

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(postService).findAll(0, 20);
    }

    @Test
    void findAll_pageBelowMinimum_returns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(get("/posts")
                        .param("page", "-1")
                        .param("size", "20"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(postService);
    }
}
