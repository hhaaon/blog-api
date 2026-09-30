package com.hao.blog.post.api;

import jakarta.validation.constraints.NotBlank;

public record UpdatePostRequest(
        @NotBlank String title,
        @NotBlank String content
) {
}
