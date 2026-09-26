package com.hao.blog.post.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "comments")
public class CommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private PostEntity post;

    protected CommentEntity() {
    }

    public CommentEntity(String content, PostEntity post) {
        this.content = content;
        this.post = post;
    }

    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public PostEntity getPost() {
        return post;
    }

    void setPost(PostEntity post) {
        this.post = post;
    }
}
