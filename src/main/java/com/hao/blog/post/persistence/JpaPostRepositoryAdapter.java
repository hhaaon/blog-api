package com.hao.blog.post.persistence;

import com.hao.blog.post.domain.PageResult;
import com.hao.blog.post.domain.Post;
import com.hao.blog.post.domain.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
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
                .map(this::toDomain);
    }

    @Override
    public PageResult<Post> findAll(int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id")
        );

        Page<PostEntity> result =
                repository.findAll(pageRequest);

        List<Post> items = result.getContent()
                .stream()
                .map(this::toDomain)
                .toList();

        return new PageResult<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Override
    public Optional<Post> update(
            Long id,
            String title,
            String content
    ) {
        return repository.findById(id)
                .map(entity -> {
                    entity.update(title, content);

                    return toDomain(entity);
                });
    }

    private Post toDomain(PostEntity entity) {
        return new Post(
                entity.getId(),
                entity.getTitle(),
                entity.getContent()
        );
    }
}
