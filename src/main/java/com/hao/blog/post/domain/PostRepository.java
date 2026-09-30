package com.hao.blog.post.domain;

import java.util.Optional;

public interface PostRepository {

    Post save(Post post);

    Optional<Post> findById(Long id);

    PageResult<Post> findAll(int page, int size);

    Optional<Post> update(
      Long id,
      String title,
      String content
    );
}
