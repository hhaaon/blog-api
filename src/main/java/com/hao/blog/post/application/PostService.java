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

    public PostService(
            PostRepository postRepository
    ) {
        this.postRepository = postRepository;
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

    public Post getPost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));
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

        return postRepository.update(id, title, content)
                .orElseThrow(() -> new PostNotFoundException(id));
    }
}
