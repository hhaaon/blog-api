package com.hao.blog.post.domain;

public class Post {

    private final Long id;
    private final String title;
    private final String content;

    public Post(Long id, String title, String content) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }

        this.id = id;
        this.title = title;
        this.content = content;
    }

    public Long id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String content() {
        return content;
    }
}
