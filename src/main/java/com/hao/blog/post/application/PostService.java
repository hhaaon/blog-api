package com.hao.blog.post.application;

import com.hao.blog.post.domain.PageResult;
import com.hao.blog.post.domain.Post;
import com.hao.blog.post.domain.PostRepository;
import com.hao.blog.user.domain.UserRole;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final PostCacheService postCacheService;

    public PostService(
            PostRepository postRepository,
            PostCacheService postCacheService
    ) {
        this.postRepository = postRepository;
        this.postCacheService = postCacheService;
    }

    @Transactional
    public Post createPost(String title, String content, Long authorId) {
        Post post = new Post(
                null,
                title,
                content,
                authorId
        );

        return postRepository.save(post);
    }

    @Transactional(readOnly = true)
    public PostView getPost(Long id) {
        return postCacheService.getPost(id);
    }

    public PageResult<Post> findAll(int page, int size) {
        return postRepository.findAll(page, size);
    }

    @Transactional
    public Post updatePost(
            Long id,
            String title,
            String content,
            Long callerId,
            UserRole callerRole
    ) {
        Post existing = postRepository.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));

        if (!existing.authorId().equals(callerId) && callerRole != UserRole.ADMIN) {
            throw new PostAccessDeniedException();
        }

        Post updated = postRepository.update(id, title, content)
                .orElseThrow(() -> new PostNotFoundException(id));

        postCacheService.evictPost(id);

        return updated;
    }
}
