package com.hao.blog.post.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PostJpaRepository
        extends JpaRepository<PostEntity, Long> {
    @Query("""
            select distinct p
            from PostEntity p
            left join fetch p.comments
    """)
    List<PostEntity> findAllWithComments();
}
