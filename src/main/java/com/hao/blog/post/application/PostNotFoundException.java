package com.hao.blog.post.application;

public class PostNotFoundException extends RuntimeException{

    public PostNotFoundException(Long id) {
        super("Post not found: " + id);
    }
}
