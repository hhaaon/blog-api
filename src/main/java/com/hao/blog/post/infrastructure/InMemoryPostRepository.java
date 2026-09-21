package com.hao.blog.post.infrastructure;

import com.hao.blog.post.domain.Post;
import com.hao.blog.post.domain.PostRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class InMemoryPostRepository implements PostRepository {

    private final Map<Long, Post> posts = new HashMap<>();
    private long idCounter = 1L;

    @Override
    public Post save(Post post) {
        Long id = idCounter++;

        Post savedPost = new Post(
                id,
                post.title(),
                post.content()
        );

        posts.put(
                id,
                savedPost
        );

        return savedPost;
    }

    @Override
    public Optional<Post> findById(Long id) {
        return Optional.ofNullable(posts.get(id));
    }
}
