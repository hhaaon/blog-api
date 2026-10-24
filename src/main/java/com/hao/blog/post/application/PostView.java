package com.hao.blog.post.application;

public record PostView(
        Long id,
        String title,
        String content,
        Long authorId
) {}
