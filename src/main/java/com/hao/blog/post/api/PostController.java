package com.hao.blog.post.api;

import com.hao.blog.post.application.PostService;
import com.hao.blog.post.domain.PageResult;
import com.hao.blog.post.domain.Post;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

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

    @GetMapping("/posts/{id}")
    PostResponse getPost(@PathVariable Long id) {
        Post post = postService.getPost(id);

        return new PostResponse(
                post.id(),
                post.title(),
                post.content()
        );
    }

    @GetMapping("/posts")
    public PageResponse<PostResponse> findAll(
            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size
    ) {
        PageResult<Post> result = postService.findAll(page, size);

        List<PostResponse> items = result.items()
                .stream()
                .map(p -> new PostResponse(
                        p.id(),
                        p.title(),
                        p.content()
                ))
                .toList();

        return new PageResponse<>(
                items,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }

    @PutMapping("/posts/{id}")
    public PostResponse updatePost(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePostRequest request
    ) {
        Post updatedPost = postService.updatePost(
                id,
                request.title(),
                request.content()
        );

        return new PostResponse(
                updatedPost.id(),
                updatedPost.title(),
                updatedPost.content()
        );
    }
}
