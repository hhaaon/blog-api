package com.hao.blog.post.api;

public record PostResponse(
        Long id,
        String title,
        String content
) {
}
