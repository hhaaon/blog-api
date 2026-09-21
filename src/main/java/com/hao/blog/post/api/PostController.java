package com.hao.blog.post.api;

import com.hao.blog.post.application.PostService;
import com.hao.blog.post.domain.Post;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class PostController {

    private final PostService postService;

    public PostController(
            PostService postService
    ) {
        this.postService = postService;
    }

    @PostMapping("/posts")
    public ResponseEntity<PostResponse> create(
            @Valid @RequestBody CreatePostRequest request
    ) {
        Post createdPost = postService.createPost(
                request.title(),
                request.content()
        );

        PostResponse response = new PostResponse(
                createdPost.id(),
                createdPost.title(),
                createdPost.content()
        );

        URI location = URI.create("/posts/" + createdPost.id());

        return ResponseEntity.
                created(location).
                body(response);
    }
}
