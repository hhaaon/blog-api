package com.hao.blog.post.application;

import com.hao.blog.post.domain.Post;
import com.hao.blog.post.domain.PostRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class PostCacheService {

    private final PostRepository postRepository;

    public PostCacheService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Cacheable(cacheNames = "posts", key = "#id")
    public PostView getPost(Long id) {
        Post post =
                postRepository.findById(id)
                        .orElseThrow(() -> new PostNotFoundException(id));

        return toView(post);
    }

    @CacheEvict(cacheNames = "posts", key = "#id")
    public void evictPost(Long id) {
    }

    private PostView toView(Post post) {
        return new PostView(
                post.id(),
                post.title(),
                post.content(),
                post.authorId()
        );
    }
}
