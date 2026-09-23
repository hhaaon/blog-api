package com.hao.blog.post.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostJpaRepository
        extends JpaRepository<PostEntity, Long> {

}
