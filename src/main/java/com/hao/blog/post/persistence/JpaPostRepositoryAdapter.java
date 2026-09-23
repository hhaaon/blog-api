package com.hao.blog.post.persistence;

import com.hao.blog.post.domain.Post;
import com.hao.blog.post.domain.PostRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaPostRepositoryAdapter implements PostRepository {

    private final PostJpaRepository repository;

    public JpaPostRepositoryAdapter(PostJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Post save(Post post) {
        PostEntity postEntity =
                new PostEntity(
                        post.title(),
                        post.content()
                );

        PostEntity result = repository.save(postEntity);

        return new Post(
                result.getId(),
                result.getTitle(),
                result.getContent()
        );
    }

    @Override
    public Optional<Post> findById(Long id) {
        return repository.findById(id)
                .map(postEntity -> new Post(
                        postEntity.getId(),
                        postEntity.getTitle(),
                        postEntity.getContent()
                        )
                );
    }
}
